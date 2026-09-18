package htsjdk.utils;

import com.google.common.collect.Lists;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.collections.Sets;

/**
 * Small class implementing some utility functions that are useful for test and interfacing with the TestNG framework.
 */
public class TestNGUtils {

    /** A Method that returns all the Methods that are annotated with @DataProvider
     * in a given package.
     *
     * @param packageName the package under which to look for classes and methods
     * @return an iterator to collection of Object[]'s consisting of {Method method, Class clazz} pair.
     * where method has the @DataProviderAnnotation and is a member of clazz.
     */
    public static Iterator<Object[]> getDataProviders(final String packageName) {
        return getDataProviders(packageName, Collections.emptySet());
    }

    /**
     * Returns all methods annotated with @DataProvider in the given package, excluding classes
     * that belong to any of the specified test groups.
     *
     * @param packageName the package under which to look for classes and methods
     * @param excludedGroups groups to exclude — classes with a class-level @Test annotation
     *                       containing any of these groups will be skipped
     * @return an iterator to collection of Object[]'s consisting of {Method method, Class clazz} pairs
     */
    public static Iterator<Object[]> getDataProviders(final String packageName, final Set<String> excludedGroups) {
        List<Object[]> data = new ArrayList<>();
        final ClassFinder classFinder = new ClassFinder();
        classFinder.find(packageName, Object.class);

        for (final Class<?> testClass : classFinder.getClasses()) {
            if (Modifier.isAbstract(testClass.getModifiers()) || Modifier.isInterface(testClass.getModifiers()))
                continue;
            if (hasExcludedGroup(testClass, excludedGroups)) continue;
            Set<Method> methodSet = Sets.newHashSet();
            methodSet.addAll(Arrays.asList(testClass.getDeclaredMethods()));
            methodSet.addAll(Arrays.asList(testClass.getMethods()));

            for (final Method method : methodSet) {
                if (method.isAnnotationPresent(DataProvider.class)) {
                    data.add(new Object[] {method, testClass});
                }
            }
        }

        return data.iterator();
    }

    /** Returns true if the class has a class-level @Test annotation with any group in excludedGroups. */
    private static boolean hasExcludedGroup(final Class<?> testClass, final Set<String> excludedGroups) {
        if (excludedGroups.isEmpty()) return false;
        final Test testAnnotation = testClass.getAnnotation(Test.class);
        if (testAnnotation == null) return false;
        for (final String group : testAnnotation.groups()) {
            if (excludedGroups.contains(group)) return true;
        }
        return false;
    }

    /**
     * Combine two or more Dataproviders by taking the cartesian product of the their test cases.
     *
     * Note:  In the case of a an empty provider, the result will be the product of the non-empty providers.
     * This is different from the traditional definition of the cartesian product.
     * @return the cartesian product of two or more DataProviders than can be used as a new dataprovider
     */
    public static Object[][] cartesianProduct(Object[][]... dataProviders) {
        List<List<List<Object>>> lists = Arrays.stream(dataProviders)
                .map(TestNGUtils::nestedArraysToNestedLists)
                .collect(Collectors.toList());
        final List<List<List<Object>>> product = Lists.cartesianProduct(lists);
        final List<List<Object>> mergeProduct = product.stream()
                .map(l -> l.stream().flatMap(Collection::stream).collect(Collectors.toList()))
                .collect(Collectors.toList());
        return nestedListsToNestedArrays(mergeProduct);
    }

    /**
     * @param dataProvider a nested Object array
     * @return an equivalent nested List
     */
    public static List<List<Object>> nestedArraysToNestedLists(Object[][] dataProvider) {
        return Arrays.stream(dataProvider).map(Arrays::asList).collect(Collectors.toList());
    }

    /**
     * @param lists a nested List
     * @return an equivalent nested array
     */
    public static Object[][] nestedListsToNestedArrays(List<List<Object>> lists) {
        return lists.stream().map(List::toArray).toArray(Object[][]::new);
    }

    /**
     * Returns the bytes of heap that the calling thread has allocated so far, for tests that assert how much memory
     * some code allocates: take it before and after. Counting one thread's allocation keeps the measurement
     * undisturbed by tests running in parallel. The calling test is skipped on a JVM that cannot measure this.
     */
    public static long bytesAllocatedByCurrentThread() {
        if (!(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean threads)
                || !threads.isThreadAllocatedMemorySupported()) {
            throw new SkipException("This JVM does not measure the memory allocated by a thread");
        }
        // Whether the measurement is on by default depends on the platform, and while it is off the count is -1.
        threads.setThreadAllocatedMemoryEnabled(true);
        final long allocated = threads.getCurrentThreadAllocatedBytes();
        Assert.assertTrue(allocated >= 0, "The memory allocated by this thread is not being measured");
        return allocated;
    }
}
