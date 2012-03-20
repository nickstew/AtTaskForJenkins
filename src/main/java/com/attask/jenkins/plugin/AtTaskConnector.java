package com.attask.jenkins.plugin;

import com.attask.sdk.api.API;
import com.attask.sdk.api.connectors.rest.RESTConnector;
import com.attask.sdk.enums.AssignmentStatus;
import com.attask.sdk.enums.Condition;
import com.attask.sdk.enums.ProjectConditionType;
import com.attask.sdk.model.*;
import com.attask.sdk.services.*;

import java.io.PrintStream;
import java.util.*;

/**
 * User: brentlee
 * Date: 1/30/12
 * Time: 4:17 PM
 */
public class AtTaskConnector implements RemoteConnector {

	public static PrintStream logger;
	public static final List<String> ISSUEFIELDS = Arrays.asList("projectID", "name", "lastNote", "assignedToID", "status", "assignments:status", "condition");
	public static final List<String> USERFIELDS = Arrays.asList("homeGroupID", "name", "username");
	public static final List<String> PROJECTFIELDS = Arrays.asList("condition", "name");
	private API sdkAPI;

	private int sessionTimeout = 180000; // 3 minutes
	private Long creationTime;
	private ProjectService projectService;
	private OpTaskService opTaskService;
    private NoteService noteService;
    private UserService userService;
    private CustomEnumService customEnumService;
	private GroupService groupService;

    private static final String ATTASK_API_PATH = "/attask/api-internal/";
	private Map<String, User> userCache = new HashMap<String, User>();
	private ExpiringMap<String, OpTask> issueCache;
	private ExpiringMap<String, Project> projectCache;
	private String username;
	private String password;

	public AtTaskConnector(String username, String password, String url, PrintStream logger) throws Exception {
		AtTaskConnector.logger = logger;
        sdkAPI = API.create(new RESTConnector(url, ATTASK_API_PATH, null));
		this.username = username;
		this.password = password;
		this.creationTime = Calendar.getInstance().getTimeInMillis();

		issueCache = new ExpiringMap<String, OpTask>(15, 5);
		projectCache = new ExpiringMap<String, Project>(30, 10);

		issueCache.getExpirer().startExpiringIfNotStarted();
		projectCache.getExpirer().startExpiringIfNotStarted();
	}

    private void login(){
        Thread r = new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(15000);
                    throw new RuntimeException("Connection.");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        r.start();
        sdkAPI.login(username, password);
        r.stop();
    }

	private boolean isLoggedIn() {
		return Calendar.getInstance().getTimeInMillis() - creationTime < sessionTimeout && sdkAPI.getSession() != null;
	}

	public void loginAsUser(String username) {
		if (isLoggedIn())
			sdkAPI.loginAsUser(username);
	}

    public Group getGroup(String groupName, String username){

        Map<String, Object> filters = new HashMap<String, Object>();
        filters.put("name", groupName);
        List<Group> groupList = getGroupService().search(filters);

		if (groupList.size() == 0) {
			User user = getUser(username, false);
			filters.clear();
			filters.put("ID", user.getHomeGroupID());
		}

		groupList = getGroupService().search(filters, Arrays.asList("name"));


        return groupList.get(0);
    }

	public String getResolvedStatus() {
		return getEquivalentStatus("RLV");
	}

	/**
     *
     * @param status
     * @return equivalent status if one is found. If no equivalent is found RLV is returned
     */
    public String getEquivalentStatus(String status){
        String equivStatus = status;
        Map<String, Object> filters = new HashMap<String, Object>();
        filters.put("equatesWith", status);
        filters.put("isPrimary", true);
        List<CustomEnum> customEnums = getCustomEnumService().search(filters);
        if(customEnums.size() > 0){
            //equivStatus = customEnums.get(0).getValueAsString();// get("valueAsString");
        }
        return equivStatus;
    }

	/**
     *
     * @param name is the name of the project
     * @return returns the first project found that matches the search name
     */
    public Project getProject(String name){
        Map<String, Object> filters = new HashMap<String, Object>();
        filters.put("name", name);
        List<Project> projectList = getProjectService().search(filters, PROJECTFIELDS);
        //we need to verify that project names are unique otherwise it's possible that we will get more than
        //one project back from a search
		if (projectList.size() == 0)
			return null;

		projectCache.put(name, projectList.get(0));

        return projectCache.get(name);
    }

    /**
     * Creates Project that DOESN'T exist in AtTaskConnector.
     * @param name is the name of the Project.
     */
    public Project addProject(String name, Group group){
        Project project = new Project();
        project.setName(name);
		project.setConditionType(ProjectConditionType.MANUAL);
        project.setCondition(JobConditionEnum.Stable.getAtTaskProjectCondition());
        project.setGroup(group);
        project.setGroupID(group.getID());
        return getProjectService().add(project, Arrays.asList("condition", "name"));
    }

    /**
     * Updates the condition of an existing project, and adds a note.
     * @param condition is the new status the Project's condition will be changed to.
     */
    public Project updateProject(Project currentProject, JobConditionEnum condition, String buildLink){
		currentProject.setURL(buildLink);
		currentProject.setConditionType(ProjectConditionType.MANUAL);
		currentProject.setCondition(condition.getAtTaskProjectCondition());
        return getProjectService().edit(currentProject, PROJECTFIELDS);
    }

	public List<User> getUsers(String groupName) {
		Map<String, Object> filters = new HashMap<String, Object>();
        filters.put("homeGroup:name", groupName);
		filters.put("isActive",true);
		filters.put("$$LIMIT",500);

		return getUserService().search(filters, USERFIELDS);
	}

	public User getUserByID(String userID) {
		return getUserService().get(userID, USERFIELDS);
	}

	/**
	 *
	 * @param username
	 * @return returns The users ID that has an email address that matches the username.
	 * Returns null if no User is found.
	 *
	 */
	public User getUser(String username, boolean strict){
		if (userCache.containsKey(username))
			return userCache.get(username);

		User user;
		Map<String, Object> filters = new HashMap<String, Object>();

		if (strict) {
			filters.put("name", username);
		} else {
			filters.put("username", username);
			filters.put("OR:a:ssoUsername", username);
			filters.put("OR:b:emailAddr", username);
			filters.put("OR:b:emailAddr_Mod","cicontains");
			filters.put("OR:c:name", username);
		}

		List<User> users = getUserService().search(filters, USERFIELDS);
		if(users.size() == 0){
			user = null;
		}else{
			user = users.get(0);
			userCache.put(username, user);
		}

		return user;
	}

    public Note addNote(String message, String objID, String objCode, List<User> committers){
        Note note = new Note();
        List<NoteTag> noteTags = createNoteTags(committers);

        note.setTags(noteTags);
        note.setNoteText(message);
        note.setAttachObjID(objID);
		note.setAttachObjCode(objCode);
		note.setObjID(objID);
		note.setNoteObjCode(objCode);
		note.setTopNoteObjCode(Project.OBJCODE);
		note.setIsPrivate(true);

		try {
			return getNoteService().add(note, Arrays.asList("noteText"));
		} catch (Exception e) {
			e.printStackTrace(logger);
		}

		return null;
    }

	/**
     * Creates a notetag when an email address cannot be found for a user
     */
    private List<NoteTag> createNoteTags(List<User> users){
        List<NoteTag> noteTags = new ArrayList<NoteTag>();
        if(users != null){
            for(User u : users){
                NoteTag tag = new NoteTag();
				tag.setObjObjCode("USER");
				tag.setObjID(u.getID());
                noteTags.add(tag);
            }
        }
        return noteTags;
    }

    /**
     * Creates an Issue linked to a Project that DOESN'T exist in AtTaskConnector.
     * @param proj is the ID of the project this Issue is located inside.
     * @param name is the name of the Test using the format: TestClass#testMethod()
     *             Contents include: //TODO list data to be inserted into the new note
     *             Requested format: //TODO give example of format for note
     */
    public OpTask addIssue(String name, Project proj) {
        OpTask issue = new OpTask();
        issue.setProjectID(proj.getID());
        issue.setProject(proj);
        issue.setName(name);
		issue.setStatus(OpTaskStatusEnum.NEW.getValue());
        issueCache.put(issue.getName(), getOpTaskService().add(issue, ISSUEFIELDS));
		return issueCache.get(issue.getName());
    }

	public OpTask assignIssue(OpTask issue, User user){
		issue.setAssignedToID(user.getID());
		issue.setAssignedTo(user);
        issueCache.put(issue.getName(), getOpTaskService().edit(issue, ISSUEFIELDS));
		return issueCache.get(issue.getName());
    }

	public OpTask unassignIssue(OpTask issue){
		issue.setAssignedToID("");
		issue.setAssignedTo(null);
		issue.setAssignments(null);

        issueCache.put(issue.getName(), getOpTaskService().edit(issue, ISSUEFIELDS));
		return issueCache.get(issue.getName());
    }

	/**
	 *
	 * @param status is the current status of the Test.
     * @param buildLink is a link to the Jenkins build.
     * @param assignedToID id of the user to be assigned to the issue
	 */
    public OpTask updateIssue(OpTask issue, String status, Condition condition, String assignedToID, String description, String buildLink){
        if(description.length() >4000){
            description = description.substring(0,3999);
        }
        issue.setDescription(description);
		issue.setStatus(status);

		issue.setCondition(condition);
        issue.setUrl(buildLink);
		issue.setAssignedToID(assignedToID);

		if (assignedToID == null || assignedToID.length() == 0) {
			issue.setAssignedTo(null);
			issue.setAssignments(null);
		}

        issueCache.put(issue.getName(), getOpTaskService().edit(issue, ISSUEFIELDS));
		return issueCache.get(issue.getName());
    }

	public OpTask getIssue(String name, Project project) {
		if (issueCache.containsKey(name))
			return issueCache.get(name);

		List<OpTask> issues;

		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put("name",name);

		if (project != null)
			filters.put("projectID", project.getID());

		issues = getOpTaskService().search(filters, ISSUEFIELDS);

		if ((project == null && issues.size() != 1) || issues.size() == 0)
			return null;

		issueCache.put(name, issues.get(0));
		return issueCache.get(name);
	}

    /**
     *
     * @param projID is the ID of the Project that holds the issues.
     * @return List of OpTasks
     */
    public List<OpTask> getIssues(String projID){
        Map<String, Object> filters = new HashMap<String, Object>();
        filters.put("projectID",projID);
		filters.put("$$LIMIT", 2000);
        return getOpTaskService().search(filters, ISSUEFIELDS);
    }

	public boolean isIssueAcknowledged(OpTask issue) {
		return issue.getAssignments() != null && issue.getAssignments().size() > 0 && issue.getAssignments().get(0).getStatus() != AssignmentStatus.AWAITING_ACCEPTANCE;
	}

    private ProjectService getProjectService() {
        if(!isLoggedIn()) login();
        if(projectService == null){
            projectService = (ProjectService)sdkAPI.getAPIService(Project.OBJCODE);
        }
        return projectService;
    }

    private OpTaskService getOpTaskService() {
         if(!isLoggedIn()) login();
        if(opTaskService == null) {
            opTaskService = (OpTaskService)sdkAPI.getAPIService(OpTask.OBJCODE);
        }
        return opTaskService;
    }

    private NoteService getNoteService() {
         if(!isLoggedIn()) login();
        if(noteService == null) {
            noteService = (NoteService)sdkAPI.getAPIService(Note.OBJCODE);
        }
        return noteService;
    }

    private UserService getUserService() {
         if(!isLoggedIn()) login();
        if(userService == null) {
            userService = (UserService)sdkAPI.getAPIService(User.OBJCODE);
        }
        return userService;
    }

    private CustomEnumService getCustomEnumService() {
         if(!isLoggedIn()) login();
        if(customEnumService == null) {
            customEnumService = (CustomEnumService)sdkAPI.getAPIService(CustomEnum.OBJCODE);
        }
        return customEnumService;
    }

    private GroupService getGroupService() {
         if(!isLoggedIn()) login();
        if(groupService == null) {
            groupService = (GroupService)sdkAPI.getAPIService(Group.OBJCODE);
        }
        return groupService;
    }
}