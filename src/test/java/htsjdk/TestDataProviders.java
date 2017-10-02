package htsjdk;

import htsjdk.utils.ClassFinder;
import org.testng.Assert;
import org.testng.annotations.*;
import org.testng.collections.Sets;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

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

     private ClassFinder classFinder;

    @Test
    public void independentTestOfDataProviderTest() throws IllegalAccessException, InvocationTargetException, InstantiationException {
        testAllDataProvidersData();
    }

    @BeforeSuite // @BeforeSuite is executed before @BeforeClass
    private void setupBeforeClassClassFinder(){
        classFinder = new ClassFinder();
    }

    @BeforeClass // @BeforeSuite is executed before @BeforeClass
    private void setupBeforeSuiteClassFinder(){
        classFinder.find("htsjdk", HtsjdkTest.class);
    }


    @DataProvider(name = "DataprovidersThatDontTestThemselves")
    private Iterator<Object[]> testAllDataProvidersData() throws IllegalAccessException, InstantiationException, InvocationTargetException {

        final List<Object[]> data = new ArrayList<>();

        for (final Class<?> testClass : classFinder.getConcreteClasses()) {
            Set<Method> methodSet = Sets.newHashSet();
            methodSet.addAll(Arrays.asList(testClass.getDeclaredMethods()));
            methodSet.addAll(Arrays.asList(testClass.getMethods()));
            for (final Method method : methodSet) {
                if (method.isAnnotationPresent(DataProvider.class)) {
                    data.add(new Object[]{method, testClass});
                }
            }
        }
        Assert.assertTrue(data.size() > 1);

        // make sure that this @DataProvider is in the list even though it's private
        Assert.assertEquals(data.stream().filter(c ->
                        ((Method) c[0]).getName().equals("testAllDataProvidersData") &&
                        ((Class)  c[1]).getName().equals(this.getClass().getName())).count(), 1L);

        return data.iterator();
    }


    // @NoInjection annotations required according to this test:
    // https://github.com/cbeust/testng/blob/master/src/test/java/test/inject/NoInjectionTest.java
    @Test(dataProvider = "DataprovidersThatDontTestThemselves")
    public void testDataProviderswithDP(@NoInjection final Method method, final Class clazz) throws IllegalAccessException, InstantiationException {

        Object instance = clazz.newInstance();

        Assert.assertTrue(HtsjdkTest.class.isAssignableFrom(clazz), "Test Classes must extend HtsJdkTest: " + clazz.getName());

        // Some tests assume that the @BeforeSuite methods will be called before the @DataProvidersSet<Method> methodSet = Sets.newHashSet();
        Set<Method> methodSet = Sets.newHashSet();
        methodSet.addAll(Arrays.asList(clazz.getDeclaredMethods()));
        methodSet.addAll(Arrays.asList(clazz.getMethods()));

        for (final Method otherMethod : methodSet) {
            if (otherMethod.isAnnotationPresent(BeforeSuite.class)) {
                try {
                    otherMethod.setAccessible(true);
                    otherMethod.invoke(instance);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException(String.format("@BeforeClass threw an exception (%s::%s). Dependent tests will be skipped. Please fix.", clazz.getName(), method.getName()), e);
                }
            }

            if (otherMethod.isAnnotationPresent(BeforeClass.class)) {
                try {
                    otherMethod.setAccessible(true);
                    otherMethod.invoke(instance);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException(String.format("@BeforeSuite threw an exception (%s::%s). Dependent tests will be skipped. Please fix.", clazz.getName(), method.getName()), e);
                }
            }
        }

        try {
            method.setAccessible(true);
            method.invoke(instance);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(String.format("@DataProvider threw an exception (%s::%s). Dependent tests will be skipped. Please fix.", clazz.getName(), method.getName()), e);
        }
    }
}
