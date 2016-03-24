package org.junit.experimental.cloud;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import at.ac.tuwien.infosys.jcloudscale.configuration.JCloudScaleConfiguration;
import at.ac.tuwien.infosys.jcloudscale.configuration.JCloudScaleConfigurationBuilder;
import at.ac.tuwien.infosys.jcloudscale.vm.JCloudScaleClient;
import at.ac.tuwien.infosys.jcloudscale.vm.docker.DockerCloudPlatformConfiguration;
import org.junit.experimental.cloud.listeners.JCSJunitExecutionListener;
import org.junit.experimental.cloud.policies.SamplePolicy;
import org.junit.experimental.cloud.scheduling.JCSParallelScheduler;
import org.junit.internal.builders.AllDefaultPossibilitiesBuilder;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

/**
 * Not using the RunnerScheduler method the instantiation and execution happen
 * at the same time (actually in the same place)
 * 
 * @author gambi
 *
 */
// Basically rely on Suite -- this solution was taken from but the original
// one had problems (no test method found !)
// http://stackoverflow.com/questions/5674774/running-junit-test-in-parallel-on-suite-level
public class JCSSuiteParallelRunner extends Suite {

    /**
     * The <code>SuiteClasses</code> annotation specifies the classes to be run
     * when a class annotated with <code>@RunWith(Suite.class)</code> is run.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    public @interface ScheduleConfiguration {
        /**
         * @return How many test cases from the suite to run in parallel.
         *         Default -1 : unlimited
         */
        int concurrentTestCases()

        default -1;

        /**
         * 
         * @return How many test methods or simply tests are active in the same
         *         test class. Default -1 : unlimited
         */
        int concurrentTestMethods()

        default -1;

        /**
         * @return How many concurrent tests in the same test case to run on the
         *         same host. Default -1 : unlimited
         */
        int concurrentTestMethodsPerHost()

        default -1;

        /**
         * @return How many threads to use. Default -1 : unlimited
         */
        int threadLimit()

        default -1;

        /**
         * @return How many cloud nodes to use. Default 1
         */
        int sizeLimit() default 1;
    }

    private int concurrentTestCasesLimit;

    private int concurrentTestsLimit;

    private int concurrentTestsPerHostLimit;

    private int threadLimit;

    private int sizeLimit;

    /**
     * Returns an empty suite.
     */
    public static Runner emptySuite() {
        try {
            return new JCSSuiteParallelRunner((Class<?>) null, new Class<?>[0]);
        } catch (InitializationError e) {
            throw new RuntimeException("This shouldn't be possible");
        }
    }

    /**
     * Called reflectively on classes annotated with
     * <code>@RunWith(Suite.class)</code>
     *
     * @param klass
     *            the root class
     * @param builder
     *            builds runners for classes in the suite
     */
    public JCSSuiteParallelRunner(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        this(builder, klass, getAnnotatedClasses(klass));
    }

    /**
     * Call this when there is no single root class (for example, multiple class
     * names passed on the command line to {@link org.junit.runner.JUnitCore}
     *
     * @param builder
     *            builds runners for classes in the suite
     * @param classes
     *            the classes in the suite
     */
    public JCSSuiteParallelRunner(RunnerBuilder builder, Class<?>[] classes)
            throws InitializationError {
        this(null, builder.runners(null, classes));
        System.out.println(
                "JCSSuiteParallelRunner (RunnerBuilder builder, Class<?>[] classes)");
    }

    public JCSSuiteParallelRunner(RunnerBuilder builder, Class<?>[] classes,
            int concurrentTestCasesLimit, int threadLimit)
            throws InitializationError {
        // TODO This might be a critical point to address for the
        // synchronization
        // issues and the parenting of runners
        this(null, builder.runners(null, classes), concurrentTestCasesLimit,
                threadLimit);
    }

    /**
     * Call this when the default builder is good enough. Left in for
     * compatibility with JUnit 4.4.
     *
     * @param klass
     *            the root of the suite
     * @param suiteClasses
     *            the classes in the suite
     */
    protected JCSSuiteParallelRunner(Class<?> klass, Class<?>[] suiteClasses)
            throws InitializationError {
        this(new AllDefaultPossibilitiesBuilder(true), klass, suiteClasses);
        System.out.println(
                "JCSSuiteParallelRunner(Class<?> klass, Class<?>[] suiteClasses)");
    }

    /**
     * Called by this class and subclasses once the classes making up the suite
     * have been determined
     *
     * @param builder
     *            builds runners for classes in the suite
     * @param klass
     *            the root of the suite
     * @param suiteClasses
     *            the classes in the suite
     */
    protected JCSSuiteParallelRunner(RunnerBuilder builder, Class<?> klass,
            Class<?>[] suiteClasses) throws InitializationError {
        this(klass, builder.runners(klass, suiteClasses));
    }

    /**
     * Called by this class and subclasses once the runners making up the suite
     * have been determined
     *
     * @param klass
     *            root of the suite
     * @param runners
     *            for each class in the suite, a {@link Runner}
     */
    // This one shall be changed !
    protected JCSSuiteParallelRunner(Class<?> klass, List<Runner> runners)
            throws InitializationError {

        super(klass, overrideRunners(runners, valuesFromAnnotation(klass)));

        ScheduleConfiguration annotation = klass
                .getAnnotation(ScheduleConfiguration.class);
        if (annotation == null) {
            concurrentTestCasesLimit = -1;
            concurrentTestsLimit = -1;
            concurrentTestsPerHostLimit = -1;
            threadLimit = -1;
            sizeLimit = 1;
        } else {
            concurrentTestCasesLimit = annotation.concurrentTestCases();
            concurrentTestsLimit = annotation.concurrentTestMethods();
            concurrentTestsPerHostLimit = annotation
                    .concurrentTestMethodsPerHost();
            threadLimit = annotation.threadLimit();
            sizeLimit = annotation.sizeLimit();
        }

        configureJCloudScale();

        setScheduler(new JCSParallelScheduler(klass, concurrentTestCasesLimit,
                threadLimit));
    }

    // This one shall be changed !
    protected JCSSuiteParallelRunner(Class<?> klass, List<Runner> runners,
            int concurrentTestCasesLimit, int threadLimit)
            throws InitializationError {
        super(klass, runners);
        // System.out.println(
        // "JCSSuiteParallelRunner.JCSSuiteParallelRunner() CREATING THE
        // WRAPPING SUITE!");
        setScheduler(new JCSParallelScheduler(klass, concurrentTestCasesLimit,
                threadLimit));
    }

    private static int[] valuesFromAnnotation(Class<?> klass) {

        ScheduleConfiguration annotation = klass
                .getAnnotation(ScheduleConfiguration.class);
        if (annotation == null) {
            return new int[] { -1, -1 };
        } else {
            return new int[] { annotation.concurrentTestMethods(),
                    annotation.threadLimit() };
        }
    }

    public void configureJCloudScale() {
        SamplePolicy policy = new SamplePolicy(sizeLimit,
                concurrentTestsPerHostLimit, -1);

        JCloudScaleConfiguration config = new JCloudScaleConfigurationBuilder(
                new DockerCloudPlatformConfiguration(
                        "http://192.168.56.101:2375", "",
                        "alessio/jcs:0.4.6-SNAPSHOT-SHADED", "", ""))
                                .with(policy)
                                .withCommunicationServerPublisher(false)
                                .withMQServer("192.168.56.101", 61616)
                                .withLoggingClient(Level.WARNING)
                                .withLoggingServer(Level.SEVERE).build();

        JCloudScaleClient.setConfiguration(config);

        // System.out.println("==== ==== ==== ==== ==== ==== \n"
        // + "JCSParallelRunner SETTING CONF () \n "
        // + "==== ==== ==== ==== ==== ==== ");
    }

    // @Override
    // public void run(RunNotifier notifier) {
    // XXX This is required to maintain the status of tests coherent
    // notifier.addListener(new JCSJunitExecutionListener());
    // super.run(notifier);
    // }

    private static List<Runner> overrideRunners(List<Runner> runners,
            int[] valuesFromAnnotation) {
        List<Runner> result = new ArrayList<Runner>();

        int concurrentTestsLimit = valuesFromAnnotation[0];
        int threadsLimit = valuesFromAnnotation[0];

        for (Runner runner : runners) {
            if (runner instanceof BlockJUnit4ClassRunner
                    && !(runner instanceof JCSRunner)) {
                try {
                    // Now runners by default have no notifier, but one is
                    // needed !
                    JCSParallelRunner newRunner = new JCSParallelRunner(
                            ((BlockJUnit4ClassRunner) runner).getTestClass()
                                    .getJavaClass(),
                            concurrentTestsLimit, threadsLimit);

                    System.out.println(
                            ">>>>>> JCSSuiteParallelRunner.overrideRunners() Runner "
                                    + runner + " with " + newRunner);
                    result.add(newRunner);

                } catch (InitializationError e) {
                    e.printStackTrace();
                    // Use the original one
                    result.add(runner);
                }

            } else {
                result.add(runner);
            }

        }
        return result;
    }

}
