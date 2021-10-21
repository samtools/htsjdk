package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;
import htsjdk.variant.VariantBaseTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class VCFHeaderMergerUnitTest extends VariantBaseTest {

    @DataProvider(name="validHeaderMergeVersions")
    public Object[][] validHeaderMergeVersion() {

        // only v4.2+ headers can be merged, merge result version is always the highest version presented
        return new Object[][] {
                // headers to merge, expected result version
                {Arrays.asList(VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF4_2), VCFHeaderVersion.VCF4_2},
                {Arrays.asList(VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF4_3), VCFHeaderVersion.VCF4_3},
                {Arrays.asList(VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_2), VCFHeaderVersion.VCF4_3},
                {Arrays.asList(VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_3), VCFHeaderVersion.VCF4_3},
                {Arrays.asList(VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF4_2), VCFHeaderVersion.VCF4_2},
                {Arrays.asList(VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_3), VCFHeaderVersion.VCF4_3},
                {Arrays.asList(VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF4_3), VCFHeaderVersion.VCF4_3},
                {Arrays.asList(VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_2), VCFHeaderVersion.VCF4_3},
                {Arrays.asList(VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF4_2), VCFHeaderVersion.VCF4_2 },
                {Arrays.asList(VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF4_3), VCFHeaderVersion.VCF4_3},
                {Arrays.asList(VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_2), VCFHeaderVersion.VCF4_3},
                {Arrays.asList(VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF4_3), VCFHeaderVersion.VCF4_3},
                {Arrays.asList(VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_3), VCFHeaderVersion.VCF4_3},
        };
    }

    @DataProvider(name="invalidHeaderMergeVersions")
    public Object[][] invalidHeaderMergeVersions() {
        // only v4.2+ headers can be merged
        return new Object[][] {
                {Arrays.asList(VCFHeaderVersion.VCF3_2, VCFHeaderVersion.VCF3_3)},
                {Arrays.asList(VCFHeaderVersion.VCF3_2, VCFHeaderVersion.VCF4_0)},
                {Arrays.asList(VCFHeaderVersion.VCF3_2, VCFHeaderVersion.VCF4_1)},
                {Arrays.asList(VCFHeaderVersion.VCF3_2, VCFHeaderVersion.VCF4_2)},
                {Arrays.asList(VCFHeaderVersion.VCF3_2, VCFHeaderVersion.VCF4_3)},

                {Arrays.asList(VCFHeaderVersion.VCF3_3, VCFHeaderVersion.VCF3_2)},
                {Arrays.asList(VCFHeaderVersion.VCF3_3, VCFHeaderVersion.VCF4_0)},
                {Arrays.asList(VCFHeaderVersion.VCF3_3, VCFHeaderVersion.VCF4_1)},
                {Arrays.asList(VCFHeaderVersion.VCF3_3, VCFHeaderVersion.VCF4_2)},
                {Arrays.asList(VCFHeaderVersion.VCF3_3, VCFHeaderVersion.VCF4_3)},

                {Arrays.asList(VCFHeaderVersion.VCF4_0, VCFHeaderVersion.VCF3_2)},
                {Arrays.asList(VCFHeaderVersion.VCF4_0, VCFHeaderVersion.VCF3_3)},
                {Arrays.asList(VCFHeaderVersion.VCF4_0, VCFHeaderVersion.VCF4_1)},
                {Arrays.asList(VCFHeaderVersion.VCF4_0, VCFHeaderVersion.VCF4_2)},
                {Arrays.asList(VCFHeaderVersion.VCF4_0, VCFHeaderVersion.VCF4_3)},

                {Arrays.asList(VCFHeaderVersion.VCF4_1, VCFHeaderVersion.VCF3_2)},
                {Arrays.asList(VCFHeaderVersion.VCF4_1, VCFHeaderVersion.VCF3_3)},
                {Arrays.asList(VCFHeaderVersion.VCF4_1, VCFHeaderVersion.VCF4_0)},
                {Arrays.asList(VCFHeaderVersion.VCF4_1, VCFHeaderVersion.VCF4_2)},
                {Arrays.asList(VCFHeaderVersion.VCF4_1, VCFHeaderVersion.VCF4_3)},

                {Arrays.asList(VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF3_2)},
                {Arrays.asList(VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF3_3)},
                {Arrays.asList(VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF4_0)},
                {Arrays.asList(VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF4_1)},

                {Arrays.asList(VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF3_2)},
                {Arrays.asList(VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF3_3)},
                {Arrays.asList(VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_0)},
                {Arrays.asList(VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_1)},
        };
    }

    @Test(dataProvider="validHeaderMergeVersions")
    public void testValidHeaderMergeVersions(final List<VCFHeaderVersion> headerVersions, final VCFHeaderVersion expectedVersion) {
        // merge the headers, and then verify that the merged lines have the expected version by
        // instantiating a VCFMetaDataLines instance to determine the resulting version
        final Set<VCFHeaderLine> mergedHeaderLines = doMultiHeaderMerge(headerVersions);
        final VCFMetaDataLines metaDataLines = new VCFMetaDataLines();
        metaDataLines.addMetaDataLines(mergedHeaderLines);
        final VCFHeaderLine versionLine = metaDataLines.getFileFormatLine();
        Assert.assertEquals(VCFHeaderVersion.toHeaderVersion(versionLine.getValue()), expectedVersion);

        // now create a new header using the merged VersionLines, and make sure *it* has the expected version
        final VCFHeader mergedHeader = new VCFHeader(mergedHeaderLines);
        Assert.assertEquals(mergedHeader.getVCFHeaderVersion(), expectedVersion);

        // also verify that all the header lines in the merged set are also in the resulting header
        Assert.assertEquals(mergedHeader.getMetaDataInInputOrder(), mergedHeaderLines);
    }

    @Test(dataProvider="invalidHeaderMergeVersions", expectedExceptions = TribbleException.class)
    public void testInvalidHeaderMergeVersions(final List<VCFHeaderVersion> headerVersions) {
        doMultiHeaderMerge(headerVersions);
    }

    private Set<VCFHeaderLine> doMultiHeaderMerge(final List<VCFHeaderVersion> headerVersions) {
        // This is a somewhat sketchy way to write a test...for each header we create here, we're
        // using the same fixed set of VCF42-conforming VCFHeader lines, and then we add a fileformat
        // line with whatever VCFVersion the test calls for. Its conceivable that as time goes on
        // and we add new versions, the VCFHeader constructor could throw if any of the lines don't
        // conform to the requested version.
        final List<VCFHeader> headerList = new ArrayList<>(headerVersions.size());
        for (final VCFHeaderVersion version : headerVersions) {
            final Set<VCFHeaderLine> metaDataSet = VCFHeaderUnitTestData.getV42HeaderLinesWITHOUTFormatString();
            metaDataSet.add(VCFHeader.makeHeaderVersionLine(version));
            final VCFHeader header = new VCFHeader(metaDataSet);
            Assert.assertEquals(header.getVCFHeaderVersion(), version);
            headerList.add(header);
        }

        return VCFUtils.smartMergeHeaders(headerList, false);
    }

    @DataProvider(name = "mergeSubsetHeader")
    public Iterator<Object[]> mergeSubsetHeader() {
        final List<VCFHeaderLine> headerLineList = new ArrayList<>(new VCFHeaderUnitTestData().getTestMetaDataLinesSet());
        final Collection<Object[]> mergeTestCase = new ArrayList<>();
        // For each header line in the list of test lines, create a test case consisting of a pair of headers,
        // one of which is a header created with all of the lines, and one of which is a subset of the full header
        // with one line removed. Skip the case where the line to be removed is a fileformat line, since thats
        // required to create a header.
        for (int i = 0; i < headerLineList.size(); i++) {
            // take the header line set and remove the ith line, unless its a fileformat line, since if we remove
            // that, then we won't be able to create a header using the resulting lines at all.
            final VCFHeaderLine candidateLine = headerLineList.get(i);
            if (!VCFHeaderVersion.isFormatString(candidateLine.getKey())) {
                List<VCFHeaderLine> subsetList = new ArrayList<>(headerLineList);
                subsetList.remove(i);
                mergeTestCase.add(
                        new Object[] {
                                new VCFHeader(VCFHeaderUnitTestData.getTestMetaDataLinesSet()),
                                new VCFHeader(new LinkedHashSet<>(subsetList))
                        });
            }
        }

        return mergeTestCase.iterator();
    }

    @Test(dataProvider = "mergeSubsetHeader")
    public void testMergeSubsetHeaders(
            final VCFHeader fullHeader,
            final VCFHeader subsetHeader)
    {
        final List<VCFHeader> headerList = new ArrayList<VCFHeader>() {{
            add(fullHeader);
            add(subsetHeader);
        }};
        Assert.assertEquals(
                VCFHeaderMerger.getMergedHeaderLines(headerList, false),
                fullHeader.getMetaDataInSortedOrder());
    }

    @DataProvider
    public Object[][] testDictionaryMergingData() {
        return new Object[][]{
                {"diagnosis_targets_testfile.vcf"},  // numerically ordered contigs

                //TODO: this fails now because the test file has lexicographically ordered contigs
                //{"dbsnp_135.b37.1000.vcf"}          // lexicographically ordered contigs
        };
    }

    @Test(dataProvider = "testDictionaryMergingData")
    public void testDictionaryMerging(final String vcfFileName) {
        final VCFHeader headerOne = new VCFFileReader(new File(variantTestDataRoot + vcfFileName), false).getFileHeader();
        final VCFHeader headerTwo = new VCFHeader(headerOne); // deep copy
        final List<String> sampleList = new ArrayList<>();
        sampleList.addAll(headerOne.getSampleNamesInOrder());

        // Check that the two dictionaries start out the same
        headerOne.getSequenceDictionary().assertSameDictionary(headerTwo.getSequenceDictionary());

        // Run the merge command
        final VCFHeader mergedHeader = new VCFHeader(VCFHeaderMerger.getMergedHeaderLines(Arrays.asList(headerOne, headerTwo), false), sampleList);

        // Check that the mergedHeader's sequence dictionary matches the first two
        mergedHeader.getSequenceDictionary().assertSameDictionary(headerOne.getSequenceDictionary());
    }

    // test merging dictionary subsets, in each direction
    // test failure for unmergable dictionaries
    // test merging across versions with lines that fail the new version (PEDIGREE, ALT ?)
    // test mering where compound lines have different attributes
}
