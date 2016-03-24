package org.junit.experimental.cloud;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;

import at.ac.tuwien.infosys.jcloudscale.annotations.CloudObject;
import at.ac.tuwien.infosys.jcloudscale.api.CloudObjects;
import at.ac.tuwien.infosys.jcloudscale.logging.Logged;
import org.junit.experimental.cloud.shared.TestToHostMapping;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

@Logged
public class JCSRunner extends BlockJUnit4ClassRunner {

    @Override
    public String toString() {
        return super.toString() + "[" + getTestClass().getName() + "]";
    }

    public JCSRunner(Class<?> klass) throws InitializationError {
        super(klass);
    }

    /**
     * This might not work for non-cloud tests
     */
    @Override
    protected void runChild(final FrameworkMethod method,
            RunNotifier notifier) {

        if (!method.getDeclaringClass()
                .isAnnotationPresent(CloudObject.class)) {
            super.runChild(method, notifier);

        } else {

            Description description = describeChild(method);
            if (isIgnored(method)) {
                notifier.fireTestIgnored(description);
            } else {
                TestToHostMapping.get().testScheduled(description);
                // This causes the creation of the test and its deployment. It's
                // blocking and we need to capture this event for
                // synchronization
                Statement statement = methodBlock(method);
                //
                TestToHostMapping.get().testDeployed(description);
                // ---
                // This is where the just created CO is used as test
                TestToHostMapping.get().testStarts(description);

                runLeaf(statement, description, notifier);

                /*
                 * The JUnit listener machinery does not work as expected for
                 * the parallel case: the tests will be notified as finished
                 * only at the end of the execution of all the test methods that
                 * belong in the same test class.
                 */
                TestToHostMapping.get().testFinishes(description);
            }
        }
    }

    /*
     * Ideally here there should be a nice way to decide if a test
     * can/shall/must be executed on the cloud or not. For the moment, we rely
     * on testers and annotations. Note that ideally annotations can be placed
     * on methods as well and not only on the class.
     */
    @SuppressWarnings("rawtypes")
    @Override
    protected Object createTest() throws Exception {
        // This is a test class so let remember it !
        TestToHostMapping.get()
                .registerTestClass(getTestClass().getJavaClass());
        // TODO Check also methods of the test class that are annotated with
        // @Cloud.
        // TODO If/when @CloudObject is not necessary anymore, we can change
        // this to something else
        for (Annotation annotation : getTestClass().getAnnotations()) {
            if (annotation.annotationType().equals(CloudObject.class)) {
                Constructor c = getTestClass().getOnlyConstructor();
                Class clazz = getTestClass().getJavaClass();

                /*
                 * Note: since we use Sample policy that must decide where to
                 * place the test THIS CALL IS BLOCKING !!
                 */
                return CloudObjects.create(c, clazz);
            }
        }
        return super.createTest();

    }

}
