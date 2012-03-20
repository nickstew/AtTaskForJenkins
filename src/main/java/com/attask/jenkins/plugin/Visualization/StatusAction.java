package com.attask.jenkins.plugin.Visualization;

import hudson.tasks.junit.TestAction;

import java.util.HashMap;
import java.util.Map;

public class StatusAction extends TestAction {
	private static Map<String, String> messages = new HashMap<String, String>();
	static {
		messages.put("AD","Accepted");
		messages.put("AA","Awaiting Acceptance");
		messages.put("DN","Done");
		messages.put("RLV","Resolved");
		messages.put("NEW","New");
	}

    private String status;
    public StatusAction(String status){
        this.status = status;
    }
    
    public String getStatus(){
        return messages.get(status) != null ? messages.get(status) : status;
    }

    public String getIconFileName() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getDisplayName() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getUrlName() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
