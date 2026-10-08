package htsjdk.samtools.cram.ref;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.SequenceUtil;
import htsjdk.testutil.http.LocalHttpServer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.testng.Assert;
import org.testng.annotations.Test;

public class EnaRefServiceTest extends HtsjdkTest {

    /** A service on {@code server} laid out as the ENA registry is, with sequences at {@code /ena/cram/md5/<md5>}. */
    private static EnaRefService serviceOn(final LocalHttpServer server) {
        return new EnaRefService(server.url("/ena/cram/md5/").toString() + "%s");
    }

    @Test
    public void getSequenceReturnsTheBasesTheServiceHasForTheMd5() throws IOException {
        final byte[] bases = "ACGTNACGTTGCA".getBytes(StandardCharsets.US_ASCII);
        final String md5 = SequenceUtil.calculateMD5String(bases);
        try (LocalHttpServer server = new LocalHttpServer().addFile("/ena/cram/md5/" + md5, bases)) {
            Assert.assertEquals(serviceOn(server).getSequence(md5), bases);
        }
    }

    @Test
    public void getSequenceReturnsNullForAnMd5TheServiceDoesNotHave() throws IOException {
        final byte[] bases = "ACGTNACGTTGCA".getBytes(StandardCharsets.US_ASCII);
        try (LocalHttpServer server =
                new LocalHttpServer().addFile("/ena/cram/md5/" + SequenceUtil.calculateMD5String(bases), bases)) {
            Assert.assertNull(serviceOn(server).getSequence("0123456789abcdef0123456789abcdef"));
        }
    }
}
