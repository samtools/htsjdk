package htsjdk.tribble.gff;

import htsjdk.HtsjdkTest;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.TestUtils;
import htsjdk.tribble.Tribble;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.readers.LineIterator;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class Gff3WriterTest extends HtsjdkTest {
    final static String DATA_DIR = TestUtils.DATA_DIR + "/gff/";
    private final Path ensembl_human_small = Paths.get(DATA_DIR + "Homo_sapiens.GRCh38.97.chromosome.1.small.gff3");
    private final Path gencode_mouse_small = Paths.get(DATA_DIR + "gencode.vM22.annotation.small.gff3");
    private final Path ncbi_woodpecker_small = Paths.get(DATA_DIR + "ref_ASM69900v1_top_level.small.gff3");
    private final Path feature_extends_past_circular_region = Paths.get(DATA_DIR + "feature_extends_past_circular_region.gff3");
    private final Path with_fasta = Paths.get(DATA_DIR + "fasta_test.gff3");
    private final Path with_fasta_artemis = Paths.get(DATA_DIR + "fasta_test_artemis.gff3");
    private final Path ordered_cofeature = Paths.get(DATA_DIR, "ordered_cofeatures.gff3");
    private final Path child_before_parent = Paths.get(DATA_DIR, "child_before_parent.gff3");

    @DataProvider(name = "roundTripDataProvider")
    public Object[][] roundTripDataProvider() {
        return new Object[][] {
                {ensembl_human_small , 5}, {gencode_mouse_small, 7}, {ncbi_woodpecker_small, 7}, {feature_extends_past_circular_region, 1}, {with_fasta, 2}, {with_fasta_artemis, 2},
                {ordered_cofeature, 3}, {child_before_parent, 1}
        };
    }

    @Test(dataProvider = "roundTripDataProvider")
    public void testRoundTrip(final Path path, final int expectedCompression) {

        final HashSet<String> comments1 = new HashSet<>();
        final HashSet<SequenceRegion> regions1 = new HashSet<>();
        final LinkedHashSet<Gff3Feature> features1 = readFromFile(path, comments1, regions1);
        
            //write out to temp files (one gzipped, on not)
        try {
            final Path tempFile = Files.createTempFile("gff3Writer", ".gff3");
            final Path tempFileGzip = Files.createTempFile("gff3Writer", ".gff3.gz");

            try (final Gff3Writer writer = new Gff3Writer(tempFile); final Gff3Writer writerGzip = new Gff3Writer(tempFileGzip)) {
                for (final String comment : comments1) {
                    writer.addComment(comment);
                    writerGzip.addComment(comment);
                }

                for (final SequenceRegion region : regions1) {
                    writer.addSequenceRegionDirective(region);
                    writerGzip.addSequenceRegionDirective(region);
                }

                for (final Gff3Feature feature : features1) {
                    writer.addFeature(feature);
                    writerGzip.addFeature(feature);
                }
            } catch (final IOException ex) {
                throw new TribbleException("Error writing gff3 files", ex);
            }

            //read temp files back in


            final long unzippedSize = tempFile.toFile().length();
            final long zippedSize = tempFileGzip.toFile().length();

            Assert.assertTrue(unzippedSize > zippedSize * expectedCompression);
            final HashSet<String> comments2 = new HashSet<>();
            final HashSet<SequenceRegion> regions2 = new HashSet<>();
            final LinkedHashSet<Gff3Feature> features2 = readFromFile(tempFile, comments2, regions2);


            final HashSet<String> comments3 = new HashSet<>();
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

    private LinkedHashSet<Gff3Feature> readFromFile(final Path path, Set<String> commentsStore, Set<SequenceRegion> regionsStore) {
        final Gff3Codec codec = new Gff3Codec();
        final LinkedHashSet<Gff3Feature> features = new LinkedHashSet<>();
        try (final AbstractFeatureReader<Gff3Feature, LineIterator> reader = AbstractFeatureReader.getFeatureReader(path.toAbsolutePath().toString(), null, codec, false)) {
            for (final Gff3Feature feature : reader.iterator()) {
                features.add(feature);
            }

            commentsStore.addAll(codec.getComments());
            regionsStore.addAll(codec.getSequenceRegions());
        } catch (final IOException ex) {
            throw new TribbleException("Error reading gff3 file " + path);
        }

        return features;
    }

}