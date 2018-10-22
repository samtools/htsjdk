package htsjdk.utils;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

public class TestNGUtilsTest extends HtsjdkTest {

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
                {new Object[][]{{1}, {2}, {3}}, new Object[][]{{'a', 'b'}, {'c', 'd'}},
                        new Object[][]{{1, 'a', 'b'}, {1, 'c', 'd'},
                                {2, 'a', 'b'}, {2, 'c', 'd'},
                                {3, 'a', 'b'}, {3, 'c', 'd'}}},
                {new Object[][]{ new Object[0], new Object[0], new Object[0]}}
        };
    }

    @Test(dataProvider = "getDataProviders")
    public void testProduct(Object[][] dataProvider1, Object[][] dataProvider2, Object[][] expectedResult) {
        final Object[][] actual = TestNGUtils.cartesianProduct(dataProvider1, dataProvider2);
        assertNestedArraysEqual(actual, expectedResult);
    }
}
