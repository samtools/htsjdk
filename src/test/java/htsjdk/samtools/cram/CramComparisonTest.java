package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

public class CramComparisonTest extends HtsjdkTest {

    @Test
    public void testNormalizeCigarNullAndEmpty() {
        Assert.assertNull(CramComparison.normalizeCigar(null));
        Assert.assertEquals(CramComparison.normalizeCigar(""), "");
        Assert.assertEquals(CramComparison.normalizeCigar("*"), "*");
    }

    @Test
    public void testNormalizeCigarPureM() {
        Assert.assertEquals(CramComparison.normalizeCigar("150M"), "150M");
    }

    @Test
    public void testNormalizeCigarEqualToM() {
        Assert.assertEquals(CramComparison.normalizeCigar("150="), "150M");
    }

    @Test
    public void testNormalizeCigarEqualsAndX() {
        Assert.assertEquals(CramComparison.normalizeCigar("35=1X5=1X5="), "47M");
    }

    @Test
    public void testNormalizeCigarWithIndelsAndSoftClips() {
        Assert.assertEquals(
                CramComparison.normalizeCigar("270S31=2I48=1D15=4I1=1I12="),
                "270S31M2I48M1D15M4I1M1I12M");
    }

    @Test
    public void testNormalizeCigarLongWithRuns() {
        Assert.assertEquals(
                CramComparison.normalizeCigar("270S31=2I48=1D15=4I1=1I12=1I38=1D30=1I53=1D23=1D35=1X5=1X5=1X5=1X5=1X5=1X8=1D39=1D19=1D6=1D42=1D28=14955S"),
                "270S31M2I48M1D15M4I1M1I12M1I38M1D30M1I53M1D23M1D74M1D39M1D19M1D6M1D42M1D28M14955S");
    }

    @Test
    public void testNormalizeCigarPreservesNonMatchOps() {
        Assert.assertEquals(CramComparison.normalizeCigar("100M100N100M"), "100M100N100M");
        Assert.assertEquals(CramComparison.normalizeCigar("10S100M10S"), "10S100M10S");
        Assert.assertEquals(CramComparison.normalizeCigar("10H100M10H"), "10H100M10H");
    }
}
