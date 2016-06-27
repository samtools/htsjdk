package htsjdk.samtools.cram;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.CRAMBAIIndexer;
import htsjdk.samtools.CRAMCRAIIndexer;
import htsjdk.samtools.cram.encoding.reader.DataReaderFactory;
import htsjdk.samtools.cram.encoding.reader.RefSeqIdReader;
import htsjdk.samtools.cram.io.DefaultBitInputStream;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.seekablestream.SeekableMemoryStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.ValidationStringency;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import java.util.List;

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
     * @param c the container to index
     */
    public void processContainer(final Container c) {
        // TODO: this should be refactored and delegate to container/slice
        if (!c.isEOF()) {
            for (int i = 0; i < c.slices.length; i++) {
                Slice s = c.slices[i];
                if (s.sequenceId == Slice.MULTI_REFERENCE) {
                    this.entries.addAll(getCRAIEntriesForMultiRefSlice(s, c.header, c.offset, c.landmarks));
                }
                else {
                    CRAIEntry e = new CRAIEntry();

                    e.sequenceId = c.sequenceId;
                    e.alignmentStart = s.alignmentStart;
                    e.alignmentSpan = s.alignmentSpan;
                    e.containerStartOffset = c.offset;
                    e.sliceOffset = c.landmarks[i];
                    e.sliceSize = s.size;
                    e.sliceIndex = i;

                    entries.add(e);
                }
            }
        }
    }

    /**
     * Return a list of CRAI Entries; one for each reference in the multireference slice.
     * TODO: this should be refactored and delegate to container/slice
     */
    private static Collection<CRAIEntry> getCRAIEntriesForMultiRefSlice(
            final Slice slice,
            final CompressionHeader header,
            final long containerOffset,
            final int[] landmarks)
    {
        final DataReaderFactory dataReaderFactory = new DataReaderFactory();
        final Map<Integer, InputStream> inputMap = new HashMap<>();
        for (final Integer exId : slice.external.keySet()) {
            inputMap.put(exId, new ByteArrayInputStream(slice.external.get(exId).getRawContent()));
        }

        final RefSeqIdReader reader = new RefSeqIdReader(
                slice.sequenceId,
                slice.alignmentStart,
                ValidationStringency.DEFAULT_STRINGENCY);
        dataReaderFactory.buildReader(
                reader,
                new DefaultBitInputStream(new ByteArrayInputStream(slice.coreBlock.getRawContent())),
                inputMap,
                header,
                slice.sequenceId
        );
        reader.APDelta = header.APDelta;

        for (int i = 0; i < slice.nofRecords; i++) {
            final CramCompressionRecord record = new CramCompressionRecord();
            record.sliceIndex = slice.index;
            record.index = i;

            reader.read();

            if (record.sequenceId == slice.sequenceId) {
                record.sequenceId = slice.sequenceId;
            }
            else if (record.sequenceId == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                record.sequenceName = SAMRecord.NO_ALIGNMENT_REFERENCE_NAME;
            }
        }

        Map<Integer, AlignmentSpan> spans = reader.getReferenceSpans();
        List<CRAIEntry> entries = new ArrayList<>(spans.size());
        for (int seqId : spans.keySet()) {
            CRAIEntry e = new CRAIEntry();
            e.sequenceId = seqId;
            AlignmentSpan span = spans.get(seqId);
            e.alignmentStart = span.getStart();
            e.alignmentSpan = span.getSpan();
            e.sliceSize = slice.size;
            e.sliceIndex = slice.index;
            e.containerStartOffset = containerOffset;
            e.sliceOffset = landmarks[slice.index];

            entries.add(e);
        }

        return entries;
    }

    public static SeekableStream openCraiFileAsBaiStream(final File cramIndexFile, final SAMSequenceDictionary dictionary) throws IOException {
        return openCraiFileAsBaiStream(new FileInputStream(cramIndexFile), dictionary);
    }

    public static SeekableStream openCraiFileAsBaiStream(final InputStream indexStream, final SAMSequenceDictionary dictionary) throws IOException, CRAIIndexException {
        final List<CRAIEntry> full = CRAMCRAIIndexer.readIndex(indexStream).getCRAIEntries();
        Collections.sort(full);

        final SAMFileHeader header = new SAMFileHeader();
        header.setSequenceDictionary(dictionary);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final CRAMBAIIndexer indexer = new CRAMBAIIndexer(baos, header);

        for (final CRAIEntry entry : full) {
            final Slice slice = new Slice();
            slice.containerOffset = entry.containerStartOffset;
            slice.alignmentStart = entry.alignmentStart;
            slice.alignmentSpan = entry.alignmentSpan;
            slice.sequenceId = entry.sequenceId;
            // https://github.com/samtools/htsjdk/issues/531
            // entry.sliceSize is the slice size in bytes, not the number of
            // records; this results in the BAMIndex metadata being wrong
            slice.nofRecords = entry.sliceSize;
            slice.index = entry.sliceIndex;
            slice.offset = entry.sliceOffset;

            indexer.processSingleReferenceSlice(slice);
        }
        indexer.finish();

        return new SeekableMemoryStream(baos.toByteArray(), "CRAI to BAI converter");
    }

    public static List<CRAIEntry> find(final List<CRAIEntry> list, final int seqId, final int start, final int span) {
        final boolean whole = start < 1 || span < 1;
        final CRAIEntry query = new CRAIEntry();
        query.sequenceId = seqId;
        query.alignmentStart = start < 1 ? 1 : start;
        query.alignmentSpan = span < 1 ? Integer.MAX_VALUE : span;
        query.containerStartOffset = Long.MAX_VALUE;
        query.sliceOffset = Integer.MAX_VALUE;
        query.sliceSize = Integer.MAX_VALUE;

        final List<CRAIEntry> l = new ArrayList<>();
        for (final CRAIEntry e : list) {
            if (e.sequenceId != seqId) {
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
            if (e.alignmentStart < left.alignmentStart) {
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

            if (midVal.sequenceId >= 0) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        if (low >= list.size()) {
            return list.size() - 1;
        }
        for (; low >= 0 && list.get(low).sequenceId == -1; low--) {
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
