package htsjdk.tribble.index.tabix;

import htsjdk.HtsjdkTest;
import htsjdk.index.BinningIndex;
import htsjdk.index.ReferenceBins;
import htsjdk.samtools.util.BlockCompressedFilePointerUtil;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.tribble.SimpleFeature;
import htsjdk.tribble.util.LittleEndianOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.OptionalLong;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TabixIndexCreatorTest extends HtsjdkTest {

    /** Three features on c1 and two on c2, each in its own BGZF block. */
    private static BinningIndex indexOfFiveFeatures(final TabixIndexType indexType) {
        final TabixIndexCreator creator = new TabixIndexCreator(null, TabixFormat.VCF, indexType);
        final String[] contigs = {"c1", "c1", "c1", "c2", "c2"};
        for (int i = 0; i < contigs.length; i++) {
            creator.addFeature(
                    new SimpleFeature(contigs[i], 1000 * (i + 1), 1000 * (i + 1)),
                    BlockCompressedFilePointerUtil.makeFilePointer(10_000L * i, 0));
        }
        final TabixIndex index =
                (TabixIndex) creator.finalizeIndex(BlockCompressedFilePointerUtil.makeFilePointer(50_000L, 0));
        return index.getBinningIndex();
    }

    private static void assertCounts(final BinningIndex index, final int reference, final long mapped) {
        final ReferenceBins.Metadata metadata =
                index.getReference(reference).getMetadata().orElseThrow();
        Assert.assertEquals(metadata.mappedCount(), mapped, "records on sequence " + reference);
        Assert.assertEquals(metadata.unmappedCount(), 0, "unmapped records on sequence " + reference);
    }

    @Test
    public void testEachSequenceOfATbiHasItsRecordCount() {
        final BinningIndex index = indexOfFiveFeatures(TabixIndexType.TBI);
        assertCounts(index, 0, 3);
        assertCounts(index, 1, 2);
    }

    @Test
    public void testEachSequenceOfACsiHasItsRecordCount() {
        final BinningIndex index = indexOfFiveFeatures(TabixIndexType.CSI);
        assertCounts(index, 0, 3);
        assertCounts(index, 1, 2);
    }

    @Test
    public void testATbiCountsNoRecordsWithoutAPosition() {
        Assert.assertEquals(indexOfFiveFeatures(TabixIndexType.TBI).getNoCoordinateCount(), OptionalLong.of(0));
    }

    @Test
    public void testACsiCountsNoRecordsWithoutAPosition() {
        Assert.assertEquals(indexOfFiveFeatures(TabixIndexType.CSI).getNoCoordinateCount(), OptionalLong.of(0));
    }

    @Test
    public void testRecordCountsSurviveWritingAndReadingATbi() throws IOException {
        final TabixIndexCreator creator = new TabixIndexCreator(null, TabixFormat.VCF, TabixIndexType.TBI);
        creator.addFeature(new SimpleFeature("c1", 1000, 1000), BlockCompressedFilePointerUtil.makeFilePointer(0, 0));
        creator.addFeature(new SimpleFeature("c1", 2000, 2000), BlockCompressedFilePointerUtil.makeFilePointer(0, 100));
        final TabixIndex index =
                (TabixIndex) creator.finalizeIndex(BlockCompressedFilePointerUtil.makeFilePointer(0, 200));
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (LittleEndianOutputStream out =
                new LittleEndianOutputStream(new BlockCompressedOutputStream(bytes, null))) {
            index.write(out);
        }
        final TabixIndex read =
                new TabixIndex(new BlockCompressedInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertCounts(read.getBinningIndex(), 0, 2);
        Assert.assertEquals(read.getBinningIndex().getNoCoordinateCount(), OptionalLong.of(0));
    }
}
