package htsjdk.samtools.cram.io;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public class CramIntArrayTest extends HtsjdkTest {

    @Test(dataProvider = "testInt32Arrays", dataProviderClass = IOTestCases.class)
    public void runTest(List<Integer> ints) throws IOException {

        int[] inputArray = ints.stream().mapToInt(Integer::intValue).toArray();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CramIntArray.write(inputArray, baos);

            try (ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray())) {
                int[] outputArray = CramIntArray.array(bais);
                Assert.assertEquals(inputArray, outputArray, "Arrays did not match");
            }
        }
    }
}
