package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.ref.ReferenceContext;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ContainerHeader {
    // required and fixed at construction time

    private final ReferenceContext referenceContext;
    private final int alignmentStart;
    private final int alignmentSpan;
    private final int nofRecords;
    private final long globalRecordCounter;

    // mutable

    /**
     * The total length of all blocks in the container, of all types.
     *
     * Equal to the total length of the Container, minus this Container Header.
     *
     * @see htsjdk.samtools.cram.structure.block.BlockContentType
     */
    private int containerBlocksByteSize;
    private long bases;
    private int blockCount;

    /**
     * Slice byte boundaries as offsets within this container,
     * counted after the container header.  The start of the compression header
     * has offset 0.
     *
     * Equal to {@link Slice#byteOffsetFromCompressionHeaderStart}.
     *
     * As an example, suppose we have:
     * - landmarks[0] = 9000
     * - landmarks[1] = 109000
     * - containerBlocksByteSize = 123456
     *
     * We therefore know:
     * - the compression header size = 9000
     * - Slice 0 has offset 9000 and size 100000 (109000 - 9000)
     * - Slice 1 has offset 109000 and size 14456 (123456 - 109000)
     */
    private int[] landmarks;

    private int checksum;

    public ContainerHeader(final ReferenceContext referenceContext,
                           final int alignmentStart,
                           final int alignmentSpan,
                           final int nofRecords,
                           final long globalRecordCounter) {

        this.referenceContext = referenceContext;
        this.alignmentStart = alignmentStart;
        this.alignmentSpan = alignmentSpan;
        this.nofRecords = nofRecords;
        this.globalRecordCounter = globalRecordCounter;
    }

    public ContainerHeader(final ReferenceContext referenceContext,
                           final int alignmentStart,
                           final int alignmentSpan,
                           final int nofRecords,
                           final long globalRecordCounter,
                           final int containerBlocksByteSize,
                           final long bases,
                           final int blockCount,
                           final int[] landmarks,
                           final int checksum) {

        this(referenceContext, alignmentStart, alignmentSpan, nofRecords, globalRecordCounter);

        this.containerBlocksByteSize = containerBlocksByteSize;
        this.bases = bases;
        this.blockCount = blockCount;
        this.landmarks = landmarks;
        this.checksum = checksum;
    }

    /**
     * Derive the container's {@link ReferenceContext} from its {@link Slice}s.
     *
     * A Single Reference Container contains only Single Reference Slices mapped to the same reference.
     * - set the Container's ReferenceContext to be the same as those slices
     * - set the Container's Alignment Start and Span to cover all slices
     *
     * A Multiple Reference Container contains only Multiple Reference Slices.
     * - set the Container's ReferenceContext to MULTIPLE_REFERENCE_CONTEXT
     * - unset the Container's Alignment Start and Span
     *
     * An Unmapped Container contains only Unmapped Slices.
     * - set the Container's ReferenceContext to UNMAPPED_UNPLACED_CONTEXT
     * - unset the Container's Alignment Start and Span
     *
     * Any other combination is invalid.
     *
     * @param containerSlices the constituent Slices of the Container
     * @throws CRAMException for invalid Container states
     * @return the initialized Container
     */
    public static ContainerHeader initializeFromSlices(final List<Slice> containerSlices,
                                                       final int recordCount,
                                                       final long globalRecordCounter) {
        final Set<ReferenceContext> sliceRefContexts = containerSlices.stream()
                .map(Slice::getReferenceContext)
                .collect(Collectors.toSet());

        if (sliceRefContexts.isEmpty()) {
            throw new CRAMException("Cannot construct a Container without any Slices");
        }
        else if (sliceRefContexts.size() > 1) {
            throw new CRAMException("Cannot construct a Container from Slices with conflicting types or sequence IDs");
        }

        final ReferenceContext commonRefContext = sliceRefContexts.iterator().next();

        int alignmentStart;
        int alignmentSpan;
        if (commonRefContext.isMappedSingleRef()) {
            int start = Integer.MAX_VALUE;
            // end is start + span - 1.  We can do slightly easier math instead.
            int endPlusOne = Integer.MIN_VALUE;

            for (final Slice slice : containerSlices) {
                start = Math.min(start, slice.alignmentStart);
                endPlusOne = Math.max(endPlusOne, slice.alignmentStart + slice.alignmentSpan);
            }

            alignmentStart = start;
            alignmentSpan = endPlusOne - start;
        }
        else {
            alignmentStart = Slice.NO_ALIGNMENT_START;
            alignmentSpan = Slice.NO_ALIGNMENT_SPAN;
        }

        return new ContainerHeader(commonRefContext, alignmentStart, alignmentSpan, recordCount, globalRecordCounter);
    }

    public boolean isEOFContainer() {
        final boolean v3 = containerBlocksByteSize == 15 && referenceContext.isUnmappedUnplaced()
                && alignmentStart == 4542278 && blockCount == 1
                && nofRecords == 0;

        final boolean v2 = containerBlocksByteSize == 11 && referenceContext.isUnmappedUnplaced()
                && alignmentStart == 4542278 && blockCount == 1
                && nofRecords == 0;

        return v3 || v2;
    }

    public ReferenceContext getReferenceContext() {
        return referenceContext;
    }

    public int getAlignmentStart() {
        return alignmentStart;
    }

    public int getAlignmentSpan() {
        return alignmentSpan;
    }

    public int getNofRecords() {
        return nofRecords;
    }

    public long getGlobalRecordCounter() {
        return globalRecordCounter;
    }

    /**
     * Byte size of the content excluding header.
     */
    public int getContainerBlocksByteSize() {
        return containerBlocksByteSize;
    }

    public void setContainerBlocksByteSize(int containerBlocksByteSize) {
        this.containerBlocksByteSize = containerBlocksByteSize;
    }

    public long getBases() {
        return bases;
    }

    public void setBases(long bases) {
        this.bases = bases;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public void setBlockCount(int blockCount) {
        this.blockCount = blockCount;
    }

    public int[] getLandmarks() {
        return landmarks;
    }

    public void setLandmarks(int[] landmarks) {
        this.landmarks = landmarks;
    }

    public int getChecksum() {
        return checksum;
    }

}
