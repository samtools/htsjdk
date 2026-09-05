package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.index.HtsFileSpan;
import htsjdk.samtools.cram.CRAIQueryIndex;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * {@link SamReader.Indexing#getHtsIndex(Class)}: format-specific index capabilities are reached by
 * asking for the type, and a file whose index is not of that type answers empty rather than
 * synthesising one.
 */
public class SamReaderTypedIndexAccessTest extends HtsjdkTest {

    private static final Path INDEXED_BAM =
            Paths.get("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam");
    private static final Path CRAI_INDEXED_CRAM =
            Paths.get("src/test/resources/htsjdk/samtools/cram/cramQueryWithCRAI.cram");
    private static final Path BAI_INDEXED_CRAM =
            Paths.get("src/test/resources/htsjdk/samtools/cram/cramQueryWithBAI.cram");

    private static SamReader open(final Path path, final SamReaderFactory.Option... options) {
        return SamReaderFactory.makeDefault()
                .validationStringency(ValidationStringency.SILENT)
                .enable(options)
                .open(path);
    }

    @Test
    public void testABamAnswersForBamIndex() throws IOException {
        try (final SamReader reader = open(INDEXED_BAM)) {
            Assert.assertTrue(reader.indexing().getHtsIndex(BAMIndex.class).isPresent());
        }
    }

    @Test
    public void testABamDoesNotAnswerForCraiQueryIndex() throws IOException {
        try (final SamReader reader = open(INDEXED_BAM)) {
            Assert.assertTrue(
                    reader.indexing().getHtsIndex(CRAIQueryIndex.class).isEmpty());
        }
    }

    @Test
    public void testACraiIndexedCramAnswersForCraiQueryIndex() throws IOException {
        try (final SamReader reader = open(CRAI_INDEXED_CRAM)) {
            Assert.assertTrue(
                    reader.indexing().getHtsIndex(CRAIQueryIndex.class).isPresent());
        }
    }

    @Test
    public void testACraiIndexedCramDoesNotSynthesiseABamIndex() throws IOException {
        // The deprecated getIndex() would build a BAI here; the typed accessor must not.
        try (final SamReader reader = open(CRAI_INDEXED_CRAM)) {
            Assert.assertTrue(reader.indexing().getHtsIndex(BAMIndex.class).isEmpty());
        }
    }

    @Test
    public void testABaiIndexedCramAnswersForBamIndex() throws IOException {
        try (final SamReader reader = open(BAI_INDEXED_CRAM)) {
            Assert.assertTrue(reader.indexing().getHtsIndex(BAMIndex.class).isPresent());
            Assert.assertTrue(
                    reader.indexing().getHtsIndex(CRAIQueryIndex.class).isEmpty());
        }
    }

    @Test
    public void testBrowseableAccessorsAgreeWithTheTypedAccessor() throws IOException {
        // A disk-based BAI is not browseable; a cached one is.
        try (final SamReader reader = open(INDEXED_BAM)) {
            Assert.assertFalse(reader.indexing().hasBrowseableIndex());
            Assert.assertTrue(
                    reader.indexing().getHtsIndex(BrowseableBAMIndex.class).isEmpty());
        }
        try (final SamReader reader = open(INDEXED_BAM, SamReaderFactory.Option.CACHE_FILE_BASED_INDEXES)) {
            Assert.assertTrue(reader.indexing().hasBrowseableIndex());
            Assert.assertSame(
                    reader.indexing().getBrowseableIndex(),
                    reader.indexing().getHtsIndex(BrowseableBAMIndex.class).orElseThrow());
        }
    }

    @Test(expectedExceptions = SAMException.class)
    public void testGetBrowseableIndexThrowsWhenTheIndexIsNotBrowseable() throws IOException {
        try (final SamReader reader = open(INDEXED_BAM)) {
            reader.indexing().getBrowseableIndex();
        }
    }

    @Test
    public void testASpanFromGetHtsIndexCanBeIteratedDirectly() throws IOException {
        try (final SamReader reader = open(INDEXED_BAM)) {
            final HtsFileSpan span = reader.indexing().getHtsIndex().getSpanOverlapping(0, 1, 1_000_000);
            Assert.assertFalse(span.isEmpty());
            try (final SAMRecordIterator iterator = reader.indexing().iterator(span)) {
                Assert.assertTrue(iterator.hasNext());
            }
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testASpanOfAForeignTypeIsRejected() throws IOException {
        try (final SamReader reader = open(INDEXED_BAM)) {
            reader.indexing().iterator((HtsFileSpan) () -> false);
        }
    }
}
