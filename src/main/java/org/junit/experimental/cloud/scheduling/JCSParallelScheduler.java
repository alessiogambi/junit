package org.junit.experimental.cloud.scheduling;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.experimental.cloud.shared.TestToHostMapping;
import org.junit.runners.model.RunnerScheduler;

/**
 * The scheduler is not aware of any deployment logic or constraints. It simply
 * creates new threads.
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

    public JCSParallelScheduler(Class<?> klass) {
        this.executorService = Executors.newCachedThreadPool(
                new NamedThreadFactory(klass.getSimpleName()));

        /*
         * Completion Service follows a producer/consumer philosophy: as soon a
         * thread is done, it puts the result into a non blocking queue so that
         * the consumer can take it.
         */
        completionService = new ExecutorCompletionService<Void>(
                executorService);

        // Ok, this is what we actually need
        // Collect the results
        tasks = new LinkedList<Future<Void>>();

    }

    @Override
    public void schedule(final Runnable childStatement) {
        // This will eventually result in CO deployment
        tasks.offer(completionService.submit(childStatement, null));
    }

    @Override
    public void finished() {
        try {
            while (!tasks.isEmpty()) {
                Future<Void> finishedTask = completionService.take();
                // Whenever something - i.e., test - finishes take will unlock
                tasks.remove(finishedTask);
                // Notify Everybody !
                System.out.println(
                        "JCSParallelScheduler.finished() " + finishedTask);
                synchronized (TestToHostMapping.get().getTestsLock()) {
                    TestToHostMapping.get().getTestsLock().notifyAll();
                }
                System.out.println("JCSParallelScheduler.finished() "
                        + finishedTask + " Notify Done");

            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            while (!tasks.isEmpty())
                tasks.poll().cancel(true);
            executorService.shutdownNow();
        }
    }

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
            System.out.println(
                    "JCSParallelRunner.NamedThreadFactory.newThread() " + t);
            return t;
        }
    }

}