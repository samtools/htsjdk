package htsjdk.utils;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TestNGUtilsTest extends HtsjdkTest {

    public static final Object[] EMPTY_ARRAY = new Object[0];

    @DataProvider
    public Object[][] getArraysAndLists() {
        return new Object[][]{
                {
                        new Object[][]{
                                {1, 2},
                                {3, 4}
                        },
                        Arrays.asList(
                                Arrays.asList(1, 2),
                                Arrays.asList(3, 4)
                        )
                },
                {
                        new Object[][]{
                                {1}
                        },
                        Arrays.asList(
                                Arrays.asList(1)
                        )
                },
                {
                        new Object[][]{
                                {1, 2, 3},
                                {4, 5, 6}
                        },
                        Arrays.asList(
                                Arrays.asList(1, 2, 3),
                                Arrays.asList(4, 5, 6)
                        )
                },
                {
                        new Object[][]{
                                {1},
                                {2},
                                {3},
                                {4}
                        },
                        Arrays.asList(
                                Arrays.asList(1),
                                Arrays.asList(2),
                                Arrays.asList(3),
                                Arrays.asList(4)
                        )
                },
        };
    }

    @Test(dataProvider = "getArraysAndLists")
    public void testObjectsToLists(Object[][] objects, List<List<Object>> lists) {
        Assert.assertEquals(TestNGUtils.nestedArraysToNestedLists(objects), lists);
    }

    @Test(dataProvider = "getArraysAndLists")
    public void testListsToArrays(Object[][] objects, List<List<Object>> lists) {
        final Object[][] convertedObjects = TestNGUtils.nestedListsToNestedArrays(lists);
        assertNestedArraysEqual(objects, convertedObjects);
    }

    private static void assertNestedArraysEqual(Object[][] objects, Object[][] convertedObjects) {
        for (int i = 0; i < objects.length; i++) {
            Assert.assertEquals(convertedObjects[i], objects[i]);
        }
    }

    @DataProvider
    public Object[][] getDataProviders() {
        return new Object[][]{
                {new Object[][]{{1, 2}}, new Object[][]{{3}, {4}}, new Object[][]{{1, 2, 3}, {1, 2, 4}}},
                {new Object[][]{{1}}, new Object[][]{{2}}, new Object[][]{{1, 2}}},
                {new Object[][]{{1}}, new Object[][]{{2, 3}}, new Object[][]{{1, 2, 3}}},
                {new Object[][]{{1}, {2}, {3}}, new Object[][]{{'a', 'b'}, {'c', 'd'}},
                        new Object[][]{{1, 'a', 'b'}, {1, 'c', 'd'},
                                {2, 'a', 'b'}, {2, 'c', 'd'},
                                {3, 'a', 'b'}, {3, 'c', 'd'}}},
                {new Object[][]{{}}, new Object[][]{{}}, new Object[][]{{}}},
                {new Object[][]{{}}, new Object[][]{{1}}, new Object[][]{{1}}},
                {new Object[][]{{}}, new Object[][]{{1, 2}}, new Object[][]{{1, 2}}},
                {new Object[][]{{1}}, new Object[][]{{}}, new Object[][]{{1}}},
                {new Object[][]{{}}, new Object[][]{{1}}, new Object[][]{{1}}},
                {new Object[][]{{EMPTY_ARRAY}}, new Object[][]{{1}}, new Object[][]{{EMPTY_ARRAY, 1}}},
                {new Object[][]{{1}, {2}, {3}}, new Object[][]{{4}, {5}, {6}},
                        new Object[][]{{1,4}, {1,5}, {1,6}, {2, 4}, {2, 5}, {2, 6}, {3, 4}, {3, 5}, {3, 6}}}
        };
    }

    @Test(dataProvider = "getDataProviders")
    public void testProduct(Object[][] dataProvider1, Object[][] dataProvider2, Object[][] expectedResult) {
        final Object[][] actual = TestNGUtils.cartesianProduct(dataProvider1, dataProvider2);
        assertNestedArraysEqual(actual, expectedResult);
    }


    @DataProvider
    public Object[][] getDifferingNumbersOfProviders() {
        final Object[][] p1 = new Object[][]{{1}, {2}};
        final Object[][] p2 = new Object[][]{{"a", "b"}};
        final Object[][] p3 = new Object[][]{{}};
        final Object[][] p4 = new Object[][]{{3}, {4}, {5}};

        final Object[][] expected0 = new Object[][]{{}};
        final Object[][] expected1 = new Object[][]{{1}, {2}};
        final Object[][] expected2 = new Object[][]{{1, "a", "b"}, {2, "a", "b"}};
        final Object[][] expected3 = expected2;
        final Object[][] expected4 = new Object[][]{
                {1, "a", "b", 3},
                {1, "a", "b", 4},
                {1, "a", "b", 5},
                {2, "a", "b", 3},
                {2, "a", "b", 4},
                {2, "a", "b", 5}
        };

        return new Object[][]{
                {Arrays.asList(), expected0},
                {Collections.singletonList(p1), expected1},
                {Arrays.asList(p1, p2), expected2},
                {Arrays.asList(p1, p2, p3), expected3},
                {Arrays.asList(p1, p2, p3, p4), expected4}
        };
    }

    @Test(dataProvider = "getDifferingNumbersOfProviders")
    public void testCartesianProductOfManyProviders(List<Object[]> providers, Object[][] expected){
        final Object[][] product = TestNGUtils.cartesianProduct(providers.toArray(new Object[][][]{}));
        assertNestedArraysEqual(product, expected);
    }

    @Test
    public void testSingleProvider() {
        final Object[][] expected = {{1, 2}};
        final Object[][] product = TestNGUtils.cartesianProduct(expected);
        assertNestedArraysEqual(product, expected);
    }

}
