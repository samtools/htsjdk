package htsjdk.tribble.readers;

import htsjdk.HtsjdkTest;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import org.testng.Assert;
import org.testng.annotations.Test;

@SuppressWarnings("deprecation")
public class AsciiLineReaderIteratorTest extends HtsjdkTest {

    @Test
    public void constructorTakingAnAsciiLineReaderIteratesItsLines() throws ReflectiveOperationException {
        // Looked up reflectively because a direct call would compile against the Utf8LineReader overload, whereas
        // code compiled against htsjdk 5.x links to exactly this signature.
        final Constructor<AsciiLineReaderIterator> constructor =
                AsciiLineReaderIterator.class.getConstructor(AsciiLineReader.class);
        final AsciiLineReader lineReader =
                new AsciiLineReader(new ByteArrayInputStream("first\nsecond\n".getBytes(StandardCharsets.UTF_8)));

        final AsciiLineReaderIterator iterator = constructor.newInstance(lineReader);

        Assert.assertEquals(iterator.next(), "first");
        Assert.assertEquals(iterator.next(), "second");
        Assert.assertFalse(iterator.hasNext());
    }
}
