package htsjdk.index;

import htsjdk.samtools.BAMFileSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * Presents an index under another numbering of its references. tabix numbers the sequences of a file in the order
 * it meets them, where a reader of the file may number them as the file's header lists them; the two agree only
 * when every sequence of the header has records, so an index made by tabix has to be asked by name, which comes
 * to this renumbering.
 */
public final class RenumberedReferenceBinsSource implements ReferenceBinsSource {
    private final ReferenceBinsSource source;
    private final int[] sourceOrdinals;

    /**
     * @param source the index, numbered its own way; closed along with this one
     * @param sourceOrdinals for each reference under the numbering to present, its number in {@code source}, or
     *     -1 if {@code source} has nothing for it
     */
    public RenumberedReferenceBinsSource(final ReferenceBinsSource source, final int[] sourceOrdinals) {
        for (final int ordinal : sourceOrdinals) {
            if (ordinal < -1 || ordinal >= source.getReferenceCount()) {
                throw new IllegalArgumentException(String.format(
                        "No reference %d in an index of %d references", ordinal, source.getReferenceCount()));
            }
        }
        this.source = source;
        this.sourceOrdinals = sourceOrdinals.clone();
    }

    @Override
    public int getMinShift() {
        return source.getMinShift();
    }

    @Override
    public int getDepth() {
        return source.getDepth();
    }

    @Override
    public int getReferenceCount() {
        return sourceOrdinals.length;
    }

    @Override
    public ReferenceBins getReference(final int referenceIndex) {
        final int sourceOrdinal = sourceOrdinals[referenceIndex];
        return sourceOrdinal == -1 ? ReferenceBins.EMPTY : source.getReference(sourceOrdinal);
    }

    @Override
    public HtsFileSpan getSpanOverlapping(final int referenceIndex, final int start, final int end) {
        if (referenceIndex < 0 || referenceIndex >= sourceOrdinals.length || sourceOrdinals[referenceIndex] == -1) {
            return new BAMFileSpan();
        }
        return source.getSpanOverlapping(sourceOrdinals[referenceIndex], start, end);
    }

    @Override
    public OptionalLong getNoCoordinateCount() {
        return source.getNoCoordinateCount();
    }

    @Override
    public BinningIndex loadAll() {
        final List<ReferenceBins> references = new ArrayList<>(sourceOrdinals.length);
        for (int i = 0; i < sourceOrdinals.length; i++) {
            references.add(getReference(i));
        }
        return new BinningIndex(
                getMinShift(), getDepth(), references, getNoCoordinateCount().orElse(-1));
    }

    @Override
    public void close() {
        source.close();
    }
}
