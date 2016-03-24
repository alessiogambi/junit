package org.junit.internal.runners.statements;

import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

public class InvokeMethod extends Statement {
    private final FrameworkMethod testMethod;

    private final Object target;

    public InvokeMethod(FrameworkMethod testMethod, Object target) {
        this.testMethod = testMethod;
        this.target = target;
    }

    @Override
    public void evaluate() throws Throwable {
        // This one is the one that actually perform the call and the deployment
        testMethod.invokeExplosively(target);
    }

    // PATCH
    public Object getTarget() {
        return target;
    }
}