/*===========================================================================
*
*                            PUBLIC DOMAIN NOTICE
*               National Center for Biotechnology Information
*
*  This software/database is a "United States Government Work" under the
*  terms of the United States Copyright Act.  It was written as part of
*  the author's official duties as a United States Government employee and
*  thus cannot be copyrighted.  This software/database is freely available
*  to the public for use. The National Library of Medicine and the U.S.
*  Government have not placed any restriction on its use or reproduction.
*
*  Although all reasonable efforts have been taken to ensure the accuracy
*  and reliability of the software and data, the NLM and the U.S.
*  Government do not and cannot warrant the performance or results that
*  may be obtained by using this software or data. The NLM and the U.S.
*  Government disclaim all warranties, express or implied, including
*  warranties of performance, merchantability or fitness for any particular
*  purpose.
*
*  Please cite the author in any work or product based on this material.
*
* ===========================================================================
*
*/

/**
 * Created by andrii.nikitiuk on 8/11/15.
 */

package htsjdk.samtools;

import htsjdk.samtools.sra.ReferenceCache;
import htsjdk.samtools.sra.SRAAccession;
import htsjdk.samtools.util.CloseableIterator;

import htsjdk.samtools.SamReader.Type;

import htsjdk.samtools.util.Log;
import ngs.ErrorMsg;
import ngs.ReadCollection;
import ngs.ReadGroupIterator;
import ngs.ReferenceIterator;
import ngs.Reference;

import java.util.ArrayList;
import java.util.List;


public class SRAFileReader extends SamReader.ReaderImplementation implements SamReader.Indexing {
    private static final Log log = Log.getInstance(SRAFileReader.class);
    private SRAAccession acc;
    private SAMFileHeader virtualHeader;
    private ReadCollection run;
    private ValidationStringency validationStringency;
    private SRAIterator.RecordRangeInfo recordRangeInfo;
    private SRAIndex index;
    private ReferenceCache cachedReferences;

    public SRAFileReader(final SRAAccession acc) {
        this.acc = acc;

        if (!acc.isValid()) {
            throw new IllegalArgumentException("Invalid SRA accession was passed to SRA reader: " + acc);
        }

        try {
            run = gov.nih.nlm.ncbi.ngs.NGS.openReadCollection(acc.toString());
            virtualHeader = loadSamHeader();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }

        cachedReferences = new ReferenceCache(run, virtualHeader);
        recordRangeInfo = SRAIterator.getRecordsRangeInfo(run);
        index = new SRAIndex(virtualHeader, recordRangeInfo);
    }

    @Override
    public Type type() {
        return Type.SRA_TYPE;
    }

    @Override
    public boolean hasIndex() {
        return true;
    }

    @Override
    public BAMIndex getIndex() {
        return index;
    }

    @Override
    public SAMFileHeader getFileHeader() {
        return virtualHeader;
    }

    @Override
    public CloseableIterator<SAMRecord> getIterator() {
        return getIterator(getFilePointerSpanningReads());
    }

    @Override
    public CloseableIterator<SAMRecord> getIterator(SAMFileSpan chunks) {
        if (run == null) {
            throw new RuntimeException("Cannot create iterator - SRA run is uninitialized");
        }

        if (virtualHeader == null) {
            throw new RuntimeException("Cannot create iterator - SAM file header is uninitialized");
        }

        List<Chunk> chunkList = ((BAMFileSpan) chunks).getChunks();

        final SRAIterator newIterator = new SRAIterator(acc, run, virtualHeader, cachedReferences, recordRangeInfo, chunkList);
        if (validationStringency != null) {
            newIterator.setValidationStringency(validationStringency);
        }

        return newIterator;
    }

    @Override
    public SAMFileSpan getFilePointerSpanningReads() {
        if (recordRangeInfo.getTotalRecordRangeLength() <= 0) {
            throw new RuntimeException("Cannot create file span - SRA file is empty");
        }

        return new BAMFileSpan(new Chunk(0, recordRangeInfo.getTotalRecordRangeLength()));
    }

    @Override
    public CloseableIterator<SAMRecord> query(QueryInterval[] intervals, boolean contained) {
        BAMFileSpan span = new BAMFileSpan();
        BrowseableBAMIndex index = getBrowseableIndex();

        for (QueryInterval interval : intervals) {
            BAMFileSpan intervalSpan;
            if (!contained) {
                intervalSpan = index.getSpanOverlapping(interval.referenceIndex, interval.start, interval.end);

            } else {
                intervalSpan = getSpanContained(interval.referenceIndex, interval.start, interval.end);
            }
            span.add(intervalSpan);
        }

        return getIterator(span);
    }

    @Override
    public CloseableIterator<SAMRecord> queryAlignmentStart(String sequence, int start) {
        int sequenceIndex = virtualHeader.getSequenceIndex(sequence);
        if (sequenceIndex == -1) {
            throw new IllegalArgumentException("Unknown sequence '" + sequence + "' was passed to SRAFileReader");
        }

        return getIterator(getSpanContained(sequenceIndex, start, -1));
    }

    @Override
    public CloseableIterator<SAMRecord> queryUnmapped() {
        if (recordRangeInfo.getTotalRecordRangeLength() <= 0) {
            throw new RuntimeException("Cannot create file span - SRA file is empty");
        }

        SAMFileSpan span = new BAMFileSpan(new Chunk(recordRangeInfo.getTotalReferencesLength(), recordRangeInfo.getTotalRecordRangeLength()));
        return getIterator(span);
    }

    @Override
    public void close() {
        run = null;
    }

    @Override
    public ValidationStringency getValidationStringency() {
        return validationStringency;
    }


    /** INDEXING */


    /**
     * Returns true if the supported index is browseable, meaning the bins in it can be traversed
     * and chunk data inspected and retrieved.
     *
     * @return True if the index supports the BrowseableBAMIndex interface.  False otherwise.
     */
    @Override
    public boolean hasBrowseableIndex() {
        return true;
    }

    /**
     * Gets an index tagged with the BrowseableBAMIndex interface.  Throws an exception if no such
     * index is available.
     *
     * @return An index with a browseable interface, if possible.
     * @throws SAMException if no such index is available.
     */
    @Override
    public BrowseableBAMIndex getBrowseableIndex() {
        return index;
    }

    /**
     * Iterate through the given chunks in the file.
     *
     * @param chunks List of chunks for which to retrieve data.
     * @return An iterator over the given chunks.
     */
    @Override
    public SAMRecordIterator iterator(final SAMFileSpan chunks) {
        CloseableIterator<SAMRecord> it = getIterator(chunks);
        if (it == null) {
            return null;
        }
        return (SAMRecordIterator) it;
    }

    /** ReaderImplementation */
    @Override
    void enableFileSource(final SamReader reader, final boolean enabled) {
        log.info("enableFileSource is not supported");
    }

    @Override
    void enableIndexCaching(final boolean enabled) {
        log.info("enableIndexCaching is not supported");
    }

    @Override
    void enableIndexMemoryMapping(final boolean enabled) {
        log.info("enableIndexMemoryMapping is not supported");
    }

    @Override
    void enableCrcChecking(final boolean enabled) {
        log.info("enableCrcChecking is not supported");
    }

    @Override
    void setSAMRecordFactory(final SAMRecordFactory factory) {
        log.info("setSAMRecordFactory is not supported");
    }

    @Override
    void setValidationStringency(final ValidationStringency validationStringency) {
        this.validationStringency = validationStringency;
    }

    protected SRAIterator.RecordRangeInfo getRecordsRangeInfo() {
        return recordRangeInfo;
    }

    private SAMFileHeader loadSamHeader() throws ErrorMsg {
        if (run == null) {
            throw new RuntimeException("Cannot load SAMFileHeader - SRA run is uninitialized");
        }

        String runName = run.getName();

        SAMFileHeader header = new SAMFileHeader();
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);

        ReadGroupIterator itRg = run.getReadGroups();
        while (itRg.nextReadGroup()) {
            String rgName = itRg.getName();
            if (rgName.isEmpty())
                rgName = runName;
            SAMReadGroupRecord rg = new SAMReadGroupRecord(rgName);
            rg.setSample(runName);
            header.addReadGroup(rg);
        }

        ReferenceIterator itRef = run.getReferences();
        while (itRef.nextReference()) {
            header.addSequence(new SAMSequenceRecord(itRef.getCanonicalName(), (int) itRef.getLength()));
        }

        return header;
    }

    private BAMFileSpan getSpanContained(int sequenceIndex, long start, long end) {
        if (recordRangeInfo.getTotalRecordRangeLength() <= 0) {
            throw new RuntimeException("Cannot create file span - SRA file is empty");
        }

        long sequenceOffset = recordRangeInfo.getReferenceOffsets().get(sequenceIndex);
        long sequenceLength = recordRangeInfo.getReferenceLengthsAligned().get(sequenceIndex);
        if (end == -1) {
            end = sequenceLength;
        }

        if (start > sequenceLength) {
            throw new IllegalArgumentException("Sequence start position is larger than its length");
        }

        if (end > sequenceLength) {
            throw new IllegalArgumentException("Sequence end position is larger than its length");
        }

        return new BAMFileSpan(new Chunk(sequenceOffset + start, sequenceOffset + end));
    }
}
