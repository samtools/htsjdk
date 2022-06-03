package htsjdk.beta.codecs.reads.cram;

import htsjdk.HtsjdkTest;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import org.testng.Assert;
import org.testng.annotations.Test;

public class CRAMDecoderOptionsTest extends HtsjdkTest {

    @Test
    public void testOverrideCRAMReferenceSource() {
        final CRAMDecoderOptions cramDecoderOptions = new CRAMDecoderOptions();
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
        cramDecoderOptions.setReferencePath(null);
        cramDecoderOptions.setReferenceSource(cramReferenceSource);

        final CRAMReferenceSource actualCRAMReferenceSource = CRAMDecoder.getCRAMReferenceSource(cramDecoderOptions);
        Assert.assertTrue(actualCRAMReferenceSource == cramReferenceSource);

        cramDecoderOptions.setReferenceSource(null);
        final IOPath dummyFASTAPath = new HtsPath("dummyFasta.fasta");
        cramDecoderOptions.setReferencePath(dummyFASTAPath);
        Assert.assertTrue(cramDecoderOptions.getReferencePath().isPresent());
        Assert.assertEquals(cramDecoderOptions.getReferencePath().get(), dummyFASTAPath);
    }
}
