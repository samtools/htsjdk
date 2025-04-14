package htsjdk.tribble.gff;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.TestUtils;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.readers.LineIterator;

public class GtfWriterTest extends HtsjdkTest {
    private final static String DATA_DIR = TestUtils.DATA_DIR + "/gff/";
    private final Path gencode47_gzipped = Paths.get(DATA_DIR + "gencode.v47.annotation.gtf.gz");
    private final Path gencode47_PCSK9 = Paths.get(DATA_DIR + "gencode.v47.PCSK9.gtf");
    private final static Path[] tmpDir = new Path[] {IOUtil.getDefaultTmpDirPath()};

    @DataProvider(name = "roundTripDataProvider")
    public Object[][] roundTripDataProvider() {
        return new Object[][] {
                {gencode47_gzipped},
                {gencode47_PCSK9}
        };
    }
    
    @Test(dataProvider = "roundTripDataProvider")
    public void testRoundTrip(final Path path) {

        final List<String> comments1 = new ArrayList<>();
        final LinkedHashSet<Gff3Feature> features1 = readFromFile(path, comments1);

            //write out to temp files (one gzipped, on not)
        try {
        	int n;
        	 IOUtil.newTempPath("gtfWriter", ".gtf", tmpDir);// REMOVE ME
            final Path tempFile = Paths.get(path.toString()+".tmp.gtf");// IOUtil.newTempPath("gtfWriter", ".gtf", tmpDir);
            final Path tempFileGzip = Paths.get(path.toString()+".tmp.gtf.gz");//IOUtil.newTempPath("gtfWriter", ".gtf.gz", tmpDir);

            n = writeToFile(tempFile, comments1, features1);
            Assert.assertEquals(n, features1.size());
            n = writeToFile(tempFileGzip, comments1, features1);
            Assert.assertEquals(n, features1.size());

            //read temp files back in

            Assert.assertTrue(isGZipped(tempFileGzip.toFile()));
            final List<String> comments2 = new ArrayList<>();
            final LinkedHashSet<Gff3Feature> features2 = readFromFile(tempFile, comments2);
            Assert.assertEquals(features1.size(), features2.size());


            final List<String> comments3 = new ArrayList<>();
            final LinkedHashSet<Gff3Feature> features3 = readFromFile(path, comments3);
            Assert.assertEquals(features1.size(), features3.size());

            Assert.assertEquals(features1, features2);
            Assert.assertEquals(features1, features3);

            Assert.assertEquals(comments1, comments2);
            Assert.assertEquals(comments1, comments3);
        } catch (final IOException ex) {
            throw new TribbleException("Error creating temp files", ex);
        }
    }

    private int writeToFile(final Path path, final List<String> comments, final Set<Gff3Feature> features) {
    	int n = 0;
        try (final AbstractGxxWriter writer =AbstractGxxWriter.openWithFileExtension(path)) {
        	Assert.assertTrue(writer instanceof GtfWriter);
            for (final String comment : comments) {
                writer.addComment(comment);
            }

            for (final Gff3Feature feature : features) {
                writer.addFeature(feature);
                n++;
            }
            return n;
        } catch (final IOException ex) {
            throw new TribbleException("Error writing to file " + path, ex);
        }
    }

    private LinkedHashSet<Gff3Feature> readFromFile(final Path path, List<String> commentsStore) {
        final GtfCodec codec = new GtfCodec();
        final LinkedHashSet<Gff3Feature> features = new LinkedHashSet<>();
        try (final AbstractFeatureReader<Gff3Feature, LineIterator> reader = AbstractFeatureReader.getFeatureReader(
        		path.toAbsolutePath().toString(), null, codec, false)) {
            for (final Gff3Feature feature : reader.iterator()) {
                features.add(feature);
            }

            commentsStore.addAll(codec.getCommentTexts());
        } catch (final IOException ex) {
            throw new TribbleException("Error reading gtf file " + path);
        }
	System.err.println("###ICIB "+features.size());
        return features;
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
        final String encoded = GtfWriter.encodeString(decoded);
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
