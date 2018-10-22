package htsjdk.utils;

import com.google.common.collect.Lists;
import org.testng.annotations.DataProvider;
import org.testng.collections.Sets;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

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
        List<Object[]> data = new ArrayList<>();
        final ClassFinder classFinder = new ClassFinder();
        classFinder.find(packageName, Object.class);

        for (final Class<?> testClass : classFinder.getClasses()) {
            if (Modifier.isAbstract(testClass.getModifiers()) || Modifier.isInterface(testClass.getModifiers()))
                continue;
            Set<Method> methodSet = Sets.newHashSet();
            methodSet.addAll(Arrays.asList(testClass.getDeclaredMethods()));
            methodSet.addAll(Arrays.asList(testClass.getMethods()));

            for (final Method method : methodSet) {
                if (method.isAnnotationPresent(DataProvider.class)) {
                    data.add(new Object[]{method, testClass});
                }
            }
        }

        return data.iterator();
    }

    /**
     * Combine two or more Dataproviders by taking the cartesian product of the their test cases.
     *
     * Note:  In the case of a an empty provider, the result will be the product of the non-empty providers.
     * This isdifferent from the traditional definition of the cartesian product.
     * @return the cartesian product of two or more DataProviders than can be used as a new dataprovider
     */
    public static Object[][] cartesianProduct(Object[][]... dataProviders) {
        List<List<List<Object>>> lists = Arrays.stream(dataProviders)
                .map(TestNGUtils::nestedArraysToNestedLists)
                .collect(Collectors.toList());
        final List<List<List<Object>>> product = Lists.cartesianProduct(lists);
        final List<List<Object>> mergeProduct = product.stream()
                .map( l -> l.stream()
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());
        return nestedListsToNestedArrays(mergeProduct);
    }

    /**
     * @param dataProvider a nested Object array
     * @return an equivalent nested List
     */
    public static List<List<Object>> nestedArraysToNestedLists(Object[][] dataProvider){
        return Arrays.stream(dataProvider)
                .map(Arrays::asList)
                .collect(Collectors.toList());
    }

    /**
     * @param lists a nested List
     * @return an equivalent nested array
     */
    public static Object[][] nestedListsToNestedArrays(List<List<Object>> lists){
        return lists.stream().map(List::toArray).toArray(Object[][]::new);
    }
}
