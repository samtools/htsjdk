package htsjdk.beta.codecs.reads.cram;

import htsjdk.HtsjdkTest;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import org.testng.Assert;
import org.testng.annotations.Test;

public class CRAMEncoderOptionsTest extends HtsjdkTest {
    @Test
    public void testOverrideCRAMReferenceSource() {
        final CRAMEncoderOptions cramEncoderOptions = new CRAMEncoderOptions();
        final CRAMReferenceSource cramReferenceSource = new CRAMReferenceSource() {
            @Override
            public byte[] getReferenceBases(SAMSequenceRecord sequenceRecord, boolean tryNameVariants) {
                return null;
            }

            @Override
            public byte[] getReferenceBasesByRegion(SAMSequenceRecord sequenceRecord, int zeroBasedStart,
                                                    int requestedRegionLength) {
                return null;
            }
        };
        cramEncoderOptions.setReferencePath(null);
        cramEncoderOptions.setReferenceSource(cramReferenceSource);

        final CRAMReferenceSource actualCRAMReferenceSource = CRAMEncoder.getCRAMReferenceSource(cramEncoderOptions);
        Assert.assertTrue(actualCRAMReferenceSource == cramReferenceSource);

        cramEncoderOptions.setReferenceSource(null);
        final IOPath dummyFASTAPath = new HtsPath("dummyFasta.fasta");
        cramEncoderOptions.setReferencePath(dummyFASTAPath);
        Assert.assertTrue(cramEncoderOptions.getReferencePath().isPresent());
        Assert.assertEquals(cramEncoderOptions.getReferencePath().get(), dummyFASTAPath);
    }

}
