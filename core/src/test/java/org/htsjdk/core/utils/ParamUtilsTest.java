package org.htsjdk.core.utils;

import org.htsjdk.test.HtsjdkBaseTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

/**
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public class ParamUtilsTest extends HtsjdkBaseTest {

    @Test
    public void testValidateArgTrueCondition() {
        ParamUtils.validate(true, () -> "Should not throw");
    }

    @Test
    public void testValidateArgFalseCondition() {
        final String message = "thrown";
        try {
            ParamUtils.validate(false, () -> message);
            Assert.fail();
        } catch (final IllegalArgumentException e) {
            Assert.assertEquals(e.getMessage(), message);
        }
    }

    @DataProvider
    public Object[][] invalidIndexes() {
        return new Object[][] {
                {-1, -1},
                {0, -1},
                {10, 10}
        };
    }


    @Test(dataProvider = "invalidIndexes", expectedExceptions = IllegalArgumentException.class)
    public void testValidateIndexInvalid(final int index, final int length) {
        ParamUtils.validateIndex(index, length);
    }

    @DataProvider
    public Object[][] validIndexes() {
        return new Object[][] {
                {0, 10},
                {5, 8},
                {0, 1},
                {10, 11}
        };
    }

    @Test(dataProvider = "validIndexes")
    public void testValidateIndexValid(final int index, final int length) {
        ParamUtils.validateIndex(index, length);
    }

    @Test
    public void testNonNullWithNullObject() {
        final String message = "is null";
        try {
            ParamUtils.nonNull(null, () -> message);
            Assert.fail();
        } catch (final IllegalArgumentException e) {
            Assert.assertEquals(e.getMessage(), message);
        }
    }

    @Test
    public void testNonNullWithNullObjectWithoutMessage() {
        try {
            ParamUtils.nonNull((String) null);
            Assert.fail();
        } catch (final IllegalArgumentException e) {
            Assert.assertEquals(e.getMessage(), "object cannot be null");
        }
    }

    @Test
    public void testNonNullReturnSame() {
        final String myString = "hello world";
        final String returned = ParamUtils.nonNull(myString, () -> "Should not throw");
        Assert.assertSame(returned, myString);
    }

    @DataProvider
    public Object[][] emptyOrNullCollections() {
        return new Object[][]{
                {null, "null collection"},
                {new ArrayList<>(), "empty list"},
                {new HashSet<>(), "empty set"}
        };
    }

    @Test(dataProvider = "emptyOrNullCollections")
    public void testNonEmptyCollection(final Collection<?> collection, final String message) {
        try {
            ParamUtils.nonEmpty(collection, () -> message);
            Assert.fail();
        } catch (final IllegalArgumentException e) {
            Assert.assertEquals(e.getMessage(), message);
        }
    }

    // Note: the extra argument message is to share data provider with testNonEmptyCollection
    @Test(dataProvider = "emptyOrNullCollections")
    public void testNonEmptyCollectionWithoutMessage(final Collection<?> collection, final String message) {
        try {
            ParamUtils.nonEmpty(collection);
            Assert.fail();
        } catch (final IllegalArgumentException e) {
            Assert.assertEquals(e.getMessage(), "collection cannot be null or empty");
        }
    }
}