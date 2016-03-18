package org.junit.experimental.cloud.listeners;

import org.junit.experimental.cloud.shared.TestToHostMapping;
import org.junit.runner.Description;
import org.junit.runner.notification.RunListener;

public class JCSJunitExecutionListener extends RunListener {

    private final TestToHostMapping mapping = TestToHostMapping.get();

    // @Override
    // public void testRunStarted(Description description) throws Exception {
    // System.out.println(
    // "Number of tests to execute: " + description.testCount());
    // }
    //
    // @Override
    // public void testRunFinished(Result result) throws Exception {
    // System.out.println("Number of tests executed: " + result.getRunCount());
    // }

    @Override
    public void testStarted(Description description) throws Exception {
        System.out.println("JCSJunitExecutionListener.testStarted()"
                + Thread.currentThread());
        mapping.testStarts(description);

    }

    @Override
    public void testFinished(Description description) throws Exception {
        System.out.println("JCSJunitExecutionListener.testFinished() "
                + Thread.currentThread());
        mapping.testFinishes(description);
    }

    // @Override
    // public void testFailure(Failure failure) throws Exception {
    // System.out
    // .println("Failed: " + failure.getDescription().getMethodName());
    // }
    //
    // @Override
    // public void testAssumptionFailure(Failure failure) {
    // System.out
    // .println("Failed: " + failure.getDescription().getMethodName());
    // }
    //
    // @Override
    // public void testIgnored(Description description) throws Exception {
    // System.out.println("Ignored: " + description.getMethodName());
    // }
}