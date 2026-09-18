package htsjdk.index;

import java.util.ArrayList;
import java.util.List;

/** Helpers for tests in other packages that need the package-private side of {@link BinningIndex}. */
public final class BinningIndexTestUtils {
    private BinningIndexTestUtils() {}

    /**
     * The index as a CSI file stores it: without a linear index, and with a no-coordinate count of 0 if it had
     * none. An index built in memory and its twin loaded from a CSI file are equal once both are in this form.
     */
    public static BinningIndex asStoredInCsi(final BinningIndex index) {
        final List<ReferenceBins> references = new ArrayList<>();
        for (int i = 0; i < index.getReferenceCount(); i++) {
            final ReferenceBins reference = index.getReference(i);
            references.add(new ReferenceBins(
                    reference.binNumbers(),
                    reference.binChunks(),
                    reference.loffsets(),
                    new long[0],
                    reference.getMetadata().orElse(null),
                    index.getDepth()));
        }
        return new BinningIndex(
                index.getMinShift(),
                index.getDepth(),
                references,
                index.getNoCoordinateCount().orElse(0));
    }
}
