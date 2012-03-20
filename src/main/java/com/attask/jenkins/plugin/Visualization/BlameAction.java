package com.attask.jenkins.plugin.Visualization;

import com.attask.jenkins.plugin.AtTaskForJenkins;
import com.attask.jenkins.plugin.DependencyFactory;
import com.attask.jenkins.plugin.RemoteConnector;
import com.attask.sdk.model.OpTask;
import com.attask.sdk.model.User;
import hudson.model.Hudson;
import hudson.tasks.junit.TestAction;
import org.apache.commons.io.output.NullOutputStream;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * User: nicholasstewart
 * Date: 2/21/12
 * Time: 3:58 PM
 */
@SuppressWarnings("deprecation")
public class BlameAction extends TestAction {
	private OpTask testObject = null;
	private User assignedTo;
    private List<User> committers;
    private List<User> groupMembers;

    private String testID;
	private String testName;
	private com.attask.sdk.model.Project project;
	private AtTaskForJenkins.DescriptorImpl descriptor;

	@SuppressWarnings(value = "@Deprecated")
	public BlameAction(OpTask testObject, User assignedTo, List<User> groupMembers, List<User> committers, AtTaskForJenkins.DescriptorImpl descriptor, String testID) {

        this.testObject = testObject;
		this.testName = testObject.getName();
        this.assignedTo = assignedTo;
        this.groupMembers = groupMembers;
        this.committers = committers;
		this.descriptor = descriptor;
		this.testID = testID;

        if(committers.size() == 0)
            this.committers = new ArrayList<User>();
	}

    public BlameAction(OpTask testObject, List<User> groupMembers, List<User> committers, AtTaskForJenkins.DescriptorImpl descriptor, String testID) {

        this.testObject = testObject;
		this.testName = testObject.getName();
        this.groupMembers = groupMembers;
        this.committers = committers;
		this.descriptor = descriptor;
		this.testID = testID;

        if(committers.size() == 0)
            this.committers = new ArrayList<User>();
    }

	public BlameAction(String testName, com.attask.sdk.model.Project project, List<User> groupMembers, List<User> committers, AtTaskForJenkins.DescriptorImpl descriptor, String testID) {

        this.testName = testName;
		this.project = project;
        this.groupMembers = groupMembers;
        this.committers = committers;
		this.descriptor = descriptor;
		this.testID = testID;

        if(committers.size() == 0)
            this.committers = new ArrayList<User>();
    }

	public void doBlame(StaplerRequest request, StaplerResponse response) throws Exception {
		String attaskId = request.getParameter("blameId");

        RemoteConnector connector = DependencyFactory.getConnector(descriptor.getUrl(), descriptor.getUsername(), descriptor.getPassword());

		try {
		// Try logging in as the user making the request
		User requestor = connector.getUser(Hudson.getAuthentication().getName(), false);
		if (requestor != null)
			connector = DependencyFactory.getConnector(descriptor.getUrl(), requestor.getUsername(), descriptor.getUsername(), descriptor.getPassword(), new PrintStream(new NullOutputStream()));
		} catch (Exception e) {
            
        };
		
        try {
			if (testObject == null && project != null) {
				testObject = connector.getIssue(testName, project);

				if (testObject == null) {
					testObject = connector.addIssue(testName, project);
				}
			}

			if (attaskId == null || attaskId.length() == 0) {
				connector.unassignIssue(testObject);
			} else {
                User assignee = connector.getUserByID(attaskId); // TODO: check with Jesse about grabbing from TestActionListBuilder._cachedUsers

				if (assignee != null)
					connector.assignIssue(testObject, assignee);

			}
        } catch (Exception e) {}
        
		response.forwardToPreviousPage(request);
	}

    public String getIconFileName() {
        return null;
    }

	public String getDisplayName() {
		return null;
	}

	public String getUrlName() {
		return "blame";
	}

    public List<User> getCommitters() {
        return committers;
    }
    
    public List<User> getGroupMembers() {
        return groupMembers;
    }
    
	public String getAssignedToName() {
		return getAssignedTo() != null ? getAssignedTo().getName() : "Unassigned";
	}
    
	public String getBlameUrl() {
		StringBuilder builder = new StringBuilder();

		if (testID != null && testID.length() > 0) {
			builder.append(testID);
		} else {
			// fall back if there's no replacement
			builder.append("(root)/");
			String[] parts = testName.replace("()","").split("#");
			builder.append(parts[0]);
			builder.append("/");
			builder.append(parts[1]);
		}
		builder.append("/blame/blame");

		return builder.toString();
	}
    public User getAssignedTo() {
        return assignedTo;
    }
    
	public OpTask getTestObject() {
        return testObject;
	}
}
