package htsjdk.samtools.cram.digest;

abstract class AbstractSerialDigest<T> {
    private final Combine<T> combine;
    T value;

    AbstractSerialDigest(final Combine<T> combine, final T value) {
        this.combine = combine;
        this.value = value;
    }

    protected abstract void resetAndUpdate(byte[] data);

    protected abstract T getValue();

    protected abstract byte[] asByteArray();

    void add(final byte[] data) {
        resetAndUpdate(data);
        final T updateValue = getValue();
        if (value == null)
            value = updateValue;
        else
            value = combine.combine(value, updateValue);
    }
}
