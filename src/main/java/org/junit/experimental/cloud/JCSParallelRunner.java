package org.junit.experimental.cloud;

import java.util.logging.Level;
import java.util.logging.Logger;

import at.ac.tuwien.infosys.jcloudscale.configuration.JCloudScaleConfiguration;
import at.ac.tuwien.infosys.jcloudscale.configuration.JCloudScaleConfigurationBuilder;
import at.ac.tuwien.infosys.jcloudscale.vm.JCloudScaleClient;
import at.ac.tuwien.infosys.jcloudscale.vm.docker.DockerCloudPlatformConfiguration;
import org.junit.experimental.cloud.policies.SamplePolicy;
import org.junit.experimental.cloud.scheduling.JCSParallelScheduler;
import org.junit.runner.Description;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;

/**
 * Not using the RunnerScheduler method the instantiation and execution happen
 * at the same time (actually in the same place)
 * 
 * @author gambi
 *
 */
public class JCSParallelRunner extends JCSRunner {

    public JCSParallelRunner(final Class<?> klass) throws InitializationError {
        super(klass);

        // Default Configuration Setting for JCS - This also means scaling
        // policy !
        // JCloudScaleConfiguration config = new
        // JCloudScaleConfigurationBuilder(
        // new LocalCloudPlatformConfiguration()).with(new SamplePolicy())
        // .withLogging(Level.OFF).build();

        JCloudScaleConfiguration config = new JCloudScaleConfigurationBuilder(
                new DockerCloudPlatformConfiguration(
                        "http://192.168.56.101:2375", "",
                        "alessio/jcs:0.4.6-SNAPSHOT-SHADED", "", ""))
                                .with(new SamplePolicy(10, 1)) // Max 4 hosts, 1
                                                               // MIP for each
                                .withCommunicationServerPublisher(false)
                                .withMQServer("192.168.56.101", 61616)
                                .withLoggingClient(Level.OFF)
                                .withLoggingServer(Level.OFF).build();

        JCloudScaleClient.setConfiguration(config);

        // int testLimit = 1;
        // int threadLimit = 10;
        // setScheduler(new JCSParallelScheduler(klass, testLimit,
        // threadLimit));

        // HARD LIMIT: limits at the same time thread and test count
        setScheduler(new JCSParallelScheduler(klass, 10));
    }

    @Override
    protected Object createTest() throws Exception {
        System.out.println(
                Thread.currentThread() + "JCSParallelRunner.createTest()");
        return super.createTest();
    }

    @Override
    protected Description describeChild(FrameworkMethod method) {
        Description d = super.describeChild(method);
        System.out.println(
                Thread.currentThread() + "JCSParallelRunner.describeChild() "
                        + d.getClassName() + "." + d.getMethodName());
        return d;
    }

    @Override
    public String toString() {
        System.out.println(Thread.currentThread() + "JCSParallelRunner - ");
        return super.toString();
    }

}
