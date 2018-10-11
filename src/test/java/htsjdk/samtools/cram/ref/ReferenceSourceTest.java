package htsjdk.samtools.cram.ref;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import htsjdk.samtools.util.SequenceUtil;
import java.util.Arrays;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Created by vadim on 29/06/2017. */
public class ReferenceSourceTest extends HtsjdkTest {

  @Test
  public void testReferenceSourceUpperCasesBases() {
    final String sequenceName = "1";
    final String nonIupacCharacters = "1=eE";
    final byte[] originalRefBases =
        (nonIupacCharacters + SequenceUtil.getIUPACCodesString()).getBytes();
    SAMSequenceRecord sequenceRecord = new SAMSequenceRecord(sequenceName, originalRefBases.length);

    InMemoryReferenceSequenceFile memoryReferenceSequenceFile = new InMemoryReferenceSequenceFile();
    memoryReferenceSequenceFile.add(
        sequenceName, Arrays.copyOf(originalRefBases, originalRefBases.length));
    Assert.assertEquals(
        memoryReferenceSequenceFile.getSequence(sequenceName).getBases(), originalRefBases);

    ReferenceSource referenceSource = new ReferenceSource(memoryReferenceSequenceFile);
    byte[] refBasesFromSource = referenceSource.getReferenceBases(sequenceRecord, false);

    Assert.assertNotEquals(refBasesFromSource, originalRefBases);
    Assert.assertEquals(refBasesFromSource, SequenceUtil.upperCase(originalRefBases));
  }
}
