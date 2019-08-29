package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.ref.ReferenceContext;

public class ContainerHeader {
    // total length of all blocks in this container (total length of this container, minus the Container Header).
    public int containerBlocksByteSize = 0;
    // if MULTIPLE_REFERENCE_ID, all slices in the container must also be MULTIPLE_REFERENCE_ID

    private final ReferenceContext referenceContext;

    // container header as defined in the specs, in addition to sequenceId from ReferenceContext

    // minimum alignment start of the reads in this Container
    // uses a 1-based coordinate system
    //TODO: finals ?
    private int alignmentStart = Slice.NO_ALIGNMENT_START;
    private int alignmentSpan = Slice.NO_ALIGNMENT_SPAN;
    private int nofRecords = 0;
    private long globalRecordCounter = 0;
    private long bases = 0;
    private int blockCount = -1;

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

    private int checksum = 0;

    public ContainerHeader(int containerBlocksByteSize, ReferenceContext referenceContext, int alignmentStart,
                           int alignmentSpan, int nofRecords, long globalRecordCounter, long bases, int blockCount,
                           int[] landmarks, int checksum) {
        this.containerBlocksByteSize = containerBlocksByteSize;
        this.referenceContext = referenceContext;
        this.alignmentStart = alignmentStart;
        this.alignmentSpan = alignmentSpan;
        this.nofRecords = nofRecords;
        this.globalRecordCounter = globalRecordCounter;
        this.bases = bases;
        this.blockCount = blockCount;
        this.landmarks = landmarks;
        this.checksum = checksum;
    }

    public ContainerHeader(
            final ReferenceContext referenceContext,
            final long globalRecordCounter,
            final int blockCount,
            final int bases) {
        this.referenceContext = referenceContext;
        this.globalRecordCounter = globalRecordCounter;
        this.blockCount = blockCount;
        this.bases = bases;
        this.landmarks = new int[0];
    }

    public int getContainerBlocksByteSize() {
        return containerBlocksByteSize;
    }

    public void setContainerBlocksByteSize(int containerBlocksByteSize) {
        this.containerBlocksByteSize = containerBlocksByteSize;
    }

    public ReferenceContext getReferenceContext() {
        return referenceContext;
    }

    public int getAlignmentStart() {
        return alignmentStart;
    }

    public void setAlignmentStart(int alignmentStart) {
        this.alignmentStart = alignmentStart;
    }

    public int getAlignmentSpan() {
        return alignmentSpan;
    }

    public void setAlignmentSpan(int alignmentSpan) {
        this.alignmentSpan = alignmentSpan;
    }

    public int getNofRecords() {
        return nofRecords;
    }

    public void setNofRecords(int nofRecords) {
        this.nofRecords = nofRecords;
    }

    public long getGlobalRecordCounter() {
        return globalRecordCounter;
    }

    public void setGlobalRecordCounter(long globalRecordCounter) {
        this.globalRecordCounter = globalRecordCounter;
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

    @Override
    public String toString() {
        return String
                .format("seqID=%s, start=%d, span=%d, nRecords=%d, nBlocks=%d",
                        referenceContext, alignmentStart, alignmentSpan, nofRecords, blockCount);
    }

    public boolean isEOF() {
        final boolean v3 = containerBlocksByteSize == 15 && referenceContext.isUnmappedUnplaced()
                && alignmentStart == 4542278 && blockCount == 1
                && nofRecords == 0;

        final boolean v2 = containerBlocksByteSize == 11 && referenceContext.isUnmappedUnplaced()
                && alignmentStart == 4542278 && blockCount == 1
                && nofRecords == 0;

        return v3 || v2;
    }

}
