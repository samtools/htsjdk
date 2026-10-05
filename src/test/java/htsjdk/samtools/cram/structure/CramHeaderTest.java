package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.common.CramVersions;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.testng.Assert;
import org.testng.annotations.Test;

public class CramHeaderTest extends HtsjdkTest {

    @Test
    public void anIdIsWrittenAsUtf8AndPaddedWithZeros() {
        final byte[] expected = Arrays.copyOf("é.cram".getBytes(StandardCharsets.UTF_8), CramHeader.CRAM_ID_LENGTH);
        Assert.assertEquals(new CramHeader(CramVersions.CRAM_v3, "é.cram").getId(), expected);
    }

    @Test
    public void anIdLongerThanTheFieldIsTruncatedByBytesNotCharacters() {
        final String id = "é".repeat(15); // 30 bytes in UTF-8
        final byte[] expected = Arrays.copyOf(id.getBytes(StandardCharsets.UTF_8), CramHeader.CRAM_ID_LENGTH);
        Assert.assertEquals(new CramHeader(CramVersions.CRAM_v3, id).getId(), expected);
    }
}
