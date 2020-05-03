package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

public class RecyclingSupplierTest extends HtsjdkTest {
    @Test
    public void testExistingInstancesAreReused() {
        RecyclingSupplier<Object> rs = new RecyclingSupplier<Object>(() -> new Object());
        Object r1 = rs.get();
        Object r2 = rs.get();
        Assert.assertNotSame(r1, r2);
        rs.recycle(r1);
        Object r1again = rs.get();
        Assert.assertSame(r1, r1again);
        Object r3 = rs.get();
        Assert.assertNotSame(r1, r3);
        Assert.assertNotSame(r2, r3);
    }
}