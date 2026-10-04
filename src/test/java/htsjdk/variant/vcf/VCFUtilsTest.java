package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class VCFUtilsTest extends HtsjdkTest {

    private static VCFHeader headerOfVersion(final String version) {
        return new VCFHeader(VCFHeaderVersion.toHeaderVersion(version), Collections.emptySet(), Collections.emptySet());
    }

    private static VCFHeader merged(final String... versions) {
        final List<VCFHeader> headers = new ArrayList<>();
        for (final String version : versions) {
            headers.add(headerOfVersion(version));
        }
        return new VCFHeader(VCFUtils.smartMergeHeaders(headers, true));
    }

    @Test
    public void mergedHeaderTakesTheHighestVersion() {
        Assert.assertEquals(merged("VCFv4.0", "VCFv4.3").getVCFHeaderVersion(), VCFHeaderVersion.VCF4_3);
        Assert.assertEquals(merged("VCFv4.3", "VCFv4.0").getVCFHeaderVersion(), VCFHeaderVersion.VCF4_3);
        Assert.assertEquals(
                merged("VCFv4.2", "VCFv4.5", "VCFv4.1", "VCFv4.4").getVCFHeaderVersion(), VCFHeaderVersion.VCF4_5);
        Assert.assertEquals(merged("VCFv4.2", "VCFv4.2").getVCFHeaderVersion(), VCFHeaderVersion.VCF4_2);
    }

    @Test
    public void mergedHeaderOfVersionlessHeadersDeclaresNoVersion() {
        final List<VCFHeader> headers = List.of(new VCFHeader(), new VCFHeader());
        Assert.assertNull(new VCFHeader(VCFUtils.smartMergeHeaders(headers, true)).getVCFHeaderVersion());
    }

    @Test
    public void versionlessHeaderDoesNotLowerTheMergedVersion() {
        final List<VCFHeader> headers = List.of(new VCFHeader(), headerOfVersion("VCFv4.3"));
        Assert.assertEquals(
                new VCFHeader(VCFUtils.smartMergeHeaders(headers, true)).getVCFHeaderVersion(),
                VCFHeaderVersion.VCF4_3);
    }

    @Test
    public void mergedLinesCarryExactlyOneVersionLineFirst() {
        final Set<VCFHeaderLine> lines =
                VCFUtils.smartMergeHeaders(List.of(headerOfVersion("VCFv4.1"), headerOfVersion("VCFv4.3")), true);
        final List<VCFHeaderLine> versionLines = lines.stream()
                .filter(line -> VCFHeaderVersion.isFormatString(line.getKey()))
                .collect(Collectors.toList());
        Assert.assertEquals(versionLines.size(), 1);
        Assert.assertEquals(versionLines.get(0).getValue(), "VCFv4.3");
        Assert.assertEquals(lines.iterator().next(), versionLines.get(0));
    }

    @DataProvider(name = "caseIntolerantDoubles")
    public Object[][] getCaseIntolerantDoubles() {
        return new Object[][] {
            {Double.NaN, Arrays.asList("NaN", "nan", "+nan", "-nan")},
            {
                Double.POSITIVE_INFINITY,
                Arrays.asList("+Infinity", "+infinity", "+Inf", "+inf", "Infinity", "infinity", "Inf", "inf")
            },
            {Double.NEGATIVE_INFINITY, Arrays.asList("-Infinity", "-infinity", "-Inf", "-inf")},
            {null, Arrays.asList("znan", "nanz", "zinf", "infz", "hello")},
        };
    }

    @Test(dataProvider = "caseIntolerantDoubles")
    public void testCaseIntolerantDoubles(Double value, final List<String> stringDoubles) {
        stringDoubles.forEach(sd -> {
            try {
                Assert.assertEquals(VCFUtils.parseVcfDouble(sd), value);
            } catch (NumberFormatException e) {
                Assert.assertNull(value);
            }
        });
    }

    @Test
    public void rebuildingAVersionlessHeaderDoesNotChangeWhatAMergeDeclares() {
        final VCFHeader versionless = new VCFHeader(Collections.singleton(new VCFHeaderLine("source", "test")));
        final VCFHeader rebuilt = new VCFHeader(versionless.getMetaDataInInputOrder());
        final VCFHeader v40 = new VCFHeader(VCFHeaderVersion.VCF4_0, Collections.emptySet(), Collections.emptySet());
        Assert.assertEquals(
                new VCFHeader(VCFUtils.smartMergeHeaders(List.of(versionless, v40), false)).getVCFHeaderVersion(),
                VCFHeaderVersion.VCF4_0);
        Assert.assertEquals(
                new VCFHeader(VCFUtils.smartMergeHeaders(List.of(rebuilt, v40), false)).getVCFHeaderVersion(),
                VCFHeaderVersion.VCF4_0);
    }

    // merging same-ID lines that agree on their definition but not on Source, Version or another attribute

    private static VCFHeader headerWithInfoLine(final String attributesAfterTheDefinition) {
        final VCFHeader header = new VCFHeader();
        header.addMetaDataLine(new VCFInfoHeaderLine(
                "<ID=X,Number=1,Type=Integer,Description=\"x\"" + attributesAfterTheDefinition + ">",
                VCFHeaderVersion.VCF4_2));
        return header;
    }

    private static VCFInfoHeaderLine mergedInfoLine(final VCFHeader first, final VCFHeader second) {
        return new VCFHeader(VCFUtils.smartMergeHeaders(List.of(first, second), false)).getInfoHeaderLine("X");
    }

    @Test
    public void theLaterVersionOfTheSameSourceWinsWhicheverHeaderComesFirst() {
        final VCFHeader v138 = headerWithInfoLine(",Source=\"dbsnp\",Version=\"138\"");
        final VCFHeader v151 = headerWithInfoLine(",Source=\"dbsnp\",Version=\"151\"");
        Assert.assertEquals(mergedInfoLine(v138, v151).getVersion(), "151");
        Assert.assertEquals(mergedInfoLine(v151, v138).getVersion(), "151");
    }

    @Test
    public void sourcesAreComparedWithoutRegardToCase() {
        final VCFHeader older = headerWithInfoLine(",Source=\"dbSNP\",Version=\"138\"");
        final VCFHeader newer = headerWithInfoLine(",Source=\"dbsnp\",Version=\"151\"");
        Assert.assertEquals(mergedInfoLine(older, newer).getVersion(), "151");
    }

    @Test
    public void versionsAreComparedAsAPersonReadsThem() {
        Assert.assertEquals(
                mergedInfoLine(headerWithInfoLine(",Version=\"1.9\""), headerWithInfoLine(",Version=\"1.10\""))
                        .getVersion(),
                "1.10");
        Assert.assertEquals(
                mergedInfoLine(headerWithInfoLine(",Version=\"99\""), headerWithInfoLine(",Version=\"151\""))
                        .getVersion(),
                "151");
        Assert.assertEquals(
                mergedInfoLine(headerWithInfoLine(",Version=\"2023-01\""), headerWithInfoLine(",Version=\"2021-03\""))
                        .getVersion(),
                "2023-01");
        Assert.assertEquals(
                mergedInfoLine(headerWithInfoLine(",Version=\"b37\""), headerWithInfoLine(",Version=\"b38\""))
                        .getVersion(),
                "b38");
    }

    @Test
    public void theWinningLineIsTakenWhole() {
        final VCFInfoHeaderLine merged = mergedInfoLine(
                headerWithInfoLine(",Source=\"dbsnp\",Version=\"138\",IDX=1"),
                headerWithInfoLine(",Source=\"dbsnp\",Version=\"151\",Note=\"newer\""));
        Assert.assertEquals(merged.getGenericFieldValue("Note"), "newer");
        Assert.assertNull(merged.getGenericFieldValue("IDX"));
    }

    @Test
    public void versionsOfDifferentSourcesAreNotComparedAndTheFirstLineIsKept() {
        final VCFInfoHeaderLine merged = mergedInfoLine(
                headerWithInfoLine(",Source=\"dbsnp\",Version=\"138\""),
                headerWithInfoLine(",Source=\"clinvar\",Version=\"2023\""));
        Assert.assertEquals(merged.getSource(), "dbsnp");
        Assert.assertEquals(merged.getVersion(), "138");
    }

    @Test
    public void aLineWithoutAVersionIsNotReplaced() {
        final VCFInfoHeaderLine merged =
                mergedInfoLine(headerWithInfoLine(""), headerWithInfoLine(",Source=\"dbsnp\",Version=\"151\""));
        Assert.assertNull(merged.getVersion());
    }

    @Test
    public void linesDifferingOnlyInAnotherAttributeKeepTheFirst() {
        final VCFInfoHeaderLine merged = mergedInfoLine(headerWithInfoLine(",IDX=1"), headerWithInfoLine(",IDX=2"));
        Assert.assertEquals(merged.getGenericFieldValue("IDX"), "1");
    }

    @Test
    public void aLaterVersionDoesNotOverrideADifferentDefinition() {
        // Number differs, so the existing rule applies (Number becomes unbounded on the line already there)
        final VCFHeader first = headerWithInfoLine(",Source=\"dbsnp\",Version=\"138\"");
        final VCFHeader second = new VCFHeader();
        second.addMetaDataLine(new VCFInfoHeaderLine(
                "<ID=X,Number=2,Type=Integer,Description=\"x\",Source=\"dbsnp\",Version=\"151\">",
                VCFHeaderVersion.VCF4_2));
        final VCFInfoHeaderLine merged = mergedInfoLine(first, second);
        Assert.assertEquals(merged.getCountType(), VCFHeaderLineCount.UNBOUNDED);
        Assert.assertEquals(merged.getVersion(), "138");
    }

    @Test
    public void theWinningLineStaysWhereTheFirstWas() {
        final VCFHeader first = headerWithInfoLine(",Source=\"dbsnp\",Version=\"138\"");
        first.addMetaDataLine(new VCFInfoHeaderLine("Y", 1, VCFHeaderLineType.Integer, "y"));
        final VCFHeader second = headerWithInfoLine(",Source=\"dbsnp\",Version=\"151\"");
        final List<String> ids = new ArrayList<>();
        for (final VCFHeaderLine line : VCFUtils.smartMergeHeaders(List.of(first, second), false)) {
            if (line instanceof VCFInfoHeaderLine) {
                ids.add(((VCFInfoHeaderLine) line).getID());
            }
        }
        Assert.assertEquals(ids, List.of("X", "Y"));
    }

    // merging an Integer and a Float definition of the same field

    private static VCFHeader headerWithInfoLineOfType(final VCFHeaderLineType type) {
        final VCFHeader header = new VCFHeader();
        header.addMetaDataLine(new VCFInfoHeaderLine("X", 1, type, "x"));
        return header;
    }

    @Test
    public void aFloatDefinitionMetFirstSurvivesAnIntegerOne() {
        final VCFHeader merged = new VCFHeader(VCFUtils.smartMergeHeaders(
                List.of(
                        headerWithInfoLineOfType(VCFHeaderLineType.Float),
                        headerWithInfoLineOfType(VCFHeaderLineType.Integer)),
                false));
        Assert.assertEquals(merged.getInfoHeaderLine("X").getType(), VCFHeaderLineType.Float);
    }

    @Test
    public void anIntegerDefinitionMetFirstIsPromotedToFloat() {
        final VCFHeader merged = new VCFHeader(VCFUtils.smartMergeHeaders(
                List.of(
                        headerWithInfoLineOfType(VCFHeaderLineType.Integer),
                        headerWithInfoLineOfType(VCFHeaderLineType.Float)),
                false));
        Assert.assertEquals(merged.getInfoHeaderLine("X").getType(), VCFHeaderLineType.Float);
    }

    @Test
    public void makeContigHeaderLinesCarryMd5UrlAndSpecies() {
        final SAMSequenceRecord record = new SAMSequenceRecord("chr1", 248956422);
        record.setMd5("6aef897c3d6ff0c78aff06ac189178dd");
        record.setAttribute(SAMSequenceRecord.URI_TAG, "https://example.com/hg38.fa");
        record.setSpecies("Homo sapiens");

        // the assembly comes from the reference's file name, which is not opened
        final List<VCFContigHeaderLine> lines =
                VCFUtils.makeContigHeaderLines(new SAMSequenceDictionary(List.of(record)), Path.of("hg38.fa"));

        Assert.assertEquals(lines.size(), 1);
        Assert.assertEquals(
                lines.get(0).toString(),
                "contig=<ID=chr1,length=248956422,assembly=hg38,md5=6aef897c3d6ff0c78aff06ac189178dd,"
                        + "species=\"Homo sapiens\",URL=https://example.com/hg38.fa>");
        Assert.assertEquals(lines.get(0).getContigIndex(), Integer.valueOf(0));
    }

    /** A header whose dictionary is the given contigs, in order, each 1000 bases long. */
    private static VCFHeader headerWithContigs(final String... contigs) {
        final VCFHeader header = new VCFHeader();
        header.setSequenceDictionary(new SAMSequenceDictionary(Arrays.stream(contigs)
                .map(contig -> new SAMSequenceRecord(contig, 1000))
                .collect(Collectors.toList())));
        return header;
    }

    /** The merged header's contigs as {@code name@index}, in the order the header lists them. */
    private static List<String> mergedContigs(final VCFHeader... headers) {
        final VCFHeader merged = new VCFHeader(VCFUtils.smartMergeHeaders(List.of(headers), false));
        final List<String> contigLines = merged.getContigLines().stream()
                .map(line -> line.getID() + "@" + line.getContigIndex())
                .collect(Collectors.toList());
        final List<String> sortedLines = merged.getMetaDataInSortedOrder().stream()
                .filter(line -> line instanceof VCFContigHeaderLine)
                .map(line -> ((VCFContigHeaderLine) line).getID() + "@" + ((VCFContigHeaderLine) line).getContigIndex())
                .collect(Collectors.toList());
        final List<String> dictionary = merged.getSequenceDictionary().getSequences().stream()
                .map(record -> record.getSequenceName() + "@" + record.getSequenceIndex())
                .collect(Collectors.toList());
        // the lines written out come from the sorted view, so all three must agree
        Assert.assertEquals(sortedLines, contigLines);
        Assert.assertEquals(dictionary, contigLines);
        return contigLines;
    }

    @Test
    public void mergingASubsetDictionaryFirstKeepsEveryContigInTheOrderFirstSeen() {
        Assert.assertEquals(
                mergedContigs(headerWithContigs("chr3", "chr4"), headerWithContigs("chr1", "chr2", "chr3", "chr4")),
                List.of("chr3@0", "chr4@1", "chr1@2", "chr2@3"));
    }

    @Test
    public void mergingASupersetDictionaryFirstKeepsItsOrder() {
        Assert.assertEquals(
                mergedContigs(headerWithContigs("chr1", "chr2", "chr3", "chr4"), headerWithContigs("chr3", "chr4")),
                List.of("chr1@0", "chr2@1", "chr3@2", "chr4@3"));
    }

    @Test
    public void mergingDisjointDictionariesKeepsEveryContig() {
        Assert.assertEquals(
                mergedContigs(headerWithContigs("x", "y"), headerWithContigs("p", "q")),
                List.of("x@0", "y@1", "p@2", "q@3"));
    }

    @Test
    public void aContigRedefinedWithAnotherLengthKeepsTheFirstDefinition() {
        final VCFHeader first = headerWithContigs("chr1", "chr2");
        final VCFHeader second = new VCFHeader();
        second.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr2", 5000))));
        final VCFHeader merged = new VCFHeader(VCFUtils.smartMergeHeaders(List.of(first, second), false));
        Assert.assertEquals(merged.getSequenceDictionary().getSequence("chr2").getSequenceLength(), 1000);
        Assert.assertEquals(merged.getContigLines().size(), 2);
    }

    @Test
    public void renumberedContigsKeepTheirOtherAttributes() {
        final SAMSequenceRecord chr9 = new SAMSequenceRecord("chr9", 1000);
        chr9.setMd5("0123456789abcdef0123456789abcdef");
        chr9.setAssembly("hg38");
        final VCFHeader second = new VCFHeader();
        second.setSequenceDictionary(new SAMSequenceDictionary(List.of(chr9)));
        final VCFHeader merged =
                new VCFHeader(VCFUtils.smartMergeHeaders(List.of(headerWithContigs("chr1"), second), false));
        final SAMSequenceRecord mergedChr9 = merged.getSequenceDictionary().getSequence("chr9");
        Assert.assertEquals(mergedChr9.getSequenceIndex(), 1);
        Assert.assertEquals(mergedChr9.getMd5(), "0123456789abcdef0123456789abcdef");
        Assert.assertEquals(mergedChr9.getAssembly(), "hg38");
    }
}
