package org.junit.internal.builders;

import org.junit.experimental.cloud.JCSParallelRunner;
import org.junit.experimental.cloud.shared.TestToHostMapping;
import org.junit.runner.Runner;
import org.junit.runners.model.RunnerBuilder;

/**
 * This is somehow the class that is called when @RunWith is not present.
 * 
 * There is one of those thing for each test to run. We'll be configured trhouth
 * pom/conf
 * 
 * @author gambi
 *
 */
public class JUnit4Builder extends RunnerBuilder {

    @Override
    public Runner runnerForClass(Class<?> testClass) throws Throwable {
        return new JCSParallelRunner(testClass, // UGLYYYY
                TestToHostMapping.get().getConcurrentTestsLimit(),
                TestToHostMapping.get().getThreadLimit());
    }
}