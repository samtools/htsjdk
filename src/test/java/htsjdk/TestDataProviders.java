package htsjdk;

import htsjdk.utils.ClassFinder;
import org.testng.Assert;
import org.testng.annotations.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This test is a mechanism to check that none of the data-providers fail to run.
 * It is needed because in the case that a data-provider fails (for some reason, perhaps a change in some other code
 * causes it to throw an exception) the tests that rely on it will be silently skipped.
 * The only mention of this will be in test logs but since we normally avoid reading these logs and rely on the
 * exit code, it will look like all the tests have passed.
 *
 * @author Yossi Farjoun
 */
public class TestDataProviders extends HtsjdkTest{

    @Test
    public void independentTestOfDataProviderTest() throws IllegalAccessException, InvocationTargetException, InstantiationException {
        testAllDataProvidersData();
    }

    @DataProvider(name = "DataprovidersThatDontTestThemselves")
    public Iterator<Object[]> testAllDataProvidersData() throws IllegalAccessException, InstantiationException, InvocationTargetException {

        List<Object[]> data = new ArrayList<>();
        final ClassFinder classFinder = new ClassFinder();
        classFinder.find("htsjdk", HtsjdkTest.class);

        for (final Class<?> testClass : classFinder.getConcreteClasses()) {
            for (final Method method : testClass.getMethods()) {
                if (method.isAnnotationPresent(DataProvider.class)) {
                    data.add(new Object[]{method, testClass});
                }
            }
        }
        Assert.assertTrue(data.size() > 1);

        // make sure that this @DataProvider is in the list
        // make sure that this @DataProvider is in the list
        Assert.assertEquals(data.stream().filter(c ->
                        ((Method) c[0]).getName().equals("testDependenceData") &&
                        ((Class)  c[1]).getName().equals(this.getClass().getName())).count(), 1L);

        return data.iterator();
    }

    // @NoInjection annotations required according to this test:
    // https://github.com/cbeust/testng/blob/master/src/test/java/test/inject/NoInjectionTest.java
    @Test(dataProvider = "DataprovidersThatDontTestThemselves")
    public void testDataProviderswithDP(@NoInjection final Method method, final Class clazz) throws IllegalAccessException, InstantiationException, InvocationTargetException {

        Object instance = clazz.newInstance();

        // Some tests assume that the @BeforeSuite methods will be called before the @DataProviders
        for (final Method otherMethod : clazz.getMethods()) {
            if (otherMethod.isAnnotationPresent(BeforeSuite.class)) {
                otherMethod.invoke(instance);
            }
            if (otherMethod.isAnnotationPresent(BeforeClass.class)) {
                otherMethod.invoke(instance);
            }
            if (otherMethod.isAnnotationPresent(BeforeMethod.class)) {
                otherMethod.invoke(instance);
            }
        }
        method.invoke(instance);
    }
}
