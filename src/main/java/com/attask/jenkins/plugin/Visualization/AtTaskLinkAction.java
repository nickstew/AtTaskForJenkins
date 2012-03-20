package com.attask.jenkins.plugin.Visualization;

import hudson.tasks.junit.TestAction;

/**
 * Created by IntelliJ IDEA.
 * User: jenniferrumbaugh
 * Date: 2/27/12
 * Time: 2:52 PM
 * To change this template use File | Settings | File Templates.
 */
public class AtTaskLinkAction extends TestAction {

    private String atTaskLink;
    private String testName;
    
    public AtTaskLinkAction(String atTaskLink, String testName){
        this.atTaskLink = atTaskLink;
        this.testName = testName;
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
    
    public String getLink() {
        return atTaskLink;
    }
    
    public String getTestName() {
        return testName;
    }
}
