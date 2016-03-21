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

}
