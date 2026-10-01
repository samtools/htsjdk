package htsjdk.samtools.cram.compression.nametokenisation;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.compression.CompressionUtils;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class NameTokenisationTest extends HtsjdkTest {
    public static final CharSequence LOCAL_NAME_SEPARATOR_CHARSEQUENCE =
            new String(new byte[] {NameTokenisationDecode.NAME_SEPARATOR});

    private static class TestDataEnvelope {
        public final byte[] testArray;
        public final boolean useArith;

        public TestDataEnvelope(final byte[] testdata, final boolean useArith) {
            this.testArray = testdata;
            this.useArith = useArith;
        }

        public String toString() {
            return String.format("Array of size %d/%b", testArray.length, useArith);
        }
    }

    @DataProvider(name = "nameTokenisation")
    public Object[][] getNameTokenisationTestData() {

        final List<String> readNamesTestCases = new ArrayList<>();

        // a subset of read names from
        // src/test/resources/htsjdk/samtools/cram/CEUTrio.HiSeq.WGS.b37.NA12878.20.first.8000.bam
        readNamesTestCases.add("20FUKAAXX100202:6:27:4968:125377" + LOCAL_NAME_SEPARATOR_CHARSEQUENCE
                + "20FUKAAXX100202:6:27:4986:125375"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "20FUKAAXX100202:5:62:8987:1929"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "20GAVAAXX100126:1:28:4295:139802"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "20FUKAAXX100202:4:23:8516:117251"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "20FUKAAXX100202:6:23:6442:37469"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "20FUKAAXX100202:8:24:10477:24196"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "20GAVAAXX100126:8:63:5797:158250"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "20FUKAAXX100202:1:45:12798:104365"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "20GAVAAXX100126:3:23:6419:199245"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "20FUKAAXX100202:8:48:6663:137967"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "20FUKAAXX100202:6:68:17726:162601"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE);

        // a subset of read names from
        // src/test/resources/htsjdk/samtools/longreads/NA12878.m64020_190210_035026.chr21.5011316.5411316.unmapped.bam
        readNamesTestCases.add("m64020_190210_035026/44368402/ccs" + LOCAL_NAME_SEPARATOR_CHARSEQUENCE);

        readNamesTestCases.add("m64020_190210_035026/44368402/ccs" + LOCAL_NAME_SEPARATOR_CHARSEQUENCE
                + "m64020_190210_035026/124127126/ccs"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "m64020_190210_035026/4981311/ccs"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "m64020_190210_035026/80022195/ccs"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "m64020_190210_035026/17762104/ccs"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "m64020_190210_035026/62981096/ccs"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "m64020_190210_035026/86968803/ccs"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "m64020_190210_035026/46400955/ccs"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "m64020_190210_035026/137561592/cc0"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "m64020_190210_035026/52233471/ccs"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "m64020_190210_035026/97127189/ccs"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "m64020_190210_035026/115278035/ccs"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "m64020_190210_035026/155256324/ccs"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "m64020_190210_035026/163644151/ccs"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "m64020_190210_035026/162728365/ccs"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "m64020_190210_035026/160238116/ccs"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "m64020_190210_035026/147719983/ccs"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "m64020_190210_035026/60883331/ccs"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "m64020_190210_035026/1116165/ccs"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "m64020_190210_035026/75893199/ccs"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE);

        // source: https://gatk.broadinstitute.org/hc/en-us/articles/360035890671-Read-groups
        readNamesTestCases.add("H0164ALXX140820:2:1101:10003:23460" + LOCAL_NAME_SEPARATOR_CHARSEQUENCE
                + "H0164ALXX140820:2:1101:15118:25288" + LOCAL_NAME_SEPARATOR_CHARSEQUENCE);

        readNamesTestCases.add("H0164ALXX140820:2:1101:10003:23460" + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "1"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "H0164ALXX140820:3:1101:10003:23460"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "1" + LOCAL_NAME_SEPARATOR_CHARSEQUENCE
                + "H0164ALXX140820:27:1101:10003:23460"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "1" + LOCAL_NAME_SEPARATOR_CHARSEQUENCE);

        // duplicate names
        readNamesTestCases.add("H0164ALXX140820:2:1101:10003:23460" + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "1"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "H0164ALXX140820:2:1101:10003:23460"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "1" + LOCAL_NAME_SEPARATOR_CHARSEQUENCE);

        // a few degenerate test cases; these are unlikely to be seen in practice, but they exist
        // in test data and need to be handled
        readNamesTestCases.add("H0164ALXX140820:2:1101:10003:23460" + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "1"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE);

        readNamesTestCases.add("H0164ALXX140820:2:1101:10003:23460" + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "1"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "2"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE);
        readNamesTestCases.add("1" + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "H0164ALXX140820:2:1101:10003:23460"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE);

        readNamesTestCases.add(
                "A read name" + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "1" + LOCAL_NAME_SEPARATOR_CHARSEQUENCE);

        readNamesTestCases.add(
                "1" + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "A read name" + LOCAL_NAME_SEPARATOR_CHARSEQUENCE);

        readNamesTestCases.add("1" + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "A read name"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "2"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE);

        readNamesTestCases.add("H0164ALXX140820:2:1101:10003:23460" + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "1"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "H0164ALXX140820:2:1101:15118:25288"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE);

        readNamesTestCases.add("H0164ALXX140820:2:1101:10003:23460" + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "1"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "A read name"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE);

        readNamesTestCases.add("H0164ALXX140820:2:1101:10003:23460" + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "1"
                + LOCAL_NAME_SEPARATOR_CHARSEQUENCE + "A read name" + LOCAL_NAME_SEPARATOR_CHARSEQUENCE);

        // IonTorrent, ONT and PacBio CLR names, each a format whose prefix the encoder treats as fixed
        readNamesTestCases.add(joinNames(List.of("ZBP6J:02796:01418", "K3NWE:00012:00345", "ZBP6J:02797:01420")));
        readNamesTestCases.add(joinNames(List.of(
                "f33d30d5-6eb8-4115-8f46-154c2620a5da_Basecall_1D_template",
                "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d_Basecall_1D_template")));
        readNamesTestCases.add(joinNames(List.of(
                "m140905_042212_sidney_c100564852550000001823085912221377_s1_X0/1234/0_5000",
                "m140905_042212_sidney_c100564852550000001823085912221377_s1_X0/1235/0_7000",
                "m140905_042212_sidney_c100564852550000001823085912221377_s1_X0/1234/0_5000")));

        // two Illumina flowcells interleaved, with mates repeating earlier names
        readNamesTestCases.add(joinNames(List.of(
                "A00217:60:HCVVJDSXX:2:2558:1108:18537",
                "A00404:52:HG25YDSXX:4:2678:14516:34115",
                "A00217:60:HCVVJDSXX:2:2558:1108:18537",
                "A00217:60:HCVVJDSXX:2:2558:1120:18540",
                "A00404:52:HG25YDSXX:4:2678:14516:34115",
                "A00404:52:HG25YDSXX:4:2678:14530:34120")));

        final List<Object[]> testCases = new ArrayList<>();
        for (final String readName : readNamesTestCases) {
            for (boolean useArith : Arrays.asList(true, false)) {
                testCases.add(new Object[] {new TestDataEnvelope(readName.getBytes(), useArith)});
            }
        }
        return testCases.toArray(new Object[][] {});
    }

    @Test(dataProvider = "nameTokenisation")
    public void testRoundTrip(final TestDataEnvelope td) {
        final NameTokenisationEncode nameTokenisationEncode = new NameTokenisationEncode();
        final NameTokenisationDecode nameTokenisationDecode = new NameTokenisationDecode();

        final ByteBuffer uncompressedBuffer = ByteBuffer.wrap(td.testArray);
        final ByteBuffer compressedBuffer =
                nameTokenisationEncode.compress(uncompressedBuffer, td.useArith, NameTokenisationDecode.NAME_SEPARATOR);
        final ByteBuffer decompressedNames = CompressionUtils.wrap(
                nameTokenisationDecode.uncompress(compressedBuffer, NameTokenisationDecode.NAME_SEPARATOR));
        uncompressedBuffer.rewind();
        Assert.assertEquals(decompressedNames, uncompressedBuffer);
    }

    private static String joinNames(final List<String> names) {
        final StringBuilder joined = new StringBuilder();
        for (final String name : names) {
            joined.append(name).append(LOCAL_NAME_SEPARATOR_CHARSEQUENCE);
        }
        return joined.toString();
    }

    private static int compressedSize(final List<String> names) {
        return new NameTokenisationEncode()
                .compress(
                        ByteBuffer.wrap(joinNames(names).getBytes(StandardCharsets.ISO_8859_1)),
                        false,
                        NameTokenisationDecode.NAME_SEPARATOR)
                .limit();
    }

    /** Names from one Illumina flowcell lane and tile, each a small step further across the tile than the last. */
    private static List<String> illuminaNames(final String flowcellPrefix, final int count) {
        final List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            names.add(flowcellPrefix + "2:1101:" + (1000 + 7 * i) + ":" + (2000 + 13 * i));
        }
        return names;
    }

    private static List<String> interleaved(final List<String> first, final List<String> second) {
        final List<String> names = new ArrayList<>();
        for (int i = 0; i < first.size(); i++) {
            names.add(first.get(i));
            names.add(second.get(i));
        }
        return names;
    }

    private static List<String> grouped(final List<String> first, final List<String> second) {
        final List<String> names = new ArrayList<>(first);
        names.addAll(second);
        return names;
    }

    @Test
    public void namesInterleavedFromTwoIlluminaFlowcellsCompressAboutAsWellAsGrouped() {
        final List<String> first = illuminaNames("A00217:60:HCVVJDSXX:", 1000);
        final List<String> second = illuminaNames("A00404:52:HG25YDSXX:", 1000);
        Assert.assertTrue(
                compressedSize(interleaved(first, second)) < 1.25 * compressedSize(grouped(first, second)),
                "each name should be encoded against the last name from its own flowcell");
    }

    @Test
    public void namesInterleavedFromTwoIonTorrentRunsCompressAboutAsWellAsGrouped() {
        final List<String> first = new ArrayList<>();
        final List<String> second = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            first.add(String.format("ZBP6J:%05d:%05d", 100 + i / 50, 200 + 3 * (i % 50)));
            second.add(String.format("K3NWE:%05d:%05d", 100 + i / 50, 200 + 3 * (i % 50)));
        }
        Assert.assertTrue(
                compressedSize(interleaved(first, second)) < 1.25 * compressedSize(grouped(first, second)),
                "each name should be encoded against the last name from its own run");
    }

    @Test
    public void namesFollowingTheirMatesCompressAboutAsWellAsWithoutThem() {
        final List<String> names = illuminaNames("A00217:60:HCVVJDSXX:", 2000);
        // each name is repeated, as its mate's, after the next name: n0 n1 n0 n2 n1 n3 n2 ...
        final List<String> withMates = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            withMates.add(names.get(i));
            if (i > 0) withMates.add(names.get(i - 1));
        }
        Assert.assertTrue(
                compressedSize(withMates) < 2 * compressedSize(names),
                "a name after a repeated one should be encoded against the repeated name's tokens");
    }

    /** Two names ending in 0xE9 ("é" in ISO-8859-1) and in 0xC3 0xA9 ("é" in UTF-8). */
    private static byte[] namesWithNonAsciiBytes() {
        final byte sep = NameTokenisationDecode.NAME_SEPARATOR;
        return new byte[] {'r', 'e', 'a', 'd', (byte) 0xE9, sep, 'r', 'e', 'a', 'd', (byte) 0xC3, (byte) 0xA9, sep};
    }

    private static void assertRoundTripsUnchanged(final byte[] names, final boolean useArith) {
        final ByteBuffer compressed = new NameTokenisationEncode()
                .compress(ByteBuffer.wrap(names), useArith, NameTokenisationDecode.NAME_SEPARATOR);
        final ByteBuffer decompressed = CompressionUtils.wrap(
                new NameTokenisationDecode().uncompress(compressed, NameTokenisationDecode.NAME_SEPARATOR));
        Assert.assertEquals(decompressed, ByteBuffer.wrap(names));
    }

    @Test
    public void testNonAsciiBytesRoundTripUnchangedWithRans() {
        assertRoundTripsUnchanged(namesWithNonAsciiBytes(), false);
    }

    @Test
    public void testNonAsciiBytesRoundTripUnchangedWithArithmeticCoding() {
        assertRoundTripsUnchanged(namesWithNonAsciiBytes(), true);
    }
}
