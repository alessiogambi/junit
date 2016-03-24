package org.junit.experimental.cloud.scheduling;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.experimental.cloud.shared.TestToHostMapping;
import org.junit.runners.model.RunnerScheduler;

/**
 * The scheduler is not aware of any deployment logic or constraints. It simply
 * creates new threads and associates them to TestJobs (test jobs can be one or
 * more test methods from the same test class depending on the Actual JUNIT
 * runner.
 * 
 * 
 * TODO We can do also use scheduler with plain JCSRunner I guess. Check if
 * JCSParallel runner has only the scheduler more than plain JCSRunner.
 * 
 * TODO With the JCSJunitCoreWrapper we can also inject the VERY same scheduler
 * if need across the various test classes
 * 
 * @author gambi
 *
 */
public class JCSParallelScheduler implements RunnerScheduler {

    private ExecutorService executorService;

    // Order of finishing does not matter. TODO Notify the shared data about it
    // !
    private CompletionService<Void> completionService;

    private Queue<Future<Void>> tasks;

    private final Semaphore testsSemaphore;

    private final String factoryName;

    private final boolean wrappingSuite;

    /**
     * Since threads limit here enforces test limit we can also use one
     * parameter.
     * 
     * @param klass
     * @param hardLimit
     */
    public JCSParallelScheduler(Class<?> klass, int hardLimit) {
        this(klass, hardLimit, hardLimit);
    }

    public JCSParallelScheduler(Class<?> klass, int testLimit,
            int threadLimit) {

        wrappingSuite = klass == null;

        factoryName = wrappingSuite
                ? "Wrapping SUITE {concurrent.test.cases=" + testLimit
                        + ",concurrent.threads=" + threadLimit + "}"
                : klass.getSimpleName();

        // TODO Change this if you want to control the scheduling order, e.g.,
        // with priority or stuff
        if (threadLimit > 0) {
            this.executorService = Executors.newFixedThreadPool(threadLimit,
                    new NamedThreadFactory(factoryName));
        } else {
            this.executorService = Executors
                    .newCachedThreadPool(new NamedThreadFactory(factoryName));
        }

        /*
         * Completion Service follows a producer/consumer philosophy: as soon a
         * thread is done, it puts the result into a non blocking queue so that
         * the consumer can take it.
         */
        completionService = new ExecutorCompletionService<Void>(
                executorService);

        /*
         * This is what we actually need for later collect the results. Note
         * that somewhere the time given by JUnit are not really accurate !
         */
        tasks = new LinkedList<Future<Void>>();

        // TODO - Limit the amount of tests concurrently "processed"
        // to the framework. This can be used to enforce a GLOBAL parallelism
        // level, not sure if needed really

        // Note that threads are generated before and for all the tests
        testsSemaphore = new Semaphore(
                testLimit > 0 ? testLimit : Integer.MAX_VALUE);

        // System.out.println("JCSParallelScheduler.JCSParallelScheduler()\n"
        // + "\tSUMMARY\n" + "\t\t " + testLimit + " using " + threadLimit
        // + " threads");

    }

    /**
     * Blocking this will block the entire submission since schedule is invoked
     * by Thread.main
     * 
     * @param childStatement
     */
    @Override
    public void schedule(final Runnable childStatement) {

        /*
         * Wrap the original Runnable into a new one that use the semaphore for
         * concurrency management. Here I guess is the place were to enforce any
         * specific ordering (or reordering of elements)
         */
        // System.out.println(Thread.currentThread() + "Submit " +
        // childStatement
        // + " for execution");
        tasks.offer(completionService.submit(new Runnable() {
            @Override
            public void run() {
                try {

                    // System.out.println(Thread.currentThread() + " "
                    // + childStatement + " start execution " + " using "
                    // + testsSemaphore);
                    testsSemaphore.acquire();
                    // Execute
                    childStatement.run();
                } catch (InterruptedException e) {
                    System.err.println(Thread.currentThread() + " INTERRUPTED");
                } finally {
                    // System.out.println(Thread.currentThread()
                    // + " release permit from semaphore " + testsSemaphore
                    // + "{" + testsSemaphore.availablePermits() + "}");
                    testsSemaphore.release();
                }
            }
        }, null));
    }

    @Override
    public void finished() {
        // System.out.println("==========================\n"
        // + "Finished submission of all tests for " + factoryName + "\n"
        // + "Wait for tests to end\n" + "==========================\n");
        try {
            while (!tasks.isEmpty()) {
                Future<Void> finishedTask = completionService.take();

                tasks.remove(finishedTask);

                synchronized (TestToHostMapping.get().getTestsLock()) {
                    TestToHostMapping.get().getTestsLock().notifyAll();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            while (!tasks.isEmpty())
                tasks.poll().cancel(true);
            executorService.shutdownNow();
        }
        // System.out.println(
        // "==========================\n" + "Finished all tests for "
        // + factoryName + "\n" + "==========================\n");

    }

    // Note this is shared among all the JCSParallelRunner instances
    static final class NamedThreadFactory implements ThreadFactory {
        static final AtomicInteger poolNumber = new AtomicInteger(1);

        final AtomicInteger threadNumber = new AtomicInteger(1);

        final ThreadGroup group;

        NamedThreadFactory(String poolName) {
            group = new ThreadGroup(
                    poolName + "-" + poolNumber.getAndIncrement());
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, group.getName() + "-thread-"
                    + threadNumber.getAndIncrement(), 0);
            return t;
        }
    }

}