package htsjdk.samtools.cram.digest;

interface Combine<T> {

    T combine(T state, T update);
}
