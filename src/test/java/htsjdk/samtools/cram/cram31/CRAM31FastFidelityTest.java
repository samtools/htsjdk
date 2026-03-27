package htsjdk.samtools.cram.cram31;

import htsjdk.io.IOPath;
import org.testng.annotations.Test;

import java.io.IOException;

public class CRAM31FastFidelityTest extends CRAM31FidelityTestBase {
    @Override
    protected String getProfile() { return "fast"; }

    @Test(dataProvider = "inputs", groups = "samtools")
    public void testFastFidelity(final IOPath input, final IOPath reference) throws IOException {
        testCRAM31Fidelity(input, reference);
    }
}
