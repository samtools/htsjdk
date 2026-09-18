package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/** {@link BamIndexType#resolve}: which index {@link BamIndexType#AUTO} stands for. */
public class BamIndexTypeTest extends HtsjdkTest {
    private static final int BAI_REACH = 1 << 29;

    private static SAMSequenceDictionary dictionary(final int... lengths) {
        final SAMSequenceDictionary dictionary = new SAMSequenceDictionary();
        for (int i = 0; i < lengths.length; i++) {
            dictionary.addSequence(new SAMSequenceRecord("chr" + (i + 1), lengths[i]));
        }
        return dictionary;
    }

    @Test
    public void testAutoIsBaiWhenEverySequenceFits() {
        Assert.assertEquals(BamIndexType.AUTO.resolve(dictionary(249_000_000, 1_000)), BamIndexType.BAI);
    }

    @Test
    public void testAutoIsBaiForASequenceExactlyAtTheLimit() {
        Assert.assertEquals(BamIndexType.AUTO.resolve(dictionary(BAI_REACH)), BamIndexType.BAI);
    }

    @Test
    public void testAutoIsCsiWhenAnySequenceIsBeyondTheLimit() {
        Assert.assertEquals(BamIndexType.AUTO.resolve(dictionary(1_000, BAI_REACH + 1, 1_000)), BamIndexType.CSI);
    }

    @Test
    public void testAutoIsBaiForAnEmptyDictionary() {
        Assert.assertEquals(BamIndexType.AUTO.resolve(new SAMSequenceDictionary(List.of())), BamIndexType.BAI);
    }

    @Test
    public void testExplicitTypesResolveToThemselves() {
        Assert.assertEquals(BamIndexType.BAI.resolve(dictionary(BAI_REACH + 1)), BamIndexType.BAI);
        Assert.assertEquals(BamIndexType.CSI.resolve(dictionary(1_000)), BamIndexType.CSI);
    }
}
