package junit.framework;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import at.ac.tuwien.infosys.jcloudscale.annotations.CloudObject;
import at.ac.tuwien.infosys.jcloudscale.api.CloudObjects;
import at.ac.tuwien.infosys.jcloudscale.logging.Logged;
import org.junit.experimental.cloud.scheduling.JCSParallelScheduler;
import org.junit.experimental.cloud.shared.TestToHostMapping;
import org.junit.internal.MethodSorter;
import org.junit.runners.model.RunnerScheduler;

/*
 * Modified version to use the JCS Framework
 */
@Logged
public class JCSTestSuite implements Test {

    /**
     * ...as the moon sets over the early morning Merlin, Oregon mountains, our
     * intrepid adventurers type...
     * 
     * This is blocking ! So we need to do this trick: as soon as you schedule,
     * enqueue in the scheduler another runnable for the execution !
     */
    static public Test createTest(Class<?> theClass, String name) {
        Constructor<?> constructor;
        try {
            constructor = getTestConstructor(theClass);
        } catch (NoSuchMethodException e) {
            return warning("Class " + theClass.getName()
                    + " has no public constructor TestCase(String name) or TestCase()");
        }

        Object test;
        try {
            if (theClass.isAnnotationPresent(CloudObject.class)) {
                System.out.println(
                        "JCSTestSuite.createTest() WARNING THIS IS UNDER DEVELOPMENT !!!");
            }

            // TestToHostMapping.get().testScheduled(theClass);
            //
            // if (constructor.getParameterTypes().length == 0) {
            //
            // // Probably null would work just fine
            // test = CloudObjects.create(constructor, theClass,
            // new Object[0]);
            //
            // // At this point we should already have the sample policy
            // // deploying our stuff !
            //
            // // test = constructor.newInstance(new Object[0]);
            // if (test instanceof TestCase) {
            // ((TestCase) test).setName(name);
            // }
            // } else {
            // // TODO Not sure if this will result in an error !
            // test = CloudObjects.create(constructor, theClass,
            // new Object[] { name });
            // // test = constructor.newInstance(new Object[] { name });
            // }
            //
            // TestToHostMapping.get().testDeployed((Test) test);
            //
            // } else {
            if (constructor.getParameterTypes().length == 0) {
                test = constructor.newInstance(new Object[0]);
                if (test instanceof TestCase) {
                    ((TestCase) test).setName(name);
                }
            } else {
                test = constructor.newInstance(new Object[] { name });
            }
            // }

        } catch (InstantiationException e) {
            return (warning("Cannot instantiate test case: " + name + " ("
                    + exceptionToString(e) + ")"));
        } catch (InvocationTargetException e) {
            return (warning("Exception in constructor: " + name + " ("
                    + exceptionToString(e.getTargetException()) + ")"));
        } catch (IllegalAccessException e) {
            return (warning("Cannot access test case: " + name + " ("
                    + exceptionToString(e) + ")"));
        }
        return (Test) test;
    }

    /**
     * Gets a constructor which takes a single String as its argument or a no
     * arg constructor.
     */
    public static Constructor<?> getTestConstructor(Class<?> theClass)
            throws NoSuchMethodException {
        try {
            return theClass.getConstructor(String.class);
        } catch (NoSuchMethodException e) {
            // fall through
        }
        return theClass.getConstructor();
    }

    /**
     * Returns a test which will fail and log a warning message.
     */
    public static Test warning(final String message) {
        return new TestCase("warning") {
            @Override
            protected void runTest() {
                fail(message);
            }
        };
    }

    /**
     * Converts the stack trace into a string
     */
    private static String exceptionToString(Throwable e) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        e.printStackTrace(writer);
        return stringWriter.toString();
    }

    private String fName;

    /*
     * Cannot convert this to List because it is used directly by some test
     * runners
     */
    private Vector<Test> fTests = new Vector<Test>(10);

    /**
     * Constructs an empty TestSuite.
     */
    public JCSTestSuite() {
    }

    /**
     * Constructs a TestSuite from the given class. Adds all the methods
     * starting with "test" as test cases to the suite. Parts of this method
     * were written at 2337 meters in the Hueffihuette, Kanton Uri
     */
    public JCSTestSuite(final Class<?> theClass) {
        addTestsFromTestCase(theClass);
    }

    private void addTestsFromTestCase(final Class<?> theClass) {
        fName = theClass.getName();
        try {
            getTestConstructor(theClass); // Avoid generating multiple error
                                          // messages
        } catch (NoSuchMethodException e) {
            addTest(warning("Class " + theClass.getName()
                    + " has no public constructor TestCase(String name) or TestCase()"));
            return;
        }

        if (!Modifier.isPublic(theClass.getModifiers())) {
            addTest(warning("Class " + theClass.getName() + " is not public"));
            return;
        }

        Class<?> superClass = theClass;
        List<String> names = new ArrayList<String>();
        while (Test.class.isAssignableFrom(superClass)) {
            for (Method each : MethodSorter.getDeclaredMethods(superClass)) {
                addTestMethod(each, names, theClass);
            }
            superClass = superClass.getSuperclass();
        }
        if (fTests.size() == 0) {
            addTest(warning("No tests found in " + theClass.getName()));
        }
    }

    /**
     * Constructs a TestSuite from the given class with the given name.
     *
     * @see JCSTestSuite#TestSuite(Class)
     */
    public JCSTestSuite(Class<? extends TestCase> theClass, String name) {
        this(theClass);
        setName(name);
    }

    /**
     * Constructs an empty TestSuite.
     */
    public JCSTestSuite(String name) {
        setName(name);
    }

    /**
     * Constructs a TestSuite from the given array of classes.
     *
     * @param classes
     *            {@link TestCase}s
     */
    public JCSTestSuite(Class<?>... classes) {
        for (Class<?> each : classes) {
            addTest(testCaseForClass(each));
        }
    }

    private Test testCaseForClass(Class<?> each) {
        if (TestCase.class.isAssignableFrom(each)) {
            return new JCSTestSuite(each.asSubclass(TestCase.class));
        } else {
            return warning(
                    each.getCanonicalName() + " does not extend TestCase");
        }
    }

    /**
     * Constructs a TestSuite from the given array of classes with the given
     * name.
     *
     * @see JCSTestSuite#TestSuite(Class[])
     */
    public JCSTestSuite(Class<? extends TestCase>[] classes, String name) {
        this(classes);
        setName(name);
    }

    /**
     * Adds a test to the suite.
     */
    public void addTest(Test test) {
        fTests.add(test);
    }

    /**
     * Adds the tests from the given class to the suite
     */
    public void addTestSuite(Class<? extends TestCase> testClass) {
        addTest(new JCSTestSuite(testClass));
    }

    /**
     * Counts the number of test cases that will be run by this test.
     */
    public int countTestCases() {
        // This would be wrong since tests can be created on asycn
        int count = 0;
        for (Test each : fTests) {
            count += each.countTestCases();
        }
        return count;
    }

    /**
     * Returns the name of the suite. Not all test suites have a name and this
     * method can return null.
     */
    public String getName() {
        return fName;
    }

    /**
     * Runs the tests and collects their result in a TestResult.
     */
    public void run(final TestResult result) {
        // TODO Somehow output this which is crucial
        // System.out.println("---- JCSTestSuite.run() ---- ");

        /*
         * The generation of tests classes as CO is blocking so we make it asyn.
         * At this point however we might not have all the tests ready,
         * therefore we wait for them and re-enqueue them on the fly
         * 
         */
        try {
            while (!tasks.isEmpty()) {
                Future<Test> finishedTask = completionService.take();
                tasks.remove(finishedTask);

                final Test test = finishedTask.get();

                // Update shared structures
                addTest(test);

                // Creation, management and execution of tests are decoupled !
                scheduler.schedule(new Runnable() {

                    @Override
                    public void run() {
                        runTest(test, result);
                    }
                });

            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } finally {
            while (!tasks.isEmpty())
                tasks.poll().cancel(true);
            executorService.shutdownNow();
        }

        // At this point we need to wait for the tests to finish !
        scheduler.finished();
    }

    public void runTest(Test test, TestResult result) {
        try {
            // TODO
            System.out.println(
                    "JCSTestSuite.runTest() WARNING THIS IS UNDER DEVELOPMENT !");
            // TestToHostMapping.get().testStarts(test);
            test.run(result);
        } finally {
            // TestToHostMapping.get().testFinishes(test);
        }
    }

    /**
     * Sets the name of the suite.
     *
     * @param name
     *            the name to set
     */
    public void setName(String name) {
        fName = name;
    }

    /**
     * Returns the test at the given index
     */
    public Test testAt(int index) {
        return fTests.get(index);
    }

    /**
     * Returns the number of tests in this suite
     */
    public int testCount() {
        return fTests.size();
    }

    /**
     * Returns the tests as an enumeration
     */
    public Enumeration<Test> tests() {
        return fTests.elements();
    }

    /**
     */
    @Override
    public String toString() {
        if (getName() != null) {
            return getName();
        }
        return super.toString();
    }

    private ExecutorService executorService = Executors.newCachedThreadPool();// new
    // NamedThreadFactory("JCSTestSuite"));

    private CompletionService<Test> completionService = new ExecutorCompletionService<Test>(
            executorService);

    private Queue<Future<Test>> tasks = new LinkedList<Future<Test>>();

    // Limit execution to 1 for the moment
    private RunnerScheduler scheduler = new JCSParallelScheduler(null, 1);

    public void setScheduler(RunnerScheduler scheduler) {
        this.scheduler = scheduler;
    }

    private void addTestMethod(Method m, final List<String> names,
            final Class<?> theClass) {

        final String name = m.getName();
        if (names.contains(name)) {
            return;
        }
        if (!isPublicTestMethod(m)) {
            if (isTestMethod(m)) {
                addTest(warning("Test method isn't public: " + m.getName() + "("
                        + theClass.getCanonicalName() + ")"));
            }
            return;
        }
        // Enqueue the creation of test objects since this is blocking
        tasks.offer(//
                completionService.submit(new Callable<Test>() {

                    @Override
                    public Test call() throws Exception {
                        names.add(name);
                        return createTest(theClass, name);
                    }
                }));

    }

    private boolean isPublicTestMethod(Method m) {
        return isTestMethod(m) && Modifier.isPublic(m.getModifiers());
    }

    private boolean isTestMethod(Method m) {
        return m.getParameterTypes().length == 0
                && m.getName().startsWith("test")
                && m.getReturnType().equals(Void.TYPE);
    }
}
