package htsjdk.samtools.cram.structure;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SliceTestHelper {

    public static Slice getSingleRecordSlice() {
        final SAMRecord samRecord = new SAMRecord(new SAMFileHeader());
        samRecord.setReadName("testRead");
        return new Slice(
                Collections.singletonList(new CRAMRecord(
                        CramVersions.CRAM_v3,
                        new CRAMEncodingStrategy(),
                        samRecord,
                        new byte[1],
                        1,
                        new HashMap<>())),
                new CompressionHeader(), 0);
    }

    // populate a SliceBlocksObject with an external block for each DataSeries, plus
    // core and embedded reference block content, and update expectedExternalContent
    static Slice getSliceWithSliceBlocksForAllDataSeries(
            final BlockCompressionMethod compressionMethod,
            final byte[] coreBlockContent,
            final byte[] embeddedRefBlockContent,
            final int embeddedRefBlockContentID,
            final Map<Integer, String> expectedExternalContentStrings) {

        final SAMRecord samRecord = new SAMRecord(new SAMFileHeader());
        samRecord.setReadName("testRead");
        final Slice slice = new Slice(
                Collections.singletonList(new CRAMRecord(
                        CramVersions.CRAM_v3,
                        new CRAMEncodingStrategy(),
                        samRecord,
                        new byte[1],
                        1,
                        new HashMap<>())),
                new CompressionHeader(), 0);

        // add a core block (technically, the core block is a bit stream, but for this test we'll have it
        // carry a stream of bits that are bytes we'll interpret as the bytes of a String)
        slice.getSliceBlocks().setCoreBlock(Block.createRawCoreDataBlock(coreBlockContent));

        // add an embedded reference block
        slice.setEmbeddedReferenceBlock(
                Block.createExternalBlock(
                        compressionMethod,
                        embeddedRefBlockContentID,
                        // use a non-default RANS order (ORDER one) compressor to ensure that the order
                        // is round-tripped correctly
                        ExternalCompressor.getCompressorForMethod(
                                compressionMethod,
                                compressionMethod == BlockCompressionMethod.RANS ?
                                        1 :
                                        ExternalCompressor.NO_COMPRESSION_ARG).compress(embeddedRefBlockContent),
                        embeddedRefBlockContent.length));

        // add one external block for each Data Series
        for (final DataSeries dataSeries : DataSeries.values()) {
            final String uncompressedContent = dataSeries.getCanonicalName();
            slice.getSliceBlocks().addExternalBlock(
                    Block.createExternalBlock(
                            compressionMethod,
                            dataSeries.getExternalBlockContentId(),
                            ExternalCompressor.getCompressorForMethod(
                                    compressionMethod,
                                    ExternalCompressor.NO_COMPRESSION_ARG).compress(uncompressedContent.getBytes()),
                            dataSeries.getCanonicalName().getBytes().length));
            expectedExternalContentStrings.put(dataSeries.getExternalBlockContentId(), uncompressedContent);
        }

        return slice;
    }

}
