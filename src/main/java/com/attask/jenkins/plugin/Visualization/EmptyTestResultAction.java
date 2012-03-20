package com.attask.jenkins.plugin.Visualization;

import hudson.tasks.junit.TestAction;

/**
 * User: nicholasstewart
 * Date: 3/8/12
 * Time: 7:09 PM
 */
public class EmptyTestResultAction extends TestAction {
    private String text;
    private String classTag;
    
    public EmptyTestResultAction(String classTag, String text){
        this.text = text;
        this.classTag = classTag;
    }

    public String getIconFileName() {
        return "";
    }

    public String getDisplayName() {
        return "Empty Action";
    }

    public String getUrlName() {
        return "empty";
    }
    
    public String getText(){
        return text;
    }
    
    public String getClassTag(){
        return classTag;
    }
}
