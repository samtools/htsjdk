package htsjdk.samtools;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author nhomer
 */
public class SamFlagFieldTest {

    @Test
    public void testAllFlags() {
        int flagAsInteger = 0;
        for (final SAMFlag samFlag : SAMFlag.values()) {
            flagAsInteger |= samFlag.flag;
        }
        final String flagAsString = SamFlagField.STRING.format(flagAsInteger);

        Assert.assertEquals(flagAsString, "urURpP12sSxd");
        Assert.assertEquals(SamFlagField.STRING.parse(flagAsString), flagAsInteger);
    }

    @Test
    public void testAllFlagsReverseOrder() {
        int flagAsInteger = 0;
        for (final SAMFlag samFlag : SAMFlag.values()) {
            flagAsInteger |= samFlag.flag;
        }
        final String flagAsString = new StringBuilder("urURpP12sSxd").reverse().toString();

        Assert.assertEquals(flagAsInteger, SamFlagField.STRING.parse(flagAsString));
        Assert.assertEquals(SamFlagField.STRING.parse(flagAsString), flagAsInteger);
    }

    @Test
    public void testForwardStrandFlags() {
        final int flagAsInteger = SamFlagField.STRING.parse("f");
        final String flagAsString = SamFlagField.STRING.format(flagAsInteger);

        Assert.assertEquals(flagAsString, "mf");
        Assert.assertEquals(SamFlagField.STRING.parse(flagAsString), flagAsInteger);
    }

    @Test
    public void testPairedForwardStrandFlags() {
        final int flagAsInteger = SamFlagField.STRING.parse("mfMFp");
        final String flagAsString = SamFlagField.STRING.format(flagAsInteger);

        Assert.assertEquals(flagAsString, "mfMFp");
        Assert.assertEquals(SamFlagField.STRING.parse(flagAsString), flagAsInteger);
    }

    @Test
    public void testMappedFlags() {
        final int flagAsInteger = SamFlagField.STRING.parse("m");
        final String flagAsString = SamFlagField.STRING.format(flagAsInteger);

        Assert.assertEquals(flagAsString, "mf");
        Assert.assertEquals(SamFlagField.STRING.parse(flagAsString), flagAsInteger);
    }

    @Test
    public void testPairedMappedFlags() {
        final int flagAsInteger = SamFlagField.STRING.parse("pmM");
        final String flagAsString = SamFlagField.STRING.format(flagAsInteger);

        Assert.assertEquals(flagAsString, "mfMFp");
        Assert.assertEquals(SamFlagField.STRING.parse(flagAsString), flagAsInteger);
    }

    @Test
    public void testMateMappedNotOnFragmentFlags() {
        final int flagAsInteger = SAMFlag.MATE_UNMAPPED.flag;
        final String flagAsString = SamFlagField.STRING.format(flagAsInteger);

        Assert.assertEquals(flagAsString, "mfU");
        Assert.assertEquals(SamFlagField.STRING.parse(flagAsString), flagAsInteger);
    }

    @Test
    public void testMateMappedOnlyOnPairsFlags() {
        final int flagAsInteger = SAMFlag.MATE_UNMAPPED.flag | SAMFlag.READ_PAIRED.flag;
        final String flagAsString = SamFlagField.STRING.format(flagAsInteger);

        Assert.assertEquals(flagAsString, "mfUFp");
        Assert.assertEquals(SamFlagField.STRING.parse(flagAsString), flagAsInteger);
    }

    @Test
    public void testMateForwardStrandNotOnFragmentFlags() {
        final int flagAsInteger = 0;
        final String flagAsString = SamFlagField.STRING.format(flagAsInteger);

        Assert.assertEquals(flagAsString, "mf");
        Assert.assertEquals(SamFlagField.STRING.parse(flagAsString), flagAsInteger);
    }

    @Test
    public void testMateForwardStrandOnlyOnPairsFlags() {
        final int flagAsInteger = SAMFlag.READ_PAIRED.flag;
        final String flagAsString = SamFlagField.STRING.format(flagAsInteger);

        Assert.assertEquals(flagAsString, "mfMFp");
        Assert.assertEquals(SamFlagField.STRING.parse(flagAsString), flagAsInteger);
    }

    @Test
    public void testFlagTypesParsing() {
        Assert.assertEquals(SamFlagField.of("0"), SamFlagField.DECIMAL);
        Assert.assertEquals(SamFlagField.of("1234"), SamFlagField.DECIMAL);
        Assert.assertEquals(SamFlagField.of("0xDOESNOTMATTER"), SamFlagField.HEXADECIMAL);
        Assert.assertEquals(SamFlagField.of("0x"), SamFlagField.HEXADECIMAL);
        Assert.assertEquals(SamFlagField.of("0[^x]DOESNOTMATTER"), SamFlagField.OCTAL);
        Assert.assertEquals(SamFlagField.of("0a"), SamFlagField.OCTAL);
        Assert.assertEquals(SamFlagField.of("DOESNOTMATTER"), SamFlagField.STRING);
    }

    @Test
    public void testFlagTypesFormatting() {

        Assert.assertEquals(SamFlagField.DECIMAL.format(1), "1");
        Assert.assertEquals(SamFlagField.DECIMAL.format(124), "124");

        Assert.assertEquals(SamFlagField.HEXADECIMAL.format(1), "0x1");
        Assert.assertEquals(SamFlagField.HEXADECIMAL.format(9), "0x9");
        Assert.assertEquals(SamFlagField.HEXADECIMAL.format(10), "0xa");
        Assert.assertEquals(SamFlagField.HEXADECIMAL.format(16), "0x10");

        Assert.assertEquals(SamFlagField.OCTAL.format(1), "01");
        Assert.assertEquals(SamFlagField.OCTAL.format(124), "0174");

        Assert.assertEquals(SamFlagField.STRING.format(337), "mrMFp1s");
    }
    
    @Test(expectedExceptions = SAMFormatException.class)
    public void testIllegalStringFlagCharacter(){
        SamFlagField.STRING.parse("HELLO WORLD");
    }

    @Test(expectedExceptions = SAMFormatException.class)
    public void testIllegalHexadecimalFlagCharacter(){
        SamFlagField.HEXADECIMAL.parse("HELLO WORLD");
    }

    @Test(expectedExceptions = SAMFormatException.class)
    public void testIllegalStringFlagCharacterExclamation(){
        SamFlagField.STRING.parse("pmMr!F1s");
    }
}