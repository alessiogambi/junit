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
import org.junit.experimental.cloud.LocalHost;
import org.junit.runner.Description;

// FIXME Use a logger here ... please !
// TODO Collect Statistics
// TODO Integrate with Events and Monitoring system of JCS
// TODO Live counter implemented a bit better !
//@Logged
public class TestToHostMapping {

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

    private Map<IHost, Map<Class<?>, AtomicInteger>> runningTestPerTypeOnHostMapping = new ConcurrentHashMap<IHost, Map<Class<?>, AtomicInteger>>();

    private Map<IHost, Map<Class<?>, AtomicInteger>> deployedTestPerTypeOnHostMapping = new ConcurrentHashMap<IHost, Map<Class<?>, AtomicInteger>>();

    private Map<Description, ClientCloudObject> descriptionToCloudObjectMapping = new ConcurrentHashMap<Description, ClientCloudObject>();

    private Map<Description, TestStatus> descriptionToTestStatusMapping = new ConcurrentHashMap<Description, TestStatus>();

    private TestToHostMapping() {
        // By default this host is there !
        registerHost(LocalHost.get());
    }

    private void updateTests(Description description, TestStatus status,
            Object _proxyObject) {

        // Wait for the lock
        synchronized (getTestsLock()) {
            if (!descriptionToTestStatusMapping.containsKey(description)) {
                descriptionToTestStatusMapping.put(description,
                        TestStatus.INITIAL);
            }

            ClientCloudObject cloudObject = null;
            IHost host = null;
            TestStatus previousState = null;
            Object proxyObject = null;
            //
            int totalDeployedTestsPerHost = -1;
            int totalRunningTestsPerHost = -1;
            int totalDeployedTestsOfTypePerHost = -1;
            int totalRunningTestsOfTypePerHost = -1;
            //
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

                // Check if its a LocalHost object
                if (cloudObject == null)
                    cloudObject = LocalHost.get()
                            .getClientCloudObject(proxyObject);

                // System.out.println("TestToHostMapping.updateTests() desc: "
                // + description + " co : " + cloudObject);
                // This cannot be null
                host = testToHostMapping.get(cloudObject);

                // System.out.println(
                // "TestToHostMapping.updateTests() Tests deployed on "
                // + host + " " + deployedTests);

                descriptionToCloudObjectMapping.put(description, cloudObject);

                break;

            case STARTED:
                try {
                    // Update the status
                    previousState = descriptionToTestStatusMapping
                            .put(description, status);

                    cloudObject = descriptionToCloudObjectMapping
                            .get(description);

                    // This cannot be null
                    host = testToHostMapping.get(cloudObject);
                    //
                    totalDeployedTestsPerHost = deployedTestOnHostMapping
                            .get(host).decrementAndGet();

                    totalRunningTestsPerHost = runningTestOnHostMapping
                            .get(host).incrementAndGet();

                    //
                    totalDeployedTestsOfTypePerHost = deployedTestPerTypeOnHostMapping
                            .get(host).get(cloudObject.getCloudObjectClass())
                            .decrementAndGet();
                    //
                    if (!runningTestPerTypeOnHostMapping.get(host)
                            .containsKey(cloudObject.getCloudObjectClass())) {
                        runningTestPerTypeOnHostMapping.get(host).put(
                                cloudObject.getCloudObjectClass(),
                                new AtomicInteger());
                    }
                    totalRunningTestsOfTypePerHost = runningTestPerTypeOnHostMapping
                            .get(host).get(cloudObject.getCloudObjectClass())
                            .incrementAndGet();

                    //
                    // System.out.println(
                    // "TestToHostMapping.updateTests() Tests deployed on "
                    // + host + " " + deployedTests);
                    // System.out.println(
                    // "TestToHostMapping.updateTests() Tests running on "
                    // + host + " " + runningTests);
                } catch (Throwable e) {
                    e.printStackTrace();
                    throw e;
                }
                break;

            case FINISHED:
                try {
                    // Update the status
                    previousState = descriptionToTestStatusMapping
                            .put(description, status);

                    cloudObject = descriptionToCloudObjectMapping
                            .get(description);

                    // This cannot be null
                    host = testToHostMapping.get(cloudObject);

                    totalRunningTestsPerHost = runningTestOnHostMapping
                            .get(host).decrementAndGet();

                    totalRunningTestsOfTypePerHost = runningTestPerTypeOnHostMapping
                            .get(host).get(cloudObject.getCloudObjectClass())
                            .decrementAndGet();
                    //
                    // System.out.println(
                    // "TestToHostMapping.updateTests() Tests running on "
                    // + host + " " + runningTests);
                    // // At this point we can also do the clean up at app level
                    descriptionToCloudObjectMapping.keySet()
                            .remove(cloudObject);
                    testToHostMapping.keySet().remove(cloudObject);
                    hostToTestMapping.get(host).remove(cloudObject);
                    //
                    // System.out.println("Wake up waiting threads on
                    // TestsLock");
                    //
                    getTestsLock().notifyAll();
                } catch (Throwable e) {
                    e.printStackTrace();
                    throw e;
                }
                break;
            }

            // Summary
            // System.out.println("TestToHostMapping.updateTests() SUMMARY:\n"//
            // + "\t description " + description + "\n" //
            // + "\t description.full " + description.getClassName() + "."
            // + description.getMethodName() + "\n"//
            // + "\t proxyObject " + proxyObject + "\n"//
            // + "\t state " + previousState + "--> " + status);

        }
    }

    public synchronized static TestToHostMapping get() {
        if (INSTANCE == null)
            INSTANCE = new TestToHostMapping();
        return INSTANCE;
    }

    public void testScheduled(Description description) {
        updateTests(description, TestStatus.SCHEDULED, null);
        // TODO Not clear on how to publish data around
        // TestLifeCycleEvent event = new TestLifeCycleEvent(description,
        // TestStatus.SCHEDULED);
        // try {
        // MonitoringMQHelper.getInstance().sendEvent(event);
        // } catch (JMSException e) {
        // e.printStackTrace();
        // }
    }

    public void testDeployed(Description description, Object theTest) {
        updateTests(description, TestStatus.DEPLOYED, theTest);
    }

    public void testStarts(Description description) {
        updateTests(description, TestStatus.STARTED, null);
    }

    public void testFinishes(Description description) {
        updateTests(description, TestStatus.FINISHED, null);
    }

    public void registerHost(IHost host) {
        hostToTestMapping.put(host, new HashSet<ClientCloudObject>());
        runningTestOnHostMapping.put(host, new AtomicInteger());
        deployedTestOnHostMapping.put(host, new AtomicInteger());
        //
        runningTestPerTypeOnHostMapping.put(host,
                new ConcurrentHashMap<Class<?>, AtomicInteger>());
        deployedTestPerTypeOnHostMapping.put(host,
                new ConcurrentHashMap<Class<?>, AtomicInteger>());

    }

    public void deregisterHost(IHost host) {
        hostToTestMapping.remove(host);
        runningTestOnHostMapping.remove(host);
        deployedTestOnHostMapping.remove(host);
        //
        runningTestPerTypeOnHostMapping.remove(host);
        deployedTestPerTypeOnHostMapping.remove(host);
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

        // Can we remove this tot ?
        int tot = deployedTestOnHostMapping.get(selectedHost).incrementAndGet();

        if (!deployedTestPerTypeOnHostMapping.get(selectedHost)
                .containsKey(cloudObject.getCloudObjectClass())) {
            deployedTestPerTypeOnHostMapping.get(selectedHost).put(
                    cloudObject.getCloudObjectClass(), new AtomicInteger());
        }
        int totPerType = deployedTestPerTypeOnHostMapping.get(selectedHost)
                .get(cloudObject.getCloudObjectClass()).incrementAndGet();

//        System.out.println(Thread.currentThread() + " Deploy Test "
//                + cloudObject + " to " + selectedHost + "=="
//                + hostToTestMapping.get(selectedHost));

//        System.out.println(Thread.currentThread()
//                + " Total tests deployed on host " + selectedHost + " is " + tot
//                + " per type " + totPerType);

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
        // System.out.println("TestToHostMapping.registerTestClass() " + clazz);
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

    // TODO Merge these two together !
    // Note that we always live in the assumption that 1 object 1 test method !
    public synchronized int countRunningTestsOfTypeForHost(Class<?> testType,
            IHost host) {
        // int count = 0;
        // for (ClientCloudObject cloudObject : getTestsForHost(host)) {
        // if (cloudObject.getCloudObjectClass().equals(testType)) {
        // if (TestStatus.STARTED.equals(
        // descriptionToCloudObjectMapping.get(cloudObject)))
        // count++;
        // }
        // }
        // return count;

        if (!runningTestPerTypeOnHostMapping.containsKey(host))
            return 0;
        if (!runningTestPerTypeOnHostMapping.get(host).containsKey(testType))
            return 0;

        return runningTestPerTypeOnHostMapping.get(host).get(testType)
                .intValue();
    }

    public synchronized int countScheduledTestsOfTypeForHost(Class<?> testType,
            IHost host) {
        // int count = 0;
        // for (ClientCloudObject cloudObject : getTestsForHost(host)) {
        // if (cloudObject.getCloudObjectClass().equals(cloudObjectClass)) {
        // if (TestStatus.SCHEDULED.equals(
        // descriptionToCloudObjectMapping.get(cloudObject)))
        // count++;
        // }
        // }

        if (!deployedTestPerTypeOnHostMapping.containsKey(host))
            return 0;
        if (!deployedTestPerTypeOnHostMapping.get(host).containsKey(testType))
            return 0;

        return deployedTestPerTypeOnHostMapping.get(host).get(testType)
                .intValue();
    }

}
