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


public class GtfCodecTest extends HtsjdkTest {

    final static String DATA_DIR = TestUtils.DATA_DIR + "/gff/";
    private final Path gencode47_gzipped = Paths.get(DATA_DIR + "gencode.v47.annotation.gtf.gz");

    @DataProvider(name = "basicDecodeDataProvider")
    Object[][] basicDecodeDataProvider() {
        return new Object[][]{
                {gencode47_gzipped, 2}
        };
    }

    @Test(dataProvider = "basicDecodeDataProvider")
    public void basicDecodeTest(final Path inputGtf, final int expectedTotalFeatures) throws IOException {
        Assert.assertTrue((new GtfCodec()).canDecode(inputGtf.toAbsolutePath().toString()));
        final AbstractFeatureReader<Gff3Feature, LineIterator> reader = AbstractFeatureReader.getFeatureReader(inputGtf.toAbsolutePath().toString(), null, new Gff3Codec(), false);
        int countTotalFeatures = 0;
        for (final Gff3Feature feature : reader.iterator()) {
            countTotalFeatures++;
        }

        Assert.assertEquals(countTotalFeatures, expectedTotalFeatures);
    }

    @Test(dataProvider = "basicDecodeDataProvider")
    public void codecFilterOutFieldsTest(final Path inputGtf, final int expectedTotalFeatures) throws IOException {
        final Set<String> skip_attributes = new HashSet<>(Arrays.asList("version","rank","biotype","transcript_support_level","mgi_id","havana_gene","tag"));
        final GtfCodec codec = new GtfCodec(GtfCodec.DecodeDepth.SHALLOW, S->skip_attributes.contains(S));
        Assert.assertTrue(codec.canDecode(inputGtf.toAbsolutePath().toString()));
        final AbstractFeatureReader<Gff3Feature, LineIterator> reader = AbstractFeatureReader.getFeatureReader(inputGtf.toAbsolutePath().toString(), null,codec, false);
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




}