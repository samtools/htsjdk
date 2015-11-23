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
import ngs.ErrorMsg;
import ngs.Read;
import ngs.ReadCollection;
import ngs.ReadIterator;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Iterator for unaligned reads.
 * Is used from SRAIterator.
 *
 * Created by andrii.nikitiuk on 9/3/15.
 */
public class SRAUnalignmentIterator implements Iterator<SAMRecord> {
    private ValidationStringency validationStringency;

    private SRAAccession accession;
    private ReadCollection run;
    private SAMFileHeader header;
    private SRAIterator.RecordRangeInfo recordRangeInfo;

    private ReadIterator unalignedIterator;
    private boolean hasMoreUnalignedReads = true;
    private Boolean hasMoreUnalignedFragments = false;
    private int lastUnalignedFragmentIndex;

    private SRALazyRecord lastRecord;

    /**
     *
     * @param run opened read collection
     * @param header sam header
     * @param recordRangeInfo info about record ranges withing SRA archive
     * @param chunk used to determine which unaligned reads the iterator should return
     */
    public SRAUnalignmentIterator(SRAAccession accession, final ReadCollection run, final SAMFileHeader header, SRAIterator.RecordRangeInfo recordRangeInfo, Chunk chunk) {
        this.accession = accession;
        this.run = run;
        this.header = header;
        this.recordRangeInfo = recordRangeInfo;

        long readStart = chunk.getChunkStart() - recordRangeInfo.getTotalReferencesLength();
        if (readStart < 0) {
            readStart = 0;
        } else if (readStart >= recordRangeInfo.getNumberOfReads()) {
            throw new RuntimeException("Invalid chunk provided: chunkStart position is after last read");
        }

        long readEnd = chunk.getChunkEnd() - recordRangeInfo.getTotalReferencesLength();
        if (readEnd > recordRangeInfo.getNumberOfReads()) {
            readEnd = recordRangeInfo.getNumberOfReads();
        } else if (readEnd <= 0) {
            throw new RuntimeException("Invalid chunk provided: chunkEnd position is before last read");
        }

        try {
            unalignedIterator = run.getReadRange(readStart + 1, readEnd - readStart, Read.partiallyAligned | Read.unaligned);
            nextUnalignedFragment();

        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean hasNext() {
        // check unaligned
        if (hasMoreUnalignedFragments == null) {
            try {
                lastRecord.detachFromIterator();
                nextUnalignedFragment();
            } catch (ErrorMsg e) {
                throw new RuntimeException(e);
            }
        }
        return hasMoreUnalignedFragments;
    }

    @Override
    public SAMRecord next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more alignments are available");
        }

        return nextUnalignment();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Removal of records not implemented.");
    }

    public void setValidationStringency(ValidationStringency validationStringency) {
        this.validationStringency = validationStringency;
    }

    private SAMRecord nextUnalignment() {
        try {
            lastRecord = new SRALazyRecord(header, accession, run, unalignedIterator, unalignedIterator.getReadId(), lastUnalignedFragmentIndex);
        } catch (ErrorMsg e) {
            throw new RuntimeException(e);
        }

        if (validationStringency != null) {
            lastRecord.setValidationStringency(validationStringency);
        }

        hasMoreUnalignedFragments = null;

        return lastRecord;
    }

    private void nextUnalignedFragment() throws ErrorMsg {
        while (hasMoreUnalignedFragments == null || hasMoreUnalignedFragments) {
            hasMoreUnalignedFragments = unalignedIterator.nextFragment();
            lastUnalignedFragmentIndex++;

            if (hasMoreUnalignedFragments && !unalignedIterator.isAligned()) {
                return;
            }
        }

        if (!hasMoreUnalignedReads) {
            throw new RuntimeException("Cannot get next unaligned read - already at last one");
        }

        while (true) {
            hasMoreUnalignedReads = unalignedIterator.nextRead();
            lastUnalignedFragmentIndex = -1;
            if (!hasMoreUnalignedReads) {
                break;
            }

            // search for unaligned fragment
            do {
                hasMoreUnalignedFragments = unalignedIterator.nextFragment();
                lastUnalignedFragmentIndex++;
            } while (hasMoreUnalignedFragments && unalignedIterator.isAligned());

            // means that we found fragment
            if (hasMoreUnalignedFragments) {
                return;
            }
        }
    }
}
