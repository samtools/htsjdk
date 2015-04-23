package htsjdk.samtools.cram.ref;

import htsjdk.samtools.util.Log;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;

/**
 * Created by vadim on 23/04/2015.
 */
public class ENA_RefServiceTest {

    @Test
    public void test() throws IOException, ENA_RefService.GaveUpException {
        Assert.assertNotNull(new ENA_RefService().getSequence("57151e6196306db5d9f33133572a5482"));
        Assert.assertNotNull(new ENA_RefService().getSequence("0000088cbcebe818eb431d58c908c698"));
    }
}
