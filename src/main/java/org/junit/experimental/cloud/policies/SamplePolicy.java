package org.junit.experimental.cloud.policies;

import java.util.Random;

import at.ac.tuwien.infosys.jcloudscale.exception.JCloudScaleException;
import at.ac.tuwien.infosys.jcloudscale.policy.AbstractScalingPolicy;
import at.ac.tuwien.infosys.jcloudscale.vm.ClientCloudObject;
import at.ac.tuwien.infosys.jcloudscale.vm.IHost;
import at.ac.tuwien.infosys.jcloudscale.vm.IHostPool;
import org.junit.experimental.cloud.shared.TestToHostMapping;

/**
 * Assure that a CO is always deployed in an empty cloud host.
 * 
 * TODO Probably the Scheduler is the right place to put al this complex stuff,
 * and a semaphor is the right concurrent data structure to use and probably the
 * TestToHostMapping is the place to put all the crap
 * 
 * @author gambi
 *
 */
public class SamplePolicy extends AbstractScalingPolicy {

    // Define the overall number of hosts
    private int maxHosts = 1;

    // Define the max concurrent tests from the same class on the same host !
    private int maxConcurrentTestsFromSameTestClassPerHost = -1;

    // Define the max number of tests that can run in a host, but not limited to
    // the same test case
    private int maxConcurrentTestsPerHost = -1;

    // -1 means infinity
    public SamplePolicy(int maxHosts, int maxConcurrentTestsPerHost,
            int maxConcurrentTestMethodsPerHost) {
        super();
        this.maxHosts = maxHosts;
        this.maxConcurrentTestsPerHost = maxConcurrentTestsPerHost;
        this.maxConcurrentTestsFromSameTestClassPerHost = maxConcurrentTestMethodsPerHost;
    }

    // Map<IHost, Set<ClientCloudObject>> mapping = new HashMap<IHost,
    // Set<ClientCloudObject>>();
    private TestToHostMapping mapping = TestToHostMapping.get();

    private final Random random = new Random();

    /**
     * We use the scaling policy to block workers threads for test objects, so
     * we must remove synchronize. This might introduce problems with other data
     * structures !
     */

    // For hostPools
    private Object hostsLock = new Object();

    private IHost pickRandomOrStart(IHostPool pool) {
        int hostsCount = pool.getHostsCount();
        if (hostsCount > 0) {
            return (IHost) pool.getHosts().toArray()[random
                    .nextInt(hostsCount)];
        } else {
            // Starting a new host. This shall be synchronized anyway
            // right ,if before was synch the start for the moment we
            // keep the same semantic
            return pool.startNewHost();
        }
    }

    /**
     * Either there is a free host or you can start a new one
     * 
     * @param test
     * @param pool
     * @return
     */
    private boolean canDeployTest(ClientCloudObject test, IHostPool pool) {

        // This is to avoid that others can change pool in the meanwhile
        // System.out.println(
        // "SamplePolicy.canDeployTest() " + Thread.currentThread());

        synchronized (hostsLock) {

            for (IHost host : pool.getHosts()) {
                // Count how many test methods from the same class are running
                // or scheduled (just to about to start)
                int sum = mapping.countRunningTestsOfTypeForHost(
                        test.getCloudObjectClass(), host)
                        + mapping.countScheduledTestsOfTypeForHost(
                                test.getCloudObjectClass(), host);

                boolean maxConcurrentTestMethodsPerHostOk = maxConcurrentTestsFromSameTestClassPerHost < 1
                        || (maxConcurrentTestsFromSameTestClassPerHost >= 1
                                && sum < maxConcurrentTestsFromSameTestClassPerHost);

                if (!maxConcurrentTestMethodsPerHostOk)
                    continue;

                // Count how many test methods are running or scheduled (just to
                // about to start)
                int totalSum = mapping.countRunningTestsForHost(host)
                        + mapping.countScheduledTestsForHost(host);

                boolean maxConcurrentTestsPerHostOk = maxConcurrentTestsPerHost < 1
                        || (maxConcurrentTestsPerHost >= 1
                                && totalSum < maxConcurrentTestsPerHost);

                if (!maxConcurrentTestsPerHostOk) {
                    continue;
                }
                // here both are true
                return true;
            }

            // System.out.println(
            // "SamplePolicy.canDeployTest() 3 " + Thread.currentThread());

            // Here we need to start an host on our own and then return true for
            // this thread !

            if (maxHosts < 1
                    || (maxHosts >= 1 && pool.getHostsCount() < maxHosts)) {
                IHost host = pool.startNewHost();
                mapping.registerHost(host);
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public /* synchronized */ IHost selectHost(ClientCloudObject cloudObject,
            IHostPool pool) {

        /*
         * Block the thread until the conditions for its execution are
         * satisfied, but only if it carries a Test
         */
        if (mapping.isTestClass(cloudObject.getCloudObjectClass())) {
            synchronized (TestToHostMapping.get().getTestsLock()) {
                while (!canDeployTest(cloudObject, pool)) {
                    try {
                        // System.out.println("SamplePolicy.selectHost()"
                        // + Thread.currentThread()
                        // + " Waiting for lock.");
                        TestToHostMapping.get().getTestsLock().wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        throw new JCloudScaleException(e);
                    }
                }

                /*
                 * If a thread arrives here then it is safe to assume that there
                 * is and actual place for it. This is true for every thread
                 * that arrive here and might include the possibility to we can
                 * start new machines. We still need to synch because other
                 * threads not related to testing classes might be active and
                 * cause host changes
                 */

                // Try to find an available host that can host this test
                // Note that the number of hosts might vary !!
                synchronized (hostsLock) {
                    for (IHost host : pool.getHosts()) {

                        int sum = mapping.countRunningTestsOfTypeForHost(
                                cloudObject.getCloudObjectClass(), host)
                                + mapping.countScheduledTestsOfTypeForHost(
                                        cloudObject.getCloudObjectClass(),
                                        host);
                        int totalSum = mapping.countRunningTestsForHost(host)
                                + mapping.countScheduledTestsForHost(host);

                        boolean maxConcurrentTestMethodsPerHostOk = maxConcurrentTestsFromSameTestClassPerHost < 1
                                || (maxConcurrentTestsFromSameTestClassPerHost >= 1
                                        && sum < maxConcurrentTestsFromSameTestClassPerHost);

                        // if (!maxConcurrentTestMethodsPerHostOk)
                        // System.out.println("SamplePolicy.selectHost()"
                        // + Thread.currentThread()
                        // + " too many tests on the host ");
                        boolean maxConcurrentTestsPerHostOk = maxConcurrentTestsPerHost < 1
                                || (maxConcurrentTestsPerHost >= 1
                                        && totalSum < maxConcurrentTestsPerHost);
                        // if (!maxConcurrentTestsPerHostOk)
                        // System.out.println("SamplePolicy.selectHost()"
                        // + Thread.currentThread()
                        // + " too many tests methods for the same test on the
                        // host ");
                        if (maxConcurrentTestMethodsPerHostOk
                                && maxConcurrentTestsPerHostOk) {

                            mapping.deployTestObjectToHost(host, cloudObject);
                            TestToHostMapping.get().getTestsLock().notifyAll();
                            return host;
                        }
                    }
                }

                // Not sure if here we shall be do this or not
                // System.out.println(
                // "SamplePolicy.selectHost() STARTING A NEW HOST");
                // No hosts cannot be found, we should be able to start a
                // new one.
                // TODO Will this be ok ?! I am not sure !
                synchronized (hostsLock) {
                    IHost selectedHost = pool.startNewHost();
                    mapping.registerHost(selectedHost);

                    // System.out.println("SamplePolicy.selectHost() "
                    // + Thread.currentThread().getName() + " deploy CO "
                    // + cloudObject + " of class "
                    // + cloudObject.getCloudObjectClass() + " on "
                    // + selectedHost.getIpAddress());

                    // Bookkeeping
                    mapping.deployTestObjectToHost(selectedHost, cloudObject);
                    TestToHostMapping.get().getTestsLock().notifyAll();
                    return selectedHost;
                }
            }
        } else {
            synchronized (hostsLock) {
                return pickRandomOrStart(pool);
            }
        }
    }

    @Override
    public boolean scaleDown(IHost scaledHost, IHostPool hostPool) {
        boolean scaleDownDecision = false;

        if (scaleDownDecision) {
            mapping.deregisterHost(scaledHost);
        }
        return scaleDownDecision;
    }

}