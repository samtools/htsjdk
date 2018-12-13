package htsjdk.samtools.cram;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.CRAMBAIIndexer;
import htsjdk.samtools.CRAMCRAIIndexer;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.cram.structure.slice.IndexableSlice;
import htsjdk.samtools.seekablestream.SeekableMemoryStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.ValidationStringency;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CRAI index used for CRAM files.
 */
public class CRAIIndex {
    public static final String CRAI_INDEX_SUFFIX = ".crai";
    final private List<CRAIEntry> entries = new ArrayList<>();

    /**
     * Add a single entry to the CRAI index.
     * @param entry entry to be added
     */
    public void addEntry(CRAIEntry entry) {
        entries.add(entry);
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
        Collections.sort(entries, CRAIEntry.byStartDesc);
        entries.stream().forEach(e -> e.writeToStream(os));
    }

    /**
     * Create index entries for a single container.
     * @param container the container to index
     */
    public void processContainer(final Container container) {
        // TODO: this should be refactored and delegate to container/slice
        if (!container.isEOF()) {
            for (final IndexableSlice slice : container.slices) {
                if (slice.hasMultipleReferences()) {
                    final Map<Integer, AlignmentSpan> spans =
                            slice.getMultiRefAlignmentSpans(container.header, ValidationStringency.DEFAULT_STRINGENCY);

                    final List<CRAIEntry> entries = spans.entrySet().stream()
                            .map(span -> slice.getCRAIEntry(span.getKey(), span.getValue(), container.offset))
                            .collect(Collectors.toList());

                    this.entries.addAll(entries);
                } else {
                    this.entries.add(slice.getCRAIEntry(container.offset));
                }
            }
        }
    }

    public static SeekableStream openCraiFileAsBaiStream(final InputStream indexStream,
                                                         final SAMSequenceDictionary dictionary) throws CRAIIndexException {
        final List<CRAIEntry> full = CRAMCRAIIndexer.readIndex(indexStream).getCRAIEntries();
        Collections.sort(full);

        final SAMFileHeader header = new SAMFileHeader();
        header.setSequenceDictionary(dictionary);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final CRAMBAIIndexer indexer = new CRAMBAIIndexer(baos, header);

        for (final CRAIEntry entry : full) {
            // NOTE: the recordCount and sliceIndex fields can't be derived from the CRAM index
            // so we can only set them to zero
            // see https://github.com/samtools/htsjdk/issues/531
            indexer.processSingleReferenceSlice(entry.toBAIMetadata(0, 0));
        }
        indexer.finish();

        return new SeekableMemoryStream(baos.toByteArray(), "CRAI to BAI converter");
    }

    public static List<CRAIEntry> find(final List<CRAIEntry> list, final int seqId, final int start, final int span) {
        final boolean whole = start < 1 || span < 1;
        final CRAIEntry query = new CRAIEntry(seqId,
                start < 1 ? 1 : start,
                span < 1 ? Integer.MAX_VALUE : span,
                Long.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE);

        final List<CRAIEntry> l = new ArrayList<>();
        for (final CRAIEntry e : list) {
            if (e.getSequenceId() != seqId) {
                continue;
            }
            if (whole || CRAIEntry.intersect(e, query)) {
                l.add(e);
            }
        }
        Collections.sort(l, CRAIEntry.byStart);
        return l;
    }

    public static CRAIEntry getLeftmost(final List<CRAIEntry> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        CRAIEntry left = list.get(0);

        for (final CRAIEntry e : list) {
            if (e.getAlignmentStart() < left.getAlignmentStart()) {
                left = e;
            }
        }

        return left;
    }

    /**
     * Find index of the last aligned entry in the list. Assumes the index is sorted by coordinate and unmapped entries (with sequence id = -1) follow the mapped entries.
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
