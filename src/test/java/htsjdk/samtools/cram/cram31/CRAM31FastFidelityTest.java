package htsjdk.samtools.cram.cram31;

import htsjdk.io.IOPath;
import java.io.IOException;
import org.testng.annotations.Test;

public class CRAM31FastFidelityTest extends CRAM31FidelityTestBase {
    @Override
    protected String getProfile() {
        return "fast";
    }

    @Test(dataProvider = "inputs", groups = "samtools")
    public void testFastFidelity(final IOPath input, final IOPath reference) throws IOException {
        testCRAM31Fidelity(input, reference);
    }
}
