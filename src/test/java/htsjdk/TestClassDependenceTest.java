package htsjdk;

import htsjdk.utils.ClassFinder;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.NoInjection;
import org.testng.annotations.Test;
import org.testng.collections.Sets;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Created by farjoun on 10/2/17.
 */
public class TestClassDependenceTest extends HtsjdkTest {

    @Test
    public void independentTestOfDataProviderTest() throws IllegalAccessException, InvocationTargetException, InstantiationException {
        testAllTestsData();
    }

    @DataProvider(name = "Tests")
    public Iterator<Object[]> testAllTestsData() throws IllegalAccessException, InstantiationException, InvocationTargetException {

        List<Object[]> data = new ArrayList<>();
        final ClassFinder classFinder = new ClassFinder();
        classFinder.find("htsjdk", Object.class);

        for (final Class<?> testClass : classFinder.getConcreteClasses()) {
            // getDeclaredMethods will also include private methods
            Set<Method> methodSet = Sets.newHashSet();
            methodSet.addAll(Arrays.asList(testClass.getDeclaredMethods()));
            methodSet.addAll(Arrays.asList(testClass.getMethods()));

            for (final Method method : testClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Test.class)) {
                    data.add(new Object[]{method, testClass});
                }
            }
        }
        Assert.assertTrue(data.size() > 1);

        // make sure that this @DataProvider is in the list
        Assert.assertEquals(data.stream().filter(c ->
                ((Method) c[0]).getName().equals("testDependenceData") &&
                ((Class)  c[1]).getName().equals(this.getClass().getName())).count(), 1L);

        // Make sure that we found the private method declared below, and then remove it from the list so that
        // the actual test doesn't fail.
        Optional<Object[]> perhapsPrivateMethodAndClass = data.stream().filter(c ->
                ((Method) c[0]).getName().equals("testForThePurposeOfTestingPrivateTests") &&
                ((Class)  c[1]).getName().equals(this.getClass().getName())).findFirst();

        Assert.assertTrue(perhapsPrivateMethodAndClass.isPresent());
        data.remove(perhapsPrivateMethodAndClass.get());

        return data.iterator();
    }

    // Make sure that all @Tests are public methods residing in classes that inherit from HtsjdkTest.
    @Test(dataProvider = "Tests")
    public void testDependenceData(@NoInjection final Method method, final Class clazz) throws IllegalAccessException, InstantiationException, InvocationTargetException {
        Assert.assertTrue(HtsjdkTest.class.isAssignableFrom(clazz), "Test Classes must extend HtsJdkTest: " + clazz.getName());
        Assert.assertTrue(Modifier.isPublic(method.getModifiers()),"Test methods must have public visibility: " + clazz.getName() + "::" + method.getName());
    }

    // This test is explicitly private and we test that we would have found it in the data-provider before removing it form the list (so that the test itself doesn't fail)
    // It shouldn't be run since it's private, so the tests do not actually fail.
    @Test
    private void testForThePurposeOfTestingPrivateTests() {
        Assert.assertTrue(false);
    }
}
