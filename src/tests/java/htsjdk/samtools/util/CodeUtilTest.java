package htsjdk.samtools.util;

import org.testng.Assert;
import org.testng.annotations.Test;

public class CodeUtilTest {

    @Test
    public void getOrElseTest() {
        final String notNull = "Not null!";
        Assert.assertEquals(CodeUtil.getOrElse(notNull, null), notNull);
        Assert.assertEquals(CodeUtil.getOrElse(null, notNull), notNull);
        Assert.assertEquals((Object) CodeUtil.getOrElse(null, null), (Object) null);
    }
}
