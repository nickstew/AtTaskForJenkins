package com.attask.jenkins.plugin.Visualization;

import com.attask.jenkins.plugin.*;
import com.attask.sdk.model.OpTask;
import com.attask.sdk.model.Project;
import com.attask.sdk.model.User;
import hudson.model.AbstractBuild;
import hudson.tasks.junit.*;

import java.util.*;
import java.util.logging.Logger;

/**
 * User: nicholasstewart
 * Date: 2/29/12
 * Time: 2:35 PM
 */
public class TestActionListBuilder extends hudson.tasks.junit.TestResultAction.Data {

    private static List<User> _assignees;
    public static final Logger LOG = Logger.getLogger(TestResultPublisher.class.getName());

    private RemoteConnector getConnector(AtTaskForJenkins.DescriptorImpl descriptor) throws Exception {
        return DependencyFactory.getConnector(descriptor.getUrl(), descriptor.getUsername(), descriptor.getPassword());
    }

    /**
     * Returns all TestActions for the testObject.
     *
     * @return Can be empty but never null. The caller must assume that the returned list is read-only.
     */
    @Override
    @SuppressWarnings("deprecation")
    public List<? extends TestAction> getTestAction(TestObject testObject) {
        List<TestAction> actions = new ArrayList<TestAction>();

		// We are not interested in TestResult or PackageResult right now, only CaseResult.
		if (!(testObject instanceof CaseResult))
			return actions;

        RemoteConnector connector = null;
		ConversionUtil conversionUtil = new ConversionUtil();

        try {
			AbstractBuild<?, ?> build = testObject.getOwner();
			AtTaskForJenkins.DescriptorImpl descriptor = (AtTaskForJenkins.DescriptorImpl) build.getDescriptorByName("AtTaskForJenkins");
            connector = getConnector(descriptor);

			String testClassName = testObject instanceof CaseResult ? ((CaseResult) testObject).getClassName() : "";
            String issueName = conversionUtil.createTestName(testClassName, testObject.getDisplayName());
			List<User> assignees = getAssignees(connector, descriptor.getGroupName());
            List<User> committers = getCommitters(connector, build.getCulprits());

			//AbstractBuild<?, ?> firstFailedBuild = build.getProject().getBuildByNumber(((CaseResult) testObject).getFailedSince());

			Project project = connector.getProject(conversionUtil.createProjectName(build, descriptor));
			OpTask issue = connector.getIssue(issueName, project);

			if(issue != null) {
				String attaskIssueUrl = descriptor.getUrl();
				if(!attaskIssueUrl.endsWith("/"))
					attaskIssueUrl += "/";
				attaskIssueUrl += "issue/view?ID="+issue.getID();

				actions.add(new AtTaskLinkAction(attaskIssueUrl, issueName));
				if(issue.getAssignedToID() != null) {
					User user = connector.getUserByID(issue.getAssignedToID());
					actions.add(new BlameAction(issue, user, assignees, committers, descriptor, testObject.getId()));
				}else {
					actions.add(new BlameAction(issue, assignees, committers, descriptor, testObject.getId()));
				}

				String status = issue.getStatus();

				if (!status.equals(connector.getResolvedStatus()) && issue.getAssignments() != null && issue.getAssignments().size() > 0) {
					status = issue.getAssignments().get(0).getStatus().getValue(); // Should be get label, bug sdk bug.
				}
				//TODO: this should work but there's a "java.lang.ClassNotFoundException: org.apache.struts.util.MessageResourcesFactory" in the sdk
				actions.add(new StatusAction(status));
			} else if (project != null) {
                actions.add(new BlameAction(issueName, project, assignees, committers, descriptor, testObject.getId()));
                actions.add(new StatusAction("Untracked"));
            } else {
				actions.add(new EmptyTestResultAction("testFailureAssignment  unattached",""));
                actions.add(new StatusAction("Untracked"));
			}
        } catch (Exception e) {
            LOG.info("com.attask.jenkins.plugin.Visualization.TestActionListBuilder has caught an exception.\n" +
                    "It will now return a list of empty Actions to properly display testReport table.");
            actions.add(new EmptyTestResultAction("testFailureAssignment unattached",""));
            actions.add(new StatusAction("Untracked"));
        }

        return actions;
    }

    private List<User> getAssignees(RemoteConnector connector, String groupName){
        if(_assignees==null){
            List<User> assignees = new ArrayList<User>();
            assignees.addAll(connector.getUsers(groupName));
			Collections.sort(assignees, new Comparator<User>() {
				public int compare(User user, User user1) {
					return user.getName().compareTo(user1.getName());
				}
			});
            _assignees = assignees;
        }
        return _assignees;
    }

    private List<User> getCommitters(RemoteConnector connector, Set<hudson.model.User> culprits){
        List<User> committers = new ArrayList<User>();

		for(hudson.model.User culprit : culprits) {
			User committer = connector.getUser(culprit.getFullName(), false);
			if(committer != null) {
				committers.add(committer);
			}
		}

		return committers;
    }
}

