package org.junit.experimental.cloud.listeners;

import org.junit.experimental.cloud.shared.TestToHostMapping;
import org.junit.runner.Description;
import org.junit.runner.notification.RunListener;

public class JCSJunitExecutionListener extends RunListener {

    private final TestToHostMapping mapping = TestToHostMapping.get();

    @Override
    public void testStarted(Description description) throws Exception {
        System.out.println(Thread.currentThread()
                + " JCSJunitExecutionListener.testStarted()");
        mapping.testStarts(description);

    }

    @Override
    public void testFinished(Description description) throws Exception {
        System.out.println(Thread.currentThread()
                + " JCSJunitExecutionListener.testFinished()");
        mapping.testFinishes(description);
    }
}