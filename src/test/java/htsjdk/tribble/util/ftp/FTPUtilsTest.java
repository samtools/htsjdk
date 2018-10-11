package htsjdk.tribble.util.ftp;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.ftp.FTPUtils;
import java.net.URL;
import org.testng.annotations.Test;

/**
 * @author Jim Robinson
 * @since 10/4/11
 */
public class FTPUtilsTest extends HtsjdkTest {

  @Test(groups = "ftp")
  public void testResourceAvailable() throws Exception {

    URL goodUrl = new URL("ftp://ftp.broadinstitute.org/pub/igv/TEST/test.txt");
    assertTrue(FTPUtils.resourceAvailable(goodUrl));

    URL nonExistentURL = new URL("ftp://ftp.broadinstitute.org/pub/igv/TEST/doesntExist");
    assertFalse(FTPUtils.resourceAvailable(nonExistentURL));

    URL nonExistentServer = new URL("ftp://noSuchServer/pub/igv/TEST/doesntExist");
    assertFalse(FTPUtils.resourceAvailable(nonExistentServer));
  }
}
