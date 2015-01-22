package htsjdk.samtools.cram.digest;

abstract class AbstractSerialDigest<T> {
    protected Combine<T> combine;
    protected T value;

    protected AbstractSerialDigest(Combine<T> combine, T value) {
        this.combine = combine;
        this.value = value;
    }

    protected abstract void resetAndUpdate(byte[] data);

    protected abstract T getValue();

    protected abstract byte[] asByteArray();

    void add(byte[] data) {
        resetAndUpdate(data);
        T updateValue = getValue();
        if (value == null)
            value = updateValue;
        else
            value = combine.combine(value, updateValue);
    }
}
