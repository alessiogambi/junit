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
    private IHost canDeployTest(ClientCloudObject test, IHostPool pool) {

        synchronized (hostsLock) {

            for (IHost host : pool.getHosts()) {
                // Count how many test methods from the same class are running
                // or scheduled (just to about to start)

                int running = mapping.countRunningTestsOfTypeForHost(
                        test.getCloudObjectClass(), host);
                int scheduled = mapping.countScheduledTestsOfTypeForHost(
                        test.getCloudObjectClass(), host);

                int sum = running + scheduled;

                // System.out.println("------------------------------\n"
                // + "SamplePolicy.canDeployTest(): " + "Inside CH " + host
                // + " there are " + sum + "(" + running + "," + scheduled
                // + ") tests from the same class, and max is"
                // + maxConcurrentTestsFromSameTestClassPerHost + "\n"
                // + "------------------------------");

                boolean maxConcurrentTestMethodsPerHostOk = maxConcurrentTestsFromSameTestClassPerHost < 1
                        || (maxConcurrentTestsFromSameTestClassPerHost >= 1
                                && sum < maxConcurrentTestsFromSameTestClassPerHost);

                if (!maxConcurrentTestMethodsPerHostOk) {
                    continue;
                }

                // Count how many test methods are running or scheduled (just to
                // about to start)
                int totalRunning = mapping.countRunningTestsForHost(host);
                int totalScheduled = mapping.countScheduledTestsForHost(host);

                int totalSum = totalRunning + totalScheduled;

                boolean maxConcurrentTestsPerHostOk = maxConcurrentTestsPerHost < 1
                        || (maxConcurrentTestsPerHost >= 1
                                && totalSum < maxConcurrentTestsPerHost);

                // System.out.println("------------------------------\n"
                // + "SamplePolicy.canDeployTest(): " + "Inside CH " + host
                // + " there are TOTAL " + totalSum + "(" + totalRunning
                // + "," + totalScheduled
                // + ") tests from the same class, and totalMax is"
                // + maxConcurrentTestsPerHost + "\n"
                // + "------------------------------");

                if (!maxConcurrentTestsPerHostOk) {
                    continue;
                }

                // NOTE THIS ONE ! - This one sets the counters !
                mapping.deployTestObjectToHost(host, test);
                return host;
            }

            // Here we need to start an host on our own and then return true for
            // this thread !

            if (maxHosts < 1
                    || (maxHosts >= 1 && pool.getHostsCount() < maxHosts)) {
                IHost host = pool.startNewHost();
                mapping.registerHost(host);
                // System.out.println(Thread.currentThread()
                // + " SamplePolicy.canDeployTest() Start a new host "
                // + host);
                mapping.deployTestObjectToHost(host, test);
                return host;
            } else {
                // System.out.println(Thread.currentThread()
                // + " SamplePolicy.canDeployTest() Cannot deploy test "
                // + test);
                return null;
            }
        }
    }

    /**
     * This cannot be synchronized because it must be blocking !
     */
    @Override
    public /* synchronized */ IHost selectHost(ClientCloudObject cloudObject,
            IHostPool pool) {

        // System.out.println(Thread.currentThread()
        // + " SamplePolicy.selectHost() selecting host for"
        // + cloudObject);

        /*
         * Block the thread until the conditions for its execution are
         * satisfied, but only if it carries a Test
         */
        if (mapping.isTestClass(cloudObject.getCloudObjectClass())) {

            synchronized (mapping.getTestsLock()) {
                IHost host = null;
                while ((host = canDeployTest(cloudObject, pool)) == null) {
                    try {
                        // System.out.println(Thread.currentThread()
                        // + " Waiting on TestLock for available slots.");

                        mapping.getTestsLock().wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        throw new JCloudScaleException(e);
                    }
                }

                // System.out.println("SamplePolicy.selectHost() "
                // + Thread.currentThread().getName() + " deploy CO "
                // + cloudObject + " of class "
                // + cloudObject.getCloudObjectClass() + " on " + host);
                //
                mapping.getTestsLock().notifyAll();
                return host;
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