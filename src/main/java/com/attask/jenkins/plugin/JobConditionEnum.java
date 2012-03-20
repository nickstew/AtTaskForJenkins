package com.attask.jenkins.plugin;

import com.attask.sdk.enums.Condition;
import com.attask.sdk.enums.ProjectCondition;

public enum JobConditionEnum {
	Unstable("UNSTABLE",Condition.SOME_CONCERNS,ProjectCondition.ATRISK," is unstable on build"),
	Stable("SUCCESS", Condition.GOING_SMOOTHLY,ProjectCondition.ONTARGET," is now stable on build"),
	Failed("FAILURE",Condition.MAJOR_ROADBLOCKS,ProjectCondition.INTROUBLE," failed on build"),
	NotBuilt("NOT_BUILT",Condition.MAJOR_ROADBLOCKS,ProjectCondition.ATRISK," was not built"),
	Aborted("ABORTED",Condition.MAJOR_ROADBLOCKS,ProjectCondition.INTROUBLE," aborted on build");

	private String jenkinsCondition;
	private ProjectCondition atTaskProjectCondition;
	private String message;
	private Condition atTaskIssueCondition;

	JobConditionEnum(String jenkinsCondition,  Condition issueCondition, ProjectCondition projectCondition, String message) {
		this.jenkinsCondition = jenkinsCondition;
		this.atTaskProjectCondition = projectCondition;
		this.atTaskIssueCondition = issueCondition;
		this.message = message;
	}

	public Condition getAtTaskIssueCondition() {
		return atTaskIssueCondition;
	}

	public ProjectCondition getAtTaskProjectCondition() {
		return this.atTaskProjectCondition;
	}

	public String getJenkinsCondition() {
		return this.jenkinsCondition;
	}

	public String getMessage() {
		return this.message;
	}

	public static JobConditionEnum parse(String jenkinsCondition) {
		for (JobConditionEnum projectConditionEnum : values()) {
			if (projectConditionEnum.getJenkinsCondition().equals(jenkinsCondition))
				return projectConditionEnum;
		}

		return null;
	}


}
