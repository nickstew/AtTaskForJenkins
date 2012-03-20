package com.attask.jenkins.plugin.Visualization;

import com.attask.jenkins.plugin.AtTaskForJenkins;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.junit.*;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * User: nicholasstewart
 * Date: 2/22/12
 * Time: 3:59 PM
 */
public class TestResultPublisher extends TestDataPublisher {

    @DataBoundConstructor
    public TestResultPublisher() {
        super();
    }

    /**
     * Called after test results are collected by Hudson, to create a resolver for {@link hudson.tasks.junit.TestAction}s.
     *
     * @return can be null to indicate that there's nothing to contribute for this test result.
     */
    @Override
    public hudson.tasks.junit.TestResultAction.Data getTestData(final AbstractBuild<?, ?> build,
                                             Launcher launcher,
                                             BuildListener listener,
                                             TestResult testResult) throws IOException, InterruptedException {
        return new TestActionListBuilder();
    }


    @Extension
    public static class DescriptorImplementator extends Descriptor<TestDataPublisher> {
        @Override
        public String getDisplayName() {
            return "Include AtTask test result information";
        }
    }
}
