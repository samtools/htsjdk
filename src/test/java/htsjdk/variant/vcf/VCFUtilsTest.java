package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
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
}
