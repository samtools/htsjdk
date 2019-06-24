package htsjdk.samtools.cram.structure;

import com.google.gson.Gson;
import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

public class CRAMEncodingStrategyTest extends HtsjdkTest {

    @Test
    public void testRoundTripThroughPath() throws IOException {
        final File tempFile = File.createTempFile("encodingStrategyTest", ".json");
        tempFile.deleteOnExit();

        final CRAMEncodingStrategy cramEncodingStrategy = new CRAMEncodingStrategy();
        cramEncodingStrategy.writeToPath(tempFile.toPath());
        final CRAMEncodingStrategy roundTripEncodingStrategy = CRAMEncodingStrategy.readFromPath(tempFile.toPath());

        final Gson gson = new Gson();

        final String originalEncodingString = gson.toJson(cramEncodingStrategy);
        final String roundTripEncodingString = gson.toJson(roundTripEncodingStrategy);
        System.out.println("Original: " + originalEncodingString);
        System.out.println("RoundTrip: " + roundTripEncodingString);

        Assert.assertEquals(roundTripEncodingStrategy, cramEncodingStrategy);
        Assert.assertEquals(roundTripEncodingString, originalEncodingString);
    }

}
