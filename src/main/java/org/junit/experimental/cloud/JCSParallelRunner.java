package org.junit.experimental.cloud;

import org.junit.experimental.cloud.scheduling.JCSParallelScheduler;
import org.junit.runners.model.InitializationError;

/**
 * Not using the RunnerScheduler method the instantiation and execution happen
 * at the same time (actually in the same place)
 * 
 * @author gambi
 *
 */
public class JCSParallelRunner extends JCSRunner {

    // TODO Policy should be configurable
    // TODO JCSParallelRunner should be the defaul configuration, and by default
    // should work like the traditional single threaded Runner (set hard limit
    // to 1)

    public JCSParallelRunner(final Class<?> klass) throws InitializationError {
        super(klass);
        // Use Default values 1, -1
        setScheduler(new JCSParallelScheduler(klass, 1, -1));
    }

    public JCSParallelRunner(final Class<?> klass, //
            int concurrentTestsLimit, int threadsLimit)
                    throws InitializationError {
        super(klass);

        setScheduler(new JCSParallelScheduler(klass, concurrentTestsLimit,
                threadsLimit));
    }

    // @Override
    // protected Description describeChild(FrameworkMethod method) {
    // Description d = super.describeChild(method);
    // // TODO Pay attention to this, we need to understand when we can
    // // associate an instance to a set of test(methods) to retrieve data on
    // // timing, dependencies, etc.
    // //
    // // System.out.println("--------\n" + Thread.currentThread()
    // // + "JCSParallelRunner.describeChild() " + d.getClassName() + "."
    // // + d.getMethodName() + "\n--------");
    // return d;
    // }

}
