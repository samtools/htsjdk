package htsjdk.tribble.gff;

import com.google.common.collect.ImmutableMap;
import htsjdk.HtsjdkTest;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.TestUtils;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.annotation.Strand;
import htsjdk.tribble.readers.LineIterator;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


public class Gff3CodecTest extends HtsjdkTest {

    final static String DATA_DIR = TestUtils.DATA_DIR + "/gff/";
    private final Path ensembl_human_small = Paths.get(DATA_DIR + "Homo_sapiens.GRCh38.97.chromosome.1.small.gff3");
    private final Path gencode_mouse_small = Paths.get(DATA_DIR + "gencode.vM22.annotation.small.gff3");
    private final Path ncbi_woodpecker_small = Paths.get(DATA_DIR + "ref_ASM69900v1_top_level.small.gff3");
    private final Path feature_extends_past_region = Paths.get(DATA_DIR + "feature_extends_past_region.gff3");
    private final Path feature_extends_past_circular_region = Paths.get(DATA_DIR + "feature_extends_past_circular_region.gff3");
    private final Path feature_outside_region = Paths.get(DATA_DIR + "feature_outside_region.gff3");
    private final Path with_fasta = Paths.get(DATA_DIR + "fasta_test.gff3");
    private final Path with_fasta_artemis = Paths.get(DATA_DIR + "fasta_test_artemis.gff3");
    private final Path ordered_cofeature = Paths.get(DATA_DIR, "ordered_cofeatures.gff3");
    private final Path child_before_parent = Paths.get(DATA_DIR, "child_before_parent.gff3");

    private final Path ensembl_human_small_gzipped = Paths.get(DATA_DIR + "Homo_sapiens.GRCh38.97.chromosome.1.small.gff3.gz");
    private final Path gencode_mouse_small_gzipped = Paths.get(DATA_DIR + "gencode.vM22.annotation.small.gff3.gz");
    private final Path ncbi_woodpecker_small_gzipped = Paths.get(DATA_DIR + "ref_ASM69900v1_top_level.small.gff3.gz");
    private final Path with_fasta_gzipped = Paths.get(DATA_DIR + "fasta_test.gff3.gz");
    private final Path with_fasta_artemis_gzipped = Paths.get(DATA_DIR + "fasta_test_artemis.gff3.gz");


    @DataProvider(name = "basicDecodeDataProvider")
    Object[][] basicDecodeDataProvider() {
        return new Object[][]{
                {ensembl_human_small, 82},
                {gencode_mouse_small, 70},
                {ncbi_woodpecker_small, 185},
                {with_fasta, 12},
                {with_fasta_artemis, 12},
                {ordered_cofeature, 4},
                {child_before_parent, 2}
        };
    }

    @Test(dataProvider = "basicDecodeDataProvider")
    public void basicDecodeTest(final Path inputGff3, final int expectedTotalFeatures) throws IOException {
        Assert.assertTrue((new Gff3Codec()).canDecode(inputGff3.toAbsolutePath().toString()));
        final AbstractFeatureReader<Gff3Feature, LineIterator> reader = AbstractFeatureReader.getFeatureReader(inputGff3.toAbsolutePath().toString(), null, new Gff3Codec(), false);
        int countTotalFeatures = 0;
        for (final Gff3Feature feature : reader.iterator()) {
            countTotalFeatures++;
        }

        Assert.assertEquals(countTotalFeatures, expectedTotalFeatures);
    }

    @Test(dataProvider = "basicDecodeDataProvider")
    public void codecFilterOutFieldsTest(final Path inputGff3, final int expectedTotalFeatures) throws IOException {
        final Set<String> skip_attributes = new HashSet<>(Arrays.asList("version","rank","biotype","transcript_support_level","mgi_id","havana_gene","tag"));
        final Gff3Codec codec = new Gff3Codec(Gff3Codec.DecodeDepth.SHALLOW, S->skip_attributes.contains(S));
        Assert.assertTrue(codec.canDecode(inputGff3.toAbsolutePath().toString()));
        final AbstractFeatureReader<Gff3Feature, LineIterator> reader = AbstractFeatureReader.getFeatureReader(inputGff3.toAbsolutePath().toString(), null,codec, false);
        int countTotalFeatures = 0;
        for (final Gff3Feature feature : reader.iterator()) {
            for(final String key : skip_attributes) {
                Assert.assertTrue(feature.getAttribute(key).isEmpty());
                Assert.assertFalse(feature.hasAttribute(key));
                Assert.assertFalse(feature.getUniqueAttribute(key).isPresent());
            }
            countTotalFeatures++;
        }

        Assert.assertEquals(countTotalFeatures, expectedTotalFeatures);
    }

    
    
    @Test(dataProvider = "basicDecodeDataProvider")
    public void basicShallowDecodeTest(final Path inputGff3, final int expectedTotalFeatures) throws IOException {
        Assert.assertTrue((new Gff3Codec(Gff3Codec.DecodeDepth.SHALLOW)).canDecode(inputGff3.toAbsolutePath().toString()));
        final AbstractFeatureReader<Gff3Feature, LineIterator> reader = AbstractFeatureReader.getFeatureReader(inputGff3.toAbsolutePath().toString(), null, new Gff3Codec(Gff3Codec.DecodeDepth.SHALLOW), false);
        int countTotalFeatures = 0;
        for (final Gff3Feature feature : reader.iterator()) {
            countTotalFeatures++;
            //shouldn't have any children, parents, or cofeatures
            Assert.assertEquals(feature.getAncestors().size(), 0);
            Assert.assertEquals(feature.getDescendents().size(), 0);
            Assert.assertEquals(feature.getCoFeatures().size(), 0);
        }

        Assert.assertEquals(countTotalFeatures, expectedTotalFeatures);


    }

    @DataProvider(name = "testGZippedDataProvider")
    Object[][] testGZippedDataProvider(){
        return new Object[][] {
                {ensembl_human_small, ensembl_human_small_gzipped},
                {gencode_mouse_small, gencode_mouse_small_gzipped},
                {ncbi_woodpecker_small, ncbi_woodpecker_small_gzipped},
                {with_fasta, with_fasta_gzipped},
                {with_fasta_artemis, with_fasta_artemis_gzipped}
        };
    }

    @Test(dataProvider = "testGZippedDataProvider")
    public void testGZipped(final Path inputGff3, final Path inputGff3GZipped) throws IOException {
        Assert.assertTrue((new Gff3Codec()).canDecode(inputGff3.toAbsolutePath().toString()));
        Assert.assertTrue((new Gff3Codec()).canDecode(inputGff3GZipped.toAbsolutePath().toString()));

        final AbstractFeatureReader<Gff3Feature, LineIterator> reader = AbstractFeatureReader.getFeatureReader(inputGff3.toAbsolutePath().toString(), null, new Gff3Codec(), false);
        final AbstractFeatureReader<Gff3Feature, LineIterator> readerGZipped = AbstractFeatureReader.getFeatureReader(inputGff3GZipped.toAbsolutePath().toString(), null, new Gff3Codec(), false);

        final Set<Gff3Feature> topLevelFeatures = new HashSet<>();
        final Set<Gff3Feature> topLevelFeaturesGZipped = new HashSet<>();

        for (final Gff3Feature feature : reader.iterator()) {
            topLevelFeatures.add(feature);
        }

        for (final Gff3Feature feature : readerGZipped.iterator()) {
            topLevelFeaturesGZipped.add(feature);
        }

        Assert.assertEquals(topLevelFeatures, topLevelFeaturesGZipped);
    }

    @Test(dataProvider = "testGZippedDataProvider")
    public void testGZippedShallow(final Path inputGff3, final Path inputGff3GZipped) throws IOException {
        Assert.assertTrue((new Gff3Codec()).canDecode(inputGff3.toAbsolutePath().toString()));
        Assert.assertTrue((new Gff3Codec()).canDecode(inputGff3GZipped.toAbsolutePath().toString()));

        final AbstractFeatureReader<Gff3Feature, LineIterator> reader = AbstractFeatureReader.getFeatureReader(inputGff3.toAbsolutePath().toString(), null, new Gff3Codec(Gff3Codec.DecodeDepth.SHALLOW), false);
        final AbstractFeatureReader<Gff3Feature, LineIterator> readerGZipped = AbstractFeatureReader.getFeatureReader(inputGff3GZipped.toAbsolutePath().toString(), null, new Gff3Codec(Gff3Codec.DecodeDepth.SHALLOW), false);

        final Set<Gff3Feature> features = new HashSet<>();
        final Set<Gff3Feature> featuresGZipped = new HashSet<>();

        for (final Gff3Feature feature : reader.iterator()) {
            features.add(feature);
            Assert.assertEquals(feature.getAncestors().size(), 0);
            Assert.assertEquals(feature.getDescendents().size(), 0);
            Assert.assertEquals(feature.getCoFeatures().size(), 0);
        }

        for (final Gff3Feature feature : readerGZipped.iterator()) {
            featuresGZipped.add(feature);
            Assert.assertEquals(feature.getAncestors().size(), 0);
            Assert.assertEquals(feature.getDescendents().size(), 0);
            Assert.assertEquals(feature.getCoFeatures().size(), 0);
        }

        Assert.assertEquals(features, featuresGZipped);
    }

    @DataProvider(name = "sequenceRegionValidationDataProvider")
    Object[][] sequenceRegionValidationDataProvider() {
        return new Object[][] {
                {feature_extends_past_region, true},
                {feature_extends_past_circular_region, false},
                {feature_outside_region, true}
        };
    }

    @Test(dataProvider = "sequenceRegionValidationDataProvider")
    public void sequenceRegionValidationTest(final Path inputGff3, final boolean expectExpection) throws IOException {
        Assert.assertTrue((new Gff3Codec()).canDecode(inputGff3.toAbsolutePath().toString()));
        final AbstractFeatureReader<Gff3Feature, LineIterator> reader = AbstractFeatureReader.getFeatureReader(inputGff3.toAbsolutePath().toString(), null, new Gff3Codec(), false);
        if (expectExpection) {
            Assert.assertThrows(() -> reader.iterator().forEachRemaining(f -> {}));
        } else {
            reader.iterator().forEachRemaining(f -> {});
        }
    }

    @Test
    public void urlDecodingTest() throws IOException {
        final AbstractFeatureReader<Gff3Feature, LineIterator> reader = AbstractFeatureReader.getFeatureReader(DATA_DIR + "url_encoding.gff3", null, new Gff3Codec(), false);
        final List<Gff3Feature> features = reader.iterator().stream().collect(Collectors.toList());
        Assert.assertEquals(features.size(), 1);
        final Gff3Feature feature = features.get(0);

        Assert.assertEquals(feature.getContig(), "the contig");
        Assert.assertEquals(feature.getSource(), "a source & also a str*)%nge source");
        Assert.assertEquals(feature.getType(), "a region");
        Assert.assertEquals(feature.getID(), "this is the ID of this wacky feature^&%##$%*&>,. ,.");
        Assert.assertEquals(feature.getAttribute("Another key"), Arrays.asList("Another=value", "And a second, value"));
        Assert.assertTrue(feature.hasAttribute("Another key"));
        Assert.assertTrue(feature.hasAttribute(Gff3Constants.ID_ATTRIBUTE_KEY));
        Assert.assertTrue(feature.getUniqueAttribute(Gff3Constants.ID_ATTRIBUTE_KEY).isPresent());
        Assert.assertFalse(feature.getUniqueAttribute("missing").isPresent());
    }


    /*
    examples from https://github.com/The-Sequence-Ontology/Specifications/blob/31f62ad469b31769b43af42e0903448db1826925/gff3.md
     */


    @DataProvider(name = "examplesDataProvider")
    public Object[][] examplesDataProvider() {
        final ArrayList<Object[]> examples = new ArrayList<>();
        // some of these examples are from https://github.com/The-Sequence-Ontology/Specifications/blob/31f62ad469b31769b43af42e0903448db1826925/gff3.md

        //canonical gene
        final ArrayList<Object> canonicalGeneExample = new ArrayList<>(Collections.singletonList(DATA_DIR + "canonical_gene.gff3"));

        final Set<Gff3Feature> canonicalGeneFeatures = new HashSet<>();

        final Gff3FeatureImpl canonicalGene_gene00001 = new Gff3FeatureImpl("ctg123", ".", "gene", 1000, 9000, 1030d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene00001"), "Name", Collections.singletonList("EDEN")));
        canonicalGeneFeatures.add(canonicalGene_gene00001);

        final Gff3FeatureImpl canonicalGene_tfbs00001 = new Gff3FeatureImpl("ctg123", ".", "TF_binding_site", 1000, 1012, 0.999d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("tfbs00001"), "Parent", Collections.singletonList("gene00001")));
        canonicalGene_tfbs00001.addParent(canonicalGene_gene00001);
        canonicalGeneFeatures.add(canonicalGene_tfbs00001);

        final Gff3FeatureImpl canonicalGene_mRNA00001 = new Gff3FeatureImpl("ctg123", ".", "mRNA", 1050, 9000, 1.37d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("mRNA00001"), "Name", Collections.singletonList("EDEN.1"), "Parent", Collections.singletonList("gene00001")));
        canonicalGene_mRNA00001.addParent(canonicalGene_gene00001);
        canonicalGeneFeatures.add(canonicalGene_mRNA00001);

        final Gff3FeatureImpl canonicalGene_mRNA00002 = new Gff3FeatureImpl("ctg123", ".", "mRNA", 1050, 9000, -1d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("mRNA00002"), "Name", Collections.singletonList("EDEN.2"), "Parent", Collections.singletonList("gene00001")));
        canonicalGene_mRNA00002.addParent(canonicalGene_gene00001);
        canonicalGeneFeatures.add(canonicalGene_mRNA00002);

        final Gff3FeatureImpl canonicalGene_mRNA00003 = new Gff3FeatureImpl("ctg123", ".", "mRNA", 1300, 9000, -1d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("mRNA00003"), "Name", Collections.singletonList("EDEN.3"), "Parent", Collections.singletonList("gene00001")));
        canonicalGene_mRNA00003.addParent(canonicalGene_gene00001);
        canonicalGeneFeatures.add(canonicalGene_mRNA00003);

        final Gff3FeatureImpl canonicalGene_exon00001 = new Gff3FeatureImpl("ctg123", ".", "exon", 1300, 1500, -1d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("exon00001"), "Parent", Collections.singletonList("mRNA00003")));
        canonicalGene_exon00001.addParent(canonicalGene_mRNA00003);
        canonicalGeneFeatures.add(canonicalGene_exon00001);

        final Gff3FeatureImpl canonicalGene_exon00002 = new Gff3FeatureImpl("ctg123", ".", "exon", 1050, 1500, -1d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("exon00002"), "Parent", Arrays.asList("mRNA00001", "mRNA00002")));
        canonicalGene_exon00002.addParent(canonicalGene_mRNA00001);
        canonicalGene_exon00002.addParent(canonicalGene_mRNA00002);
        canonicalGeneFeatures.add(canonicalGene_exon00002);

        final Gff3FeatureImpl canonicalGene_exon00003 = new Gff3FeatureImpl("ctg123", ".", "exon", 3000, 3902, -1d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("exon00003"), "Parent", Arrays.asList("mRNA00001", "mRNA00003")));
        canonicalGene_exon00003.addParent(canonicalGene_mRNA00001);
        canonicalGene_exon00003.addParent(canonicalGene_mRNA00003);
        canonicalGeneFeatures.add(canonicalGene_exon00003);

        final Gff3FeatureImpl canonicalGene_exon00004 = new Gff3FeatureImpl("ctg123", ".", "exon", 5000, 5500, -1d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("exon00004"), "Parent", Arrays.asList("mRNA00001", "mRNA00002", "mRNA00003")));
        canonicalGene_exon00004.addParent(canonicalGene_mRNA00001);
        canonicalGene_exon00004.addParent(canonicalGene_mRNA00002);
        canonicalGene_exon00004.addParent(canonicalGene_mRNA00003);
        canonicalGeneFeatures.add(canonicalGene_exon00004);

        final Gff3FeatureImpl canonicalGene_exon00005 = new Gff3FeatureImpl("ctg123", ".", "exon", 7000, 9000, -1d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("exon00005"), "Parent", Arrays.asList("mRNA00001", "mRNA00002", "mRNA00003")));
        canonicalGene_exon00005.addParent(canonicalGene_mRNA00001);
        canonicalGene_exon00005.addParent(canonicalGene_mRNA00002);
        canonicalGene_exon00005.addParent(canonicalGene_mRNA00003);
        canonicalGeneFeatures.add(canonicalGene_exon00005);

        final Gff3FeatureImpl canonicalGene_cds00001_1 = new Gff3FeatureImpl("ctg123", ".", "CDS", 1201, 1500, -1d, Strand.POSITIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds00001"), "Parent", Collections.singletonList("mRNA00001"), "Name", Collections.singletonList("edenprotein.1")));
        canonicalGene_cds00001_1.addParent(canonicalGene_mRNA00001);
        canonicalGeneFeatures.add(canonicalGene_cds00001_1);

        final Gff3FeatureImpl canonicalGene_cds00001_2 = new Gff3FeatureImpl("ctg123", ".", "CDS", 3000, 3902, -1d, Strand.POSITIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds00001"), "Parent", Collections.singletonList("mRNA00001"), "Name", Collections.singletonList("edenprotein.1")));
        canonicalGene_cds00001_2.addParent(canonicalGene_mRNA00001);
        canonicalGene_cds00001_2.addCoFeature(canonicalGene_cds00001_1);
        canonicalGeneFeatures.add(canonicalGene_cds00001_2);

        final Gff3FeatureImpl canonicalGene_cds00001_3 = new Gff3FeatureImpl("ctg123", ".", "CDS", 5000, 5500, -1d, Strand.POSITIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds00001"), "Parent", Collections.singletonList("mRNA00001"), "Name", Collections.singletonList("edenprotein.1")));
        canonicalGene_cds00001_3.addParent(canonicalGene_mRNA00001);
        canonicalGene_cds00001_3.addCoFeature(canonicalGene_cds00001_1);
        canonicalGene_cds00001_3.addCoFeature(canonicalGene_cds00001_2);
        canonicalGeneFeatures.add(canonicalGene_cds00001_3);

        final Gff3FeatureImpl canonicalGene_cds00001_4 = new Gff3FeatureImpl("ctg123", ".", "CDS", 7000, 7600, -1d, Strand.POSITIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds00001"), "Parent", Collections.singletonList("mRNA00001"), "Name", Collections.singletonList("edenprotein.1")));
        canonicalGene_cds00001_4.addParent(canonicalGene_mRNA00001);
        canonicalGene_cds00001_4.addCoFeature(canonicalGene_cds00001_1);
        canonicalGene_cds00001_4.addCoFeature(canonicalGene_cds00001_2);
        canonicalGene_cds00001_4.addCoFeature(canonicalGene_cds00001_3);
        canonicalGeneFeatures.add(canonicalGene_cds00001_4);

        final Gff3FeatureImpl canonicalGene_cds00002_1 = new Gff3FeatureImpl("ctg123", ".", "CDS", 1201, 1500, -1d, Strand.POSITIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds00002"), "Parent", Collections.singletonList("mRNA00002"), "Name", Collections.singletonList("edenprotein.2")));
        canonicalGene_cds00002_1.addParent(canonicalGene_mRNA00002);
        canonicalGeneFeatures.add(canonicalGene_cds00002_1);

        final Gff3FeatureImpl canonicalGene_cds00002_2 = new Gff3FeatureImpl("ctg123", ".", "CDS", 5000, 5500, -1d, Strand.POSITIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds00002"), "Parent", Collections.singletonList("mRNA00002"), "Name", Collections.singletonList("edenprotein.2")));
        canonicalGene_cds00002_2.addParent(canonicalGene_mRNA00002);
        canonicalGene_cds00002_2.addCoFeature(canonicalGene_cds00002_1);
        canonicalGeneFeatures.add(canonicalGene_cds00002_2);

        final Gff3FeatureImpl canonicalGene_cds00002_3 = new Gff3FeatureImpl("ctg123", ".", "CDS", 7000, 7600, -1d, Strand.POSITIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds00002"), "Parent", Collections.singletonList("mRNA00002"), "Name", Collections.singletonList("edenprotein.2")));
        canonicalGene_cds00002_3.addParent(canonicalGene_mRNA00002);
        canonicalGene_cds00002_3.addCoFeature(canonicalGene_cds00002_1);
        canonicalGene_cds00002_3.addCoFeature(canonicalGene_cds00002_2);
        canonicalGeneFeatures.add(canonicalGene_cds00002_3);

        final Gff3FeatureImpl canonicalGene_cds00003_1 = new Gff3FeatureImpl("ctg123", ".", "CDS", 3301, 3902, -1d, Strand.POSITIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds00003"), "Parent", Collections.singletonList("mRNA00003"), "Name", Collections.singletonList("edenprotein.3")));
        canonicalGene_cds00003_1.addParent(canonicalGene_mRNA00003);
        canonicalGeneFeatures.add(canonicalGene_cds00003_1);

        final Gff3FeatureImpl canonicalGene_cds00003_2 = new Gff3FeatureImpl("ctg123", ".", "CDS", 5000, 5500, -1d, Strand.POSITIVE, 1, ImmutableMap.of("ID", Collections.singletonList("cds00003"), "Parent", Collections.singletonList("mRNA00003"), "Name", Collections.singletonList("edenprotein.3")));
        canonicalGene_cds00003_2.addParent(canonicalGene_mRNA00003);
        canonicalGene_cds00003_2.addCoFeature(canonicalGene_cds00003_1);
        canonicalGeneFeatures.add(canonicalGene_cds00003_2);

        final Gff3FeatureImpl canonicalGene_cds00003_3 = new Gff3FeatureImpl("ctg123", ".", "CDS", 7000, 7600, -1d, Strand.POSITIVE, 1, ImmutableMap.of("ID", Collections.singletonList("cds00003"), "Parent", Collections.singletonList("mRNA00003"), "Name", Collections.singletonList("edenprotein.3")));
        canonicalGene_cds00003_3.addParent(canonicalGene_mRNA00003);
        canonicalGene_cds00003_3.addCoFeature(canonicalGene_cds00003_1);
        canonicalGene_cds00003_3.addCoFeature(canonicalGene_cds00003_2);
        canonicalGeneFeatures.add(canonicalGene_cds00003_3);

        final Gff3FeatureImpl canonicalGene_cds00004_1 = new Gff3FeatureImpl("ctg123", ".", "CDS", 3391, 3902, -1d, Strand.POSITIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds00004"), "Parent", Collections.singletonList("mRNA00003"), "Name", Collections.singletonList("edenprotein.4")));
        canonicalGene_cds00004_1.addParent(canonicalGene_mRNA00003);
        canonicalGeneFeatures.add(canonicalGene_cds00004_1);
        
        final Gff3FeatureImpl canonicalGene_cds00004_2 = new Gff3FeatureImpl("ctg123", ".", "CDS", 5000, 5500, -1d, Strand.POSITIVE, 1, ImmutableMap.of("ID", Collections.singletonList("cds00004"), "Parent", Collections.singletonList("mRNA00003"), "Name", Collections.singletonList("edenprotein.4")));
        canonicalGene_cds00004_2.addParent(canonicalGene_mRNA00003);
        canonicalGene_cds00004_2.addCoFeature(canonicalGene_cds00004_1);
        canonicalGeneFeatures.add(canonicalGene_cds00004_2);

        final Gff3FeatureImpl canonicalGene_cds00004_3 = new Gff3FeatureImpl("ctg123", ".", "CDS", 7000, 7600, -1d, Strand.POSITIVE, 1, ImmutableMap.of("ID", Collections.singletonList("cds00004"), "Parent", Collections.singletonList("mRNA00003"), "Name", Collections.singletonList("edenprotein.4")));
        canonicalGene_cds00004_3.addParent(canonicalGene_mRNA00003);
        canonicalGene_cds00004_3.addCoFeature(canonicalGene_cds00004_1);
        canonicalGene_cds00004_3.addCoFeature(canonicalGene_cds00004_2);
        canonicalGeneFeatures.add(canonicalGene_cds00004_3);

        canonicalGeneExample.add(canonicalGeneFeatures);
        examples.add(canonicalGeneExample.toArray());

        //polycisctronic transcript
        final ArrayList<Object> polycistronicTranscriptExample = new ArrayList<>(Collections.singletonList(DATA_DIR + "polycistronic_transcript.gff3"));

        final Set<Gff3Feature> polycisctronicTranscriptFeatures = new HashSet<>();

        final Gff3FeatureImpl polycistronicTranscript_gene01 = new Gff3FeatureImpl("chrX", ".", "gene", 100, 200, -1d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene01"), "name", Collections.singletonList("resA")));
        final Gff3FeatureImpl polycistronicTranscript_gene02 = new Gff3FeatureImpl("chrX", ".", "gene", 250, 350, -1d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene02"), "name", Collections.singletonList("resB")));
        final Gff3FeatureImpl polycistronicTranscript_gene03 = new Gff3FeatureImpl("chrX", ".", "gene", 400, 500, -1d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene03"), "name", Collections.singletonList("resX")));
        final Gff3FeatureImpl polycistronicTranscript_gene04 = new Gff3FeatureImpl("chrX", ".", "gene", 550, 650, -1d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene04"), "name", Collections.singletonList("resZ")));
        polycisctronicTranscriptFeatures.add(polycistronicTranscript_gene01);
        polycisctronicTranscriptFeatures.add(polycistronicTranscript_gene02);
        polycisctronicTranscriptFeatures.add(polycistronicTranscript_gene03);
        polycisctronicTranscriptFeatures.add(polycistronicTranscript_gene04);

        final Gff3FeatureImpl polycistronicTranscript_mRNA = new Gff3FeatureImpl("chrX", ".", "mRNA", 100, 650, -1d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("tran01"), "Parent", Arrays.asList("gene01", "gene02", "gene03", "gene04")));
        polycistronicTranscript_mRNA.addParent(polycistronicTranscript_gene01);
        polycistronicTranscript_mRNA.addParent(polycistronicTranscript_gene02);
        polycistronicTranscript_mRNA.addParent(polycistronicTranscript_gene03);
        polycistronicTranscript_mRNA.addParent(polycistronicTranscript_gene04);
        polycisctronicTranscriptFeatures.add(polycistronicTranscript_mRNA);

        final Gff3FeatureImpl polycistronicTranscript_exon = new Gff3FeatureImpl("chrX", ".", "exon", 100, 650, -1d, Strand.POSITIVE, -1, ImmutableMap.of("Parent", Collections.singletonList("tran01")));
        polycistronicTranscript_exon.addParent(polycistronicTranscript_mRNA);
        polycisctronicTranscriptFeatures.add(polycistronicTranscript_exon);


        final Gff3FeatureImpl polycistronicTranscript_CDS1 = new Gff3FeatureImpl("chrX", ".", "CDS", 100, 200, -1d, Strand.POSITIVE, 0, ImmutableMap.of("Parent", Collections.singletonList("tran01"), "Derives_from", Collections.singletonList("gene01")));
        polycistronicTranscript_CDS1.addParent(polycistronicTranscript_mRNA);
        polycisctronicTranscriptFeatures.add(polycistronicTranscript_CDS1);

        final Gff3FeatureImpl polycistronicTranscript_CDS2 = new Gff3FeatureImpl("chrX", ".", "CDS", 250, 350, -1d, Strand.POSITIVE, 0, ImmutableMap.of("Parent", Collections.singletonList("tran01"), "Derives_from", Collections.singletonList("gene02")));
        polycistronicTranscript_CDS2.addParent(polycistronicTranscript_mRNA);
        polycisctronicTranscriptFeatures.add(polycistronicTranscript_CDS2);

        final Gff3FeatureImpl polycistronicTranscript_CDS3 = new Gff3FeatureImpl("chrX", ".", "CDS", 400, 500, -1d, Strand.POSITIVE, 0, ImmutableMap.of("Parent", Collections.singletonList("tran01"), "Derives_from", Collections.singletonList("gene03")));
        polycistronicTranscript_CDS3.addParent(polycistronicTranscript_mRNA);
        polycisctronicTranscriptFeatures.add(polycistronicTranscript_CDS3);

        final Gff3FeatureImpl polycistronicTranscript_CDS4 = new Gff3FeatureImpl("chrX", ".", "CDS", 550, 650, -1d, Strand.POSITIVE, 0, ImmutableMap.of("Parent", Collections.singletonList("tran01"), "Derives_from", Collections.singletonList("gene04")));
        polycistronicTranscript_CDS4.addParent(polycistronicTranscript_mRNA);
        polycisctronicTranscriptFeatures.add(polycistronicTranscript_CDS4);

        polycistronicTranscriptExample.add(polycisctronicTranscriptFeatures);
        examples.add(polycistronicTranscriptExample.toArray());

        //programmed frameshift
        final ArrayList<Object> programmedFrameshiftExample = new ArrayList<>(Collections.singletonList(DATA_DIR + "programmed_frameshift.gff3"));

        final Set<Gff3Feature> programmedFrameshiftFeatures = new HashSet<>();

        final Gff3FeatureImpl programmedFrameshift_gene = new Gff3FeatureImpl("chrX", ".", "gene", 100, 200, -1d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene01"), "name", Collections.singletonList("my_gene")));
        programmedFrameshiftFeatures.add(programmedFrameshift_gene);

        final Gff3FeatureImpl programmedFrameshift_mRNA = new Gff3FeatureImpl("chrX", ".", "mRNA", 100, 200, -1d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("tran01"), "Parent", Collections.singletonList("gene01"), "Ontology_term", Collections.singletonList("SO:1000069")));
        programmedFrameshift_mRNA.addParent(programmedFrameshift_gene);
        programmedFrameshiftFeatures.add(programmedFrameshift_mRNA);

        final Gff3FeatureImpl programmedFrameshift_exon = new Gff3FeatureImpl("chrX", ".", "exon", 100, 200, -1d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("exon01"), "Parent", Collections.singletonList("tran01")));
        programmedFrameshift_exon.addParent(programmedFrameshift_mRNA);
        programmedFrameshiftFeatures.add(programmedFrameshift_exon);


        final Gff3FeatureImpl programmedFrameshift_CDS1_1 = new Gff3FeatureImpl("chrX", ".", "CDS", 100, 150, -1d, Strand.POSITIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds01"), "Parent",Collections.singletonList( "tran01")));
        programmedFrameshift_CDS1_1.addParent(programmedFrameshift_mRNA);
        programmedFrameshiftFeatures.add(programmedFrameshift_CDS1_1);

        final Gff3FeatureImpl programmedFrameshift_CDS1_2 = new Gff3FeatureImpl("chrX", ".", "CDS", 149, 200, -1d, Strand.POSITIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds01"), "Parent", Collections.singletonList("tran01")));
        programmedFrameshift_CDS1_2.addParent(programmedFrameshift_mRNA);
        programmedFrameshift_CDS1_2.addCoFeature(programmedFrameshift_CDS1_1);
        programmedFrameshiftFeatures.add(programmedFrameshift_CDS1_2);

        programmedFrameshiftExample.add(programmedFrameshiftFeatures);
        examples.add(programmedFrameshiftExample.toArray());

        //multiple genes
        final ArrayList<Object> multipleGenesExample = new ArrayList<>(Collections.singletonList(DATA_DIR + "multiple_genes.gff3"));

        final Set<Gff3Feature> multipleGenesFeatures = new HashSet<>();

        final Gff3FeatureImpl multipleGenes_gene1 = new Gff3FeatureImpl("ctg123", ".", "gene", 1000, 1500, -1d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene00001")));
        multipleGenesFeatures.add(multipleGenes_gene1);

        final Gff3FeatureImpl multipleGenes_mRNA1 = new Gff3FeatureImpl("ctg123", ".", "mRNA", 1050, 1400, -1d, Strand.POSITIVE, -1, ImmutableMap.of("Parent", Collections.singletonList("gene00001")));
        multipleGenes_mRNA1.addParent(multipleGenes_gene1);
        multipleGenesFeatures.add(multipleGenes_mRNA1);

        final Gff3FeatureImpl multipleGenes_gene2 = new Gff3FeatureImpl("ctg123", ".", "gene", 2000, 2500, -1d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene00002")));
        multipleGenesFeatures.add(multipleGenes_gene2);

        final Gff3FeatureImpl multipleGenes_mRNA2 = new Gff3FeatureImpl("ctg123", ".", "mRNA", 2050, 2400, -1d, Strand.POSITIVE, -1, ImmutableMap.of("Parent", Collections.singletonList("gene00002")));
        multipleGenes_mRNA2.addParent(multipleGenes_gene2);
        multipleGenesFeatures.add(multipleGenes_mRNA2);

        multipleGenesExample.add(multipleGenesFeatures);
        examples.add(multipleGenesExample.toArray());

        return examples.toArray(new Object[0][]);
    }

    @Test(dataProvider = "examplesDataProvider")
    public void examplesTest(final String inputGff, final Set<Gff3Feature> expectedFeatures) throws IOException {
        final AbstractFeatureReader<Gff3Feature, LineIterator> reader = AbstractFeatureReader.getFeatureReader(inputGff, null, new Gff3Codec(), false);
        int observedFeatures = 0;
        for (final Gff3Feature feature : reader.iterator()) {

            observedFeatures++;
            Assert.assertTrue(expectedFeatures.contains(feature));
        }

        Assert.assertEquals(observedFeatures, expectedFeatures.size());
    }

    @DataProvider(name = "directiveDataProvider")
    public Object[][] directiveDataProvider() {
        return new Object[][] {
                {"##gff-version 3.1.25", Gff3Codec.Gff3Directive.VERSION3_DIRECTIVE, "3.1.25"},
                {"##gff-version 3.7", Gff3Codec.Gff3Directive.VERSION3_DIRECTIVE, "3.7"},
                {"##gff-version 3", Gff3Codec.Gff3Directive.VERSION3_DIRECTIVE, "3"},
                {"##gff-version 3.112.25.4.2", Gff3Codec.Gff3Directive.VERSION3_DIRECTIVE, "3.112.25.4.2"},
                {"##gff-version 2.7", null, null},
                {"##sequence-region chr10 250 277", Gff3Codec.Gff3Directive.SEQUENCE_REGION_DIRECTIVE, new SequenceRegion("chr10", 250, 277)},
                {"###", Gff3Codec.Gff3Directive.FLUSH_DIRECTIVE, null},
                {"####", null, null},
                {"##FASTA", Gff3Codec.Gff3Directive.FASTA_DIRECTIVE, null}
        };
    }

    @Test(dataProvider = "directiveDataProvider")
    public void directiveTest(final String line, final Gff3Codec.Gff3Directive expectedDirectiveType, final Object expectedDecodedDirective) throws IOException {
        final Gff3Codec.Gff3Directive directive = Gff3Codec.Gff3Directive.toDirective(line);
        Assert.assertEquals(directive, expectedDirectiveType);
        if (directive != null) {
            Assert.assertEquals(directive.decode(line), expectedDecodedDirective);
            if (expectedDecodedDirective != null) {
                Assert.assertEquals(directive.encode(expectedDecodedDirective), line);
            }
        }
    }

    @DataProvider(name = "directiveEncodingDataProvider")
    public Object [][] directiveEncodingDataProvider() {
        return new Object[][] {
                {Gff3Codec.Gff3Directive.VERSION3_DIRECTIVE, "3.1.3", "##gff-version 3.1.3"},
                {Gff3Codec.Gff3Directive.SEQUENCE_REGION_DIRECTIVE, new SequenceRegion("theContig", 101, 170), "##sequence-region theContig 101 170"},
                {Gff3Codec.Gff3Directive.FLUSH_DIRECTIVE, null, "###"},
                {Gff3Codec.Gff3Directive.FASTA_DIRECTIVE, null, "##FASTA"}
        };
    }

    @Test(dataProvider = "directiveEncodingDataProvider")
    public void directiveEncodingTest(final Gff3Codec.Gff3Directive directive, final Object object, final String expectedEncoding) {
        final String encoding = directive.encode(object);

        Assert.assertEquals(encoding, expectedEncoding);
    }

    @DataProvider(name = "version3InvalidDirectives")
    public Object[][] version3InvalidDirectivesDataProvider() {
        return new Object[][] {
                {"3.1.a"},
                {"2"},
                {"2.1"},
                {".3.1"}
        };
    }

    @Test(dataProvider = "version3InvalidDirectives", expectedExceptions = TribbleException.class)
    public void version3InvalidDirectivesTest(final String v3Directive) {
        Gff3Codec.Gff3Directive.VERSION3_DIRECTIVE.encode(v3Directive);
    }

    @DataProvider(name = "decodeAttributeValueDataProvider")
    public Object[][] decodeAttributeValueDataProvider() {
        return new Object[][] {
                {"value1, value2, value3", Arrays.asList("value1", "value2", "value3")},
                {"value1, value %3B with %3D special %26 encoded %2C characters, value3", Arrays.asList("value1", "value ; with = special & encoded , characters", "value3")}
        };
    }

    @Test(dataProvider = "decodeAttributeValueDataProvider")
    public void decodeAttributeValueTest(final String attributeValueString, final List<String> expectedAttributeValues) {
        final List<String> attributeValues = Gff3Codec.decodeAttributeValue(attributeValueString);

        Assert.assertEquals(attributeValues, expectedAttributeValues);
    }

    @DataProvider(name = "extractSingleAttributeDataProvider")
    public Object[][] extractSingleAttributeDataProvider() {
        return new Object[][] {
                {null, null, false}, //null returns null
                {Collections.emptyList(), null, false}, //empty returns null
                {Collections.singletonList("single value"), "single value", false}, //single value returns single value
                {Arrays.asList("value1", "value2"), null, true} //multiple values throws exception
        };
    }

    @Test(dataProvider = "extractSingleAttributeDataProvider")
    public void extractSingleAttributeTest(final List<String> attributes, final String expectedSingleAttribute, final boolean expectException) {
        if (expectException) {
            Assert.assertThrows(() -> Gff3Codec.extractSingleAttribute(attributes));
        } else {
            final String singleAttribute = Gff3Codec.extractSingleAttribute(attributes);
            Assert.assertEquals(singleAttribute, expectedSingleAttribute);
        }
    }
}