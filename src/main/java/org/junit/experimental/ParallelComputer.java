package org.junit.experimental;

import org.junit.experimental.cloud.scheduling.JCSParallelScheduler;
import org.junit.runner.Computer;
import org.junit.runner.Runner;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

public class ParallelComputer extends Computer {

    private int concurrentTestCasesLimit;

    private int concurrentTestPerTestClassLimit;

    public ParallelComputer(int concurrentTestCasesLimit,
            int concurrentTestPerTestClassLimit) {
        super();
        this.concurrentTestCasesLimit = concurrentTestCasesLimit;
        this.concurrentTestPerTestClassLimit = concurrentTestPerTestClassLimit;
    }

    @Override
    public Runner getSuite(RunnerBuilder builder, java.lang.Class<?>[] classes)
            throws InitializationError {

        Runner suite = super.getSuite(builder, classes);
        ((ParentRunner<?>) suite).setScheduler(
                new JCSParallelScheduler(null, concurrentTestCasesLimit));
        return suite;
    }

    @Override
    protected Runner getRunner(RunnerBuilder builder, Class<?> testClass)
            throws Throwable {
        Runner runner = super.getRunner(builder, testClass);
        ((ParentRunner<?>) runner).setScheduler(new JCSParallelScheduler(null,
                concurrentTestPerTestClassLimit));

        return runner;
    }

    public static Class<?> classes() {
        // TODO Auto-generated method stub
        return null;
    }

    public static Class<?> methods() {
        // TODO Auto-generated method stub
        return null;
    }
}
