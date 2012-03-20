package com.attask.jenkins.plugin;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;

public class ConversionUtil {
	public JobConditionEnum convertBuildResult(String buildResult){
        //Converts project status to condition for AtTask
        JobConditionEnum condition = JobConditionEnum.parse(buildResult);
		return condition != null ? condition : JobConditionEnum.Stable;
    }

    public String createBuildURL(String hostURL, String buildURL){
        StringBuilder builder = new StringBuilder();
        builder.append(hostURL).append(buildURL);
        return builder.toString().replaceAll(" ", "%20");
    }

	public String createTestName(String className, String methodName){
        StringBuilder sb = new StringBuilder();
        String [] nameParts = className.split("\\.");
        String newName = nameParts[nameParts.length-1];
        sb.append(newName);
        sb.append("#");
        sb.append(methodName);
        sb.append("()");
        return sb.toString();
    }

	public String getMessage(String itemName, int buildNumber, String buildLink, JobConditionEnum condition, String changeSet) {
        StringBuilder builder = new StringBuilder();
		builder.append(itemName);
		builder.append(condition.getMessage());
		builder.append(" #").append(buildNumber).append(".").append("\n").append(buildLink);

		if (changeSet != null && changeSet.length() > 0)
			builder.append("\n").append(changeSet);

		return builder.toString();
	}

	public String createProjectName(AbstractBuild build, AtTaskForJenkins.DescriptorImpl descriptor) {
		AtTaskForJenkins job = (AtTaskForJenkins) build.getProject().getPublishersList().get(descriptor);

		if (job.getCustomProjectName() != null && job.getCustomProjectName().trim().length() > 0) {
			String expandedProjectName = job.getCustomProjectName();

			try {
				EnvVars env = build.getCharacteristicEnvVars();
				for (Object o : build.getBuildVariables().keySet()) {
					env.put(o.toString(), (String) build.getBuildVariables().get(o));
				}
				expandedProjectName = env.expand(expandedProjectName);
			} catch (Exception e) {
				// do nothing;
			}

			return expandedProjectName;
		}

		return "Jenkins "+build.getProject().getName()+" results";
	}
}
