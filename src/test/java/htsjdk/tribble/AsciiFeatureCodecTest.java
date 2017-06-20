package htsjdk.tribble;

import htsjdk.samtools.util.LocationAware;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.variant.variantcontext.VariantContext;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;

public class AsciiFeatureCodecTest {

    @Test
    public void testMakeIndexableSourceFromUnknownStream() {
        // test the case where we try to create a codec using a stream that is neither a
        // BlockCompressedInputStream nor a PositionalBufferedStream
        final ByteArrayInputStream is = new ByteArrayInputStream(new byte[10]);
        LocationAware locationAware = new AsciiFeatureCodec<VariantContext>(VariantContext.class) {
            public Object readActualHeader(final LineIterator reader) {
                return new Object();
            }

            @Override
            public VariantContext decode(String s) {
                return null;
            }

            @Override
            public boolean canDecode(String path) {
                return false;
            }
        }.makeIndexableSourceFromStream(is);
        Assert.assertEquals(locationAware.getPosition(), 0);
    }
}
