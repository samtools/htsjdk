package htsjdk.utils;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Created by farjoun on 9/28/17.
 */
public class ClassFinderTest extends HtsjdkTest {

    @Test
    public void testFindTestClass() {
        ClassFinder finder = new ClassFinder();
        finder.find("htsjdk", HtsjdkTest.class);
        Assert.assertFalse(finder.getClasses().isEmpty());

        Assert.assertEquals(finder.getClasses().stream()
                .filter(c -> c.getName().equals("htsjdk.utils.ClassFinderTest")).count(), 1);
    }

    @Test
    public void testFindClassFinder() {
        ClassFinder finder = new ClassFinder();
        finder.find("htsjdk", Object.class);

        Assert.assertFalse(finder.getClasses().isEmpty());
        Assert.assertEquals(finder.getClasses().stream()
                .filter(c -> c.getName().equals("htsjdk.utils.ClassFinder")).count(), 1);
    }
}