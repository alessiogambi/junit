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
import jnr.ffi.Struct.caddr_t;
import org.junit.runner.Description;

//@Logged
public class TestToHostMapping {

    private static final String INITIAL = "INITIAL";

    private static final String SCHEDULED = "SCHEDULED";

    private static final String DEPLOYED = "DEPLOYED";

    private static final String STARTED = "STARTED";

    private static final String FINISHED = "FINISHED";

    private static final String FAILED = "FAILED";

    private static TestToHostMapping INSTANCE;

    @SuppressWarnings("rawtypes")
    private Set<Class> testClasses = new HashSet<Class>();

    /*
     * TODO USE at.ac.tuwien.infosys.jcloudscale.utility.ReferenceHashmap
     * instead USE CO id instead of references !
     */

    private Map<IHost, Set<ClientCloudObject>> hostToTestMapping = new ConcurrentHashMap<IHost, Set<ClientCloudObject>>();

    private Map<ClientCloudObject, IHost> testToHostMapping = new ConcurrentHashMap<ClientCloudObject, IHost>();

    private Map<IHost, AtomicInteger> runningTestOnHostMapping = new ConcurrentHashMap<IHost, AtomicInteger>();

    private Map<IHost, AtomicInteger> deployedTestOnHostMapping = new ConcurrentHashMap<IHost, AtomicInteger>();

    private Map<Description, ClientCloudObject> descriptionToCloudObjectMapping = new ConcurrentHashMap<Description, ClientCloudObject>();

    private Map<Description, String> descriptionToTestStatusMapping = new ConcurrentHashMap<Description, String>();

    private synchronized void updateTests(Description description,
            String status, Object _proxyObject) {

        if (!descriptionToTestStatusMapping.containsKey(description)) {
            descriptionToTestStatusMapping.put(description, INITIAL);
        }

        ClientCloudObject cloudObject = null;
        IHost host = null;
        String previousState = null;
        Object proxyObject = null;
        //
        int deployedTests = -1;
        int runningTests = -1;
        switch (status) {
        case INITIAL:
            // Do nothing - This actually should never happen
            throw new RuntimeException("Test is in wrong state " + status);
        case SCHEDULED:
            // Update the status
            previousState = descriptionToTestStatusMapping.put(description,
                    status);
            break;
        case DEPLOYED:
            // Update the status
            previousState = descriptionToTestStatusMapping.put(description,
                    status);

            proxyObject = _proxyObject;

            cloudObject = CloudManager.getInstance()
                    .getClientCloudObject(proxyObject);
            // This cannot be null
            host = testToHostMapping.get(cloudObject);
            //
            deployedTests = deployedTestOnHostMapping.get(host)
                    .incrementAndGet();

            System.out.println(
                    "TestToHostMapping.updateTests() Tests deployed on " + host
                            + " " + deployedTests);

            // TODO Link Description and CloudObject via ProxyObject (theTest) -
            // Why is this needed anyway ?!
            descriptionToCloudObjectMapping.put(description, cloudObject);

            break;

        case STARTED:
            try {
                // Update the status
                previousState = descriptionToTestStatusMapping.put(description,
                        status);

                cloudObject = descriptionToCloudObjectMapping.get(description);

                // This cannot be null
                host = testToHostMapping.get(cloudObject);
                //
                deployedTests = deployedTestOnHostMapping.get(host)
                        .decrementAndGet();

                runningTests = runningTestOnHostMapping.get(host)
                        .incrementAndGet();
                //
                System.out.println(
                        "TestToHostMapping.updateTests() Tests deployed on "
                                + host + " " + deployedTests);
                System.out.println(
                        "TestToHostMapping.updateTests() Tests running on "
                                + host + " " + runningTests);
            } catch (Throwable e) {
                e.printStackTrace();
                throw e;
            }
            break;

        case FINISHED:
            try {
                // Update the status
                previousState = descriptionToTestStatusMapping.put(description,
                        status);

                cloudObject = descriptionToCloudObjectMapping.get(description);

                // This cannot be null
                host = testToHostMapping.get(cloudObject);

                runningTests = runningTestOnHostMapping.get(host)
                        .decrementAndGet();
                //
                System.out.println(
                        "TestToHostMapping.updateTests() Tests running on "
                                + host + " " + runningTests);
                // // At this point we can also do the clean up at app level
                descriptionToCloudObjectMapping.keySet().remove(cloudObject);
                testToHostMapping.keySet().remove(cloudObject);
                hostToTestMapping.get(host).remove(cloudObject);
                //
            } catch (Throwable e) {
                e.printStackTrace();
                throw e;
            }
            break;
        }

        // Summary
        System.out.println("TestToHostMapping.updateTests() SUMMARY:\n"//
                + "\t description " + description + "\n" //
                + "\t description.full " + description.getClassName() + "."
                + description.getMethodName() + "\n"//
                + "\t proxyObject " + proxyObject + "\n"//
                + "\t state " + previousState + "--> " + status);

    }

    public synchronized static TestToHostMapping get() {
        if (INSTANCE == null)
            INSTANCE = new TestToHostMapping();
        return INSTANCE;
    }

    public void testScheduled(Description description) {
        updateTests(description, SCHEDULED, null);
    }

    public void testDeployed(Description description, Object theTest) {
        updateTests(description, DEPLOYED, theTest);
    }

    public void testStarts(Description description) {
        updateTests(description, STARTED, null);
    }

    public void testFinishes(Description description) {
        updateTests(description, FINISHED, null);
    }

    // private synchronized void updateTestObject(Object test, String status) {
    //
    // System.out.println(
    // "TestToHostMapping.updateTestObject() WARN NOT YET IMPLEMENTED !");
    //
    // // if (SCHEDULED.equals(status)) {
    // // System.out.println(test + " initial --> " + status);
    // // return;
    // // } else if (DEPLOYED.equals(status)) {
    // // System.out.println(test + " " + SCHEDULED + " --> " + status);
    // // return;
    // // }
    // //
    // // // The object that we pass along is the actual test class object, and
    // // we
    // // // assume that there is a different instance for a different test
    // // // method!
    // // ClientCloudObject cloudObject = CloudManager.getInstance()
    // // .getClientCloudObject(test);
    //
    // //
    // // // Update the status - Note this
    // // String previousState =
    // // descriptionToCloudObjectMapping.put(cloudObject,
    // // status);
    // //
    // // // For Deployed this should be aready deployed !
    // // System.out.println(test + " " + previousState + " --> " + status);
    // //
    // // // Update the count
    // // IHost host = testToHostMapping.get(cloudObject);
    // //
    // // AtomicInteger runningTest = null;
    // // AtomicInteger deployedTest = null;
    // //
    // // try {
    // // runningTest = runningTestOnHostMapping.containsKey(host)
    // // ? runningTestOnHostMapping.get(host) : null;
    // //
    // // deployedTest = deployedTestOnHostMapping.containsKey(host)
    // // ? deployedTestOnHostMapping.get(host) : null;
    // //
    // // } catch (NullPointerException npe) {
    // // System.err.println(
    // // "TestToHostMapping.updateTestObjectByDescription() DUMP:\n"
    // // + "HOST IS " + host + " For " + test
    // // + " Cloud Object " + cloudObject
    // // + " and proxy object " + test + "\n" +
    // //
    // // "\n------ \ntestToHostMapping full content for "
    // // + Thread.currentThread() + " \n "
    // // + testToHostMapping + "\n------- \n"
    // // + "\n------ \nhostToTestMapping full content for "
    // // + Thread.currentThread() + " \n "
    // // + hostToTestMapping + "\n-------");
    // //
    // // }
    // //
    // // if (runningTest == null)
    // // return;
    // //
    // // switch (status) {
    // //
    // // case STARTED:
    // // runningTest.incrementAndGet();
    // // deployedTest.decrementAndGet();
    // //
    // // break;
    // //
    // // case FINISHED:
    // // runningTest.decrementAndGet();
    // // // At this point we can also do the clean up at app level
    // // descriptionToCloudObjectMapping.keySet().remove(cloudObject);
    // // testToHostMapping.keySet().remove(cloudObject);
    // // hostToTestMapping.get(host).remove(cloudObject);
    // //
    // // break;
    // // }
    // }
    //
    // public void testScheduled(Object test) {
    // updateTestObject(test, SCHEDULED);
    // }
    //
    // public void testDeployed(Object test) {
    // updateTestObject(test, DEPLOYED);
    // }
    //
    // public void testStarts(Object test) {
    // updateTestObject(test, STARTED);
    // }
    //
    // public void testFinishes(Object test) {
    // updateTestObject(test, FINISHED);
    //
    // System.out.println("Wake up waiting threads on TestsLock");
    // synchronized (getTestsLock()) {
    // getTestsLock().notifyAll();
    // }
    // }

    public void registerHost(IHost host) {
        hostToTestMapping.put(host, new HashSet<ClientCloudObject>());
        runningTestOnHostMapping.put(host, new AtomicInteger());
        deployedTestOnHostMapping.put(host, new AtomicInteger());
    }

    public void deregisterHost(IHost host) {
        hostToTestMapping.remove(host);
        runningTestOnHostMapping.remove(host);
        deployedTestOnHostMapping.remove(host);
    }

    /**
     * Unsafe: Assume registerHost was already called we store the info about
     * which test is deployed where. This one is called by the SamplePolicy
     * 
     * @param selectedHost
     * @param cloudObject
     */
    public void deployTestObjectToHost(IHost selectedHost,
            ClientCloudObject cloudObject) {

        hostToTestMapping.get(selectedHost).add(cloudObject);
        testToHostMapping.put(cloudObject, selectedHost);

        System.out
                .println("deployTestObjectToHost() \n" + Thread.currentThread()
                        + " Deploy Test " + cloudObject + " to " + selectedHost
                        + "==" + hostToTestMapping.get(selectedHost));
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

    /**
     * Unregistered classes are managed as plain CO therefore no specific
     * constraints on their behavior will be enforced
     * 
     * @param clazz
     */
    public void registerTestClass(Class<?> clazz) {
        System.out.println("TestToHostMapping.registerTestClass() " + clazz);
        synchronized (testClasses) {
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

    public synchronized int countRunningTestsOfTypeForHost(
            Class<?> cloudObjectClass, IHost host) {
        int count = 0;
        for (ClientCloudObject cloudObject : getTestsForHost(host)) {
            if (cloudObject.getCloudObjectClass().equals(cloudObjectClass)) {
                if (STARTED.equals(
                        descriptionToCloudObjectMapping.get(cloudObject)))
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
                if (SCHEDULED.equals(
                        descriptionToCloudObjectMapping.get(cloudObject)))
                    count++;
            }
        }
        return count;
    }

    // private int concurrentTestsLimit = -1;
    //
    // public void setConcurrentTestsLimit(int concurrentTestsLimit) {
    // this.concurrentTestsLimit = concurrentTestsLimit;
    // }
    //
    // public int getConcurrentTestsLimit() {
    // return concurrentTestsLimit;
    // }
    //
    // private int threadLimit = -1;
    //
    // public int getThreadLimit() {
    // return threadLimit;
    // }
    //
    // public void setThreadLimit(int threadLimit) {
    // this.threadLimit = threadLimit;
    // }

}
