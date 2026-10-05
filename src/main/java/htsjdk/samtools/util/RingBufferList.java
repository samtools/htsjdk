package htsjdk.samtools.util;

import htsjdk.utils.ValidationUtils;
import java.util.AbstractList;
import java.util.Objects;
import java.util.RandomAccess;

/**
 * A {@link java.util.List} backed by a circular array, for use as a queue that also needs indexed access.
 *
 * <p>{@link #get(int)} and adding or removing at either end take constant time, and removing the first {@code n}
 * elements (as {@code subList(0, n).clear()} does) takes time proportional to {@code n}, whereas
 * {@link java.util.ArrayList} shifts every remaining element on any removal from the front. An insertion or removal
 * in the middle shifts the elements on whichever side of it is shorter. Null elements are allowed. Not safe for
 * concurrent use.
 */
final class RingBufferList<E> extends AbstractList<E> implements RandomAccess {
    /**
     * The element at logical index {@code i} is in slot {@code (head + i) % elements.length}. Slots outside the
     * {@code size} occupied ones are always null so that removed elements can be garbage collected.
     */
    private Object[] elements;

    /** Slot holding the element at logical index 0; always in {@code [0, elements.length)}. */
    private int head = 0;

    private int size = 0;

    /**
     * @param initialCapacity the number of elements held before the backing array first grows
     * @throws IllegalArgumentException if {@code initialCapacity} is negative
     */
    RingBufferList(final int initialCapacity) {
        ValidationUtils.validateArg(initialCapacity >= 0, () -> "Negative initial capacity: " + initialCapacity);
        // At least one slot, so that doubling always grows the array
        this.elements = new Object[Math.max(1, initialCapacity)];
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    @SuppressWarnings("unchecked")
    public E get(final int index) {
        Objects.checkIndex(index, size);
        return (E) elements[slot(index)];
    }

    @Override
    @SuppressWarnings("unchecked")
    public E set(final int index, final E element) {
        Objects.checkIndex(index, size);
        final int slot = slot(index);
        final E previous = (E) elements[slot];
        elements[slot] = element;
        return previous;
    }

    @Override
    public void add(final int index, final E element) {
        Objects.checkIndex(index, size + 1);
        if (size == elements.length) {
            grow();
        }
        if (index < size - index) {
            // Move the head back one slot, then shift the elements before the insertion point down into the gap
            head = head == 0 ? elements.length - 1 : head - 1;
            for (int i = 0; i < index; i++) {
                elements[slot(i)] = elements[slot(i + 1)];
            }
        } else {
            for (int i = size; i > index; i--) {
                elements[slot(i)] = elements[slot(i - 1)];
            }
        }
        elements[slot(index)] = element;
        size++;
        modCount++;
    }

    @Override
    public E remove(final int index) {
        final E removed = get(index);
        removeRange(index, index + 1);
        return removed;
    }

    /**
     * Removes the elements at logical indices {@code [fromIndex, toIndex)} by shifting whichever side of the range
     * is shorter, so removing from the front only nulls the removed slots and advances the head. {@link #clear()} and
     * {@code subList(fromIndex, toIndex).clear()} both come here.
     */
    @Override
    protected void removeRange(final int fromIndex, final int toIndex) {
        Objects.checkFromToIndex(fromIndex, toIndex, size);
        final int removedCount = toIndex - fromIndex;
        if (fromIndex < size - toIndex) {
            for (int i = fromIndex - 1; i >= 0; i--) {
                elements[slot(i + removedCount)] = elements[slot(i)];
            }
            for (int i = 0; i < removedCount; i++) {
                elements[slot(i)] = null;
            }
            head = slot(removedCount);
        } else {
            for (int i = toIndex; i < size; i++) {
                elements[slot(i - removedCount)] = elements[slot(i)];
            }
            for (int i = size - removedCount; i < size; i++) {
                elements[slot(i)] = null;
            }
        }
        size -= removedCount;
        modCount++;
    }

    /** @return the slot in {@link #elements} holding logical index {@code index}, for {@code 0 <= index <= size} */
    private int slot(final int index) {
        final int slot = head + index;
        return slot < elements.length ? slot : slot - elements.length;
    }

    /** Doubles the backing array, unwrapping the elements so that the head is at slot 0. */
    private void grow() {
        final Object[] larger = new Object[elements.length * 2];
        final int headToEnd = Math.min(size, elements.length - head);
        System.arraycopy(elements, head, larger, 0, headToEnd);
        System.arraycopy(elements, 0, larger, headToEnd, size - headToEnd);
        elements = larger;
        head = 0;
    }
}
