package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import org.testng.Assert;
import org.testng.annotations.Test;

public class RingBufferListTest extends HtsjdkTest {

    /** @return a list with the given initial capacity holding {@code values} in order */
    private static RingBufferList<Integer> listOf(final int initialCapacity, final Integer... values) {
        final RingBufferList<Integer> list = new RingBufferList<>(initialCapacity);
        for (final Integer value : values) {
            list.add(value);
        }
        return list;
    }

    @Test
    public void getReturnsElementsInTheOrderAdded() {
        final RingBufferList<Integer> list = listOf(8, 10, 20, 30);
        Assert.assertEquals(list.size(), 3);
        Assert.assertEquals(list.get(0), Integer.valueOf(10));
        Assert.assertEquals(list.get(1), Integer.valueOf(20));
        Assert.assertEquals(list.get(2), Integer.valueOf(30));
    }

    @Test
    public void addAtIndexZeroPrependsTheElement() {
        final RingBufferList<Integer> list = listOf(8, 2, 3);
        list.add(0, 1);
        list.add(0, 0);
        Assert.assertEquals(list, List.of(0, 1, 2, 3));
    }

    @Test
    public void addAtIndexZeroOfAnEmptyListAddsTheElement() {
        final RingBufferList<Integer> list = new RingBufferList<>(8);
        list.add(0, 7);
        Assert.assertEquals(list, List.of(7));
    }

    @Test
    public void removeAtIndexZeroReturnsAndRemovesTheFirstElement() {
        final RingBufferList<Integer> list = listOf(8, 1, 2, 3);
        Assert.assertEquals(list.remove(0), Integer.valueOf(1));
        Assert.assertEquals(list, List.of(2, 3));
        Assert.assertEquals(list.remove(0), Integer.valueOf(2));
        Assert.assertEquals(list.remove(0), Integer.valueOf(3));
        Assert.assertTrue(list.isEmpty());
    }

    @Test
    public void clearingASubListAtTheFrontRemovesThoseElements() {
        final RingBufferList<Integer> list = listOf(8, 1, 2, 3, 4, 5);
        list.subList(0, 3).clear();
        Assert.assertEquals(list, List.of(4, 5));
    }

    @Test
    public void clearingASubListInTheMiddleRemovesThoseElements() {
        final RingBufferList<Integer> list = listOf(16, 1, 2, 3, 4, 5, 6, 7, 8);
        list.subList(1, 3).clear();
        Assert.assertEquals(list, List.of(1, 4, 5, 6, 7, 8));
        list.subList(3, 5).clear();
        Assert.assertEquals(list, List.of(1, 4, 5, 8));
    }

    @Test
    public void addInTheMiddleShiftsTheFollowingElements() {
        final RingBufferList<Integer> list = listOf(16, 1, 2, 3, 4, 5, 6);
        list.add(1, 100);
        list.add(6, 200);
        Assert.assertEquals(list, List.of(1, 100, 2, 3, 4, 5, 200, 6));
    }

    @Test
    public void removeInTheMiddleShiftsTheFollowingElements() {
        final RingBufferList<Integer> list = listOf(16, 1, 2, 3, 4, 5, 6);
        Assert.assertEquals(list.remove(1), Integer.valueOf(2));
        Assert.assertEquals(list.remove(3), Integer.valueOf(5));
        Assert.assertEquals(list, List.of(1, 3, 4, 6));
    }

    @Test
    public void setReplacesTheElementAndReturnsThePreviousOne() {
        final RingBufferList<Integer> list = listOf(8, 1, 2, 3);
        Assert.assertEquals(list.set(1, 20), Integer.valueOf(2));
        Assert.assertEquals(list, List.of(1, 20, 3));
    }

    @Test
    public void elementsKeepTheirOrderAfterManyAddAndRemoveCyclesWrapTheBuffer() {
        final RingBufferList<Integer> list = listOf(4, 0, 1, 2);
        for (int next = 3; next < 100; next++) {
            Assert.assertEquals(list.remove(0), Integer.valueOf(next - 3));
            list.add(next);
            Assert.assertEquals(list, List.of(next - 2, next - 1, next));
        }
    }

    @Test
    public void addingPastTheInitialCapacityKeepsEveryElementInOrder() {
        final RingBufferList<Integer> list = new RingBufferList<>(2);
        final List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            list.add(i);
            expected.add(i);
        }
        Assert.assertEquals(list, expected);
    }

    @Test
    public void growingAfterTheBufferHasWrappedKeepsEveryElementInOrder() {
        final RingBufferList<Integer> list = listOf(4, 0, 1, 2, 3);
        list.remove(0);
        list.remove(0);
        list.add(4);
        list.add(5);
        list.add(6);
        list.add(0, 1);
        Assert.assertEquals(list, List.of(1, 2, 3, 4, 5, 6));
    }

    @Test
    public void aZeroInitialCapacityGrowsOnTheFirstAdd() {
        final RingBufferList<Integer> list = new RingBufferList<>(0);
        list.add(1);
        list.add(0, 0);
        Assert.assertEquals(list, List.of(0, 1));
    }

    @Test
    public void clearEmptiesTheListAndLeavesItUsable() {
        final RingBufferList<Integer> list = listOf(4, 1, 2, 3);
        list.remove(0);
        list.add(4);
        list.clear();
        Assert.assertTrue(list.isEmpty());
        list.add(5);
        list.add(0, 6);
        Assert.assertEquals(list, List.of(6, 5));
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void getAtTheSizeIsRejected() {
        listOf(8, 1, 2).get(2);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void removeFromAnEmptyListIsRejected() {
        new RingBufferList<Integer>(8).remove(0);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void addPastTheSizeIsRejected() {
        listOf(8, 1, 2).add(3, 3);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void aNegativeInitialCapacityIsRejected() {
        new RingBufferList<Integer>(-1);
    }

    @Test(expectedExceptions = ConcurrentModificationException.class)
    public void anIteratorFailsAfterTheListIsModified() {
        final RingBufferList<Integer> list = listOf(8, 1, 2, 3);
        final Iterator<Integer> iterator = list.iterator();
        iterator.next();
        list.remove(0);
        iterator.next();
    }

    @Test
    public void aRandomSequenceOfOperationsMatchesAnArrayList() {
        final Random random = new Random(42);
        final RingBufferList<Integer> list = new RingBufferList<>(2);
        final List<Integer> expected = new ArrayList<>();
        for (int step = 0; step < 20_000; step++) {
            final int value = random.nextInt();
            final int size = expected.size();
            // Weight adds above removals while small so the list keeps growing and wrapping
            final int operation = random.nextInt(size < 50 ? 6 : 10);
            switch (operation) {
                case 0, 1, 2 -> {
                    list.add(value);
                    expected.add(value);
                }
                case 3 -> {
                    list.add(0, value);
                    expected.add(0, value);
                }
                case 4 -> {
                    final int index = random.nextInt(size + 1);
                    list.add(index, value);
                    expected.add(index, value);
                }
                case 5 -> {
                    if (size > 0) {
                        final int index = random.nextInt(size);
                        Assert.assertEquals(list.set(index, value), expected.set(index, value));
                    }
                }
                case 6 -> {
                    if (size > 0) {
                        Assert.assertEquals(list.remove(0), expected.remove(0));
                    }
                }
                case 7 -> {
                    if (size > 0) {
                        final int index = random.nextInt(size);
                        Assert.assertEquals(list.remove(index), expected.remove(index));
                    }
                }
                case 8 -> {
                    final int count = random.nextInt(Math.min(size, 20) + 1);
                    list.subList(0, count).clear();
                    expected.subList(0, count).clear();
                }
                default -> {
                    final int from = random.nextInt(size + 1);
                    final int to = from + random.nextInt(Math.min(size - from, 20) + 1);
                    list.subList(from, to).clear();
                    expected.subList(from, to).clear();
                }
            }
            Assert.assertEquals(list.size(), expected.size(), "size after step " + step);
            Assert.assertEquals(list, expected, "contents after step " + step);
        }
    }
}
