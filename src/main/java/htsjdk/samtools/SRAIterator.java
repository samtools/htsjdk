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

import htsjdk.samtools.SAMFileHeader.SortOrder;

import htsjdk.samtools.sra.ReferenceCache;
import htsjdk.samtools.sra.SRAAccession;
import htsjdk.samtools.sra.SRAAlignmentIterator;
import htsjdk.samtools.sra.SRAUnalignmentIterator;
import htsjdk.samtools.sra.SRAUtils;
import ngs.ErrorMsg;
import ngs.ReadCollection;
import ngs.Reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * SRA iterator which returns SAMRecords for requested list of chunks
 */
public class SRAIterator implements SAMRecordIterator {
    private ValidationStringency validationStringency;

    private SRAAccession accession;
    private ReadCollection run;
    private SAMFileHeader header;
    private ReferenceCache cachedReferences;
    private RecordRangeInfo recordRangeInfo;
    private Iterator<Chunk> chunksIterator;
    private Chunk currentChunk;

    private SRAAlignmentIterator alignmentIterator;
    private SRAUnalignmentIterator unalignmentIterator;

    /**
     * Describes record ranges info needed for emulating BAM index
     */
    public static class RecordRangeInfo {
        private List<Long> referenceOffsets;
        private List<Long> referenceLengthsAligned;
        private long totalReferencesLength;
        private long numberOfReads; // is used for unaligned read space
        private long totalRecordRangeLength;

        /**
         * @param referenceLengthsAligned a list with lengths of each reference
         * @param numberOfReads total number of reads within SRA archive
         */
        public RecordRangeInfo(List<Long> referenceLengthsAligned, long numberOfReads) {
            this.numberOfReads = numberOfReads;
            this.referenceLengthsAligned = referenceLengthsAligned;

            referenceOffsets = new ArrayList<Long>();

            totalReferencesLength = 0;
            for (Long refLen : referenceLengthsAligned) {
                referenceOffsets.add(totalReferencesLength);
                totalReferencesLength += refLen;
            }

            totalRecordRangeLength = totalReferencesLength + this.numberOfReads;
        }

        public long getNumberOfReads() {
            return numberOfReads;
        }

        public long getTotalReferencesLength() {
            return totalReferencesLength;
        }

        public long getTotalRecordRangeLength() {
            return totalRecordRangeLength;
        }

        public final List<Long> getReferenceOffsets() {
            return Collections.unmodifiableList(referenceOffsets);
        }

        public final List<Long> getReferenceLengthsAligned() {
            return Collections.unmodifiableList(referenceLengthsAligned);
        }
    }

    /**
     * Loads record ranges needed for emulating BAM index
     * @param run read collection
     * @return record ranges
     */
    public static RecordRangeInfo getRecordsRangeInfo(ReadCollection run) {
        try {
            return new RecordRangeInfo(SRAUtils.getReferencesLengthsAligned(run), SRAUtils.getNumberOfReads(run));
        } catch (ErrorMsg e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param run opened read collection
     * @param header sam header
     * @param cachedReferences list of cached references shared among all iterators from a single SRAFileReader
     * @param recordRangeInfo info about record ranges withing SRA archive
     * @param chunks used to determine which records the iterator should return
     */
    public SRAIterator(SRAAccession accession, final ReadCollection run, final SAMFileHeader header, ReferenceCache cachedReferences,
                       final RecordRangeInfo recordRangeInfo, final List<Chunk> chunks) {
        this.accession = accession;
        this.run = run;
        this.header = header;
        this.cachedReferences = cachedReferences;
        this.recordRangeInfo = recordRangeInfo;
        chunksIterator = chunks.iterator();
        if (chunksIterator.hasNext()) {
            currentChunk = chunksIterator.next();
        }

        hasNext();
    }

    /**
     * NGS iterators implement a single method "nextObject" which return true if the operation was successful or
     * false if there are no more objects available.
     * That means that there is no way to check "hasNext" without actually moving the iterator forward.
     * Because of that all the logic of moving iterator forward is actually happens in "hasNext".
     *
     * Here is explanation of how it works:
     *  Iterator holds a list of chunks of requested records. Here we have chunksIterator that walks though that list.
     *  We walk though that list using chunksIterator. If current chunk can represent aligned fragments then we create
     *  SRAAlignmentIterator iterator, pass the chunk into it and ask if it can find any record. If record was found,
     *  we say that we have next; otherwise we check if the chunk can represent unaligned fragments and then create
     *  SRAUnalignmentIterator if so and do the same steps as with alignemnt iterator.
     *
     *  If record was not found in both SRAAlignmentIterator and SRAUnalignmentIterator (it is possible that reference
     *  range has no alignments or that reads range has all aligned fragment), we try the next chunk.
     *
     *  When there are no more chunks and both iterators have no more records we return false.
     *
     * @return true if there are more records available
     */
    @Override
    public boolean hasNext() {
        while (currentChunk != null) {
            if (alignmentIterator == null) {
                if (currentChunk.getChunkStart() < recordRangeInfo.getTotalReferencesLength()) {
                    alignmentIterator = new SRAAlignmentIterator(accession, run, header, cachedReferences, recordRangeInfo, currentChunk);
                    if (validationStringency != null) {
                        alignmentIterator.setValidationStringency(validationStringency);
                    }
                }
            }

            if (alignmentIterator != null && alignmentIterator.hasNext()) {
                return true;
            }

            if (unalignmentIterator == null) {
                if (currentChunk.getChunkEnd() > recordRangeInfo.getTotalReferencesLength()) {
                    unalignmentIterator = new SRAUnalignmentIterator(accession, run, header, recordRangeInfo, currentChunk);
                    if (validationStringency != null) {
                        unalignmentIterator.setValidationStringency(validationStringency);
                    }
                }
            }
            if (unalignmentIterator != null && unalignmentIterator.hasNext()) {
                return true;
            }

            if (alignmentIterator != null) {
                alignmentIterator.close();
            }
            alignmentIterator = null;
            unalignmentIterator = null;
            if (chunksIterator.hasNext()) {
                currentChunk = chunksIterator.next();
            } else {
                currentChunk = null;
            }
        }
        return false;
    }

    /**
     * Call hasNext to make sure that one of inner iterators points to the next record, the retrieve the record from
     * one of them.
     * @return lazy SRA record
     */
    @Override
    public SAMRecord next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more records are available in SRAIterator");
        }

        if (alignmentIterator != null && alignmentIterator.hasNext()) {
            return alignmentIterator.next();
        }

        return unalignmentIterator.next();
    }

    @Override
    public void remove() { throw new UnsupportedOperationException("Removal of records not implemented."); }

    @Override
    public void close() {
        if (alignmentIterator != null) {
            alignmentIterator.close();
            alignmentIterator = null;
        }
    }

    @Override
    public SAMRecordIterator assertSorted(final SortOrder sortOrder) { throw new UnsupportedOperationException("assertSorted is not implemented."); }

    public void setValidationStringency(ValidationStringency validationStringency) {
        this.validationStringency = validationStringency;

        if (alignmentIterator != null) {
            alignmentIterator.setValidationStringency(validationStringency);
        }
        if (unalignmentIterator != null) {
            unalignmentIterator.setValidationStringency(validationStringency);
        }
    }
}
