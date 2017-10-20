package htsjdk.utils;

import org.testng.annotations.DataProvider;
import org.testng.collections.Sets;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

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
}
