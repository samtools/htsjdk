package htsjdk.tribble.gff;

import htsjdk.HtsjdkTest;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.TestUtils;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;


public class Gff3CodecTest extends HtsjdkTest {

    final static String DATA_DIR = TestUtils.DATA_DIR + "/gff/";
    private final Path ensembl_human_small = Paths.get(DATA_DIR + "Homo_sapiens.GRCh38.97.chromosome.1.small.gff3");
    private final Path gencode_mouse_small = Paths.get(DATA_DIR + "gencode.vM22.annotation.small.gff3");
    private final Path ncbi_woodpecker_small = Paths.get(DATA_DIR + "ref_ASM69900v1_top_level.small.gff3");
    private final Path feature_extends_past_region = Paths.get(DATA_DIR + "feature_extends_past_region.gff3");
    private final Path feature_extends_past_circular_region = Paths.get(DATA_DIR + "feature_extends_past_circular_region.gff3");
    private final Path feature_outside_region = Paths.get(DATA_DIR + "feature_outside_region.gff3");


    @DataProvider(name = "basicDecodeDataProvider")
    Object[][] basicDecodeDataProvider() {
        return new Object[][]{
                {ensembl_human_small, 33, 82},
                {gencode_mouse_small, 16, 70},
                {ncbi_woodpecker_small, 10, 185},
                {Paths.get(DATA_DIR + "fasta_test.gff3"), 4, 12},
                {Paths.get(DATA_DIR + "fasta_test_artemis.gff3"), 4, 12}
        };
    }

    @Test(dataProvider = "basicDecodeDataProvider")
    public void basicDecodeTest(final Path inputGff3, final int expectedTopLevelFeatures, final int expectedTotalFeatures) throws IOException {
        Assert.assertTrue((new Gff3Codec()).canDecode(inputGff3.toAbsolutePath().toString()));
        final AbstractFeatureReader<Gff3Feature, LineIterator> reader = AbstractFeatureReader.getFeatureReader(inputGff3.toAbsolutePath().toString(), null, new Gff3Codec(), false);
        int countTopLevelFeatures = 0;
        int countTotalFeatures = 0;
        for (final Gff3Feature feature : reader.iterator()) {
            countTopLevelFeatures++;
            countTotalFeatures++;
            countTotalFeatures+=feature.getDescendents().size();
        }
        Assert.assertEquals(countTopLevelFeatures, expectedTopLevelFeatures);
        Assert.assertEquals(countTotalFeatures, expectedTotalFeatures);
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
        Assert.assertEquals(feature.getAttribute("Another key"), "Another=value");
    }


    /*
    examples from https://github.com/The-Sequence-Ontology/Specifications/blob/master/gff3.md
     */

    public static <T> Collector<T, ?, T> toSingleton() {
        return Collectors.collectingAndThen(
                Collectors.toList(),
                list -> {
                    if (list.size() != 1) {
                        throw new IllegalStateException();
                    }
                    return list.get(0);
                }
        );
    }

    private class featureInfo {
        int nDecendents;
        Set<String> decendentIDs;

        int nAncestors;
        Set<String> ancestorIDs;

        int nCofeatures;

        public featureInfo(int nDecendents, Set<String> decendentIDs, int nAncestors, Set<String> ancestorIDs, int nCofeatures) {
            this.nDecendents = nDecendents;
            this.decendentIDs = decendentIDs;
            this.nAncestors = nAncestors;
            this.ancestorIDs = ancestorIDs;
            this.nCofeatures = nCofeatures;
        }
    }

    @DataProvider(name = "examplesDataProvider")
    public Object[][] examplesDataProvider () {
        final ArrayList<Object[]> examples = new ArrayList<>();

        // examples from https://github.com/The-Sequence-Ontology/Specifications/blob/master/gff3.md

        //canonical gene
        final ArrayList<Object> canonicalGeneExample = new ArrayList<>(Arrays.asList(DATA_DIR + "canonical_gene.gff3",1));
        //map between top level feature IDs and number of features when flattened
        HashMap<String, Integer> topLevelFlattenedMap = new HashMap<>();
        topLevelFlattenedMap.put("gene00001", 23);
        canonicalGeneExample.add(topLevelFlattenedMap);

        //map between feature id's and feature Info
        HashMap<String, featureInfo> featureInfoMap = new HashMap<>();
        featureInfoMap.put("gene00001",
            new featureInfo(22,
                    new HashSet<>(Arrays.asList("tfbs00001", "mRNA00001", "mRNA00002", "mRNA00003", "exon00001", "exon00002", "exon00003", "exon00004", "exon00005",
                            "cds00001", "cds00002", "cds00003", "cds00004")
                    ),
                    0, Collections.EMPTY_SET, 0
            )
        );
        featureInfoMap.put("tfbs00001",
            new featureInfo(0, Collections.EMPTY_SET, 1, new HashSet<>(Arrays.asList("gene00001")), 0)
        );
        featureInfoMap.put("mRNA00001",
            new featureInfo(8, new HashSet<>(Arrays.asList("exon00002", "exon00003", "exon00004", "exon00005", "cds00001")),
                    1, new HashSet<>(Arrays.asList("gene00001")), 0)
        );
        featureInfoMap.put("mRNA00002",
            new featureInfo(6, new HashSet<>(Arrays.asList("exon00002", "exon00004", "exon00005", "cds00002")),
                    1, new HashSet<>(Arrays.asList("gene00001")), 0)
        );
        featureInfoMap.put("mRNA00003",
            new featureInfo(10, new HashSet<>(Arrays.asList("exon00001", "exon00003", "exon00004", "exon00005", "cds00003", "cds00004")),
                    1, new HashSet<>(Arrays.asList("gene00001")), 0)
        );
        featureInfoMap.put("exon00001",
                new featureInfo(0, Collections.EMPTY_SET,
                        2, new HashSet<>(Arrays.asList("gene00001", "mRNA00003")), 0)
        );
        featureInfoMap.put("exon00002",
                new featureInfo(0, Collections.EMPTY_SET,
                        3, new HashSet<>(Arrays.asList("gene00001", "mRNA00001", "mRNA00002")), 0)
        );
        featureInfoMap.put("exon00003",
                new featureInfo(0, Collections.EMPTY_SET,
                        3, new HashSet<>(Arrays.asList("gene00001", "mRNA00001", "mRNA00003")), 0)
        );
        featureInfoMap.put("exon00004",
                new featureInfo(0, Collections.EMPTY_SET,
                        4, new HashSet<>(Arrays.asList("gene00001", "mRNA00001", "mRNA00002", "mRNA00003")), 0)
        );
        featureInfoMap.put("exon00005",
                new featureInfo(0, Collections.EMPTY_SET,
                        4, new HashSet<>(Arrays.asList("gene00001", "mRNA00001", "mRNA00002", "mRNA00003")), 0)
        );
        featureInfoMap.put("cds00001",
                new featureInfo(0, Collections.EMPTY_SET,
                        2, new HashSet<>(Arrays.asList("gene00001", "mRNA00001")), 3)
        );
        featureInfoMap.put("cds00002",
                new featureInfo(0, Collections.EMPTY_SET,
                        2, new HashSet<>(Arrays.asList("gene00001", "mRNA00002")), 2)
        );
        featureInfoMap.put("cds00003",
                new featureInfo(0, Collections.EMPTY_SET,
                        2, new HashSet<>(Arrays.asList("gene00001", "mRNA00003")), 2)
        );
        featureInfoMap.put("cds00004",
                new featureInfo(0, Collections.EMPTY_SET,
                        2, new HashSet<>(Arrays.asList("gene00001", "mRNA00003")), 2)
        );
        canonicalGeneExample.add(featureInfoMap);

        examples.add(canonicalGeneExample.toArray(new Object[0]));


        //polycistronic transcript
        final ArrayList<Object> polycistronicTranscriptExample = new ArrayList<>(Arrays.asList(DATA_DIR + "polycistronic_transcript.gff3",4));
        //map between top level feature IDs and number of features when flattened
        topLevelFlattenedMap = new HashMap<>();
        topLevelFlattenedMap.put("gene01", 4);
        topLevelFlattenedMap.put("gene02",4);
        topLevelFlattenedMap.put("gene03",4);
        topLevelFlattenedMap.put("gene04",4);
        polycistronicTranscriptExample.add(topLevelFlattenedMap);

        //map between feature id's and feature Info
        featureInfoMap = new HashMap<>();
        featureInfoMap.put("gene01",
                new featureInfo(3,
                        new HashSet<>(Arrays.asList("tran01", "exon00001", "cds01")),
                        0, Collections.EMPTY_SET, 0)
        );
        featureInfoMap.put("gene02",
                new featureInfo(3,
                        new HashSet<>(Arrays.asList("tran01", "exon00001", "cds02")),
                        0, Collections.EMPTY_SET, 0)
        );
        featureInfoMap.put("gene03",
                new featureInfo(3,
                        new HashSet<>(Arrays.asList("tran01", "exon00001", "cds03")),
                        0, Collections.EMPTY_SET, 0)
        );
        featureInfoMap.put("gene04",
                new featureInfo(3,
                        new HashSet<>(Arrays.asList("tran01", "exon00001", "cds04")),
                        0, Collections.EMPTY_SET, 0)
        );
        featureInfoMap.put("tran01",
                new featureInfo(5,
                        new HashSet<>(Arrays.asList("exon00001", "cds01", "cds02", "cds03", "cds04")),
                        4, new HashSet<>(Arrays.asList("gene01", "gene02", "gene03", "gene04")), 0)
        );
        featureInfoMap.put("exon00001",
                new featureInfo(0,
                        Collections.EMPTY_SET,
                        5, new HashSet<>(Arrays.asList("gene01", "gene02", "gene03", "gene04", "tran01")), 0)
        );
        featureInfoMap.put("cds01",
                new featureInfo(0, Collections.EMPTY_SET,
                        2, new HashSet<>(Arrays.asList("gene01", "tran01")), 0)
        );
        featureInfoMap.put("cds02",
                new featureInfo(0, Collections.EMPTY_SET,
                        2, new HashSet<>(Arrays.asList("gene02", "tran01")), 0)
        );
        featureInfoMap.put("cds03",
                new featureInfo(0, Collections.EMPTY_SET,
                        2, new HashSet<>(Arrays.asList("gene03", "tran01")), 0)
        );
        featureInfoMap.put("cds04",
                new featureInfo(0, Collections.EMPTY_SET,
                        2, new HashSet<>(Arrays.asList("gene04", "tran01")), 0)
        );
        polycistronicTranscriptExample.add(featureInfoMap);
        examples.add(polycistronicTranscriptExample.toArray(new Object[0]));

        //programmed frameshift
        final ArrayList<Object> programmedFrameshiftExample = new ArrayList<>(Arrays.asList(DATA_DIR + "programmed_frameshift.gff3",1));
        //map between top level feature IDs and number of features when flattened
        topLevelFlattenedMap = new HashMap<>();
        topLevelFlattenedMap.put("gene01", 5);
        programmedFrameshiftExample.add(topLevelFlattenedMap);

        //map between feature id's and feature Info
        featureInfoMap = new HashMap<>();
        featureInfoMap.put("gene01",
                new featureInfo(4,
                        new HashSet<>(Arrays.asList("tran01", "exon01", "cds01")),
                        0, Collections.EMPTY_SET, 0)
        );
        featureInfoMap.put("tran01",
                new featureInfo(3,
                        new HashSet<>(Arrays.asList("exon01", "cds01")),
                            1, new HashSet<>(Arrays.asList("gene01")), 0)
        );
        featureInfoMap.put("exon01",
                new featureInfo(0,
                        Collections.EMPTY_SET,
                        2, new HashSet<>(Arrays.asList("gene01", "tran01")), 0)
        );
        featureInfoMap.put("cds01",
                new featureInfo(0,
                        Collections.EMPTY_SET,
                        2, new HashSet<>(Arrays.asList("gene01", "tran01")), 1)
        );

        programmedFrameshiftExample.add(featureInfoMap);
        examples.add(programmedFrameshiftExample.toArray(new Object[0]));

        return examples.toArray(new Object[0][]);
    }

    @Test(dataProvider = "examplesDataProvider")
    public void exampleGeneTests(final String inputGff, final int nTopLevelFeatures, final Map<String, Integer> topLevelFlattenedMap, final Map<String, featureInfo> featureInfoMap ) throws IOException {
        final AbstractFeatureReader<Gff3Feature, LineIterator> reader = AbstractFeatureReader.getFeatureReader(inputGff, null, new Gff3Codec(), false);
        final List<Gff3Feature> topLevelFeatures = reader.iterator().stream().collect(Collectors.toList());
        Assert.assertEquals(topLevelFeatures.size(), nTopLevelFeatures);
        for (final Gff3Feature topLevelFeature : topLevelFeatures) {
            final List<Gff3Feature> flattenedFeatures = topLevelFeature.flatten();
            Assert.assertEquals(flattenedFeatures.size(), (int)topLevelFlattenedMap.get(topLevelFeature.getID()), "For feature " + topLevelFeature.getID());
            for (final Gff3Feature feature : flattenedFeatures) {
                final featureInfo info = featureInfoMap.get(feature.getID());
                Assert.assertNotNull(info, "For feature " + feature.getID());

                final List<Gff3Feature> decendents = feature.getDescendents();
                Assert.assertEquals(decendents.size(), info.nDecendents, "For feature " + feature.getID());
                final Set<String> decendentIDs = decendents.stream().map(Gff3Feature::getID).collect(Collectors.toSet());
                Assert.assertEquals(decendentIDs, info.decendentIDs, "For feature " + feature.getID());

                final List<Gff3Feature> ancestors = feature.getAncestors();
                Assert.assertEquals(ancestors.size(), info.nAncestors, "For feature " + feature.getID());
                final Set<String> ancestorIDs = ancestors.stream().map(Gff3Feature::getID).collect(Collectors.toSet());
                Assert.assertEquals(ancestorIDs, info.ancestorIDs, "For feature " + feature.getID());

                Assert.assertEquals(feature.getCoFeatures().size(), info.nCofeatures, "For feature " + feature.getID());
            }
        }
    }

}