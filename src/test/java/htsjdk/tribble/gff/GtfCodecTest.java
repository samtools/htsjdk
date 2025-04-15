package htsjdk.tribble.gff;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.TestUtils;
import htsjdk.tribble.annotation.Strand;
import htsjdk.tribble.readers.LineIterator;


public class GtfCodecTest extends HtsjdkTest {

    final static String DATA_DIR = TestUtils.DATA_DIR + "/gff/";
    private final Path gencode47_gzipped = Paths.get(DATA_DIR + "gencode.v47.annotation.gtf.gz");
    private final Path gencode47_PCSK9 = Paths.get(DATA_DIR + "gencode.v47.PCSK9.gtf");

    @DataProvider(name = "basicDecodeDataProvider")
    Object[][] basicDecodeDataProvider() {
        return new Object[][] {{gencode47_gzipped, 842}, {gencode47_PCSK9, 248}};
    }

    private void basicDecodeTest(final Path inputGtf, GtfCodec.DecodeDepth decodeDepth,
            final int expectedCount) throws IOException {
        final GtfCodec codec = new GtfCodec(decodeDepth);
        Assert.assertTrue(codec.canDecode(inputGtf.toAbsolutePath().toString()));

        try (final AbstractFeatureReader<Gff3Feature, LineIterator> reader = AbstractFeatureReader
                .getFeatureReader(inputGtf.toAbsolutePath().toString(), null, codec, false)) {
            int countTotalFeatures = 0;
            for (final Gff3Feature feature : reader.iterator()) {
                countTotalFeatures++;
            }
            Assert.assertEquals(countTotalFeatures, expectedCount);
        }
    }



    @Test(dataProvider = "basicDecodeDataProvider")
    public void basicDecodeDeepTest(final Path inputGtf, final int expectedCount)
            throws IOException {
        basicDecodeTest(inputGtf, GtfCodec.DecodeDepth.DEEP, expectedCount);
    }

    @Test(dataProvider = "basicDecodeDataProvider")
    public void basicDecodeShallowTest(final Path inputGtf, final int expectedCount)
            throws IOException {
        basicDecodeTest(inputGtf, GtfCodec.DecodeDepth.SHALLOW, expectedCount);
    }

    @Test(dataProvider = "basicDecodeDataProvider")
    public void codecFilterOutFieldsTest(final Path inputGtf, int expectedTotalFeatures)
            throws IOException {
        final Set<String> skip_attributes = new HashSet<>(Arrays.asList("tag", "havana_gene"));
        final GtfCodec codec =
                new GtfCodec(GtfCodec.DecodeDepth.SHALLOW, S -> skip_attributes.contains(S));
        Assert.assertTrue(codec.canDecode(inputGtf.toAbsolutePath().toString()));
        AbstractFeatureReader<Gff3Feature, LineIterator> reader = AbstractFeatureReader
                .getFeatureReader(inputGtf.toAbsolutePath().toString(), null, codec, false);
        int countTotalFeatures = 0;
        for (final Gff3Feature feature : reader.iterator()) {
            for (final String key : skip_attributes) {
                Assert.assertTrue(feature.getAttribute(key).isEmpty());
                Assert.assertFalse(feature.hasAttribute(key));
                Assert.assertFalse(feature.getUniqueAttribute(key).isPresent());
            }
            countTotalFeatures++;
        }
        Assert.assertEquals(countTotalFeatures, expectedTotalFeatures);
    }

    @Test
    public void FeatureContentTest() throws IOException {
        final GtfCodec codec = new GtfCodec(GtfCodec.DecodeDepth.SHALLOW);
        try (final AbstractFeatureReader<Gff3Feature, LineIterator> reader =
                AbstractFeatureReader.getFeatureReader(gencode47_PCSK9.toAbsolutePath().toString(),
                        null, codec, false)) {
            try (CloseableIterator<Gff3Feature> iter = reader.iterator()) {
                Assert.assertTrue(iter.hasNext());
                Gff3Feature feature = iter.next();
                Assert.assertNotNull(feature);
                Assert.assertEquals(feature.getContig(), "chr1");
                Assert.assertEquals(feature.getSource(), "HAVANA");
                Assert.assertEquals(feature.getType(), "UTR");
                Assert.assertEquals(feature.getStart(), 55039445);
                Assert.assertEquals(feature.getEnd(), 55039837);
                Assert.assertEquals(feature.getScore(), -1);
                Assert.assertEquals(feature.getStrand(), Strand.POSITIVE);
                Assert.assertEquals(feature.getPhase(), -1);
                Assert.assertEquals(feature.getUniqueAttribute(GtfConstants.GENE_ID).get(),
                        "ENSG00000169174.13");
                Assert.assertEquals(feature.getUniqueAttribute(GtfConstants.TRANSCRIPT_ID).get(),
                        "ENST00000713785.1");
                Assert.assertEquals(feature.getUniqueAttribute("gene_type").get(),
                        "protein_coding");
                Assert.assertEquals(feature.getUniqueAttribute("gene_name").get(), "PCSK9");
                Assert.assertEquals(feature.getUniqueAttribute("transcript_type").get(),
                        "nonsense_mediated_decay");
                Assert.assertEquals(feature.getUniqueAttribute("transcript_name").get(),
                        "PCSK9-208");
                Assert.assertEquals(feature.getUniqueAttribute("exon_number").get(), "1");
                Assert.assertEquals(feature.getUniqueAttribute("exon_id").get(),
                        "ENSE00004011055.2");
                Assert.assertEquals(feature.getUniqueAttribute("level").get(), "2");
                Assert.assertEquals(feature.getUniqueAttribute("protein_id").get(),
                        "ENSP00000519087.1");
                Assert.assertEquals(feature.getUniqueAttribute("hgnc_id").get(), "HGNC:20001");
                Assert.assertEquals(feature.getUniqueAttribute("havana_gene").get(),
                        "OTTHUMG00000008136.2");
                Assert.assertFalse(feature.getUniqueAttribute("zz").isPresent());
                Assert.assertTrue(iter.hasNext());
            }
        }
    }

    @Test(dataProvider = "basicDecodeDataProvider")
    private void deepTreeTest(final Path path, int expectNumber) throws IOException {
        final GtfCodec codec = new GtfCodec(GtfCodec.DecodeDepth.DEEP);
        try (final AbstractFeatureReader<Gff3Feature, LineIterator> reader = AbstractFeatureReader
                .getFeatureReader(path.toAbsolutePath().toString(), null, codec, false)) {
            try (CloseableIterator<Gff3Feature> iter = reader.iterator()) {
                Assert.assertTrue(iter.hasNext());
                while (iter.hasNext()) {
                    Gff3Feature f1 = iter.next();
                    if (!f1.getType().equals("gene"))
                        continue;
                    Assert.assertEquals(f1.getType(), "gene");
                    Assert.assertTrue(f1.hasAttribute(GtfConstants.GENE_ID));
                    Assert.assertTrue(f1.hasAttribute("gene_name"));
                    Assert.assertTrue(f1.hasChildren());
                    for (Gff3Feature f2 : f1.getChildren()) {
                        Assert.assertEquals(f2.getType(), "transcript");
                        Assert.assertTrue(f2.hasAttribute(GtfConstants.GENE_ID));
                        Assert.assertTrue(f2.hasAttribute(GtfConstants.TRANSCRIPT_ID));

                        Assert.assertTrue(f2.hasAttribute("gene_name"));
                        Assert.assertEquals(f1.getUniqueAttribute("gene_name").get(),
                                f2.getUniqueAttribute("gene_name").get());
                        Assert.assertEquals(f1.getUniqueAttribute(GtfConstants.GENE_ID).get(),
                                f2.getUniqueAttribute(GtfConstants.GENE_ID).get());
                        Assert.assertTrue(f2.hasChildren());

                        for (Gff3Feature f3 : f2.getChildren()) {
                            Assert.assertNotEquals(f3.getType(), "gene");
                            Assert.assertNotEquals(f3.getType(), "transcript");
                            Assert.assertTrue(f3.hasAttribute(GtfConstants.GENE_ID));
                            Assert.assertTrue(f3.hasAttribute(GtfConstants.TRANSCRIPT_ID));
                            Assert.assertEquals(f2.getUniqueAttribute(GtfConstants.GENE_ID).get(),
                                    f3.getUniqueAttribute(GtfConstants.GENE_ID).get());
                            Assert.assertEquals(
                                    f2.getUniqueAttribute(GtfConstants.TRANSCRIPT_ID).get(),
                                    f3.getUniqueAttribute(GtfConstants.TRANSCRIPT_ID).get());
                            Assert.assertFalse(f3.hasChildren());
                        }
                    }

                }
            }
        }
    }

    @Test
    public void decodeFeaturesTest() throws IOException {
        String s = GtfConstants.UNDEFINED_FIELD_VALUE;
        Map<String, List<String>> h = GtfCodec.parseAttributes(s);
        Assert.assertTrue(h.isEmpty());

        h = GtfCodec.parseAttributes("key1 \"ABCD\"; key2 12345 ; key3 'hello'");
        for (String k : h.keySet()) {
            Assert.assertEquals(h.get(k).size(), 1);
        }
        Assert.assertEquals(h.get("key1").get(0), "ABCD");
        Assert.assertEquals(h.get("key2").get(0), "12345");
        Assert.assertEquals(h.get("key3").get(0), "hello");

        h = GtfCodec.parseAttributes("key1 \"A\"; key1 B ; key1 C");
        Assert.assertEquals(h.size(), 1);
        Assert.assertEquals(h.get("key1").size(), 3);
        Assert.assertEquals(h.get("key1"), Arrays.asList("A", "B", "C"));

        h = GtfCodec.parseAttributes("key1 \"A\\tB\"");
        Assert.assertEquals(h.get("key1").get(0), "A\tB");
    }
}
