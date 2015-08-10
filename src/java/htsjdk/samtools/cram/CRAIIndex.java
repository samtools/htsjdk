package htsjdk.samtools.cram;

import htsjdk.samtools.CRAMIndexer;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.cram.structure.Slice;
import htsjdk.samtools.seekablestream.SeekableMemoryStream;
import htsjdk.samtools.seekablestream.SeekableStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;

/**
 * A collection of static methods to read, write and convert CRAI index.
 */
public class CRAIIndex {
    public static final String CRAI_SUFFIX = ".crai";

    public static void writeIndex(OutputStream os, List<CRAIEntry> index) throws IOException {
        for (CRAIEntry e : index) {
            os.write(e.toString().getBytes());
            os.write('\n');
        }
    }

    public static List<CRAIEntry> readIndex(InputStream is) {
        List<CRAIEntry> list = new LinkedList<CRAIEntry>();
        Scanner scanner = new Scanner(is);

        try {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                CRAIEntry entry = new CRAIEntry(line);
                list.add(entry);
            }
        } finally {
            try {
                scanner.close();
            } catch (Exception e) {
            }
        }

        return list;
    }

    public static List<CRAIEntry> find(List<CRAIEntry> list, int seqId, int start, int span) {
        boolean whole = start < 1 || span < 1;
        CRAIEntry query = new CRAIEntry();
        query.sequenceId = seqId;
        query.alignmentStart = start < 1 ? 1 : start;
        query.alignmentSpan = span < 1 ? Integer.MAX_VALUE : span;
        query.containerStartOffset = Long.MAX_VALUE;
        query.sliceOffset = Integer.MAX_VALUE;
        query.sliceSize = Integer.MAX_VALUE;

        List<CRAIEntry> l = new ArrayList<CRAIEntry>();
        for (CRAIEntry e : list) {
            if (e.sequenceId != seqId)
                continue;
            if (whole || CRAIEntry.intersect(e, query))
                l.add(e);
        }
        Collections.sort(l, CRAIEntry.byStart);
        return l;
    }

    public static CRAIEntry getLeftmost(List<CRAIEntry> list) {
        if (list == null || list.isEmpty())
            return null;
        CRAIEntry left = list.get(0);

        for (CRAIEntry e : list)
            if (e.alignmentStart < left.alignmentStart)
                left = e;

        return left;
    }

    public static int findLastAlignedEntry(List<CRAIEntry> list) {
        int low = 0;
        int high = list.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            CRAIEntry midVal = list.get(mid);

            if (midVal.sequenceId >= 0)
                low = mid + 1;
            else
                high = mid - 1;
        }
        return low;
    }

    public static SeekableStream openCraiFileAsBaiStream(File cramIndexFile, SAMSequenceDictionary dictionary) throws IOException {
        return openCraiFileAsBaiStream(new FileInputStream(cramIndexFile), dictionary);
    }

    public static SeekableStream openCraiFileAsBaiStream(InputStream indexStream, SAMSequenceDictionary dictionary) throws IOException {
        List<CRAIEntry> full = CRAIIndex.readIndex(new GZIPInputStream(indexStream));
        Collections.sort(full);

        SAMFileHeader header = new SAMFileHeader();
        header.setSequenceDictionary(dictionary);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CRAMIndexer indexer = new CRAMIndexer(baos, header);

        for (CRAIEntry entry : full) {
            Slice slice = new Slice();
            slice.containerOffset = entry.containerStartOffset;
            slice.alignmentStart = entry.alignmentStart;
            slice.alignmentSpan = entry.alignmentSpan;
            slice.sequenceId = entry.sequenceId;
            slice.nofRecords = entry.sliceSize;
            slice.index = entry.sliceIndex;
            slice.offset = entry.sliceOffset;

            indexer.processAlignment(slice);
        }
        indexer.finish();

        return new SeekableMemoryStream(baos.toByteArray(), null);
    }
}
