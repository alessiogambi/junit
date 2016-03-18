package org.junit.experimental.cloud.policies;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import com.google.common.collect.Iterables;

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

    private int maxHosts;

    private int maxConcurrentTestsPerHost;

    // -1 means infinity
    public SamplePolicy(int maxHosts, int maxConcurrentTestsPerHost) {
        super();
        this.maxHosts = maxHosts;
        this.maxConcurrentTestsPerHost = maxConcurrentTestsPerHost;
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
            System.out.println(
                    "SamplePolicy.selectHost() Starting new for for NON-Test CO");
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

            // System.out.println(
            // "SamplePolicy.canDeployTest() 1 " + Thread.currentThread());
            /*
             * Synchronize actual to local data, basically remove objects that
             * are not there anymore. This basically repeats the same for all
             * the waiting threads and should be optimized !
             */
            for (IHost host : pool.getHosts()) {
                Set<ClientCloudObject> hostObjects = new HashSet<ClientCloudObject>();
                Iterables.addAll(hostObjects, host.getCloudObjects());
                mapping.undeployTestsInsideHost(host, hostObjects);
            }

            // System.out.println(
            // "SamplePolicy.canDeployTest() 2 " + Thread.currentThread());

            for (IHost host : pool.getHosts()) {
                int sum = mapping.countRunningTestsForHost(host)
                        + mapping.countScheduledTestsForHost(host);
                if (maxConcurrentTestsPerHost < 1
                        || (maxConcurrentTestsPerHost >= 1
                                && sum < maxConcurrentTestsPerHost)) {

                    System.out.println("SamplePolicy.canDeployTest() host free "
                            + host + " for " + Thread.currentThread());
                    /*
                     * Has free space the host. Note that it might happen that
                     * the test will be not scheduled on that particular host!
                     */
                    return true;
                }
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
    public /* synchronized */IHost selectHost(ClientCloudObject cloudObject,
            IHostPool pool) {

        // Block the thread until the condition is satisfied but only if it
        // carries a Test
        if (mapping.isTestClass(cloudObject.getCloudObjectClass())) {
            // TODO Make this parametric on the host and introduce start host
            // here ? This should be implemented via TestMapping class !
            synchronized (TestToHostMapping.get().getTestsLock()) {
                while (!canDeployTest(cloudObject, pool)) {
                    try {
                        System.out.println("SamplePolicy.selectHost() "
                                + Thread.currentThread() + " WAITING !");

                        TestToHostMapping.get().getTestsLock().wait();

                        System.out.println("SamplePolicy.selectHost() "
                                + Thread.currentThread() + " UNLOCKED !");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        throw new JCloudScaleException(e);
                    }
                }

                System.out.println("SamplePolicy.selectHost() "
                        + Thread.currentThread() + " DEPLOYING TEST !");

                /*
                 * If a thread arrives here then it is safe to assume that there
                 * is and actual place for the test and for every thread, or we
                 * can start new machines. we still need to synch on this
                 * because other threads not related to testing classes might be
                 * active
                 */

                // Try to find an available host that can host this test
                // Note that the number of hosts might vary !!
                synchronized (hostsLock) {
                    for (IHost host : pool.getHosts()) {
                        if ((mapping.countRunningTestsForHost(host)
                                + mapping.countScheduledTestsForHost(
                                        host)) < maxConcurrentTestsPerHost) {

                            System.out.println("SamplePolicy.selectHost() "
                                    + Thread.currentThread().getName()
                                    + " deploy CO " + cloudObject + " of class "
                                    + cloudObject.getCloudObjectClass() + " on "
                                    + host.getIpAddress());
                            // Bookkeeping
                            mapping.deployTestObjectToHost(host, cloudObject);
                            TestToHostMapping.get().getTestsLock().notifyAll();
                            return host;
                        }
                    }
                }

                // Not sure if here we shall be do this or not
                System.out.println(
                        "SamplePolicy.selectHost() STARTING A NEW HOST");
                // No hosts cannot be found, we should be able to start a
                // new one.
                // TODO Will this be ok ?! I am not sure !
                synchronized (hostsLock) {
                    IHost selectedHost = pool.startNewHost();
                    mapping.registerHost(selectedHost);
                    System.out.println("SamplePolicy.selectHost() "
                            + Thread.currentThread().getName() + " deploy CO "
                            + cloudObject + " of class "
                            + cloudObject.getCloudObjectClass() + " on "
                            + selectedHost.getIpAddress());

                    // Bookkeeping
                    mapping.deployTestObjectToHost(selectedHost, cloudObject);
                    TestToHostMapping.get().getTestsLock().notifyAll();
                    return selectedHost;
                }
            }
        } else {
            System.out.println(
                    "SamplePolicy.selectHost() Not a test, just go on");
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