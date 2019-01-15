package htsjdk.samtools.cram;

import htsjdk.samtools.*;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.cram.structure.slice.MappedSliceMetadata;
import htsjdk.samtools.cram.structure.slice.Slice;
import htsjdk.samtools.cram.structure.slice.SliceMetadata;
import htsjdk.samtools.seekablestream.SeekableMemoryStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.*;
import java.util.*;
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
            for (final Slice s: container.slices) {
                if (s.isMultiref()) {
                    final Map<Integer, SliceMetadata> metadataMap = s.getMultiRefAlignmentMetadata(container.header, ValidationStringency.DEFAULT_STRINGENCY);

                    // References must be processed in order, with unmapped last
                    // TODO refactor w/ CRAMBAIIndexer.processContainer()

                    this.entries.addAll(metadataMap
                            .entrySet()
                            .stream()
                            .filter(entry -> entry.getKey() != SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX)
                            .sorted(Map.Entry.comparingByKey())
                            .map(e -> new CRAIEntry(e.getKey(),
                                    ((MappedSliceMetadata) e.getValue()).getAlignmentStart(),
                                    ((MappedSliceMetadata) e.getValue()).getAlignmentSpan(),
                                    container.offset,
                                    container.landmarks[s.index],
                                    s.size))
                            .collect(Collectors.toList()));

                    final SliceMetadata unmapped = metadataMap.get(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
                    if (unmapped != null) {
                        this.entries.add(new CRAIEntry(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                                0,  // TODO: does it matter what we set here?
                                0,
                                container.offset,
                                container.landmarks[s.index],
                                s.size));
                    }
                 } else {
                    // TODO test that this is also correct for unmapped
                    entries.add(s.getCRAIEntry(container.offset));
                }
            }
        }
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
        header.setSequenceDictionary(dictionary);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final CRAMBAIIndexer indexer = new CRAMBAIIndexer(baos, header);

        for (final CRAIEntry entry : full) {
            final Slice slice = new Slice();
            slice.containerOffset = entry.getContainerStartByteOffset();
            slice.alignmentStart = entry.getAlignmentStart();
            slice.alignmentSpan = entry.getAlignmentSpan();
            slice.sequenceId = entry.getSequenceId();
            // NOTE: the recordCount and sliceIndex fields can't be derived from the CRAM index
            // so we can only set them to zero
            // see https://github.com/samtools/htsjdk/issues/531
            slice.nofRecords = 0;
            slice.index = 0;
            slice.offset = entry.getSliceByteOffset();

            indexer.processSingleReferenceSlice(slice);
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
