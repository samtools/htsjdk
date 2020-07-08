package htsjdk.tribble.gff;

import com.google.common.collect.ImmutableMap;
import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.TestUtils;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.readers.LineIterator;
import org.testng.Assert;
import org.testng.TestException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

public class Gff3WriterTest extends HtsjdkTest {
    private final static String DATA_DIR = TestUtils.DATA_DIR + "/gff/";
    private final Path ensembl_human_small = Paths.get(DATA_DIR + "Homo_sapiens.GRCh38.97.chromosome.1.small.gff3");
    private final Path gencode_mouse_small = Paths.get(DATA_DIR + "gencode.vM22.annotation.small.gff3");
    private final Path ncbi_woodpecker_small = Paths.get(DATA_DIR + "ref_ASM69900v1_top_level.small.gff3");
    private final Path feature_extends_past_circular_region = Paths.get(DATA_DIR + "feature_extends_past_circular_region.gff3");
    private final Path with_fasta = Paths.get(DATA_DIR + "fasta_test.gff3");
    private final Path with_fasta_artemis = Paths.get(DATA_DIR + "fasta_test_artemis.gff3");
    private final Path ordered_cofeature = Paths.get(DATA_DIR, "ordered_cofeatures.gff3");
    private final Path child_before_parent = Paths.get(DATA_DIR, "child_before_parent.gff3");
    private final Path url_encoding = Paths.get(DATA_DIR, "url_encoding.gff3");
    private final static Path[] tmpDir = new Path[] {IOUtil.getDefaultTmpDirPath()};
    private final static String version3Directive = "##gff-version 3.1.25\n";

    @DataProvider(name = "roundTripDataProvider")
    public Object[][] roundTripDataProvider() {
        return new Object[][] {
                {ensembl_human_small}, {gencode_mouse_small}, {ncbi_woodpecker_small}, {feature_extends_past_circular_region}, {with_fasta}, {with_fasta_artemis},
                {ordered_cofeature}, {child_before_parent}, {url_encoding}
        };
    }

    @Test(dataProvider = "roundTripDataProvider")
    public void testRoundTrip(final Path path) {

        final List<String> comments1 = new ArrayList<>();
        final HashSet<SequenceRegion> regions1 = new HashSet<>();
        final LinkedHashSet<Gff3Feature> features1 = readFromFile(path, comments1, regions1);

            //write out to temp files (one gzipped, on not)
        try {
            final Path tempFile = IOUtil.newTempPath("gff3Writer", ".gff3", tmpDir);
            final Path tempFileGzip = IOUtil.newTempPath("gff3Writer", ".gff3.gz", tmpDir);

            writeToFile(tempFile, comments1, regions1, features1);
            writeToFile(tempFileGzip, comments1, regions1, features1);

            //read temp files back in

            Assert.assertTrue(isGZipped(tempFileGzip.toFile()));
            final List<String> comments2 = new ArrayList<>();
            final HashSet<SequenceRegion> regions2 = new HashSet<>();
            final LinkedHashSet<Gff3Feature> features2 = readFromFile(tempFile, comments2, regions2);


            final List<String> comments3 = new ArrayList<>();
            final HashSet<SequenceRegion> regions3 = new HashSet<>();
            final LinkedHashSet<Gff3Feature> features3 = readFromFile(path, comments3, regions3);

            Assert.assertEquals(features1, features2);
            Assert.assertEquals(features1, features3);

            Assert.assertEquals(comments1, comments2);
            Assert.assertEquals(comments1, comments3);
            Assert.assertEquals(regions1, regions2);
            Assert.assertEquals(regions1, regions3);
        } catch (final IOException ex) {
            throw new TribbleException("Error creating temp files", ex);
        }
    }

    private void writeToFile(final Path path, final List<String> comments, final Set<SequenceRegion> regions, final Set<Gff3Feature> features) {
        try (final Gff3Writer writer = new Gff3Writer(path)) {
            for (final String comment : comments) {
                writer.addComment(comment);
            }

            for (final SequenceRegion region : regions) {
                writer.addDirective(Gff3Codec.Gff3Directive.SEQUENCE_REGION_DIRECTIVE, region);
            }

            for (final Gff3Feature feature : features) {
                writer.addFeature(feature);
            }
        } catch (final IOException ex) {
            throw new TribbleException("Error writing to file " + path, ex);
        }
    }

    private LinkedHashSet<Gff3Feature> readFromFile(final Path path, List<String> commentsStore, Set<SequenceRegion> regionsStore) {
        final Gff3Codec codec = new Gff3Codec();
        final LinkedHashSet<Gff3Feature> features = new LinkedHashSet<>();
        try (final AbstractFeatureReader<Gff3Feature, LineIterator> reader = AbstractFeatureReader.getFeatureReader(path.toAbsolutePath().toString(), null, codec, false)) {
            for (final Gff3Feature feature : reader.iterator()) {
                features.add(feature);
            }

            commentsStore.addAll(codec.getCommentTexts());
            regionsStore.addAll(codec.getSequenceRegions());
        } catch (final IOException ex) {
            throw new TribbleException("Error reading gff3 file " + path);
        }

        return features;
    }

    @DataProvider(name = "writeKeyValuePairDataProvider")
    public Object[][] writeKeyValuePairDataProvider() {
        return new Object[][] {
                {"key",Arrays.asList("value1", "value2", "value3"), "key=value1,value2,value3"},
                {"key",Arrays.asList("value1", "value ; with = special & encoded , characters", "value3"), "key=value1,value %3B with %3D special %26 encoded %2C characters,value3"}
        };
    }

    @Test(dataProvider = "writeKeyValuePairDataProvider")
    public void testWriteKeyValuePair(final String key, final List<String> values, final String expectedOutput) {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try(final Gff3Writer writer = new Gff3Writer(outputStream)) {
            writer.writeKeyValuePair(key, values);
        } catch (final IOException ex) {
            throw new TestException("Error writing key value pair", ex);
        }

        final byte[] expectedBytes = (version3Directive + expectedOutput).getBytes();

        Assert.assertEquals(outputStream.toByteArray(), expectedBytes);
    }

    @DataProvider(name = "writeAttributesDataProvider")
    public Object[][] writeAttributesDataProvider() {
        return new Object[][] {
                {ImmutableMap.of("key1", Arrays.asList("value1", "value2"), "key2", Collections.singletonList("another value"), "key3", Arrays.asList("thisValue")),
                "key1=value1,value2;key2=another value;key3=thisValue"},
                {ImmutableMap.of("singleKey", Arrays.asList("multipleValue1", "multipleValue2")), "singleKey=multipleValue1,multipleValue2"},
                {ImmutableMap.of("singleKey", Collections.singletonList("singleValue")), "singleKey=singleValue"},
                {Collections.emptyMap(), "."}
        };
    }

    @Test(dataProvider = "writeAttributesDataProvider")
    public void testWriteAttributes(final Map<String, List<String>> attributes, final String expectedOutput) {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try(final Gff3Writer writer = new Gff3Writer(outputStream)) {
            writer.writeAttributes(attributes);
        } catch (final IOException ex) {
            throw new TestException("Error writing key value pair", ex);
        }

        final byte[] expectedBytes = (version3Directive + expectedOutput).getBytes();

        Assert.assertEquals(outputStream.toByteArray(), expectedBytes);
    }

    @DataProvider(name = "encodeStringDataProvider")
    public Object[][] encodeStringDataProvider() {
        return new Object[][] {
                {"%", "%25"},
                {";", "%3B"},
                {"=", "%3D"},
                {"&", "%26"},
                {",", "%2C"},
                {" ", " "},
                {"qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM ", "qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM "} //these should remain unchanged
        };
    }

    @Test(dataProvider = "encodeStringDataProvider")
    public void testEncodeString(final String decoded, final String expectedEncoded) {
        final String encoded = Gff3Writer.encodeString(decoded);

        Assert.assertEquals(encoded, expectedEncoded);
    }

    private static boolean isGZipped(final File f) {
        int magic = 0;
        try {
            RandomAccessFile raf = new RandomAccessFile(f, "r");
            magic = raf.read() & 0xff | ((raf.read() << 8) & 0xff00);
            raf.close();
        } catch (Throwable e) {
            e.printStackTrace(System.err);
        }
        return magic == GZIPInputStream.GZIP_MAGIC;
    }
}