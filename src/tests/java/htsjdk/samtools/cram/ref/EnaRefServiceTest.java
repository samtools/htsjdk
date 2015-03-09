package htsjdk.samtools.cram.ref;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;

public class EnaRefServiceTest {

    @Test
    public void test() throws IOException, EnaRefService.GaveUpException {
        Assert.assertNotNull(new EnaRefService().getSequence("57151e6196306db5d9f33133572a5482"));
        Assert.assertNotNull(new EnaRefService().getSequence("0000088cbcebe818eb431d58c908c698"));
    }
}
