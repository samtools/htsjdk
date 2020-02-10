package htsjdk.samtools.cram;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.CRAMBAIIndexer;
import htsjdk.samtools.CRAMCRAIIndexer;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.cram.structure.Slice;
import htsjdk.samtools.seekablestream.SeekableMemoryStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CRAI index used for CRAM files.
 */
public class CRAIIndex {
    /**
     * @deprecated since June 2019 Use {@link FileExtensions#CRAM_INDEX} instead.
     */
    @Deprecated
    public static final String CRAI_INDEX_SUFFIX = FileExtensions.CRAM_INDEX;
    final private List<CRAIEntry> entries = new ArrayList<>();

    /**
     * Add a single entry to the CRAI index.
     * @param entry entry to be added
     */
    public void addEntry(final CRAIEntry entry) {
        entries.add(entry);
    }

    /**
     * Add multiple entries to the CRAI index.
     * @param toAdd entries to be added
     */
    public void addEntries(final Collection<CRAIEntry> toAdd) {
        entries.addAll(toAdd);
    }

    // This is used for testing and should be removed when there are no more
    // consumers that know about the internal structure of a CRAI
    public List<CRAIEntry> getCRAIEntries() {
        return entries;
    }

    /**
     * Write out the index to an output stream;
     * @param os Stream to write index to
     */
    public void writeIndex(final OutputStream os) {
        entries.stream()
                .sorted()
                .forEach(e -> e.writeToStream(os));
    }

    /**
     * Create index entries for a single container.
     * @param container the container to index
     */
    public void processContainer(final Container container) {
        addEntries(container.getCRAIEntries());
    }

    public static SeekableStream openCraiFileAsBaiStream(final File cramIndexFile, final SAMSequenceDictionary dictionary) {
        try {
            return openCraiFileAsBaiStream(new FileInputStream(cramIndexFile), dictionary);
        }
        catch (final FileNotFoundException e) {
            throw new RuntimeIOException(e);
        }
    }

    public static SeekableStream openCraiFileAsBaiStream(final InputStream indexStream, final SAMSequenceDictionary dictionary) {
        final List<CRAIEntry> full = CRAMCRAIIndexer.readIndex(indexStream).getCRAIEntries();
        Collections.sort(full);

        final SAMFileHeader header = new SAMFileHeader();
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        header.setSequenceDictionary(dictionary);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final CRAMBAIIndexer indexer = new CRAMBAIIndexer(baos, header);

        for (final CRAIEntry entry : full) {
            final Slice slice = new Slice(new ReferenceContext(entry.getSequenceId()));
            slice.containerByteOffset = entry.getContainerStartByteOffset();
            slice.alignmentStart = entry.getAlignmentStart();
            slice.alignmentSpan = entry.getAlignmentSpan();
            slice.byteOffsetFromCompressionHeaderStart = entry.getSliceByteOffsetFromCompressionHeaderStart();

            // NOTE: the sliceIndex and read count fields can't be derived from the CRAM index
            // so we can only set them to zero
            // see https://github.com/samtools/htsjdk/issues/531

            slice.mappedReadsCount = 0;
            slice.unmappedReadsCount = 0;
            slice.unplacedReadsCount = 0;
            slice.index = 0;

            indexer.processAsSingleReferenceSlice(slice);
        }
        indexer.finish();

        return new SeekableMemoryStream(baos.toByteArray(), "CRAI to BAI converter");
    }

    public static List<CRAIEntry> find(final List<CRAIEntry> list, final int seqId, final int start, final int span) {
        final boolean matchEntireSequence = start < 1 || span < 1;
        final int dummyValue = 1;
        final CRAIEntry query = new CRAIEntry(seqId, start, span, dummyValue, dummyValue, dummyValue);

        return list.stream()
                .filter(e -> e.getSequenceId() == seqId)
                .filter(e -> matchEntireSequence || CRAIEntry.intersect(e, query))
                .sorted()
                .collect(Collectors.toList());
    }

    public static CRAIEntry getLeftmost(final List<CRAIEntry> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }

        return list.stream()
                .sorted()
                .findFirst()
                .get();
    }

    /**
     * Find index of the last aligned entry in the list. Assumes the index is sorted
     * by coordinate and unmapped entries (with sequence id = -1) follow the mapped entries.
     *
     * @param list a list of CRAI entries
     * @return integer index of the last entry with sequence id not equal to -1
     */
    public static int findLastAlignedEntry(final List<CRAIEntry> list) {
        if (list.isEmpty()) {
            return -1;
        }

        int low = 0;
        int high = list.size() - 1;

        while (low <= high) {
            final int mid = (low + high) >>> 1;
            final CRAIEntry midVal = list.get(mid);

            if (midVal.getSequenceId() >= 0) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        if (low >= list.size()) {
            return list.size() - 1;
        }
        for (; low >= 0 && list.get(low).getSequenceId() == -1; low--) {
        }
        return low;
    }

    public static class CRAIIndexException extends RuntimeException {

        public CRAIIndexException(final String s) {
            super(s);
        }

        public CRAIIndexException(final NumberFormatException e) {
            super(e);
        }
    }
}
