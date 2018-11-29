package htsjdk.samtools.cram.io;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public class CramIntArrayTest extends HtsjdkTest {

    @Test(dataProvider = "testInt32Lists", dataProviderClass = IOTestCases.class)
    public void runTest(final List<Integer> ints) throws IOException {
        byte[] written;
        final int[] inputArray = ints.stream().mapToInt(Integer::intValue).toArray();
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CramIntArray.write(inputArray, baos);
            baos.flush();
            written = baos.toByteArray();
        }

        try (final ByteArrayInputStream bais = new ByteArrayInputStream(written)) {
            final int[] outputArray = CramIntArray.array(bais);
            Assert.assertEquals(inputArray, outputArray, "Arrays did not match");
        }
    }
}
