package org.junit.internal.builders;

import java.util.Arrays;
import java.util.List;

import org.junit.runner.Runner;
import org.junit.runners.model.RunnerBuilder;

public class AllDefaultPossibilitiesBuilder extends RunnerBuilder {
    private final boolean canUseSuiteMethod;

    public AllDefaultPossibilitiesBuilder(boolean canUseSuiteMethod) {
        System.out.println(
                "AllDefaultPossibilitiesBuilder.AllDefaultPossibilitiesBuilder() canUseSuiteMethod "
                        + canUseSuiteMethod);
        this.canUseSuiteMethod = canUseSuiteMethod;
    }

    @Override
    public Runner runnerForClass(Class<?> testClass) throws Throwable {
        List<RunnerBuilder> builders = Arrays.asList(ignoredBuilder(),
                annotatedBuilder(), suiteMethodBuilder(), junit3Builder(),
                junit4Builder());

        for (RunnerBuilder each : builders) {
            Runner runner = each.safeRunnerForClass(testClass);
            if (runner != null) {
                return runner;
            }
        }
        return null;
    }

    protected JUnit4Builder junit4Builder() {
        try {
            System.out
                    .println("AllDefaultPossibilitiesBuilder.junit4Builder()");
            return new JUnit4Builder();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return null;
    }

    protected JUnit3Builder junit3Builder() {
        try {
            System.out
                    .println("AllDefaultPossibilitiesBuilder.junit3Builder()");
            return new JUnit3Builder();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return null;

    }

    protected AnnotatedBuilder annotatedBuilder() {
        return new AnnotatedBuilder(this);
    }

    protected IgnoredBuilder ignoredBuilder() {
        return new IgnoredBuilder();
    }

    protected RunnerBuilder suiteMethodBuilder() {
        if (canUseSuiteMethod) {
            return new SuiteMethodBuilder();
        }
        return new NullBuilder();
    }

    public Runner safeCloudRunnerForClass(Class<?> testClass) {
        // TODO Auto-generated method stub
        return null;
    }
}