package htsjdk.samtools;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author nhomer
 */
public class SamFlagFieldTest {

    @Test
    public void testAllFlags() {
        final int flagAsInteger = SamFlagField.STRING.parse(SamFlagField.STRING.getFlag2CharTable());
        final String flagAsString = SamFlagField.STRING.format(flagAsInteger);
        
        Assert.assertEquals(flagAsString.compareTo(SamFlagField.STRING.getFlag2CharTable().replace("\0", "")), 0);
    }

    @Test
    public void testNoFlags() {
        final int flagAsInteger = SamFlagField.STRING.parse(SamFlagField.STRING.getNotFlag2CharTable());
        final String flagAsString = SamFlagField.STRING.format(flagAsInteger);

        Assert.assertEquals(flagAsString.compareTo(SamFlagField.STRING.getNotFlag2CharTable().replace("\0", "")), 0);
    }
}