package htsjdk.samtools.cram.encoding;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.readfeatures.Bases;
import htsjdk.samtools.cram.encoding.readfeatures.Scores;
import htsjdk.samtools.cram.encoding.readfeatures.SoftClip;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ReadFeaturesTest extends HtsjdkTest {

    // equals() was incorrect for these classes

    @Test
    public void faultyEquality() {
        final Bases b1 = new Bases(0, new byte[] {});
        final Bases b2 = new Bases(0, new byte[] {});
        Assert.assertEquals(b1, b2);

        final Scores s1 = new Scores(0, new byte[] {});
        final Scores s2 = new Scores(0, new byte[] {});
        Assert.assertEquals(s1, s2);

        final SoftClip sc1 = new SoftClip(0, new byte[] {});
        final SoftClip sc2 = new SoftClip(0, new byte[] {});
        Assert.assertEquals(sc1, sc2);
    }
}
