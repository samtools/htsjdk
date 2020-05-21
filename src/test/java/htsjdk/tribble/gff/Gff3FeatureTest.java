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
        examples.add(new Object[] {new Gff3FeatureImpl("chr1", ".", "gene", 1000, 1200, -1d, Strand.NEGATIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene01"))),
                new Gff3FeatureImpl("chr1", ".", "gene", 1000, 1200, -1d, Strand.NEGATIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene01"))), true});
        examples.add(new Object[] {new Gff3FeatureImpl("chr1", ".", "CDS", 1010, 1050, -1d, Strand.NEGATIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds01"),"Parent", Collections.singletonList("gene01"))),
                new Gff3FeatureImpl("chr1", ".", "CDS", 1010, 1050, -1d, Strand.NEGATIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds01"),"Parent", Collections.singletonList("gene01"))), true});

        //two features with same baseData, one with child (or parent) feature, one without
        final Gff3FeatureImpl feature1_1 = new Gff3FeatureImpl("chr1", ".", "gene", 1000, 1200, -1d, Strand.NEGATIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene01")));
        final Gff3FeatureImpl feature2_1 = new Gff3FeatureImpl("chr1", ".", "gene", 1000, 1200, -1d, Strand.NEGATIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene01")));
        final Gff3FeatureImpl feature3_1 = new Gff3FeatureImpl("chr1", ".", "CDS", 1010, 1050, -1d, Strand.NEGATIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds01"),"Parent", Collections.singletonList("gene01")));
        feature3_1.addParent(feature1_1);
        final Gff3FeatureImpl feature4_1 = new Gff3FeatureImpl("chr1", ".", "CDS", 1010, 1050, -1d, Strand.NEGATIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds01"),"Parent", Collections.singletonList("gene01")));

        examples.add(new Object[] {feature1_1, feature2_1, false});
        examples.add(new Object[] {feature3_1, feature4_1, false});

        //give both genes child feature
        final Gff3FeatureImpl feature1_2 = new Gff3FeatureImpl("chr1", ".", "gene", 1000, 1200, -1d, Strand.NEGATIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene01")));
        final Gff3FeatureImpl feature2_2 = new Gff3FeatureImpl("chr1", ".", "gene", 1000, 1200, -1d, Strand.NEGATIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene01")));
        final Gff3FeatureImpl feature3_2 = new Gff3FeatureImpl("chr1", ".", "CDS", 1010, 1050, -1d, Strand.NEGATIVE, -0, ImmutableMap.of("ID", Collections.singletonList("cds01"),"Parent", Collections.singletonList("gene01")));
        feature3_2.addParent(feature1_2);
        final Gff3FeatureImpl feature4_2 = new Gff3FeatureImpl("chr1", ".", "CDS", 1010, 1050, -1d, Strand.NEGATIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds01"),"Parent", Collections.singletonList("gene01")));
        feature4_2.addParent(feature2_2);

        examples.add(new Object[] {feature1_2, feature2_2, true});
        examples.add(new Object[] {feature3_2, feature4_2, true});

        //give one cds a co-feature
        final Gff3FeatureImpl feature1_3 = new Gff3FeatureImpl("chr1", ".", "gene", 1000, 1200, -1d, Strand.NEGATIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene01")));
        final Gff3FeatureImpl feature2_3 = new Gff3FeatureImpl("chr1", ".", "gene", 1000, 1200, -1d, Strand.NEGATIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene01")));
        final Gff3FeatureImpl feature3_3 = new Gff3FeatureImpl("chr1", ".", "CDS", 1010, 1050, -1d, Strand.NEGATIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds01"),"Parent", Collections.singletonList("gene01")));
        feature3_3.addParent(feature1_3);
        final Gff3FeatureImpl feature4_3 = new Gff3FeatureImpl("chr1", ".", "CDS", 1010, 1050, -1d, Strand.NEGATIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds01"),"Parent", Collections.singletonList("gene01")));
        feature4_3.addParent(feature2_3);
        final Gff3FeatureImpl feature5_3 = new Gff3FeatureImpl("chr1", ".", "CDS", 1080, 1150, -1d, Strand.NEGATIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds01"),"Parent", Collections.singletonList("gene01")));
        feature5_3.addParent(feature1_3);
        final Gff3FeatureImpl feature6_3 = new Gff3FeatureImpl("chr1", ".", "CDS", 1080, 1150, -1d, Strand.NEGATIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds01"),"Parent", Collections.singletonList("gene01")));

        feature3_3.addCoFeature(feature5_3);

        examples.add(new Object[] {feature1_3, feature2_3, false});
        examples.add(new Object[] {feature3_3, feature4_3, false});
        examples.add(new Object[] {feature5_3, feature6_3, false});

        //give both cds co-features
        final Gff3FeatureImpl feature1_4 = new Gff3FeatureImpl("chr1", ".", "gene", 1000, 1200, -1d, Strand.NEGATIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene01")));
        final Gff3FeatureImpl feature2_4 = new Gff3FeatureImpl("chr1", ".", "gene", 1000, 1200, -1d, Strand.NEGATIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene01")));
        final Gff3FeatureImpl feature3_4 = new Gff3FeatureImpl("chr1", ".", "CDS", 1010, 1050, -1d, Strand.NEGATIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds01"),"Parent", Collections.singletonList("gene01")));
        feature3_4.addParent(feature1_4);
        final Gff3FeatureImpl feature4_4 = new Gff3FeatureImpl("chr1", ".", "CDS", 1010, 1050, -1d, Strand.NEGATIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds01"),"Parent", Collections.singletonList("gene01")));
        feature4_4.addParent(feature2_4);
        final Gff3FeatureImpl feature5_4 = new Gff3FeatureImpl("chr1", ".", "CDS", 1080, 1150, -1d, Strand.NEGATIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds01"),"Parent", Collections.singletonList("gene01")));
        feature5_4.addParent(feature1_4);
        final Gff3FeatureImpl feature6_4 = new Gff3FeatureImpl("chr1", ".", "CDS", 1080, 1150, -1d, Strand.NEGATIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds01"),"Parent", Collections.singletonList("gene01")));
        feature6_4.addParent(feature2_4);

        feature3_4.addCoFeature(feature5_4);
        feature4_4.addCoFeature(feature6_4);

        examples.add(new Object[] {feature1_4, feature2_4, true});
        examples.add(new Object[] {feature3_4, feature4_4, true});
        examples.add(new Object[] {feature5_4, feature6_4, true});

        return examples.toArray(new Object[0][]);
    }

    @Test(dataProvider = "equalityTestDataProvider")
    public void testEquals(final Gff3FeatureImpl feature1, final Gff3FeatureImpl feature2, final boolean equal) {
        Assert.assertEquals(feature1.equals(feature2), equal);
        if (equal) {
            Assert.assertEquals(feature1.hashCode(), feature2.hashCode());
        }
    }

    @Test
    public void testChildren() {
        //test that when a feature has a parent it is added as it's parent's child
        final Gff3FeatureImpl feature1 = new Gff3FeatureImpl("chr1", ".", "gene", 1000, 1200, -1d, Strand.NEGATIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene01")));
        final Gff3FeatureImpl feature2 = new Gff3FeatureImpl("chr1", ".", "CDS", 1010, 1050, -1d, Strand.NEGATIVE, -1, ImmutableMap.of("ID", Collections.singletonList("cds01"),"Parent", Collections.singletonList("gene01")));
        feature2.addParent(feature1);

        Assert.assertTrue(feature1.getChildren().contains(feature2));
        Assert.assertTrue(feature2.getParents().contains(feature1));
    }

    @Test
    public void testCofeatures() {
        //test that when a adding a cofeature it is reciprocated
        final Gff3FeatureImpl region = new Gff3FeatureImpl("chr1", ".", "region", 1, 10000, -1d, Strand.NONE, -1, ImmutableMap.of("ID", Collections.singletonList("region01")));
        final Gff3FeatureImpl feature1 = new Gff3FeatureImpl("chr1", ".", "gene", 1000, 1200, -1d, Strand.NEGATIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene01"), "Parent", Collections.singletonList("region01")));
        feature1.addParent(region);
        final Gff3FeatureImpl feature2 = new Gff3FeatureImpl("chr1", ".", "gene", 1300, 1600, -1d, Strand.NEGATIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene01"), "Parent", Collections.singletonList("region01")));
        feature2.addParent(region);
        final Gff3FeatureImpl feature3 = new Gff3FeatureImpl("chr1", ".", "gene", 1700, 1900, -1d, Strand.NEGATIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene01"), "Parent", Collections.singletonList("region01")));
        feature3.addParent(region);

        feature1.addCoFeature(feature2);
        feature2.addCoFeature(feature3);

        Assert.assertEquals(feature1.getCoFeatures(), ImmutableSet.of(feature2, feature3));
        Assert.assertEquals(feature2.getCoFeatures(), ImmutableSet.of(feature1, feature3));
        Assert.assertEquals(feature3.getCoFeatures(), ImmutableSet.of(feature1, feature2));
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testCofeautresDifferentParents() {
        final Gff3FeatureImpl feature1 = new Gff3FeatureImpl("chr1", ".", "gene", 1000, 1200, -1d, Strand.NEGATIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene01")));
        final Gff3FeatureImpl feature2 = new Gff3FeatureImpl("chr1", ".", "gene", 1300, 1600, -1d, Strand.NEGATIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene02")));
        final Gff3FeatureImpl feature3 = new Gff3FeatureImpl("chr1", ".", "CDS", 1010, 1050, -1d, Strand.NEGATIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds01"),"Parent", Collections.singletonList("gene01")));
        feature3.addParent(feature1);
        final Gff3FeatureImpl feature4 = new Gff3FeatureImpl("chr1", ".", "CDS", 1310, 1350, -1d, Strand.NEGATIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds01"),"Parent", Collections.singletonList("gene02")));
        feature4.addParent(feature2);

        //should throw exception because feature3 and feature4 have different parents so should not be co-features
        feature3.addCoFeature(feature4);
    }

    @Test
    public void testAncestorsAndDescendents() {

        final int nGenerations = 10;

        final Gff3FeatureImpl topLevelFeature = new Gff3FeatureImpl("chrX", ".", "type0", 1, 100, -1d, Strand.NEGATIVE, 0, ImmutableMap.of("ID", Collections.singletonList("feature0")));
        Gff3FeatureImpl prevFeature = topLevelFeature;

        final Map<Gff3FeatureImpl, Set<Gff3FeatureImpl>> ancestorsMap = new HashMap<>();
        final Map<Gff3FeatureImpl, Set<Gff3FeatureImpl>> descendentsMap = new HashMap<>();

        descendentsMap.put(topLevelFeature, new HashSet<>());
        ancestorsMap.put(topLevelFeature, new HashSet<>());

        final List<Gff3FeatureImpl> features = new ArrayList<>(Arrays.asList(topLevelFeature));

        for (int i=1; i<nGenerations; i++) {
            final Gff3FeatureImpl newFeature = new Gff3FeatureImpl("chrX", ".", "type" + i, 1, 100, -1d, Strand.NEGATIVE, 0, ImmutableMap.of("ID", Collections.singletonList("feature" + i), "Parent", Collections.singletonList(prevFeature.getID())));
            newFeature.addParent(prevFeature);
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
    public void testFlatten() {
        final int nGenerations = 10;

        final Gff3FeatureImpl topLevelFeature = new Gff3FeatureImpl("chrX", ".", "type0", 1, 100, -1d, Strand.NEGATIVE, 0, ImmutableMap.of("ID", Collections.singletonList("feature0")));
        Gff3FeatureImpl prevFeature = topLevelFeature;

        final Map<Gff3FeatureImpl, Set<Gff3FeatureImpl>> flattenMap = new HashMap<>(Collections.singletonMap(topLevelFeature, new HashSet<>(Collections.singleton(topLevelFeature))));

        for (int i=1; i<nGenerations; i++) {
            final Gff3FeatureImpl newFeature = new Gff3FeatureImpl("chrX", ".", "type" + i, 1, 100, -1d, Strand.NEGATIVE, 0, ImmutableMap.of("ID", Collections.singletonList("feature" + i), "Parent", Collections.singletonList(prevFeature.getID())));
            newFeature.addParent(prevFeature);
            flattenMap.forEach((k, v) -> v.add(newFeature));
            flattenMap.put(newFeature, new HashSet<>(Collections.singleton(newFeature)));

            flattenMap.forEach((k, v) -> Assert.assertEquals(k.flatten(), v));
            prevFeature = newFeature;
        }
    }

    @Test
    public void testDerivesFrom() {
        final Gff3FeatureImpl region01 = new Gff3FeatureImpl("chrX", ".", "gene", 65, 1000, -1d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("region01")));

        final Gff3FeatureImpl gene01 = new Gff3FeatureImpl("chrX", ".", "gene", 1, 35, -1d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene01"), "Parent", Collections.singletonList("region01")));
        gene01.addParent(region01);
        final Gff3FeatureImpl gene02 = new Gff3FeatureImpl("chrX", ".", "gene", 70, 100, -1d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene02")));

        final Gff3FeatureImpl mRNA01 = new Gff3FeatureImpl("chrX", ".", "mRNA", 1, 100, -1d, Strand.POSITIVE, -1 , ImmutableMap.of("ID", Collections.singletonList("mRNA01"), "Parent", Arrays.asList("gene01", "gene02")));
        mRNA01.addParent(gene01);
        mRNA01.addParent(gene02);

        final Gff3FeatureImpl cds01 = new Gff3FeatureImpl("chrX", ".", "CDS", 1, 35, -1d, Strand.POSITIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds01"), "Parent", Collections.singletonList("mRNA01"), "Derives_from", Collections.singletonList("gene01")));
        cds01.addParent(mRNA01);
        final Gff3FeatureImpl cds02 = new Gff3FeatureImpl("chrX", ".", "CDS", 70, 100, -1d, Strand.POSITIVE, 0, ImmutableMap.of("ID", Collections.singletonList("cds02"), "Parent", Collections.singletonList("mRNA01"), "Derives_from", Collections.singletonList("gene02")));
        cds02.addParent(mRNA01);

        final Gff3FeatureImpl codon01 = new Gff3FeatureImpl("chrX", ".", "codon", 1, 3, -1d, Strand.POSITIVE, 0, ImmutableMap.of("ID", Collections.singletonList("codon01"), "Parent", Collections.singletonList("cds01")));
        codon01.addParent(cds01);

        Assert.assertEquals(cds01.getAncestors(), ImmutableSet.of(mRNA01, gene01, region01));
        Assert.assertEquals(cds02.getAncestors(), ImmutableSet.of(mRNA01, gene02));
        Assert.assertEquals(mRNA01.getAncestors(), ImmutableSet.of(gene01, gene02, region01));
        Assert.assertEquals(codon01.getAncestors(), ImmutableSet.of(cds01, mRNA01, gene01, region01));

        Assert.assertEquals(gene01.getDescendents(), ImmutableSet.of(mRNA01, cds01, codon01));
        Assert.assertEquals(gene02.getDescendents(), ImmutableSet.of(mRNA01, cds02));
        Assert.assertEquals(mRNA01.getDescendents(), ImmutableSet.of(cds01, cds02, codon01));
        Assert.assertEquals(region01.getDescendents(), ImmutableSet.of(gene01, mRNA01, cds01, codon01));

        Assert.assertEquals(cds01.getTopLevelFeatures(), ImmutableSet.of(region01));
        Assert.assertEquals(cds02.getTopLevelFeatures(), ImmutableSet.of(gene02));
        Assert.assertEquals(mRNA01.getTopLevelFeatures(), ImmutableSet.of(gene02, region01));
    }

    @Test
    public void testFeatureWithUnLoadedParent() {
        final Gff3FeatureImpl gene01 = new Gff3FeatureImpl("chrX", ".", "gene", 1, 35, -1d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene01"), "Parent", Collections.singletonList("region01")));
        final Gff3FeatureImpl gene02 = new Gff3FeatureImpl("chrX", ".", "gene", 1, 35, -1d, Strand.POSITIVE, -1, ImmutableMap.of("ID", Collections.singletonList("gene01"), "Parent", Collections.singletonList("region01")));

        Assert.assertEquals(gene01, gene02);

        final Gff3FeatureImpl mRNA01 = new Gff3FeatureImpl("chrX", ".", "mRNA", 1, 100, -1d, Strand.POSITIVE, -1 , ImmutableMap.of("ID", Collections.singletonList("mRNA01"), "Parent", Arrays.asList("gene01", "gene02")));
        mRNA01.addParent(gene01);

        Assert.assertNotEquals(gene01, gene02);

        mRNA01.addParent(gene02);

        Assert.assertEquals(gene01, gene02);



    }

}