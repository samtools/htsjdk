package htsjdk.tribble;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Interval;
import htsjdk.testutil.streams.RecordingChannelWrapper;
import htsjdk.tribble.IntervalList.IntervalListCodec;
import htsjdk.tribble.bed.BEDCodec;
import htsjdk.tribble.bed.BEDFeature;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFCodec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class TribbleIndexedFeatureReaderTest extends HtsjdkTest {

    @DataProvider(name = "featureFileStrings")
    public Object[][] createFeatureFileStrings() {
        return new Object[][] {
            {TestUtils.DATA_DIR + "test.vcf", 5},
            {TestUtils.DATA_DIR + "test.vcf.gz", 5},
            {TestUtils.DATA_DIR + "test.vcf.bgz", 5},
            {TestUtils.DATA_DIR + "test with spaces.vcf", 5}
        };
    }

    @Test(dataProvider = "featureFileStrings")
    public void testUnindexedVCF(final String testPath, final int expectedCount) throws IOException {
        final VCFCodec codec = new VCFCodec();
        try (final TribbleIndexedFeatureReader<VariantContext, LineIterator> featureReader =
                new TribbleIndexedFeatureReader<>(testPath, codec, false)) {

            Assert.assertEquals(featureReader.iterator().stream().count(), expectedCount);
        }
    }

    @DataProvider()
    public Object[][] createIndexedFeatureFileStrings() {
        return new Object[][] {
            {TestUtils.DATA_DIR + "test.tabix.bed", 100000},
            {TestUtils.DATA_DIR + "test.tabix.bed", 100020},
        };
    }

    @Test(
            dataProvider = "createIndexedFeatureFileStrings",
            expectedExceptions = TribbleException.MalformedFeatureFile.class)
    public void testIndexedTribble(final String testPath, final int start) throws IOException {
        final BEDCodec codec = new BEDCodec();
        try (final TribbleIndexedFeatureReader<BEDFeature, LineIterator> featureReader =
                new TribbleIndexedFeatureReader<>(testPath, codec, true)) {

            featureReader.query("chr1", start, 100040).stream().count();
        }
    }

    @DataProvider()
    public Object[][] createIntervalFileStrings() {
        return new Object[][] {{Path.of(TestUtils.DATA_DIR, "interval_list/shortExample.interval_list"), 4}};
    }

    @Test(dataProvider = "createIntervalFileStrings")
    public void testUnIndexedIntervalList(final Path testPath, final int expectedCount) throws IOException {
        final IntervalListCodec codec = new IntervalListCodec();
        try (final TribbleIndexedFeatureReader<Interval, LineIterator> featureReader =
                new TribbleIndexedFeatureReader<>(testPath.toAbsolutePath().toString(), codec, false)) {
            Assert.assertEquals(featureReader.iterator().stream().count(), expectedCount);
        }
    }

    @Test(dataProvider = "createIntervalFileStrings", expectedExceptions = TribbleException.class)
    public void testUnIndexedIntervalListWithQuery(final Path testPath, final int ignored) throws IOException {
        final IntervalListCodec codec = new IntervalListCodec();
        try (final TribbleIndexedFeatureReader<Interval, LineIterator> featureReader =
                new TribbleIndexedFeatureReader<>(testPath.toAbsolutePath().toString(), codec, false)) {

            Assert.assertEquals(
                    featureReader.query("1", 17032814, 17032814).stream().count(), 1);
        }
    }

    @Test(expectedExceptions = TribbleException.MalformedFeatureFile.class)
    public void testPoolyFormatedIntervalListWithQuery() throws IOException {
        final IntervalListCodec codec = new IntervalListCodec();
        final Path testPath = Path.of(TestUtils.DATA_DIR, "interval_list/badExample.interval_list");
        try (final TribbleIndexedFeatureReader<Interval, LineIterator> featureReader =
                new TribbleIndexedFeatureReader<>(testPath.toAbsolutePath().toString(), codec, false)) {
            final CloseableTribbleIterator<Interval> iterator = featureReader.iterator();
            int numberOfRecords = 0;
            while (iterator.hasNext()) {
                iterator.next();
                numberOfRecords++;
            }
            Assert.assertEquals(numberOfRecords, 4);
        }
    }

    /** A BED of one feature covering 1-based chr1:50-149, with a linear Tribble index beside it. */
    private static Path indexedBedWithOneFeature() throws IOException {
        final Path bed = Files.createTempFile("TribbleIndexedFeatureReaderTest", ".bed");
        IOUtil.deleteOnExit(bed);
        Files.write(bed, "chr1\t49\t149\tf1\n".getBytes(StandardCharsets.US_ASCII));
        final Path idx = Tribble.indexPath(bed);
        IOUtil.deleteOnExit(idx);
        IndexFactory.createLinearIndex(bed, new BEDCodec()).write(idx);
        return bed;
    }

    private static int countQueried(final Path bed, final int start, final int end) throws IOException {
        try (TribbleIndexedFeatureReader<BEDFeature, LineIterator> reader =
                        new TribbleIndexedFeatureReader<>(bed.toString(), new BEDCodec(), true);
                CloseableTribbleIterator<BEDFeature> features = reader.query("chr1", start, end)) {
            int count = 0;
            while (features.hasNext()) {
                features.next();
                count++;
            }
            return count;
        }
    }

    @Test
    public void aQueryOfOneBaseInsideAFeatureReturnsIt() throws IOException {
        Assert.assertEquals(countQueried(indexedBedWithOneFeature(), 100, 100), 1);
    }

    @Test
    public void anEmptyQueryReturnsNothing() throws IOException {
        Assert.assertEquals(countQueried(indexedBedWithOneFeature(), 100, 99), 0);
    }

    @Test
    public void anInvertedQueryReturnsNothing() throws IOException {
        Assert.assertEquals(countQueried(indexedBedWithOneFeature(), 100, 98), 0);
    }

    @Test
    public void testClosingTheReaderClosesTheStreamOfAnIteratorLeftOpen() throws IOException {
        final RecordingChannelWrapper channels = new RecordingChannelWrapper();
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path vcf = fs.getPath("/variants.vcf");
            Files.writeString(
                    vcf,
                    "##fileformat=VCFv4.2\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n"
                            + "chr1\t1\t.\tA\tC\t.\t.\t.\nchr1\t2\t.\tA\tC\t.\t.\t.\n");
            final TribbleIndexedFeatureReader<VariantContext, LineIterator> featureReader =
                    new TribbleIndexedFeatureReader<>(
                            vcf.toUri().toString(), null, new VCFCodec(), false, channels, null);
            Assert.assertEquals(featureReader.iterator().stream().count(), 2);
            featureReader.close();

            // checked before the file system is closed, as that closes every channel open on it
            Assert.assertEquals(channels.openedCount(), 2);
            Assert.assertEquals(channels.stillOpenCount(), 0);
        }
    }

    @Test
    public void testTheStreamOfAnIteratorReadToItsEndIsClosedBeforeTheReaderIs() throws IOException {
        final RecordingChannelWrapper channels = new RecordingChannelWrapper();
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
                TribbleIndexedFeatureReader<BEDFeature, LineIterator> featureReader = new TribbleIndexedFeatureReader<>(
                        writeIndexedBed(fs).toUri().toString(), null, new BEDCodec(), false, channels, null)) {
            final long openWithoutIterators = channels.stillOpenCount();
            Assert.assertEquals(featureReader.iterator().stream().count(), 3);

            Assert.assertEquals(channels.stillOpenCount(), openWithoutIterators);
        }
    }

    @Test
    public void testClosingTheReaderClosesAQueryIteratorLeftOpen() throws IOException {
        final CloseCountingBEDCodec codec = new CloseCountingBEDCodec(false);
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path bed = writeIndexedBed(fs);
            final TribbleIndexedFeatureReader<BEDFeature, LineIterator> featureReader =
                    new TribbleIndexedFeatureReader<>(bed.toUri().toString(), indexOf(bed), codec, true, null, null);
            Assert.assertTrue(featureReader.query("chr1", 100, 400).hasNext());
            featureReader.close();
        }
        Assert.assertEquals(codec.closes, 1);
    }

    @Test
    public void testClosingTheReaderClosesEveryIteratorAndItsStreamEvenIfAnIteratorFailsToClose() throws IOException {
        final RecordingChannelWrapper channels = new RecordingChannelWrapper();
        final CloseCountingBEDCodec codec = new CloseCountingBEDCodec(true);
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path bed = writeIndexedBed(fs);
            final TribbleIndexedFeatureReader<BEDFeature, LineIterator> featureReader =
                    new TribbleIndexedFeatureReader<>(
                            bed.toUri().toString(), indexOf(bed), codec, true, channels, null);
            Assert.assertTrue(featureReader.iterator().hasNext());
            Assert.assertTrue(featureReader.iterator().hasNext());
            Assert.assertTrue(featureReader.query("chr1", 100, 400).hasNext());

            final IllegalStateException thrown = Assert.expectThrows(IllegalStateException.class, featureReader::close);
            Assert.assertEquals(thrown.getSuppressed().length, 2);
            Assert.assertEquals(codec.closes, 3);
            // checked before the file system is closed, as that closes every channel open on it
            Assert.assertEquals(channels.stillOpenCount(), 0);
        }
    }

    /** Writes three overlapping BED records to {@code fs}, with a tribble index beside them. */
    private static Path writeIndexedBed(final FileSystem fs) throws IOException {
        final Path bed = fs.getPath("/features.bed");
        Files.writeString(bed, "chr1\t100\t200\nchr1\t150\t250\nchr1\t300\t400\n");
        IndexFactory.createLinearIndex(bed, new BEDCodec()).write(Tribble.indexPath(bed));
        return bed;
    }

    private static String indexOf(final Path feature) {
        return Tribble.indexPath(feature).toUri().toString();
    }

    /** A BED codec that counts the sources it is asked to close, and can be made to fail after closing each. */
    private static final class CloseCountingBEDCodec extends BEDCodec {
        private final boolean failToClose;
        private int closes = 0;

        private CloseCountingBEDCodec(final boolean failToClose) {
            this.failToClose = failToClose;
        }

        @Override
        public void close(final LineIterator lineIterator) {
            closes++;
            super.close(lineIterator);
            if (failToClose) {
                throw new IllegalStateException("injected failure to close");
            }
        }
    }
}
