package com.attask.jenkins.plugin;

import com.attask.sdk.api.API;
import com.attask.sdk.api.APIException;
import com.attask.sdk.model.Group;
import com.attask.sdk.model.Note;
import com.attask.sdk.model.OpTask;
import com.attask.sdk.model.Project;
import com.attask.sdk.services.GroupService;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.model.User;
import hudson.scm.ChangeLogSet;
import hudson.tasks.*;
import hudson.tasks.junit.CaseResult;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Brent Lee & Jen Rumbaugh
 */
public class AtTaskForJenkins extends Notifier {

    public static PrintStream logger;
    public static final Logger LOG = Logger.getLogger(AtTaskForJenkins.class.getName());

    private transient RemoteConnector connector;
	private transient ConversionUtil conversionUtil;

	private boolean notifyCommitters;
	private boolean notifyCulprits;
	private boolean assignCommitters;
	private String customProjectName;
	private String extraCommitters;
	private String maxAssignments;
	private String maxTests;
	private boolean overrideMaxValues;


	@DataBoundConstructor
    public AtTaskForJenkins(boolean assignCommitters,
							boolean notifyCommitters,
							boolean notifyCulprits,
							String customProjectName,
							String extraCommitters,
							boolean overrideMaxValues,
							String maxAssignments,
							String maxTests) {
		this.assignCommitters = assignCommitters;
		this.notifyCommitters = notifyCommitters;
		this.notifyCulprits = notifyCulprits;
		this.customProjectName = customProjectName;
		this.extraCommitters = extraCommitters;
		this.overrideMaxValues = overrideMaxValues;
		this.maxAssignments = maxAssignments;
		this.maxTests = maxTests;
    }

	public final boolean getOverrideMaxValues() {
		return this.overrideMaxValues;
	}

	public final String getMaxAssignments() {
		return this.maxAssignments;
	}

	public final String getMaxTests() {
		return this.maxTests;
	}

	public final String getExtraCommitters() {
		return this.extraCommitters;
	}

	public final String getCustomProjectName() {
		return this.customProjectName;
	}

	public final boolean getAssignCommitters() {
		return this.assignCommitters;
	}

	public final boolean getNotifyCommitters() {
		return this.notifyCommitters;
	}

	public final boolean getNotifyCulprits() {
		return this.notifyCulprits;
	}
	
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        logger = listener.getLogger();
        String username = getDescriptor().getUsername();
        String password = getDescriptor().getPassword();
        String host = getDescriptor().getUrl();

        LOG.info("[AtTask] Connecting to AtTask.");
        logger.println("[AtTask] Connecting to AtTask.");
        try{
            connector = DependencyFactory.getConnector(host, username, password, listener.getLogger());

			// Find the group in AtTask.
			String groupName = getDescriptor().getGroupName();
			Group group = connector.getGroup(groupName, username);

			// Gather variables
			String projectName = getConversionUtil().createProjectName(build, getDescriptor());

			String buildResult = build.getResult().toString();
			JobConditionEnum projectCondition = getConversionUtil().convertBuildResult(buildResult);
			int buildNumber = build.getNumber();
			String buildLink = getConversionUtil().createBuildURL(Hudson.getInstance().getRootUrl(), build.getUrl());

			//Gather culprits and find them in AtTask
			List<CaseResult> failedTests = build.getTestResultAction() != null ? build.getTestResultAction().getFailedTests() : new ArrayList<CaseResult>();
			List<com.attask.sdk.model.User> culprits = getNotifyCulprits() ? findCulprits(build.getCulprits()) : null;
			List<com.attask.sdk.model.User> committers = findCommitters(build);
			String changeSet = getChangeSetMessage(build);

			//Update Project
			Project currentProject = synchronizeProject(build, projectName, projectCondition, buildNumber, buildLink, changeSet, group, committers, culprits);

			//Update issues
			List<OpTask> openIssues = connector.getIssues(currentProject.getID());

			boolean newerBuildExists = checkBuildRecordIntegrity(openIssues, buildNumber);

			if (newerBuildExists) {
				LOG.warning("[AtTask] A newer build has already ran and has been recorded. No updates will be made.");
				logger.println("[AtTask] A newer build has already ran and has been recorded. No updates will be made.");
				return true;
			}

			String assignedToID = generateAssignedTo(committers);

			// We want the duplicates in when we generate the assigned To (most committed == most likely to be assigned),
			// But we want to remove duplicates before pushing to the server.
			committers = filterDuplicateCommitters(committers);

			// If the build failed or was aborted, there are no recorded test results, in which case we want to skip test sync step.
			if (projectCondition == JobConditionEnum.Stable || projectCondition == JobConditionEnum.Unstable) {
				synchronizeFailedTests(currentProject, buildNumber, buildLink, failedTests, openIssues, assignedToID, changeSet, committers, culprits);
				List<OpTask> remainingIssues = extractRemainingIssues(failedTests, openIssues);
				resolvePassingTests(buildNumber, buildLink, remainingIssues, changeSet, committers, culprits);
			}

			LOG.info("[AtTask] Synchronization complete");
			logger.println("[AtTask] Synchronization complete");
		}
        catch(Exception e){
            LOG.severe("[AtTask] Error while trying to sync with AtTask.");
            logger.println("[AtTask] Error while trying to sync with AtTask.");
			e.printStackTrace(logger);
        }

        return true;
    }

	private List<com.attask.sdk.model.User> filterDuplicateCommitters(List<com.attask.sdk.model.User> committers) {
		Map<String, com.attask.sdk.model.User> users = new HashMap<String, com.attask.sdk.model.User>();

		for (com.attask.sdk.model.User committer : committers) {
			users.put(committer.getName(), committer);
		}

		return new ArrayList<com.attask.sdk.model.User>(users.values());
	}

	private boolean checkBuildRecordIntegrity(List<OpTask> issues, int buildNumber) {
        boolean newerBuildExists = false;
        for(OpTask issue : issues){
			Note lastNote = issue.getLastNote();

			if (lastNote == null)
				return newerBuildExists;

			String noteText = lastNote.getNoteText();
			Pattern buildNumPattern = Pattern.compile("#(\\d+)");
			Matcher buildNumMatches = buildNumPattern.matcher(noteText);
			int recordedNum;
			if(buildNumMatches.find()){
				recordedNum = Integer.parseInt(buildNumMatches.group(1));
			}else{
				LOG.info("[AtTask] No recorded build number was found");
				logger.println("[AtTask] No recorded build number was found");
				recordedNum = -1;
			}
			if(recordedNum != -1 && buildNumber < recordedNum){
				newerBuildExists = true;
			}
        }
        return newerBuildExists;
    }

    private Project synchronizeProject(AbstractBuild<?,?> build, String projectName, JobConditionEnum projectCondition, int buildNumber, String buildLink, String changeSet, Group group, List<com.attask.sdk.model.User> committers, List<com.attask.sdk.model.User> culprits) {
        Project currentProject = connector.getProject(projectName);

		if(currentProject == null){
            LOG.info("[AtTask] A project by the name " + projectName + " does not currently exist. A new project is being created.");
            logger.println("[AtTask] A project by the name " + projectName + " does not currently exist. A new project is being created.");
            currentProject = connector.addProject(projectName, group);
        } else {
			//Sync project Status
			LOG.info("[AtTask] Updating " + projectName + " to " + projectCondition.getJenkinsCondition());
			logger.println("[AtTask] Updating " + projectName + " to " + projectCondition.getJenkinsCondition());
		}

        // We don't update the project if the condition is stable and unchanged.
		if (currentProject.getCondition() == projectCondition.getAtTaskProjectCondition() && projectCondition == JobConditionEnum.Stable)
			return currentProject;

		int totalFailures = getTotalFailures(build);
		int newFailures = getNewFailures(build);

		List<com.attask.sdk.model.User> potentialNotifiees = null;

		//Determine if anyone should be notified
		// If there are any new failures or the build just became stable
		if ((currentProject.getCondition() == JobConditionEnum.Stable.getAtTaskProjectCondition() && projectCondition != JobConditionEnum.Stable) ||
			projectCondition == JobConditionEnum.Stable ||
			newFailures > 0) {
			if (getNotifyCulprits()) {
				potentialNotifiees = culprits;
			}else if (getNotifyCommitters()) {
				potentialNotifiees = committers;
			}
		}

		// Otherwise, construct a note and make the change.
		String message = getConversionUtil().getMessage(projectName, buildNumber, buildLink, projectCondition, changeSet);

		if (totalFailures > 0) {
			message += ".\n " + totalFailures + " failures";
			if (newFailures > 0)
				message += " / +" + newFailures;
			else if (newFailures < 0)
				message += " / " + newFailures;
			else
				message += " / +-0";
		}
		
		connector.addNote(message, currentProject.getID(), "PROJ", potentialNotifiees);
		connector.updateProject(currentProject, projectCondition, buildLink);

        return currentProject;
    }

	private int getTotalFailures(AbstractBuild<?, ?> build) {
		return build.getTestResultAction() != null ? build.getTestResultAction().getFailCount() : 0;
	}

	private int getNewFailures(AbstractBuild<?, ?> build) {
		return build.getTestResultAction() != null && build.getTestResultAction().getPreviousResult() != null ?
			   getTotalFailures(build) - build.getTestResultAction().getPreviousResult().getFailCount() :
			   0;
	}

	private String getChangeSetMessage(AbstractBuild<?,?> build) {
		Set<String> changeSetMessages = new HashSet<String>();

		String message = getChangeSetMessage(build.getChangeSet());

		if (message != null && message.length() > 0) {
			changeSetMessages.add(message);
		}

		if (assignCommitters) {
			getChangeSetMessageRecursively(build, changeSetMessages);
		}

		if (changeSetMessages.size() == 0)
			return "";

		StringBuilder builder = new StringBuilder();
		builder.append("Changes:");
		for (String changeSetMessage : changeSetMessages) {
			builder.append("\n").append(changeSetMessage);
		}

		return builder.toString();
	}

	private void getChangeSetMessageRecursively(AbstractBuild<?, ?> build, Set<String> changeSetMessages) {
		if (build == null)
			return;
		Set<AbstractProject> upstreamProjects = build.getUpstreamBuilds().keySet();

		for (AbstractProject upstreamProject : upstreamProjects) {
			AbstractBuild<?,?> upstreamBuild = build.getUpstreamRelationshipBuild(upstreamProject);
			String message = getChangeSetMessage(upstreamBuild.getChangeSet());

			if (message != null && message.length() > 0) {
				changeSetMessages.add(message);
			}

			getChangeSetMessageRecursively(upstreamBuild, changeSetMessages);
		}
	}

	private String getChangeSetMessage(ChangeLogSet<? extends ChangeLogSet.Entry> changeSet) {

		if (changeSet == null)
			return "";

		logger.println("[AtTask] Gathering info on " + changeSet.getItems().length + " changes.");

		StringBuilder builder = new StringBuilder();

		for (ChangeLogSet.Entry entry : changeSet) {
			if (builder.length() > 0) builder.append("\n");

			builder.append(entry.getAuthor().getFullName())
				   .append("- ").append(entry.getMsg());
		}
		
		return builder.toString();
	}

	private List<com.attask.sdk.model.User> findCommitters(AbstractBuild<?,?> build) {
		List<com.attask.sdk.model.User> committers = new ArrayList<com.attask.sdk.model.User>();
		for (ChangeLogSet.Entry entry : build.getChangeSet()) {
			com.attask.sdk.model.User usr = connector.getUser(entry.getAuthor().getFullName(), false);
			LOG.info("[AtTask] Searching for committer " + entry.getAuthor().getFullName());
			if (usr != null) {
				logger.println("[AtTask] " + usr.getName() + " identified as a Committer.");
				committers.add(usr);
			}
		}

		if (assignCommitters)
			appendUpstreamCommittersRecursively(build, committers);

		if (extraCommitters != null && extraCommitters.length() > 0) {
			for (String fullName : extraCommitters.split(",")) {
				com.attask.sdk.model.User usr = connector.getUser(fullName, false);
				LOG.info("[AtTask] Searching for notification recipient " + fullName);
				if (usr != null) {
					logger.println("[AtTask] " + usr.getName() + " identified as a notification recipient.");
					committers.add(usr);
				}
			}
		}

		LOG.info(committers.size() + " committers found.");
		return committers;
	}

	private void appendUpstreamCommittersRecursively(AbstractBuild<?, ?> build, List<com.attask.sdk.model.User> committers) {
		if (build == null)
			return;
		Set<AbstractProject> upstreamProjects = build.getUpstreamBuilds().keySet();
		for (AbstractProject upstreamProject : upstreamProjects) {
			LOG.info("[AtTask] Searching upstream project " + upstreamProject.getName());
			AbstractBuild<?,?> upstreamBuild = build.getUpstreamRelationshipBuild(upstreamProject);
			for (ChangeLogSet.Entry entry : upstreamBuild.getChangeSet()) {
				com.attask.sdk.model.User usr = connector.getUser(entry.getAuthor().getFullName(), false);
				LOG.info("[AtTask] Searching for upstream committer " + entry.getAuthor().getFullName());
				if (usr != null) {
					logger.println("[AtTask] " + usr.getName() + " identified as an upstream Committer.");
					committers.add(usr);
				}
			}

			appendUpstreamCommittersRecursively(upstreamBuild, committers);
		}
	}

	private List<com.attask.sdk.model.User> findCulprits(Set <User> users){
        List<com.attask.sdk.model.User> culprits = new ArrayList<com.attask.sdk.model.User>();
		for (User user : users) {
			com.attask.sdk.model.User usr = connector.getUser(user.getFullName(), false);
			LOG.info("[AtTask] Searching for culprit " + user.getFullName());
			if (usr != null) {
				logger.println("[AtTask] " + usr.getName() + " identified as a Culprit.");
				culprits.add(usr);
			}
		}

		LOG.info(culprits.size() + " culprits found.");
        return culprits;
    }
    
    private String generateAssignedTo(List<com.attask.sdk.model.User> committers){
        String assignedToID;
        if(committers.size() == 0){
            LOG.warning("[AtTask] No culprits or committers found. Issues will not be assigned");
            logger.println("[AtTask] No culprits or committers found. Issues will not be assigned");
            assignedToID = null;
        }else{
			Collections.shuffle(committers);
            com.attask.sdk.model.User user = committers.get(0);
            assignedToID = user.getID();
            LOG.info("[AtTask] Issues will be assigned to " + user.getName());
            logger.println("[AtTask] Issues will be assigned to " + user.getName());
        }
        return assignedToID;
    }

    private void resolvePassingTests(int buildNumber, String buildLink, List<OpTask> remainingIssues, String changeSet, List<com.attask.sdk.model.User> committers, List<com.attask.sdk.model.User> culprits) {
        LOG.info("[AtTask] Updating status of " + remainingIssues.size() + " remaining issues");
        logger.println("[AtTask] Updating status of newly resolved issues");
        String resolvedEquivalent = connector.getResolvedStatus();
		for(OpTask issue : remainingIssues){
			// If it's already resolved and unassigned, do nothing.
			if (issue.getStatus().equals(resolvedEquivalent) && issue.getAssignedToID() == null) continue;

            LOG.info("[AtTask] "+issue.getName()+" is no longer failing and is being marked as resolved.");
            String[] split = issue.getName().split("#");

			List<com.attask.sdk.model.User> potentialNotifiees = null;

			// Only send notifications on issues that were assigned.
			if (issue.getAssignedToID() != null) {
				if (notifyCulprits) {
					potentialNotifiees = culprits;
				} else if (notifyCommitters) {
					potentialNotifiees = committers;
				}
			}

			String message = getConversionUtil().getMessage(split[1], buildNumber, buildLink, JobConditionEnum.Stable, changeSet);
            connector.addNote(message, issue.getID(), OpTask.OBJCODE, potentialNotifiees);
            connector.updateIssue(issue, resolvedEquivalent, JobConditionEnum.Stable.getAtTaskIssueCondition(), "", "",buildLink);
        }
    }

    private void synchronizeFailedTests(Project currentProject, int buildNumber, String buildLink, List<CaseResult> failedTests, List<OpTask> issues, String assignedToID, String changeSet, List<com.attask.sdk.model.User> committers, List<com.attask.sdk.model.User> culprits) {
        LOG.info("[AtTask] Beginning synchronization of " + failedTests.size() + " failed tests with " + issues.size() + " issues on AtTask");
        logger.println("[AtTask] Beginning synchronization of " + failedTests.size() + " failed tests with " + issues.size() + " issues on AtTask");
        int maxTests = getMaxTestsAsInt();
		int maxAssignments = getMaxAssignmentsAsInt();
        LOG.info("[AtTask] A max of " + maxAssignments + " issues will be assigned");
		LOG.info("[AtTask] A max of " + maxTests + " issues will be created");

        int count = 0;

		// Generate a map of issues for quick lookup
		Map<String, OpTask> issueMap = new HashMap<String, OpTask>();
		for (OpTask issue : issues) {
			LOG.info("[AtTask] Adding " + issue.getName() + " to existing issues collection.");
			issueMap.put(issue.getName(),issue);
		}


        for(CaseResult failedTest: failedTests){
			if (maxTests <= 0) break;
			maxTests--;

			List<com.attask.sdk.model.User> potentialNotifiees = null;
			String potentialAssignee = null;
            String issueName = getConversionUtil().createTestName(failedTest.getClassName(), failedTest.getDisplayName());
            String stackTrace = failedTest.getErrorStackTrace();
          
			LOG.info("[AtTask] Looking for issue" + issueName + " on AtTask.");

			OpTask issue = issueMap.get(issueName);

            //If issue does not exist, create it if it's a new failure.
            if(issue == null && failedTest.getAge() == 1){
                LOG.info("[AtTask] Creating issue " + issueName + " on AtTask.");
                issue = connector.addIssue(issueName, currentProject);
            } else if (issue == null) {
				continue;
			}

			LOG.info("[AtTask] Updating " + issue.getName() + ".");

			if (count <= maxAssignments && issue.getAssignedToID() == null && failedTest.getAge() == 1) {
				LOG.info("[AtTask] Assigning " + issue.getName() + " and notifying contributors.");
				// If an assignment is to be made, check the limit.
				potentialAssignee = assignedToID;

				if (getNotifyCommitters())
					potentialNotifiees = committers;
				else if (getNotifyCulprits())
					potentialNotifiees = culprits;

				count++;
			}
			
			// Now update the old or newly created issue and add the note.
			updateFailingTest(failedTests.size(), buildNumber, buildLink, JobConditionEnum.Failed, failedTest, stackTrace, issue, potentialAssignee, potentialNotifiees, changeSet);
        }
    }

	private void updateFailingTest(int totalFailures, int buildNumber, String buildLink, JobConditionEnum buildStatus, CaseResult failedTest, String stackTrace, OpTask issue, String assignedToID, List<com.attask.sdk.model.User> committers, String changeSet) {
        Note note;
		String message = getConversionUtil().getMessage(failedTest.getDisplayName(), buildNumber, buildLink, buildStatus, changeSet);

		if (totalFailures > 0)
			message += ".\nThis test was one of  " + totalFailures + " failures on this build.";

        String resolvedEquivalent = connector.getResolvedStatus();
		boolean isAccepted = connector.isIssueAcknowledged(issue);

		// If the issue was resolved before, it's a reopen.
		if(issue.getStatus().equals(resolvedEquivalent)){
        	connector.addNote(message, issue.getID(), OpTask.OBJCODE, committers);
            connector.updateIssue(issue, "NEW", buildStatus.getAtTaskIssueCondition(), assignedToID, stackTrace, buildLink);
        }else if (!isAccepted) {
			// Otherwise leave the status the same
			connector.addNote(message, issue.getID(), OpTask.OBJCODE, committers);
            connector.updateIssue(issue, issue.getStatus(), buildStatus.getAtTaskIssueCondition(), assignedToID, stackTrace, buildLink);
        }
    }

    private List<OpTask> extractRemainingIssues(List<CaseResult> failedTests, List<OpTask> issues){
        List<OpTask> newlyResolved = new ArrayList<OpTask>(issues);
		for(OpTask issue : issues){
            for(CaseResult failedTest : failedTests){
                String issueName = getConversionUtil().createTestName(failedTest.getClassName(), failedTest.getDisplayName());
                if(issue.getName().equals(issueName)){
                    newlyResolved.remove(issue);
                }
            }
        }
        return newlyResolved;
    }



	private int getMaxAssignmentsAsInt() {
		try {
			if (overrideMaxValues)
				return Integer.valueOf(getMaxAssignments().trim());
			else
				return Integer.valueOf(getDescriptor().getMaxAssignments());
		} catch (Exception e) {
			e.printStackTrace(logger);
		}

		return 10000000;
	}

	private int getMaxTestsAsInt() {
		try {
			if (overrideMaxValues)
				return Integer.valueOf(getMaxTests().trim());
			else
				return Integer.valueOf(getDescriptor().getMaxTests());
		} catch (Exception e) {
			e.printStackTrace(logger);
		}

		return 10000000;
	}

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;  //To change body of implemented methods use File | Settings | File Templates.
    }

	public ConversionUtil getConversionUtil() {
		if (conversionUtil == null)
			conversionUtil = new ConversionUtil();

		return conversionUtil;
	}

	@Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private String username;
        private String password;
        private String url;
        private String groupName;
        private String maxTests;
		private String maxAssignments;

		public DescriptorImpl() {
			load();
		}
		
        public String getUsername(){
            return username;
        }

        public String getPassword(){
            return password;
        }

        public String getUrl(){
            return url;
        }

        public String getGroupName(){
            return groupName;
        }

        public String getMaxTests(){
            return maxTests;
        }

		public String getMaxAssignments() {
			return maxAssignments;
		}

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public FormValidation doTestConnection(@QueryParameter("username") final String accessId, @QueryParameter("password") final String secretKey, @QueryParameter("url") final String url, @QueryParameter("groupName") final String groupName) throws IOException, ServletException {
            API sdkAPI;

            try {
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
                sdkAPI = API.create(url);
                r.start();
                sdkAPI.login(accessId, secretKey);
                r.stop();
                try{
                    Thread r2 = new Thread() {
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
                    GroupService groupService = (GroupService)sdkAPI.getAPIService(com.attask.sdk.model.Group.OBJCODE);
                    Map<String, Object> filters = new HashMap<String, Object>();
                    filters.put("name", groupName);
                    List<Group> groups = groupService.search(filters);
                    if(groups != null){
                        if(groups.size() == 0){
                            throw new APIException("");
                        }
                    }else{
                        throw new APIException("");
                    }
                    r2.start();
                    sdkAPI.logout();
                    r2.stop();
                    return FormValidation.ok("Success! Username, password, and Group name have been verified.");
                }catch (APIException e){
                    return FormValidation.error("Unable to find Group '"+groupName+ "'. Users Home Group will be used if Group Name is not changed.");
                } catch (RuntimeException e) {
                    return FormValidation.error("Connection timed out");
                }
            } catch (APIException e){
                return FormValidation.error("Invalid username/password/host combination.");
            } catch (RuntimeException e) {
                return FormValidation.error("Connection timed out");
            }
        }

        public String getDisplayName() {
            return "Sync results with AtTask";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            username = formData.getString("username");
            password = formData.getString("password");
            url = formData.getString("url");
            groupName = formData.getString("groupName");
            maxTests = formData.getString("maxTests");
			maxAssignments = formData.getString("maxAssignments");
            save();
            return super.configure(req, formData);
        }

    }
}