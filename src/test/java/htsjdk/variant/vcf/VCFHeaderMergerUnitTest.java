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

import static htsjdk.variant.vcf.VCFConstants.PEDIGREE_HEADER_KEY;

public class VCFHeaderMergerUnitTest extends VariantBaseTest {

    @DataProvider(name="mergeValidVersions")
    public Object[][] getMergeValidVersions() {

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

    @DataProvider(name="mergeInvalidVersions")
    public Object[][] getMergeInvalidVersions() {
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

    @Test(dataProvider="mergeValidVersions")
    public void testMergeValidVersions(final List<VCFHeaderVersion> headerVersions, final VCFHeaderVersion expectedVersion) {
        // merge the headers, and then verify that the merged lines have the expected version by
        // instantiating a VCFMetaDataLines instance to determine the resulting version
        final Set<VCFHeaderLine> mergedHeaderLines = doHeaderMergeForVersions(headerVersions);
        final VCFMetaDataLines metaDataLines = new VCFMetaDataLines();
        metaDataLines.addMetaDataLines(mergedHeaderLines);
        Assert.assertEquals(metaDataLines.getVCFVersion(), expectedVersion);

        // now create a new header using the merged VersionLines, and make sure *it* has the expected version
        final VCFHeader mergedHeader = new VCFHeader(mergedHeaderLines);
        Assert.assertEquals(mergedHeader.getVCFHeaderVersion(), expectedVersion);

        // also verify that all the header lines in the merged set are also in the resulting header
        Assert.assertEquals(mergedHeader.getMetaDataInInputOrder(), mergedHeaderLines);
    }

    @Test(dataProvider="mergeInvalidVersions", expectedExceptions = TribbleException.class)
    public void testMergeInvalidVersions(final List<VCFHeaderVersion> headerVersions) {
        doHeaderMergeForVersions(headerVersions);
    }

    @Test(expectedExceptions = TribbleException.VersionValidationFailure.class)
    public void testMergeWithValidationFailure() {
        // test mixing header versions where the old version header has a line that fails validation
        // using the resulting (newer) version

        // create a 4.2 header with a 4.2 style pedigree line (one that has no ID)
        final Set<VCFHeaderLine> oldHeaderLines = VCFHeader.makeHeaderVersionLineSet(VCFHeaderVersion.VCF4_2);
        oldHeaderLines.add(new VCFHeaderLine(PEDIGREE_HEADER_KEY, "<Name_0=G0-ID,Name_1=G1-ID>"));
        final VCFHeader oldHeader = new VCFHeader(oldHeaderLines);
        Assert.assertEquals(oldHeader.getVCFHeaderVersion(), VCFHeaderVersion.VCF4_2);

        // now create a simple 4.3 header; the merge should fail because the old PEDIGREE line isn't valid
        // for 4.3 (for which pedigree lines mut have an ID)
        final VCFHeader newHeader = new VCFHeader(VCFHeader.makeHeaderVersionLineSet(VCFHeaderVersion.VCF4_3));
        Assert.assertEquals(newHeader.getVCFHeaderVersion(), VCFHeaderVersion.VCF4_3);

        VCFHeaderMerger.getMergedHeaderLines(Arrays.asList(oldHeader, newHeader),true);
    }

    private Set<VCFHeaderLine> doHeaderMergeForVersions(final List<VCFHeaderVersion> headerVersions) {
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

    @DataProvider(name = "subsetHeaders")
    public Iterator<Object[]> getSubsetHeaders() {
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

    @Test(dataProvider = "subsetHeaders")
    public void testMergeSubsetHeaders(
            final VCFHeader fullHeader,
            final VCFHeader subsetHeader)
    {
        final List<VCFHeader> headerList = new ArrayList<VCFHeader>() {{
            add(fullHeader);
            add(subsetHeader);
            add(subsetHeader);
        }};
        Assert.assertEquals(
                VCFHeaderMerger.getMergedHeaderLines(headerList, false),
                fullHeader.getMetaDataInSortedOrder());

        // now again, in the reverse order
        final List<VCFHeader> reverseHeaderList = new ArrayList<VCFHeader>() {{
            add(subsetHeader);
            add(subsetHeader);
            add(fullHeader);
        }};
        Assert.assertEquals(
                VCFHeaderMerger.getMergedHeaderLines(reverseHeaderList, false),
                fullHeader.getMetaDataInSortedOrder());
    }

    @Test
    public void testDictionaryMergeDuplicateFile() {
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

    @DataProvider(name="dictionaryMergePositive")
    private Object[][] getDictionaryMergePositive() {
        return new Object[][] {
                // input dictionary list, expected merged dictionary
                {
                    // one dictionary
                        Arrays.asList(
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(1, 2))
                        ),
                        createTestSAMDictionary(1, 2)
                },
                {
                    // two identical dictionaries
                        Arrays.asList(
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(1, 2)),
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(1, 2))
                        ),
                        createTestSAMDictionary(1, 2)
                },
                {
                    // three different subsets; superset first
                        Arrays.asList(
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(1, 10)),
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(7, 2)),
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(3, 2))
                        ),
                        createTestSAMDictionary(1, 10)
                },
                {
                    // three different subsets; superset second
                        Arrays.asList(
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(7, 2)),
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(1, 10)),
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(3, 2))
                        ),
                        createTestSAMDictionary(1, 10)
                },
                {
                    // three different subsets; superset third (requires the merge implementation to sort on dictionary size)
                        Arrays.asList(
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(7, 2)),
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(3, 2)),
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(1, 10))
                        ),
                        createTestSAMDictionary(1, 10)
                },
                {
                    // one non-null dictionary, one null
                        Arrays.asList(
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(1, 2)),
                                createTestVCFHeaderWithSAMDictionary(null)
                        ),
                        createTestSAMDictionary(1, 2)
                },
                {
                    // one non-null dictionary, one null, in reverse direction
                        Arrays.asList(
                                createTestVCFHeaderWithSAMDictionary(null),
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(1, 2))
                        ),
                        createTestSAMDictionary(1, 2)
                },
                {
                    // three dictionaries: non-null, null, null
                        Arrays.asList(
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(1, 2)),
                                createTestVCFHeaderWithSAMDictionary(null),
                                createTestVCFHeaderWithSAMDictionary(null)
                        ),
                        createTestSAMDictionary(1, 2)
                },
                {
                    // three dictionaries: null, non-null, null
                        Arrays.asList(
                                createTestVCFHeaderWithSAMDictionary(null),
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(1, 2)),
                                createTestVCFHeaderWithSAMDictionary(null)
                        ),
                        createTestSAMDictionary(1, 2)
                },
                {
                    // three dictionaries: null, null, non-null
                        Arrays.asList(
                                createTestVCFHeaderWithSAMDictionary(null),
                                createTestVCFHeaderWithSAMDictionary(null),
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(1, 2))
                        ),
                        createTestSAMDictionary(1, 2)
                },
                {
                        // three dictionaries: non-null, null, non-null
                        Arrays.asList(
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(1, 2)),
                                createTestVCFHeaderWithSAMDictionary(null),
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(1, 2))
                        ),
                        createTestSAMDictionary(1, 2)
                },
                {
                    // three dictionaries: subset, null, superset
                        Arrays.asList(
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(1, 2)),
                                createTestVCFHeaderWithSAMDictionary(null),
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(1, 10))
                        ),
                        createTestSAMDictionary(1, 10)
                },
                {
                    // all null dictionaries
                        Arrays.asList(
                                createTestVCFHeaderWithSAMDictionary(null),
                                createTestVCFHeaderWithSAMDictionary(null)
                        ),
                        null
                }
        };
    }

    @Test(dataProvider = "dictionaryMergePositive")
    private void testDictionaryMergePositive(
            final List<VCFHeader> sourceHeaders, final SAMSequenceDictionary expectedDictionary) {
        final Set<VCFHeaderLine> mergedHeaderLines = VCFHeaderMerger.getMergedHeaderLines(sourceHeaders, false);
        final VCFHeader mergedHeader = new VCFHeader(mergedHeaderLines);
        Assert.assertEquals(mergedHeader.getSequenceDictionary(), expectedDictionary);
    }

    @DataProvider(name="dictionaryMergeNegative")
    private Object[][] getDictionaryMergeNegative() {
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
                                createTestVCFHeaderWithSAMDictionary(createDictionaryWithLengths(100)),
                                createTestVCFHeaderWithSAMDictionary(createDictionaryWithLengths(200)))
                },
                {
                    // SequenceDictionaryCompatibility.NON_CANONICAL_HUMAN_ORDER human reference detected but the order of the contigs is non-standard (lexicographic, for example)
                        Arrays.asList(
                                createTestVCFHeaderWithSAMDictionary(createDictionaryInCanonicalHumanOrder()),
                                createTestVCFHeaderWithSAMDictionary(createDictionaryInNonCanonicalHumanOrder()))
                },
                {
                    // three mutually disjoint dictionaries, no superset
                        Arrays.asList(
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(5, 2)),
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(4, 2)),
                                createTestVCFHeaderWithSAMDictionary(createTestSAMDictionary(6, 2))
                        )
                },
        };
    }

    @Test(dataProvider = "dictionaryMergeNegative", expectedExceptions = TribbleException.class)
    private void testDictionaryMergeNegative(final List<VCFHeader> sourceHeaders) {
        VCFHeaderMerger.getMergedHeaderLines(sourceHeaders, false);
    }

    @Test
    final void testDuplicateNonStructuredKeys() {
        // merge 2 headers, one has "##sample=foo", one has "##sample=bar", both should survive the merge
        final VCFHeaderLine fooLine = new VCFHeaderLine("sample", "foo");
        final Set<VCFHeaderLine> fooLines = VCFHeader.makeHeaderVersionLineSet(VCFHeader.DEFAULT_VCF_VERSION);
        fooLines.add(fooLine);
        final VCFHeader fooHeader = new VCFHeader(fooLines);

        final VCFHeaderLine barLine = new VCFHeaderLine("sample", "bar");
        final Set<VCFHeaderLine> barLines = VCFHeader.makeHeaderVersionLineSet(VCFHeader.DEFAULT_VCF_VERSION);
        barLines.add(barLine);
        final VCFHeader barHeader = new VCFHeader(barLines);

        final Set<VCFHeaderLine> mergedLines = VCFHeaderMerger.getMergedHeaderLines(Arrays.asList(fooHeader, barHeader), false);
        Assert.assertEquals(mergedLines.size(), 3);
        Assert.assertTrue(mergedLines.contains(fooLine));
        Assert.assertTrue(mergedLines.contains(barLine));
    }

    @DataProvider(name = "compatibleInfoLines")
    public Object[][] getMergerData() {
        return new Object[][]{
                // 2 lines to merge, expected result
                {
                    // mixed number, promote to "."
                        new VCFInfoHeaderLine("INFO=<ID=AB,Number=1,Type=Float,Description=\"Allele Balance for hets (ref/(ref+alt))\">",
                                VCFHeader.DEFAULT_VCF_VERSION),
                        new VCFInfoHeaderLine("INFO=<ID=AB,Number=A,Type=Float,Description=\"Allele Balance for hets (ref/(ref+alt))\">",
                                VCFHeader.DEFAULT_VCF_VERSION),
                        new VCFInfoHeaderLine("INFO=<ID=AB,Number=.,Type=Float,Description=\"Allele Balance for hets (ref/(ref+alt))\">",
                                VCFHeader.DEFAULT_VCF_VERSION),
                        "AB"
                },
                {
                    // mixed number type, promote to float
                        new VCFInfoHeaderLine("INFO=<ID=AB,Number=1,Type=Float,Description=\"Allele Balance for hets (ref/(ref+alt))\">",
                                VCFHeader.DEFAULT_VCF_VERSION),
                        new VCFInfoHeaderLine("INFO=<ID=AB,Number=1,Type=Integer,Description=\"Allele Balance for hets (ref/(ref+alt))\">",
                                VCFHeader.DEFAULT_VCF_VERSION),
                        new VCFInfoHeaderLine("INFO=<ID=AB,Number=1,Type=Float,Description=\"Allele Balance for hets (ref/(ref+alt))\">",
                                VCFHeader.DEFAULT_VCF_VERSION),
                        "AB"
                },
                {
                        // mixed number type in reverse direction, promote to float
                        new VCFInfoHeaderLine("INFO=<ID=AB,Number=1,Type=Integer,Description=\"Allele Balance for hets (ref/(ref+alt))\">",
                                VCFHeader.DEFAULT_VCF_VERSION),
                        new VCFInfoHeaderLine("INFO=<ID=AB,Number=1,Type=Float,Description=\"Allele Balance for hets (ref/(ref+alt))\">",
                                VCFHeader.DEFAULT_VCF_VERSION),
                        new VCFInfoHeaderLine("INFO=<ID=AB,Number=1,Type=Float,Description=\"Allele Balance for hets (ref/(ref+alt))\">",
                                VCFHeader.DEFAULT_VCF_VERSION),
                        "AB"
                },
        };
    }

    @Test(dataProvider = "compatibleInfoLines")
    public void testMergeCompatibleInfoLines(final VCFInfoHeaderLine line1, final VCFInfoHeaderLine line2, final VCFInfoHeaderLine expectedLine, final String id) {
        final VCFHeader hdr1 = new VCFHeader(VCFHeader.makeHeaderVersionLineSet(VCFHeader.DEFAULT_VCF_VERSION), Collections.EMPTY_SET);
        hdr1.addMetaDataLine(line1);

        final VCFHeader hdr2 = new VCFHeader(VCFHeader.makeHeaderVersionLineSet(VCFHeader.DEFAULT_VCF_VERSION), Collections.EMPTY_SET);
        hdr2.addMetaDataLine(line2);

        final VCFHeader mergedHeader = new VCFHeader(VCFHeaderMerger.getMergedHeaderLines(Arrays.asList(hdr1, hdr2), true));
        Assert.assertEquals(mergedHeader.getInfoHeaderLine(id), expectedLine);
    }

    @DataProvider(name = "mergeIncompatibleInfoLines")
    public Object[][] getMergeIncompatibleInfoLines() {
        return new Object[][]{
                // 2 lines to merge, expected result
                {
                        // mixed number AND number type (multiple different attributes)
                        new VCFInfoHeaderLine("INFO=<ID=AB,Number=A,Type=Float,Description=\"Allele Balance for hets (ref/(ref+alt))\">",
                                VCFHeader.DEFAULT_VCF_VERSION),
                        new VCFInfoHeaderLine("INFO=<ID=AB,Number=1,Type=Integer,Description=\"Allele Balance for hets (ref/(ref+alt))\">",
                                VCFHeader.DEFAULT_VCF_VERSION),
                        new VCFInfoHeaderLine("INFO=<ID=AB,Number=.,Type=Float,Description=\"Allele Balance for hets (ref/(ref+alt))\">",
                                VCFHeader.DEFAULT_VCF_VERSION),
                        "AB"
                },
                {
                        // mixed number AND number type  (multiple different attributes), reverse direction
                        new VCFInfoHeaderLine("INFO=<ID=AB,Number=1,Type=Integer,Description=\"Allele Balance for hets (ref/(ref+alt))\">",
                                VCFHeader.DEFAULT_VCF_VERSION),
                        new VCFInfoHeaderLine("INFO=<ID=AB,Number=A,Type=Float,Description=\"Allele Balance for hets (ref/(ref+alt))\">",
                                VCFHeader.DEFAULT_VCF_VERSION),
                        new VCFInfoHeaderLine("INFO=<ID=AB,Number=.,Type=Float,Description=\"Allele Balance for hets (ref/(ref+alt))\">",
                                VCFHeader.DEFAULT_VCF_VERSION),
                        "AB"
                },
        };
    }

    @Test(dataProvider = "mergeIncompatibleInfoLines", expectedExceptions=TribbleException.class)
    public void testMergeIncompatibleInfoLines(final VCFInfoHeaderLine line1, final VCFInfoHeaderLine line2, final VCFInfoHeaderLine expectedLine, final String id) {
        final VCFHeader hdr1 = new VCFHeader(VCFHeader.makeHeaderVersionLineSet(VCFHeader.DEFAULT_VCF_VERSION), Collections.EMPTY_SET);
        hdr1.addMetaDataLine(line1);
        final VCFHeader hdr2 = new VCFHeader(VCFHeader.makeHeaderVersionLineSet(VCFHeader.DEFAULT_VCF_VERSION), Collections.EMPTY_SET);
        hdr2.addMetaDataLine(line2);
        new VCFHeader(VCFHeaderMerger.getMergedHeaderLines(Arrays.asList(hdr1, hdr2), true));
    }

    private final SAMSequenceDictionary createTestSAMDictionary(final int startSequence, final int numSequences) {
        final SAMSequenceDictionary samDictionary = new SAMSequenceDictionary();
        IntStream.range(startSequence, startSequence + numSequences).forEachOrdered(
                i -> samDictionary.addSequence(new SAMSequenceRecord(Integer.toString(i), i)));
        return samDictionary;
    }

    private final VCFHeader createTestVCFHeaderWithSAMDictionary(final SAMSequenceDictionary samDictionary) {
        final VCFHeader vcfHeader = createTestVCFHeader();
        vcfHeader.setSequenceDictionary(samDictionary);
        return vcfHeader;
    }

    private SAMSequenceDictionary createDictionaryInNonCanonicalHumanOrder() {
        final List<SAMSequenceRecord> sequences = new ArrayList<>();
        sequences.add(new SAMSequenceRecord("1", 100));
        sequences.add(new SAMSequenceRecord("10", 100));
        sequences.add(new SAMSequenceRecord("2", 100));
        return new SAMSequenceDictionary(sequences);
    }

    private SAMSequenceDictionary createDictionaryInCanonicalHumanOrder() {
        final List<SAMSequenceRecord> sequences = new ArrayList<>();
        sequences.add(new SAMSequenceRecord("1", 100));
        sequences.add(new SAMSequenceRecord("2", 100));
        sequences.add(new SAMSequenceRecord("10", 100));
        return new SAMSequenceDictionary(sequences);
    }

    private SAMSequenceDictionary createDictionaryWithLengths(final int length) {
        final List<SAMSequenceRecord> sequences = new ArrayList<>();
        sequences.add(new SAMSequenceRecord("1", length));
        sequences.add(new SAMSequenceRecord("2", length));
        sequences.add(new SAMSequenceRecord("3", length));
        return new SAMSequenceDictionary(sequences);
    }

    private SAMSequenceDictionary createReverseDictionary(final SAMSequenceDictionary forwardDictionary){
        // its not sufficient to reuse the existing sequences by just reordering them, since
        // SAMSequenceDictionary *mutates* the sequence indices to match the input order. So we need
        // to create the new sequence dictionary using entirely new sequence records, and let
        // SAMSequenceDictionary assign them indices that match the input order.
        final List<SAMSequenceRecord> reverseSequences = new ArrayList<>(forwardDictionary.getSequences());
        Collections.reverse(reverseSequences);
        final SAMSequenceDictionary reverseDictionary = new SAMSequenceDictionary();

        int count = 0;
        for (final SAMSequenceRecord samSequenceRecord : reverseSequences) {
            final SAMSequenceRecord newSequenceRecord = new SAMSequenceRecord(
                    samSequenceRecord.getSequenceName(),
                    samSequenceRecord.getSequenceLength());
            reverseDictionary.addSequence(newSequenceRecord);
            Assert.assertEquals(newSequenceRecord.getSequenceIndex(), count);
            count++;
        }
        return reverseDictionary;
    }

    private final VCFHeader createTestVCFHeader() {
        return new VCFHeader(VCFHeader.makeHeaderVersionLineSet(VCFHeader.DEFAULT_VCF_VERSION));
    }

}
