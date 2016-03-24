package org.junit.experimental.cloud.shared;

import java.util.ArrayList;
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

    private static final String DEPLOYED = "DEPLOYED";

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

    private Map<IHost, AtomicInteger> deployedTestOnHostMapping = new ConcurrentHashMap<IHost, AtomicInteger>();

    private Map<ClientCloudObject, String> testObjectsToStatuMapping = new ConcurrentHashMap<ClientCloudObject, String>();

    private TestToHostMapping() {

    }

    private synchronized void updateTestObjectByDescription(
            Description description, String status) {

        if (SCHEDULED.equals(status)) {
            System.out.println("\t\t\t" + description.getClassName() + " "
                    + description.getDisplayName() + " "
                    + description.getMethodName() + " Test: no CO yet "
                    + "null/initial --> " + status);
            return;
        } else if (DEPLOYED.equals(status)) {
            System.out.println("\t\t\t" + description.getClassName() + " "
                    + description.getDisplayName() + " "
                    + description.getMethodName() + " Test: no CO yet "
                    + SCHEDULED + " --> " + status);
            return;
        }

        if (description.getStatement() instanceof InvokeMethod) {

            Object proxyObject = ((InvokeMethod) description.getStatement())
                    .getTarget();

            ClientCloudObject cloudObject = CloudManager.getInstance()
                    .getClientCloudObject(proxyObject);

            // Update the status - Note this
            String previousState = testObjectsToStatuMapping.put(cloudObject,
                    status);

            // For Deployed this should be aready deployed !
            System.out.println("\t\t\t" + description.getClassName() + " "
                    + description.getDisplayName() + " "
                    + description.getMethodName() + " Test: " + cloudObject
                    + " " + previousState + " --> " + status);

            // Update the count
            IHost host = testToHostMapping.get(cloudObject);

            AtomicInteger runningTest = null;
            AtomicInteger deployedTest = null;

            try {
                runningTest = runningTestOnHostMapping.containsKey(host)
                        ? runningTestOnHostMapping.get(host) : null;

                deployedTest = deployedTestOnHostMapping.containsKey(host)
                        ? deployedTestOnHostMapping.get(host) : null;

            } catch (NullPointerException npe) {
                npe.printStackTrace();
                System.err.println(
                        "TestToHostMapping.updateTestObjectByDescription() DUMP:\n"
                                + "HOST IS " + host + " For "
                                + description.getMethodName() + " Cloud Object "
                                + cloudObject + " and proxy object "
                                + proxyObject + "\n" +

                                "\n------ \ntestToHostMapping full content for "
                                + Thread.currentThread() + " \n "
                                + testToHostMapping + "\n------- \n"
                                + "\n------ \nhostToTestMapping full content for "
                                + Thread.currentThread() + " \n "
                                + hostToTestMapping + "\n-------");

            }

            if (runningTest == null)
                return;

            switch (status) {

            case STARTED:
                runningTest.incrementAndGet();
                deployedTest.decrementAndGet();

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

    public void testDeployed(Description description) {
        updateTestObjectByDescription(description, DEPLOYED);

    }

    public void testScheduled(Description description) {
        updateTestObjectByDescription(description, SCHEDULED);
    }

    public void testFinishes(Description description) {
        updateTestObjectByDescription(description, FINISHED);
    }

    public void testFails(Description description) {
        updateTestObjectByDescription(description, FAILED);
    }

    // JUnit 3 Compatibility

    private synchronized void updateTestObject(Object test, String status) {

        System.out.println("TestToHostMapping.updateTestObject() " + test);

        if (SCHEDULED.equals(status)) {
            System.out.println("\t\t\t" + test + " Test: no CO yet "
                    + "null/initial --> " + status);
            return;
        } else if (DEPLOYED.equals(status)) {
            System.out.println(
                    "\t\t\t" + test + " " + SCHEDULED + " --> " + status);
            return;
        }

        // The object that we pass along is the actual test class object, and we
        // assume that there is a different instance for a different test
        // method!
        ClientCloudObject cloudObject = CloudManager.getInstance()
                .getClientCloudObject(test);

        // Update the status - Note this
        String previousState = testObjectsToStatuMapping.put(cloudObject,
                status);

        // For Deployed this should be aready deployed !
        System.out.println("\t\t\t" + test + " Test: " + cloudObject + " "
                + previousState + " --> " + status);

        // Update the count
        IHost host = testToHostMapping.get(cloudObject);

        AtomicInteger runningTest = null;
        AtomicInteger deployedTest = null;

        try {
            runningTest = runningTestOnHostMapping.containsKey(host)
                    ? runningTestOnHostMapping.get(host) : null;

            deployedTest = deployedTestOnHostMapping.containsKey(host)
                    ? deployedTestOnHostMapping.get(host) : null;

        } catch (NullPointerException npe) {
            npe.printStackTrace();
            System.err.println(
                    "TestToHostMapping.updateTestObjectByDescription() DUMP:\n"
                            + "HOST IS " + host + " For " + test
                            + " Cloud Object " + cloudObject
                            + " and proxy object " + test + "\n" +

                            "\n------ \ntestToHostMapping full content for "
                            + Thread.currentThread() + " \n "
                            + testToHostMapping + "\n------- \n"
                            + "\n------ \nhostToTestMapping full content for "
                            + Thread.currentThread() + " \n "
                            + hostToTestMapping + "\n-------");

        }

        if (runningTest == null)
            return;

        switch (status) {

        case STARTED:
            runningTest.incrementAndGet();
            deployedTest.decrementAndGet();

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

    public void testScheduled(Object test) {
        updateTestObject(test, SCHEDULED);
    }

    public void testDeployed(Object test) {
        updateTestObject(test, DEPLOYED);
    }

    public void testStarts(Object test) {
        updateTestObject(test, STARTED);
    }

    public void testFinishes(Object test) {
        updateTestObject(test, FINISHED);
        //
        //
        System.out.println(
                "TestToHostMapping.testFinishes(). Notify waiting threads");

        synchronized (getTestsLock()) {
            getTestsLock().notifyAll();
        }
    }

    public void registerHost(IHost host) {
        System.out.println("TestToHostMapping.registerHost() " + host);
        hostToTestMapping.put(host, new HashSet<ClientCloudObject>());
        runningTestOnHostMapping.put(host, new AtomicInteger());
        deployedTestOnHostMapping.put(host, new AtomicInteger());
    }

    public void deregisterHost(IHost host) {
        System.out.println("TestToHostMapping.deregisterHost() " + host);
        hostToTestMapping.remove(host);
        runningTestOnHostMapping.remove(host);
        deployedTestOnHostMapping.remove(host);
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

        System.out.println("TestToHostMapping.deployTestObjectToHost() \n"
                + Thread.currentThread() + " Register " + cloudObject + " to "
                + selectedHost + " " + hostToTestMapping.get(selectedHost));
        hostToTestMapping.get(selectedHost).add(cloudObject);
        testToHostMapping.put(cloudObject, selectedHost);
        // This create the entry to host the status. and
        testObjectsToStatuMapping.put(cloudObject, DEPLOYED);
        // Increment deployed objects tests
        deployedTestOnHostMapping.get(selectedHost).incrementAndGet();
    }

    // TODO Shall we synch ? not sure the behavior of
    // Collections.unmodifiableSet
    public synchronized Collection<ClientCloudObject> getTestsForHost(
            IHost host) {
        if (hostToTestMapping.containsKey(host))
            return Collections.unmodifiableSet(hostToTestMapping.get(host));
        return new ArrayList<ClientCloudObject>();
    }

    public int countRunningTestsForHost(IHost host) {
        return runningTestOnHostMapping.containsKey(host)
                ? runningTestOnHostMapping.get(host).get() : 0;
    }

    public int countScheduledTestsForHost(IHost host) {
        return deployedTestOnHostMapping.containsKey(host)
                ? deployedTestOnHostMapping.get(host).get() : 0;
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
