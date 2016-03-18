package org.junit.experimental.cloud;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;

import at.ac.tuwien.infosys.jcloudscale.annotations.CloudObject;
import at.ac.tuwien.infosys.jcloudscale.api.CloudObjects;
import org.junit.experimental.cloud.listeners.JCSJunitExecutionListener;
import org.junit.experimental.cloud.shared.TestToHostMapping;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

public class JCSRunner extends BlockJUnit4ClassRunner {

    public JCSRunner(Class<?> klass) throws InitializationError {
        super(klass);
    }

    @Override
    public Description getDescription() {
        return super.getDescription();
    }

    @Override
    public void run(RunNotifier notifier) {
        notifier.addListener(new JCSJunitExecutionListener());
        super.run(notifier);

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
        for (Annotation annotation : getTestClass().getAnnotations()) {
            if (annotation.annotationType().equals(CloudObject.class)) {
                Constructor c = getTestClass().getOnlyConstructor();
                Class clazz = getTestClass().getJavaClass();
                // This triggers the whole JCS Machinery
                // This is a key point. We shall register this specific clazz as
                // test class so we trace its execution later
                TestToHostMapping.get().registerTestClass(clazz);
                //
                return CloudObjects.create(c, clazz);
            }
        }
        return super.createTest();

    }

}
