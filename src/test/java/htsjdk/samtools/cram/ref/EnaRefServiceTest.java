package htsjdk.samtools.cram.ref;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;

//calling this an FTP test so that it could fail in travis
@Test(groups="ftp")
public class EnaRefServiceTest extends HtsjdkTest {

    @DataProvider(name="testEnaRefServiceData")
    public Object[][] testEnaRefServiceData(){
        return new Object[][]{{"57151e6196306db5d9f33133572a5482"},
                {"0000088cbcebe818eb431d58c908c698"}};
    }

    @Test(dataProvider = "testEnaRefServiceData")
    public void testEnaRefServiceData(final String md5) throws IOException, GaveUpException {
        Assert.assertNotNull(new EnaRefService().getSequence(md5));
    }
}
