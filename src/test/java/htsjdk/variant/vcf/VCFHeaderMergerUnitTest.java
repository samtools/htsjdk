package htsjdk.variant.vcf;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.tribble.TribbleException;
import htsjdk.variant.VariantBaseTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

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

    @Test
    public void testDictionaryMergingDuplicateFile() {
        final VCFHeader headerOne = new VCFFileReader(new File(variantTestDataRoot + "diagnosis_targets_testfile.vcf"), false).getFileHeader();
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
    // test failure for unmergeable dictionaries
    //      use MultiVariantDataSourceUnitTest.testGetName and testGetSequenceDictionary test cases (have disjoint dictionaries)
    // test merging headers where one or both have no sequence dictionary
    //      make sure merging succeeds as long as NONE of the headers have a sequence dictionary ?

    // test merging across versions with lines that fail the new version (PEDIGREE, ALT ?)
    // test mering where compound lines have different attributes

    @DataProvider(name="vcfHeaderDictionaryMergePositive")
    private Object[][] getVCFHeaderDictionaryMergePositive() {
        return new Object[][] {
                {
                    // input dictionary list, expected merged dictionary
                        Arrays.asList(
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(1, 2)),
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(1, 2))
                        ),
                        createTestSAMDictionary(1, 2)
                }
        };
    }

    @Test(dataProvider = "vcfHeaderDictionaryMergePositive")
    private void testVCFHeaderDictionaryMergePositive(
            final List<VCFHeader> sourceHeaders, final SAMSequenceDictionary expectedDictionary) {
        final Set<VCFHeaderLine> mergedHeaderLines = VCFHeaderMerger.getMergedHeaderLines(sourceHeaders, false);
        final VCFHeader mergedHeader = new VCFHeader(mergedHeaderLines);
        Assert.assertEquals(mergedHeader.getSequenceDictionary(), expectedDictionary);
    }

    @DataProvider(name="vcfHeaderDictionaryMergeNegative")
    private Object[][] getVCFHeaderDictionaryMergeNegative() {
        final SAMSequenceDictionary canonicalHumanOrder = getDictionaryInCanonicalHumanOrder();
        final SAMSequenceDictionary nonCanonicalHumanOrder = getDictionaryInNonCanonicalHumanOrder();
        final SAMSequenceDictionary dictionaryWithLengths100 = getDictionaryWithLengths(100);
        final SAMSequenceDictionary dictionaryWithLengths200 =  getDictionaryWithLengths(200);

        final SAMSequenceDictionary forwardDictionary = createTestSAMDictionary(1, 2);
        final SAMSequenceDictionary reverseDictionary = createReverseDictionary(forwardDictionary);

        return new Object[][] {
                {
                    // SequenceDictionaryCompatibility.NO_COMMON_CONTIGS
                        Arrays.asList(
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(1, 2)),
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(5, 2)))
                },
                {
                    // SequenceDictionaryCompatibility.OUT_OF_ORDER
                        Arrays.asList(
                                createTestVCFHeaderWithSAMDictionary(forwardDictionary),
                                createTestVCFHeaderWithSAMDictionary(reverseDictionary))
                },
                {
                    // SequenceDictionaryCompatibility.UNEQUAL_COMMON_CONTIGS common subset has contigs that have the same name but different lengths
                        Arrays.asList(
                                createTestVCFHeaderWithSAMDictionary(dictionaryWithLengths100),
                                createTestVCFHeaderWithSAMDictionary(dictionaryWithLengths200))
                },
                {
                    // SequenceDictionaryCompatibility.NON_CANONICAL_HUMAN_ORDER human reference detected but the order of the contigs is non-standard (lexicographic, for example)
                        Arrays.asList(
                                createTestVCFHeaderWithSAMDictionary(getDictionaryInCanonicalHumanOrder()),
                                createTestVCFHeaderWithSAMDictionary(getDictionaryInNonCanonicalHumanOrder()))
                },
        };
    }

    @Test(dataProvider = "vcfHeaderDictionaryMergeNegative", expectedExceptions = TribbleException.class)
    private void testVCFHeaderDictionaryMergeNegative(final List<VCFHeader> sourceHeaders) {
        VCFHeaderMerger.getMergedHeaderLines(sourceHeaders, false);
    }

    private final SAMSequenceDictionary createTestSAMDictionary(final int startSequence, final int numSequences) {
        final SAMSequenceDictionary samDictionary = new SAMSequenceDictionary();
        IntStream.range(startSequence, startSequence + numSequences + 1).forEachOrdered(
                i -> samDictionary.addSequence(new SAMSequenceRecord(Integer.valueOf(i).toString(), i)));
        return samDictionary;
    }

    private final VCFHeader createTestVCFHeaderWithSAMDictionary(final SAMSequenceDictionary samDictionary) {
        final VCFHeader vcfHeader = createTestVCFHeader();
        vcfHeader.setSequenceDictionary(samDictionary);
        return vcfHeader;
    }

    private SAMSequenceDictionary getDictionaryInNonCanonicalHumanOrder() {
        final List<SAMSequenceRecord> sequences = new ArrayList<>();
        sequences.add(new SAMSequenceRecord("1", 100));
        sequences.add(new SAMSequenceRecord("10", 100));
        sequences.add(new SAMSequenceRecord("2", 100));
        return new SAMSequenceDictionary(sequences);
    }

    private SAMSequenceDictionary getDictionaryInCanonicalHumanOrder() {
        final List<SAMSequenceRecord> sequences = new ArrayList<>();
        sequences.add(new SAMSequenceRecord("1", 100));
        sequences.add(new SAMSequenceRecord("2", 100));
        sequences.add(new SAMSequenceRecord("10", 100));
        return new SAMSequenceDictionary(sequences);
    }

    private SAMSequenceDictionary getDictionaryWithLengths(final int length) {
        final List<SAMSequenceRecord> sequences = new ArrayList<>();
        sequences.add(new SAMSequenceRecord("1", length));
        sequences.add(new SAMSequenceRecord("2", length));
        sequences.add(new SAMSequenceRecord("3", length));
        return new SAMSequenceDictionary(sequences);

    }

    private SAMSequenceDictionary createReverseDictionary(final SAMSequenceDictionary forwardDictionary){
        // its not sufficient to just reverse the order of the sequences, since VCFHeader honors the contig
        // indices, but SAMSequenceDictionary mutates them to match the input order, so we need to create
        // an entirely new sequence dictionary using entirely new sequence records with contig indices that
        // match the input order
        final List<SAMSequenceRecord> reverseSequences = new ArrayList<>(forwardDictionary.getSequences());
        Collections.reverse(reverseSequences);
        final SAMSequenceDictionary reverseDictionary = new SAMSequenceDictionary();
        for (final SAMSequenceRecord samSequenceRecord : reverseSequences) {
            final SAMSequenceRecord newSequenceRecord = new SAMSequenceRecord(
                    samSequenceRecord.getSequenceName(),
                    samSequenceRecord.getSequenceLength());
            reverseDictionary.addSequence(newSequenceRecord);
        }
        return reverseDictionary;
    }

    private final VCFHeader createTestVCFHeader() {
        return new VCFHeader(VCFHeader.makeHeaderVersionLineSet(VCFHeader.DEFAULT_VCF_VERSION));
    }

}
