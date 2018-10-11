package htsjdk;

import htsjdk.utils.ClassFinder;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.NoInjection;
import org.testng.annotations.Test;
import org.testng.collections.Sets;

/**
 * Our testing framework assumes that all java tests extend HtsjdkTest (while Scala tests extend
 * from UnitSpec)
 *
 * <p>Tests that do not extend HtsjdkTest will not be run during testing leading to possible silent
 * "passage" of broken tests. This test suite examines the all classes that contain methods that are
 * annotated with the {@link Test} annotation, and checks that they extend HtsjdkTest.
 */
public class TestClassDependenceTest extends HtsjdkTest {

  // since there are assertions inside the dataprovider, this test check that the dataprovider
  // completes succeessfully.
  // This is needed since a failing data-provider will result in silently skipping the tests that
  // rely on it.

  // This idea led to the creation of {@link TestDataProviders}, which renders it no-longer
  // needed....
  @Test
  public void independentTestOfDataProviderTest()
      throws IllegalAccessException, InvocationTargetException, InstantiationException {
    testAllTestsData();
  }

  @DataProvider(name = "Tests")
  public Iterator<Object[]> testAllTestsData()
      throws IllegalAccessException, InstantiationException, InvocationTargetException {

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
          data.add(new Object[] {method, testClass});
        }
      }
    }
    Assert.assertTrue(data.size() > 1);

    // make sure that the @Tests in this class are found:
    Assert.assertEquals(
        data.stream()
            .filter(
                c ->
                    ((Method) c[0]).getName().equals("independentTestOfDataProviderTest")
                        && ((Class) c[1]).getName().equals(this.getClass().getName()))
            .count(),
        1L);
    Assert.assertEquals(
        data.stream()
            .filter(
                c ->
                    ((Method) c[0]).getName().equals("testDependenceData")
                        && ((Class) c[1]).getName().equals(this.getClass().getName()))
            .count(),
        1L);

    return data.iterator();
  }

  // Make sure that all @Tests are in classes that inherit from HtsjdkTest.
  @Test(dataProvider = "Tests")
  public void testDependenceData(@NoInjection final Method method, final Class clazz)
      throws IllegalAccessException, InstantiationException, InvocationTargetException {
    Assert.assertTrue(
        HtsjdkTest.class.isAssignableFrom(clazz),
        "Test Classes must extend HtsJdkTest: " + clazz.getName());
  }
}
