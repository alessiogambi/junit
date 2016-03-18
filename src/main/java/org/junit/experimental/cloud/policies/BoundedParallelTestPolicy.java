package org.junit.experimental.cloud.policies;

import at.ac.tuwien.infosys.jcloudscale.policy.AbstractScalingPolicy;
import at.ac.tuwien.infosys.jcloudscale.vm.ClientCloudObject;
import at.ac.tuwien.infosys.jcloudscale.vm.IHost;
import at.ac.tuwien.infosys.jcloudscale.vm.IHostPool;

/**
 * Assure that only up to N tests are concurrently running in each host. Start a
 * new CH when none are available.
 * 
 * Question is: when a CO is removed from a CH ?!
 * 
 * @author gambi
 *
 */
public class BoundedParallelTestPolicy extends AbstractScalingPolicy {

    private int UB;

    public BoundedParallelTestPolicy(int maxConcurrentTestPerHost) {
        this.UB = maxConcurrentTestPerHost;
    }

    public BoundedParallelTestPolicy() {
        this(1);
    }

    @Override
    public synchronized IHost selectHost(ClientCloudObject cloudObject,
            IHostPool pool) {
        // TODO
        return null;
    }

    @Override
    public boolean scaleDown(IHost scaledHost, IHostPool hostPool) {
        return false;
    }

}