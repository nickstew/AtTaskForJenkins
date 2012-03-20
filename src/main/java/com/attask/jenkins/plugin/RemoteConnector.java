package com.attask.jenkins.plugin;

import com.attask.sdk.enums.Condition;
import com.attask.sdk.model.*;

import java.util.Collection;
import java.util.List;

public interface RemoteConnector {

	Group getGroup(String groupName, String username);

	OpTask assignIssue(OpTask issue, User assignee);
	OpTask unassignIssue(OpTask issue);
	OpTask updateIssue(OpTask issue, String status, Condition atTaskIssueCondition, String assignedToID, String stackTrace, String buildLink);
	OpTask addIssue(String issueName, Project project);
	OpTask getIssue(String issueName, Project project);
	List<OpTask> getIssues(String projectID);

	boolean isIssueAcknowledged(OpTask issue);

	Project updateProject(Project currentProject, JobConditionEnum projectCondition, String buildLink);
	Project addProject(String projectName, Group group);
	Project getProject(String projectName);

	Note addNote(String message, String objID, String objCode, List<User> notifiees);

	User getUserByID(String userID);
	User getUser(String username, boolean strict);
	List<User> getUsers(String groupName);

	String getResolvedStatus();

	void loginAsUser(String username);
}
