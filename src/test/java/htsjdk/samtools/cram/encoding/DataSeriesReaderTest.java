package htsjdk.samtools.cram.encoding;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.core.BetaIntegerEncoding;
import htsjdk.samtools.cram.encoding.reader.DataSeriesReader;
import htsjdk.samtools.cram.structure.DataSeriesType;
import htsjdk.samtools.cram.structure.EncodingParams;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class DataSeriesReaderTest extends HtsjdkTest {

    @DataProvider(name = "negativeConstructor")
    public static Object[][] negativeConstructor() {
        return new Object[][] {
                // mismatch type and encoding
                {DataSeriesType.BYTE, new BetaIntegerEncoding(0, 8).toParam()}
        };
    }

    @Test(dataProvider = "negativeConstructor", expectedExceptions = RuntimeException.class)
    public void negativeConstructorTest(final DataSeriesType valueType,
                                        final EncodingParams params) {
        new DataSeriesReader(valueType, params, null, null);
    }
}
