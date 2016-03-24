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

    private final int threadsLimit;

    private final int concurrentTestsLimit;

    public JCSParallelRunner(final Class<?> klass) throws InitializationError {
        this(klass, -1, -1);
    }

    public JCSParallelRunner(final Class<?> klass, //
            int concurrentTestsLimit, int threadsLimit)
            throws InitializationError {
        super(klass);
        this.concurrentTestsLimit = concurrentTestsLimit;
        this.threadsLimit = threadsLimit;
        setScheduler(new JCSParallelScheduler(klass, concurrentTestsLimit,
                threadsLimit));
    }

    @Override
    public String toString() {
        return super.toString() + "{concurrentTestsLimit="
                + concurrentTestsLimit + ",threadsLimit=" + threadsLimit + "}";
    }

}
