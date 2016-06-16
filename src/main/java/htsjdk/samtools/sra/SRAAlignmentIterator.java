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

package htsjdk.samtools.sra;


import htsjdk.samtools.Chunk;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SRAIterator;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.util.CloseableIterator;
import ngs.Alignment;
import ngs.AlignmentIterator;
import ngs.ErrorMsg;
import ngs.ReadCollection;
import ngs.Reference;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;


/**
 * Iterator for aligned reads.
 * Is used from SRAIterator.
 * Created by andrii.nikitiuk on 9/3/15.
 */
public class SRAAlignmentIterator implements CloseableIterator<SAMRecord> {
    private ValidationStringency validationStringency;

    private SRAAccession accession;
    private ReadCollection run;
    private SAMFileHeader header;
    private ReferenceCache cachedReferences;
    private List<Long> referencesLengths;
    private Iterator<Chunk> referencesChunksIterator;
    private int currentReference = -1;

    private boolean hasMoreReferences = true;

    private AlignmentIterator alignedIterator;
    private Boolean hasMoreAlignments = false;

    private SRALazyRecord lastRecord;

    /**
     * @param run opened read collection
     * @param header sam header
     * @param cachedReferences list of cached references shared among all iterators from a single SRAFileReader
     * @param recordRangeInfo info about record ranges withing SRA archive
     * @param chunk used to determine which alignments the iterator should return
     */
    public SRAAlignmentIterator(SRAAccession accession, final ReadCollection run, final SAMFileHeader header, ReferenceCache cachedReferences,
                                final SRAIterator.RecordRangeInfo recordRangeInfo, final Chunk chunk) {
        this.accession = accession;
        this.run = run;
        this.header = header;
        this.cachedReferences = cachedReferences;
        this.referencesLengths = recordRangeInfo.getReferenceLengthsAligned();

        referencesChunksIterator = getReferenceChunks(chunk).iterator();

        try {
            nextReference();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean hasNext() {
        // check aligned
        if (lastRecord != null) {
            lastRecord.detachFromIterator();
            lastRecord = null;
        }

        if (hasMoreAlignments == null) {
            try {
                hasMoreAlignments = alignedIterator.nextAlignment();
            } catch (ErrorMsg e) {
                throw new RuntimeException(e);
            }
        }
        while (!hasMoreAlignments && hasMoreReferences) {
            nextReference();
        }

        return hasMoreAlignments;
    }

    @Override
    public SAMRecord next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more alignments are available");
        }

        return nextAlignment();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Removal of records not implemented.");
    }

    public void setValidationStringency(ValidationStringency validationStringency) {
        this.validationStringency = validationStringency;
    }

    private SAMRecord nextAlignment() {
        try {
            lastRecord = new SRALazyRecord(header, accession, run, alignedIterator, alignedIterator.getReadId(), alignedIterator.getAlignmentId());
        } catch (ErrorMsg e) {
            throw new RuntimeException(e);
        }
        if (validationStringency != null) {
            lastRecord.setValidationStringency(validationStringency);
        }

        hasMoreAlignments = null;

        return lastRecord;
    }

    private void nextReference() {
        if (!hasMoreReferences) {
            throw new NoSuchElementException("Cannot get next reference - already at last one");
        }

        try {
            alignedIterator = null;
            hasMoreAlignments = false;

            hasMoreReferences = referencesChunksIterator.hasNext();
            if (!hasMoreReferences) {
                return;
            }

            currentReference++;
            Chunk refChunk = referencesChunksIterator.next();
            if (refChunk == null) {
                return;
            }

            Reference reference = cachedReferences.get(currentReference);

            alignedIterator = reference.getFilteredAlignmentSlice(
                    refChunk.getChunkStart(), refChunk.getChunkEnd() - refChunk.getChunkStart(),
                    Alignment.all, Alignment.startWithinSlice | Alignment.passDuplicates | Alignment.passFailed, 0);

            hasMoreAlignments = alignedIterator.nextAlignment();
        } catch (ErrorMsg e) {
            throw new RuntimeException(e);
        }
    }

    private List<Chunk> getReferenceChunks(final Chunk chunk) {
        List<Chunk> referencesChunks = new ArrayList<Chunk>();
        long refOffset = 0;
        for (Long refLen : referencesLengths) {
            if (chunk.getChunkStart() - refOffset >= refLen || chunk.getChunkEnd() - refOffset <= 0) {
                referencesChunks.add(null);
            } else {
                long refChunkStart = Math.max(chunk.getChunkStart() - refOffset, 0);
                long refChunkEnd = Math.min(chunk.getChunkEnd() - refOffset, refLen);
                referencesChunks.add(new Chunk(refChunkStart, refChunkEnd));
            }

            refOffset += refLen;
        }

        return referencesChunks;
    }

    @Override
    public void close() {
        if (lastRecord != null) {
            lastRecord.detachFromIterator();
            lastRecord = null;
        }

        alignedIterator = null;
    }
}
