package org.junit.experimental.cloud.shared;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import at.ac.tuwien.infosys.jcloudscale.management.CloudManager;
import at.ac.tuwien.infosys.jcloudscale.vm.ClientCloudObject;
import at.ac.tuwien.infosys.jcloudscale.vm.IHost;
import org.junit.internal.runners.statements.InvokeMethod;
import org.junit.runner.Description;

// TODO Check thread safety !
public class TestToHostMapping {

    private static final String SCHEDULED = "SCHEDULED";

    private static final String STARTED = "STARTED";

    private static final String FINISHED = "FINISHED";

    private static final String FAILED = "FAILED";

    private static TestToHostMapping INSTANCE;

    @SuppressWarnings("rawtypes")
    private Set<Class> testClasses = new HashSet<Class>();

    // Probably this is a duplicate info but cannot see other solution now
    // Likewise we can rely only on proxy and excplicitly on cloud object !
    private Map<IHost, Set<ClientCloudObject>> hostToTestMapping = new ConcurrentHashMap<IHost, Set<ClientCloudObject>>();

    /*
     * TODO USE at.ac.tuwien.infosys.jcloudscale.utility.ReferenceHashmap
     * instead USE CO id instead of references !
     */
    private Map<ClientCloudObject, IHost> testToHostMapping = new ConcurrentHashMap<ClientCloudObject, IHost>();

    private Map<IHost, AtomicInteger> runningTestOnHostMapping = new ConcurrentHashMap<IHost, AtomicInteger>();

    private Map<IHost, AtomicInteger> scheduledTestOnHostMapping = new ConcurrentHashMap<IHost, AtomicInteger>();

    private Map<ClientCloudObject, String> testObjectsToStatuMapping = new ConcurrentHashMap<ClientCloudObject, String>();

    private TestToHostMapping() {

    }

    // FIXME UNSAFE !! NON-DEFENSIVE
    private synchronized void updateTestObjectByDescription(
            Description description, String status) {

        if (description.getStatement() instanceof InvokeMethod) {
            Object proxyObject = ((InvokeMethod) description.getStatement())
                    .getTarget();

            ClientCloudObject cloudObject = CloudManager.getInstance()
                    .getClientCloudObject(proxyObject);

            // Update the status - Note this
            String previousState = testObjectsToStatuMapping.put(cloudObject,
                    status);

            // System.out.println("\t\t\t" + description.getClassName() + " "
            // + description.getDisplayName() + " "
            // + description.getMethodName() + " Test: " + cloudObject
            // + " " + previousState + " --> " + status);

            // Update the count
            IHost host = testToHostMapping.get(cloudObject);

            AtomicInteger runningTest = null;

            try {
                runningTest = runningTestOnHostMapping.containsKey(host)
                        ? runningTestOnHostMapping.get(host) : null;
            } catch (NullPointerException npe) {
                npe.printStackTrace();
                System.out.println(
                        "TestToHostMapping.updateTestObjectByDescription() DUMP:");
                System.out.println("HOST IS " + host + " For "
                        + description.getMethodName() + " Cloud Object "
                        + cloudObject + " and proxy object " + proxyObject);
                System.err.println(
                        "\n------ \ntestToHostMapping full content for "
                                + Thread.currentThread() + " \n "
                                + testToHostMapping + "\n-------");

                System.err.println(
                        "\n------ \nhostToTestMapping full content for "
                                + Thread.currentThread() + " \n "
                                + hostToTestMapping + "\n-------");

            }

            if (runningTest == null)
                return;

            switch (status) {
            case STARTED:
                runningTest.incrementAndGet();
                // Remove the scheduled counter... this should be atomic !
                AtomicInteger scheduledTest = null;

                scheduledTest = scheduledTestOnHostMapping.containsKey(host)
                        ? scheduledTestOnHostMapping.get(host) : null;
                if (scheduledTest != null)
                    scheduledTest.decrementAndGet();

                break;

            case FINISHED:
                runningTest.decrementAndGet();
                // At this point we can also do the clean up at app level
                testObjectsToStatuMapping.keySet().remove(cloudObject);
                testToHostMapping.keySet().remove(cloudObject);
                hostToTestMapping.get(host).remove(cloudObject);

                break;
            }
        }

    }

    public synchronized static TestToHostMapping get() {
        if (INSTANCE == null)
            INSTANCE = new TestToHostMapping();
        return INSTANCE;
    }

    public void testStarts(Description description) {
        updateTestObjectByDescription(description, STARTED);
    }

    public void testFinishes(Description description) {
        updateTestObjectByDescription(description, FINISHED);
    }

    public void testFails(Description description) {
        updateTestObjectByDescription(description, FAILED);
    }

    public void registerHost(IHost host) {
        hostToTestMapping.put(host, new HashSet<ClientCloudObject>());
        runningTestOnHostMapping.put(host, new AtomicInteger());
        scheduledTestOnHostMapping.put(host, new AtomicInteger());
    }

    public void deregisterHost(IHost host) {
        hostToTestMapping.remove(host);
        runningTestOnHostMapping.remove(host);
        scheduledTestOnHostMapping.remove(host);
    }

    /**
     * Unsafe: Assume registerHost was already called we store the info about
     * which test is deployed where
     * 
     * @param selectedHost
     * @param cloudObject
     */
    public void deployTestObjectToHost(IHost selectedHost,
            ClientCloudObject cloudObject) {

        // System.out.println("TestToHostMapping.deployTestObjectToHost() \n"
        // + Thread.currentThread() + " Register " + cloudObject + " to "
        // + selectedHost + " " + hostToTestMapping.get(selectedHost));
        hostToTestMapping.get(selectedHost).add(cloudObject);
        testToHostMapping.put(cloudObject, selectedHost);
        testObjectsToStatuMapping.put(cloudObject, SCHEDULED);
        scheduledTestOnHostMapping.get(selectedHost).incrementAndGet();
    }

    // TODO Shall we synch ? not sure the behavior of
    // Collections.unmodifiableSet
    public synchronized Collection<ClientCloudObject> getTestsForHost(
            IHost host) {
        if (hostToTestMapping.containsKey(host))
            return Collections.unmodifiableSet(hostToTestMapping.get(host));
        return null;
    }

    public int countRunningTestsForHost(IHost host) {
        return runningTestOnHostMapping.containsKey(host)
                ? runningTestOnHostMapping.get(host).get() : 0;
    }

    public int countScheduledTestsForHost(IHost host) {
        return scheduledTestOnHostMapping.containsKey(host)
                ? scheduledTestOnHostMapping.get(host).get() : 0;
    }

    public void registerTestClass(Class clazz) {
        synchronized (testClasses) {
            // System.out
            // .println("TestToHostMapping.registerTestClass() " + clazz);
            testClasses.add(clazz);
        }

    }

    public boolean isTestClass(Class<?> cloudObjectClass) {
        synchronized (testClasses) {
            return testClasses.contains(cloudObjectClass);
        }
    }

    private Object testLock = new Object();

    public Object getTestsLock() {
        return testLock;
    }

    // Synch on what ?!
    public synchronized int countRunningTestsOfTypeForHost(
            Class<?> cloudObjectClass, IHost host) {
        int count = 0;
        for (ClientCloudObject cloudObject : getTestsForHost(host)) {
            if (cloudObject.getCloudObjectClass().equals(cloudObjectClass)) {
                if (STARTED.equals(testObjectsToStatuMapping.get(cloudObject)))
                    count++;
            }
        }
        return count;
    }

    public synchronized int countScheduledTestsOfTypeForHost(
            Class<?> cloudObjectClass, IHost host) {
        int count = 0;
        for (ClientCloudObject cloudObject : getTestsForHost(host)) {
            if (cloudObject.getCloudObjectClass().equals(cloudObjectClass)) {
                if (SCHEDULED
                        .equals(testObjectsToStatuMapping.get(cloudObject)))
                    count++;
            }
        }
        return count;
    }

    private int concurrentTestsLimit = -1;

    public void setConcurrentTestsLimit(int concurrentTestsLimit) {
        this.concurrentTestsLimit = concurrentTestsLimit;
    }

    public int getConcurrentTestsLimit() {
        return concurrentTestsLimit;
    }

    private int threadLimit = -1;

    public int getThreadLimit() {
        return threadLimit;
    }

    public void setThreadLimit(int threadLimit) {
        this.threadLimit = threadLimit;
    }
}
