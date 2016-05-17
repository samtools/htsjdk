package htsjdk.samtools.util;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CloseableIteratorTest {
    @Test
    public void testToList() {
        final List<Integer> expected = Arrays.asList(1,2,3,4,5);
        final PeekableIterator<Integer> peeky = new PeekableIterator<>(expected.iterator());
        final List<Integer> actual = peeky.toList();

        Assert.assertEquals(actual, expected);
        Assert.assertEquals(peeky.toList(), new ArrayList<>()); // Should be empty the second time
    }

    @Test
    public void testToStream() {
        final List<Integer> inputs = Arrays.asList(1,2,3,4,5);
        final PeekableIterator<Integer> peeky = new PeekableIterator<>(inputs.iterator());
        final List<Integer> expected = inputs.stream().map(i -> i*2).collect(Collectors.toList());
        final List<Integer> actual   = peeky.stream().map(i -> i*2).collect(Collectors.toList());

        Assert.assertEquals(actual, expected);
    }
}
