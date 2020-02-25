package htsjdk.samtools.cram.encoding;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.core.BetaIntegerEncoding;
import htsjdk.samtools.cram.encoding.writer.DataSeriesWriter;
import htsjdk.samtools.cram.structure.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class DataSeriesWriterTest extends HtsjdkTest {

    @DataProvider(name = "negativeConstructor")
    public static Object[][] negativeConstructor() {
        return new Object[][] {
                // mismatch type and encoding
                {DataSeriesType.BYTE, new BetaIntegerEncoding(0, 8).toEncodingDescriptor()}
        };
    }

    @Test(dataProvider = "negativeConstructor", expectedExceptions = IllegalArgumentException.class)
    public void negativeConstructorTest(final DataSeriesType valueType,
                                        final EncodingDescriptor params) {
        final CompressionHeader compressionHeader = new CompressionHeader();
        new DataSeriesWriter(valueType, params, new SliceBlocksWriteStreams(compressionHeader));
    }
}
