package htsjdk.tribble.gff;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import htsjdk.HtsjdkTest;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.annotation.Strand;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Gff3FeatureTest extends HtsjdkTest {

    @DataProvider(name = "equalityTestDataProvider")
    public Object[][] equalityTestDatProvider() {
        final ArrayList<Object[]> examples = new ArrayList<>();
        examples.add(new Object[] {new Gff3Feature("chr1", ".", "gene", 1000, 1200, Strand.NEGATIVE, -1, ImmutableMap.of("ID", "gene01")),
                new Gff3Feature("chr1", ".", "gene", 1000, 1200, Strand.NEGATIVE, -1, ImmutableMap.of("ID", "gene01")), true});
        examples.add(new Object[] {new Gff3Feature("chr1", ".", "CDS", 1010, 1050, Strand.NEGATIVE, 0, ImmutableMap.of("ID", "cds01","Parent", "gene01")),
                new Gff3Feature("chr1", ".", "CDS", 1010, 1050, Strand.NEGATIVE, 0, ImmutableMap.of("ID", "cds01","Parent", "gene01")), true});

        //two features with same baseData, one with child (or parent) feature, one without
        final Gff3Feature feature1_1 = new Gff3Feature("chr1", ".", "gene", 1000, 1200, Strand.NEGATIVE, -1, ImmutableMap.of("ID", "gene01"));
        final Gff3Feature feature2_1 = new Gff3Feature("chr1", ".", "gene", 1000, 1200, Strand.NEGATIVE, -1, ImmutableMap.of("ID", "gene01"));
        final Gff3Feature feature3_1 = new Gff3Feature("chr1", ".", "CDS", 1010, 1050, Strand.NEGATIVE, 0, ImmutableMap.of("ID", "cds01","Parent", "gene01"), Collections.singletonList(feature1_1));
        final Gff3Feature feature4_1 = new Gff3Feature("chr1", ".", "CDS", 1010, 1050, Strand.NEGATIVE, 0, ImmutableMap.of("ID", "cds01","Parent", "gene01"));

        examples.add(new Object[] {feature1_1, feature2_1, false});
        examples.add(new Object[] {feature3_1, feature4_1, false});

        //give both genes child feature
        final Gff3Feature feature1_2 = new Gff3Feature("chr1", ".", "gene", 1000, 1200, Strand.NEGATIVE, -1, ImmutableMap.of("ID", "gene01"));
        final Gff3Feature feature2_2 = new Gff3Feature("chr1", ".", "gene", 1000, 1200, Strand.NEGATIVE, -1, ImmutableMap.of("ID", "gene01"));
        final Gff3Feature feature3_2 = new Gff3Feature("chr1", ".", "CDS", 1010, 1050, Strand.NEGATIVE, -0, ImmutableMap.of("ID", "cds01","Parent", "gene01"), Collections.singletonList(feature1_2));
        final Gff3Feature feature4_2 = new Gff3Feature("chr1", ".", "CDS", 1010, 1050, Strand.NEGATIVE, 0, ImmutableMap.of("ID", "cds01","Parent", "gene01"), Collections.singletonList(feature2_2));

        examples.add(new Object[] {feature1_2, feature2_2, true});
        examples.add(new Object[] {feature3_2, feature4_2, true});

        //give one cds a co-feature
        final Gff3Feature feature1_3 = new Gff3Feature("chr1", ".", "gene", 1000, 1200, Strand.NEGATIVE, -1, ImmutableMap.of("ID", "gene01"));
        final Gff3Feature feature2_3 = new Gff3Feature("chr1", ".", "gene", 1000, 1200, Strand.NEGATIVE, -1, ImmutableMap.of("ID", "gene01"));
        final Gff3Feature feature3_3 = new Gff3Feature("chr1", ".", "CDS", 1010, 1050, Strand.NEGATIVE, 0, ImmutableMap.of("ID", "cds01","Parent", "gene01"), Collections.singletonList(feature1_3));
        final Gff3Feature feature4_3 = new Gff3Feature("chr1", ".", "CDS", 1010, 1050, Strand.NEGATIVE, 0, ImmutableMap.of("ID", "cds01","Parent", "gene01"), Collections.singletonList(feature2_3));
        final Gff3Feature feature5_3 = new Gff3Feature("chr1", ".", "CDS", 1080, 1150, Strand.NEGATIVE, 0, ImmutableMap.of("ID", "cds01","Parent", "gene01"), Collections.singletonList(feature1_3));
        final Gff3Feature feature6_3 = new Gff3Feature("chr1", ".", "CDS", 1080, 1150, Strand.NEGATIVE, 0, ImmutableMap.of("ID", "cds01","Parent", "gene01"));

        feature3_3.addCoFeature(feature5_3);

        examples.add(new Object[] {feature1_3, feature2_3, false});
        examples.add(new Object[] {feature3_3, feature4_3, false});
        examples.add(new Object[] {feature5_3, feature6_3, false});

        //give both cds co-features
        final Gff3Feature feature1_4 = new Gff3Feature("chr1", ".", "gene", 1000, 1200, Strand.NEGATIVE, -1, ImmutableMap.of("ID", "gene01"));
        final Gff3Feature feature2_4 = new Gff3Feature("chr1", ".", "gene", 1000, 1200, Strand.NEGATIVE, -1, ImmutableMap.of("ID", "gene01"));
        final Gff3Feature feature3_4 = new Gff3Feature("chr1", ".", "CDS", 1010, 1050, Strand.NEGATIVE, 0, ImmutableMap.of("ID", "cds01","Parent", "gene01"), Collections.singletonList(feature1_4));
        final Gff3Feature feature4_4 = new Gff3Feature("chr1", ".", "CDS", 1010, 1050, Strand.NEGATIVE, 0, ImmutableMap.of("ID", "cds01","Parent", "gene01"), Collections.singletonList(feature2_4));
        final Gff3Feature feature5_4 = new Gff3Feature("chr1", ".", "CDS", 1080, 1150, Strand.NEGATIVE, 0, ImmutableMap.of("ID", "cds01","Parent", "gene01"), Collections.singletonList(feature1_4));
        final Gff3Feature feature6_4 = new Gff3Feature("chr1", ".", "CDS", 1080, 1150, Strand.NEGATIVE, 0, ImmutableMap.of("ID", "cds01","Parent", "gene01"), Collections.singletonList(feature2_4));

        feature3_4.addCoFeature(feature5_4);
        feature4_4.addCoFeature(feature6_4);

        examples.add(new Object[] {feature1_4, feature2_4, true});
        examples.add(new Object[] {feature3_4, feature4_4, true});
        examples.add(new Object[] {feature5_4, feature6_4, true});

        return examples.toArray(new Object[0][]);
    }

    @Test(dataProvider = "equalityTestDataProvider")
    public void testEquals(final Gff3Feature feature1, final Gff3Feature feature2, final boolean equal) {
        Assert.assertEquals(feature1.equals(feature2), equal);
        if (equal) {
            Assert.assertEquals(feature1.hashCode(), feature2.hashCode());
        }
    }

    @Test
    public void testChildren() {
        //test that when a feature has a parent it is added as it's parent's child
        final Gff3Feature feature1 = new Gff3Feature("chr1", ".", "gene", 1000, 1200, Strand.NEGATIVE, -1, ImmutableMap.of("ID", "gene01"));
        final Gff3Feature feature2 = new Gff3Feature("chr1", ".", "CDS", 1010, 1050, Strand.NEGATIVE, -1, ImmutableMap.of("ID", "cds01","Parent", "gene01"), Collections.singletonList(feature1));

        Assert.assertTrue(feature1.getChildren().contains(feature2));
        Assert.assertTrue(feature2.getParents().contains(feature1));
    }

    @Test
    public void testCofeatures() {
        //test that when a adding a cofeature it is reciprocated
        final Gff3Feature feature1 = new Gff3Feature("chr1", ".", "gene", 1000, 1200, Strand.NEGATIVE, -1, ImmutableMap.of("ID", "gene01"));
        final Gff3Feature feature2 = new Gff3Feature("chr1", ".", "gene", 1300, 1600, Strand.NEGATIVE, -1, ImmutableMap.of("ID", "gene01"));
        final Gff3Feature feature3 = new Gff3Feature("chr1", ".", "gene", 1700, 1900, Strand.NEGATIVE, -1, ImmutableMap.of("ID", "gene01"));

        feature1.addCoFeature(feature2);
        feature2.addCoFeature(feature3);

        Assert.assertEquals(feature1.getCoFeatures(), ImmutableSet.of(feature2, feature3));
        Assert.assertEquals(feature2.getCoFeatures(), ImmutableSet.of(feature1, feature3));
        Assert.assertEquals(feature3.getCoFeatures(), ImmutableSet.of(feature1, feature2));
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testCofeautresDifferentParents() {
        final Gff3Feature feature1 = new Gff3Feature("chr1", ".", "gene", 1000, 1200, Strand.NEGATIVE, -1, ImmutableMap.of("ID", "gene01"));
        final Gff3Feature feature2 = new Gff3Feature("chr1", ".", "gene", 1300, 1600, Strand.NEGATIVE, -1, ImmutableMap.of("ID", "gene02"));
        final Gff3Feature feature3 = new Gff3Feature("chr1", ".", "CDS", 1010, 1050, Strand.NEGATIVE, 0, ImmutableMap.of("ID", "cds01","Parent", "gene01"), Collections.singletonList(feature1));
        final Gff3Feature feature4 = new Gff3Feature("chr1", ".", "CDS", 1310, 1350, Strand.NEGATIVE, 0, ImmutableMap.of("ID", "cds01","Parent", "gene02"), Collections.singletonList(feature2));

        //should throw exception because feature3 and feature4 have different parents so should not be co-features
        feature3.addCoFeature(feature4);
    }

    @Test
    public void testAncestorsAndDescendents() {

        final int nGenerations = 10;

        final Gff3Feature topLevelFeature = new Gff3Feature("chrX", ".", "type0", 1, 100, Strand.NEGATIVE, 0, ImmutableMap.of("ID", "feature0"));
        Gff3Feature prevFeature = topLevelFeature;

        final Map<Gff3Feature, Set<Gff3Feature>> ancestorsMap = new HashMap<>();
        final Map<Gff3Feature, Set<Gff3Feature>> descendentsMap = new HashMap<>();

        descendentsMap.put(topLevelFeature, new HashSet<>());
        ancestorsMap.put(topLevelFeature, new HashSet<>());

        final List<Gff3Feature> features = new ArrayList<>(Arrays.asList(topLevelFeature));

        for (int i=1; i<nGenerations; i++) {
            final Gff3Feature newFeature = new Gff3Feature("chrX", ".", "type" + i, 1, 100, Strand.NEGATIVE, 0, ImmutableMap.of("ID", "feature" + i, "Parent", prevFeature.getID()), Collections.singletonList(prevFeature));
            ancestorsMap.put(newFeature, new HashSet<>(ancestorsMap.get(prevFeature)));
            ancestorsMap.get(newFeature).add(prevFeature);

            descendentsMap.forEach((k, v) -> v.add(newFeature));
            descendentsMap.put(newFeature, new HashSet<>());

            descendentsMap.forEach((k, v) -> Assert.assertEquals(k.getDescendents(), v));
            ancestorsMap.forEach((k,v) -> Assert.assertEquals(k.getAncestors(), v));

            features.add(newFeature);

            features.forEach(f -> Assert.assertEquals(f.getTopLevelFeatures(), ImmutableSet.of(topLevelFeature)));

            prevFeature = newFeature;
        }
    }

    @Test
    public void testDerivesFrom() {
        final Gff3Feature gene01 = new Gff3Feature("chrX", ".", "gene", 1, 35, Strand.POSITIVE, -1, ImmutableMap.of("ID", "gene01"));
        final Gff3Feature gene02 = new Gff3Feature("chrX", ".", "gene", 70, 100, Strand.POSITIVE, -1, ImmutableMap.of("ID", "gene02"));

        final Gff3Feature mRNA01 = new Gff3Feature("chrX", ".", "mRNA", 1, 100, Strand.POSITIVE, -1 , ImmutableMap.of("ID", "mRNA01", "Parent", "gene01, gene02"), Arrays.asList(gene01, gene02));

        final Gff3Feature cds01 = new Gff3Feature("chrX", ".", "CDS", 1, 35, Strand.POSITIVE, 0, ImmutableMap.of("ID", "cds01", "Paren", "mRNA01", "Derives_from", "gene01"), Collections.singletonList(mRNA01));
        final Gff3Feature cds02 = new Gff3Feature("chrX", ".", "CDS", 70, 100, Strand.POSITIVE, 0, ImmutableMap.of("ID", "cds02", "Paren", "mRNA01", "Derives_from", "gene02"), Collections.singletonList(mRNA01));

        Assert.assertEquals(cds01.getAncestors(), ImmutableSet.of(mRNA01, gene01));
        Assert.assertEquals(cds02.getAncestors(), ImmutableSet.of(mRNA01, gene02));
        Assert.assertEquals(mRNA01.getAncestors(), ImmutableSet.of(gene01, gene02));

        Assert.assertEquals(gene01.getDescendents(), ImmutableSet.of(mRNA01, cds01));
        Assert.assertEquals(gene02.getDescendents(), ImmutableSet.of(mRNA01, cds02));
        Assert.assertEquals(mRNA01.getDescendents(), ImmutableSet.of(cds01, cds02));

        Assert.assertEquals(cds01.getTopLevelFeatures(), ImmutableSet.of(gene01));
        Assert.assertEquals(cds02.getTopLevelFeatures(), ImmutableSet.of(gene02));
        Assert.assertEquals(mRNA01.getTopLevelFeatures(), ImmutableSet.of(gene01, gene02));
    }

}