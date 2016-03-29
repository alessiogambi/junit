package org.junit.experimental.cloud.scheduling;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import at.ac.tuwien.infosys.jcloudscale.vm.JCloudScaleClient;
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

    // private final Logger log;

    private final String factoryName;

    /**
     * Since threads limit here enforces test limit we can also use one
     * parameter.
     * 
     * @param klass
     * @param hardLimit
     */
    public JCSParallelScheduler(Class<?> klass, int hardLimit) {
        this(klass, hardLimit, -1);// hardLimit);
    }

    public JCSParallelScheduler(Class<?> klass, int testLimit,
            int threadLimit) {
        this(klass != null ? klass.getSimpleName()
                : "Wrapping SUITE {" + testLimit + ", " + threadLimit + "}",
                testLimit, threadLimit);
    }

    public JCSParallelScheduler(String factoryName, int testLimit,
            int threadLimit) {

        this.factoryName = factoryName;

        // log = JCloudScaleClient.getConfiguration().getLogger(getClass());

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

        // Note that threads are generated before and for all the tests
        testsSemaphore = new Semaphore(
                testLimit > 0 ? testLimit : Integer.MAX_VALUE);

        System.out.println(factoryName + " configuration :\n" + "\tMax concurrency: "
                + testLimit + "\n" + "\tMax threads: " + threadLimit);

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
        System.out.println(Thread.currentThread() + " enqueue " + childStatement
                + " for execution");
        tasks.offer(completionService.submit(new Runnable() {
            @Override
            public void run() {
                try {

                    System.out.println(Thread.currentThread() + " "
                            + childStatement + " start execution " + " using "
                            + testsSemaphore);
                    testsSemaphore.acquire();
                    // Create Test Object and then Execute ?
                    childStatement.run();
                } catch (InterruptedException e) {
                    System.err.println(Thread.currentThread() + " INTERRUPTED");
                } finally {
                    System.out.println(Thread.currentThread()
                            + " release permit from semaphore " + testsSemaphore
                            + "{" + testsSemaphore.availablePermits() + "}");
                    testsSemaphore.release();
                }
            }
        }, null));
    }

    @Override
    public void finished() {
        System.out.println(Thread.currentThread()
                + "Finished submission of all tests for " + factoryName);
        //
        try {
            while (!tasks.isEmpty()) {
                try {
                    Future<Void> finishedTask = completionService.take();
                    tasks.remove(finishedTask);

                    System.out.println(
                            "JCSParallelScheduler.finished() Done a task");

                    finishedTask.get();
                } catch (ExecutionException e) {
                    // This is an exception raised during the execution
                    e.printStackTrace();
                } finally {
                    synchronized (TestToHostMapping.get().getTestsLock()) {
                        TestToHostMapping.get().getTestsLock().notifyAll();
                    }
                }

            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            synchronized (TestToHostMapping.get().getTestsLock()) {
                TestToHostMapping.get().getTestsLock().notifyAll();
            }
        } finally {
            while (!tasks.isEmpty())
                tasks.poll().cancel(true);
            executorService.shutdownNow();
        }
        System.out.println(Thread.currentThread() + "Finished all tests for "
                + factoryName);
        synchronized (TestToHostMapping.get().getTestsLock()) {
            TestToHostMapping.get().getTestsLock().notifyAll();
        }

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