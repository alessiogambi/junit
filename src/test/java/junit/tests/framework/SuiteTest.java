package junit.tests.framework;

import java.util.Collections;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.JCSTestSuite;

/**
 * A fixture for testing the "auto" test suite feature.
 */
public class SuiteTest extends TestCase {
    protected TestResult fResult;

    public SuiteTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() {
        fResult = new TestResult();
    }

    public static Test suite() {
        JCSTestSuite suite = new JCSTestSuite("Suite Tests");
        // build the suite manually, because some of the suites are testing
        // the functionality that automatically builds suites
        suite.addTest(new SuiteTest("testNoTestCases"));
        suite.addTest(new SuiteTest("testOneTestCase"));
        suite.addTest(new SuiteTest("testNotPublicTestCase"));
        suite.addTest(new SuiteTest("testNotVoidTestCase"));
        suite.addTest(new SuiteTest("testNotExistingTestCase"));
        suite.addTest(new SuiteTest("testInheritedTests"));
        suite.addTest(new SuiteTest("testOneTestCaseEclipseSeesSameStructureAs381"));
        suite.addTest(new SuiteTest("testNoTestCaseClass"));
        suite.addTest(new SuiteTest("testShadowedTests"));
        suite.addTest(new SuiteTest("testAddTestSuite"));
        suite.addTest(new SuiteTest("testCreateSuiteFromArray"));

        return suite;
    }

    public void testInheritedTests() {
        JCSTestSuite suite = new JCSTestSuite(InheritedTestCase.class);
        suite.run(fResult);
        assertTrue(fResult.wasSuccessful());
        assertEquals(2, fResult.runCount());
    }

    public void testNoTestCaseClass() {
        Test t = new JCSTestSuite(NoTestCaseClass.class);
        t.run(fResult);
        assertEquals(1, fResult.runCount());  // warning test
        assertTrue(!fResult.wasSuccessful());
    }

    public void testNoTestCases() {
        Test t = new JCSTestSuite(NoTestCases.class);
        t.run(fResult);
        assertTrue(fResult.runCount() == 1);  // warning test
        assertTrue(fResult.failureCount() == 1);
        assertTrue(!fResult.wasSuccessful());
    }

    public void testNotExistingTestCase() {
        Test t = new SuiteTest("notExistingMethod");
        t.run(fResult);
        assertTrue(fResult.runCount() == 1);
        assertTrue(fResult.failureCount() == 1);
        assertTrue(fResult.errorCount() == 0);
    }

    public void testNotPublicTestCase() {
        JCSTestSuite suite = new JCSTestSuite(NotPublicTestCase.class);
        // 1 public test case + 1 warning for the non-public test case
        assertEquals(2, suite.countTestCases());
    }

    public void testNotVoidTestCase() {
        JCSTestSuite suite = new JCSTestSuite(NotVoidTestCase.class);
        assertTrue(suite.countTestCases() == 1);
    }

    public void testOneTestCase() {
        JCSTestSuite t = new JCSTestSuite(OneTestCase.class);
        t.run(fResult);
        assertTrue(fResult.runCount() == 1);
        assertTrue(fResult.failureCount() == 0);
        assertTrue(fResult.errorCount() == 0);
        assertTrue(fResult.wasSuccessful());
    }

    public void testOneTestCaseEclipseSeesSameStructureAs381() {
        JCSTestSuite t = new JCSTestSuite(ThreeTestCases.class);
        assertEquals(3, Collections.list(t.tests()).size());
    }

    public void testShadowedTests() {
        JCSTestSuite suite = new JCSTestSuite(OverrideTestCase.class);
        suite.run(fResult);
        assertEquals(1, fResult.runCount());
    }

    public void testAddTestSuite() {
        JCSTestSuite suite = new JCSTestSuite();
        suite.addTestSuite(OneTestCase.class);
        suite.run(fResult);
        assertEquals(1, fResult.runCount());
    }

    public void testCreateSuiteFromArray() {
        JCSTestSuite suite = new JCSTestSuite(OneTestCase.class, DoublePrecisionAssertTest.class);
        assertEquals(2, suite.testCount());
        assertEquals("junit.tests.framework.DoublePrecisionAssertTest", ((JCSTestSuite) suite.testAt(1)).getName());
        assertEquals("junit.tests.framework.OneTestCase", ((JCSTestSuite) suite.testAt(0)).getName());
    }
}