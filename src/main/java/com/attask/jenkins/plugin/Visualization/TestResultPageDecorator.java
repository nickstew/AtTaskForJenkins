package com.attask.jenkins.plugin.Visualization;

import hudson.Extension;
import hudson.model.PageDecorator;

/**
 * User: nicholasstewart
 * Date: 3/1/12
 * Time: 2:31 PM
 */
@Extension
public class TestResultPageDecorator extends PageDecorator {
    public TestResultPageDecorator() {
        super(TestResultPageDecorator.class);
    }
}