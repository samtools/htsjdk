package htsjdk.samtools.cram.encoding;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.writer.DataSeriesWriter;
import htsjdk.samtools.cram.structure.DataSeriesType;
import htsjdk.samtools.cram.structure.EncodingParams;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class DataSeriesWriterTest extends HtsjdkTest {

    @DataProvider(name = "negativeConstructor")
    public static Object[][] negativeConstructor() {
        return new Object[][] {
                // mismatch type and encoding
                {DataSeriesType.BYTE, BetaIntegerEncoding.toParam(0, 8)}
        };
    }

    @Test(dataProvider = "negativeConstructor", expectedExceptions = RuntimeException.class)
    public void negativeConstructorTest(final DataSeriesType valueType,
                                        final EncodingParams params) {
        new DataSeriesWriter(valueType, params, null, null);
    }
}
