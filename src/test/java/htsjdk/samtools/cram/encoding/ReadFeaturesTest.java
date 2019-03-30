package htsjdk.samtools.cram.encoding;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.readfeatures.Bases;
import htsjdk.samtools.cram.encoding.readfeatures.Scores;
import htsjdk.samtools.cram.encoding.readfeatures.SoftClip;
import htsjdk.samtools.cram.encoding.readfeatures.Substitution;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

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

    @Test
    public void testSubstitutionEqualsAndHashCodeAreConsistent() {
        final List<Substitution> substitutions = new ArrayList<>();
        for (int position : new int[] {0, 1}) {
            for (byte base : new byte[] {(byte) -1, (byte) 'A'}) {
                for (byte referenceBase : new byte[] {(byte) -1, (byte) 'C'}) {
                    for (byte code : new byte[] {Substitution.NO_CODE, (byte) 1, (byte) 2}) {
                        Substitution substitution = new Substitution(position, base, referenceBase);
                        substitution.setCode(code);
                        substitutions.add(substitution);
                    }
                }
            }
        }

        for (Substitution s1 : substitutions) {
            for (Substitution s2 : substitutions) {
                if (s1.equals(s2)) {
                    Assert.assertEquals(s1.hashCode(), s2. hashCode(),
                            String.format("Comparing %s (%s) and %s (%s)", s1, s1.getCode(), s2, s2.getCode()));
                }
            }
        }
    }
}
