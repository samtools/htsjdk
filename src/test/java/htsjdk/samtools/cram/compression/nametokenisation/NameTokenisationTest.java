package htsjdk.samtools.cram.compression.nametokenisation;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class NameTokenisationTest extends HtsjdkTest {

    private static class TestDataEnvelope {
        public final byte[] testArray;
        public TestDataEnvelope(final byte[] testdata) {
            this.testArray = testdata;
        }
        public String toString() {
            return String.format("Array of size %d", testArray.length);
        }
    }

    @DataProvider(name="nameTokenisation")
    public Object[][] getNameTokenisationTestData() {

        List<String> readNamesList = new ArrayList<>();
        readNamesList.add("");

        // a subset of read names from
        // src/test/resources/htsjdk/samtools/cram/CEUTrio.HiSeq.WGS.b37.NA12878.20.first.8000.bam
        readNamesList.add("20FUKAAXX100202:6:27:4968:125377\0" +
                "20FUKAAXX100202:6:27:4986:125375\0" +
                "20FUKAAXX100202:5:62:8987:1929\0" +
                "20GAVAAXX100126:1:28:4295:139802\0" +
                "20FUKAAXX100202:4:23:8516:117251\0" +
                "20FUKAAXX100202:6:23:6442:37469\0" +
                "20FUKAAXX100202:8:24:10477:24196\0" +
                "20GAVAAXX100126:8:63:5797:158250\0" +
                "20FUKAAXX100202:1:45:12798:104365\0" +
                "20GAVAAXX100126:3:23:6419:199245\0" +
                "20FUKAAXX100202:8:48:6663:137967\0" +
                "20FUKAAXX100202:6:68:17726:162601");

        // a subset of read names from
        // src/test/resources/htsjdk/samtools/longreads/NA12878.m64020_190210_035026.chr21.5011316.5411316.unmapped.bam
        readNamesList.add("m64020_190210_035026/44368402/ccs\0");
        readNamesList.add("m64020_190210_035026/44368402/ccs");
        readNamesList.add("m64020_190210_035026/44368402/ccs\0" +
                "m64020_190210_035026/124127126/ccs\0" +
                "m64020_190210_035026/4981311/ccs\0" +
                "m64020_190210_035026/80022195/ccs\0" +
                "m64020_190210_035026/17762104/ccs\0" +
                "m64020_190210_035026/62981096/ccs\0" +
                "m64020_190210_035026/86968803/ccs\0" +
                "m64020_190210_035026/46400955/ccs\0" +
                "m64020_190210_035026/137561592/ccs\0" +
                "m64020_190210_035026/52233471/ccs\0" +
                "m64020_190210_035026/97127189/ccs\0" +
                "m64020_190210_035026/115278035/ccs\0" +
                "m64020_190210_035026/155256324/ccs\0" +
                "m64020_190210_035026/163644151/ccs\0" +
                "m64020_190210_035026/162728365/ccs\0" +
                "m64020_190210_035026/160238116/ccs\0" +
                "m64020_190210_035026/147719983/ccs\0" +
                "m64020_190210_035026/60883331/ccs\0" +
                "m64020_190210_035026/1116165/ccs\0" +
                "m64020_190210_035026/75893199/ccs");

        // source: https://gatk.broadinstitute.org/hc/en-us/articles/360035890671-Read-groups
        readNamesList.add(
                "H0164ALXX140820:2:1101:10003:23460\0" +
                "H0164ALXX140820:2:1101:15118:25288");

        final List<Object[]> testCases = new ArrayList<>();
        for (String readName : readNamesList) {
            Object[] objects = new Object[]{
                    new NameTokenisationEncode(),
                    new NameTokenisationDecode(),
                    new TestDataEnvelope(readName.getBytes())};
            testCases.add(objects);
        }
        return testCases.toArray(new Object[][]{});
    }

    @Test(dataProvider = "nameTokenisation")
    public void testRoundTrip(
            final NameTokenisationEncode nameTokenisationEncode,
            final NameTokenisationDecode nameTokenisationDecode,
            final TestDataEnvelope td) {
        ByteBuffer uncompressedBuffer =  ByteBuffer.wrap(td.testArray);
        ByteBuffer compressedBuffer = nameTokenisationEncode.compress(uncompressedBuffer, 0);
        String decompressedNames = nameTokenisationDecode.uncompress(compressedBuffer);
        ByteBuffer decompressedNamesBuffer = StandardCharsets.UTF_8.encode(decompressedNames);
        uncompressedBuffer.rewind();
        Assert.assertEquals(decompressedNamesBuffer, uncompressedBuffer);
    }

}