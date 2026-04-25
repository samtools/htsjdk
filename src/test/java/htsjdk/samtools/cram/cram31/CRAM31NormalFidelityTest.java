package htsjdk.samtools.cram.cram31;

import htsjdk.io.IOPath;
import org.testng.annotations.Test;

import java.io.IOException;

public class CRAM31NormalFidelityTest extends CRAM31FidelityTestBase {
    @Override
    protected String getProfile() { return "normal"; }

    @Test(dataProvider = "inputs", groups = "samtools")
    public void testNormalFidelity(final IOPath input, final IOPath reference) throws IOException {
        testCRAM31Fidelity(input, reference);
    }
}
