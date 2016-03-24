package junit.samples;

import junit.framework.Test;
import junit.framework.JCSTestSuite;

/**
 * TestSuite that runs all the sample tests
 */
public class AllTests {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    public static Test suite() {
        JCSTestSuite suite = new JCSTestSuite("All JUnit Tests");
        suite.addTest(ListTest.suite());
        suite.addTest(new JCSTestSuite(junit.samples.money.MoneyTest.class));
        suite.addTest(junit.tests.AllTests.suite());
        return suite;
    }
}