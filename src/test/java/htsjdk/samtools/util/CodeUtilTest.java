package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class CodeUtilTest extends HtsjdkTest {

    @Test
    public void getOrElseTest() {
        final String notNull = "Not null!";
        Assert.assertEquals(CodeUtil.getOrElse(notNull, null), notNull);
        Assert.assertEquals(CodeUtil.getOrElse(null, notNull), notNull);
        Assert.assertEquals((Object) CodeUtil.getOrElse(null, null), (Object) null);
    }

    @Test
    public void applyIfNotNullNegativeTest() {
        CodeUtil.applyIfNotNull((Object) null, (o) -> Assert.fail("this shouldn't have been called"));
        CodeUtil.applyIfNotNull((Integer) null, (o) -> Assert.fail("this shouldn't have been called"));
        CodeUtil.applyIfNotNull((Double) null, (o) -> Assert.fail("this shouldn't have been called"));
        CodeUtil.applyIfNotNull((String) null, (o) -> Assert.fail("this shouldn't have been called"));
        CodeUtil.applyIfNotNull((List<Object>) null, (o) -> Assert.fail("this shouldn't have been called"));
    }

    @Test(expectedExceptions = AssertionError.class)
    public void applyIfNotNullPositiveTest() {
        CodeUtil.applyIfNotNull(1, (o) -> Assert.assertNotEquals(o, 1,
                "Throwing this proves that the method was called with value 1"));
    }
}
