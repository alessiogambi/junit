package org.junit.experimental.cloud;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;

import at.ac.tuwien.infosys.jcloudscale.annotations.CloudObject;
import at.ac.tuwien.infosys.jcloudscale.api.CloudObjects;
import org.junit.experimental.cloud.shared.TestToHostMapping;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

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
            System.out.println(
                    "\n\n\n ***** JCSRunner.runChild() WARN no CO use regular implementation \n\n\n");
            super.runChild(method, notifier);

        } else {

            Description description = describeChild(method);
            if (isIgnored(method)) {
                notifier.fireTestIgnored(description);
            } else {
                // This might take time
                // System.out.println("BlockJUnit4ClassRunner.runChild() -
                // Create
                // the CO for " + method + "START");
                TestToHostMapping.get().testScheduled(description);
                //
                Statement statement = methodBlock(method);
                //
                TestToHostMapping.get().testDeployed(description);
                System.out.println("JCSRunner.runChild() - Create the CO for "
                        + method + "DONE");

                System.out.println("JCSRunner.runChild() - Execution of "
                        + method + " on CO START");
                // This remains stuf in the notification somehow
                TestToHostMapping.get().testStarts(description);

                runLeaf(statement, description, notifier);

                // TODO NOTE THIS ONE !! We must do this here otherwise the
                // notification crap will be delayed becuase the threas is
                // rescheduled somehow in the Queue ! Or we need to keep it
                // somewhere else !
                TestToHostMapping.get().testFinishes(description);
                System.out.println("JCSRunner.runChild() - Execution  of "
                        + method + " on CO DONE");

            }
        }
    }

    /*
     * TODO Ideally here there should be a nice way to decide if a test
     * can/shall/must be executed on the cloud or not. For the moment, we rely
     * on testers and annotations. Note that ideally annotations can be placed
     * on methods as well and not only on the class.
     */
    @SuppressWarnings("rawtypes")
    @Override
    protected Object createTest() throws Exception {
        // System.out.println("JCSRunner.createTest() " + getTestClass());
        for (Annotation annotation : getTestClass().getAnnotations()) {
            if (annotation.annotationType().equals(CloudObject.class)) {
                Constructor c = getTestClass().getOnlyConstructor();
                Class clazz = getTestClass().getJavaClass();
                // This triggers the whole JCS Machinery
                // This is a key point. We shall register this specific clazz as
                // test class so we trace its execution later
                TestToHostMapping.get().registerTestClass(clazz);
                // THIS CALL IS BLOCKING !!
                return CloudObjects.create(c, clazz);
            }
        }
        return super.createTest();

    }

}
