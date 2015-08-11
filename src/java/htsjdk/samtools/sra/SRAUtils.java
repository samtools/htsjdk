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

import ngs.ErrorMsg;
import ngs.Read;
import ngs.ReadCollection;
import ngs.ReferenceIterator;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides some functionality which can be used by other classes
 *
 * Created by andrii.nikitiuk on 10/28/15.
 */
public class SRAUtils {
    /**
     * References are stored in SRA table in chunks of 5k bases per row, while last chunk of a reference is less or
     * equal than 5k bases in size (even if the next reference follows).
     * So, it will be optimal if we align reference sizes to 5k bases to read by reference rows.
     */
    public static final int REFERENCE_ALIGNMENT = 5000;

    /**
     * Is used to build RecordRangeInfo
     * @param run open read collection
     * @return total number of reads (both aligned and unaligned) in SRA archive
     * @throws ErrorMsg
     */
    public static long getNumberOfReads(ReadCollection run) throws ErrorMsg {
        return run.getReadCount(Read.all);
    }

    /**
     * Loads reference lengths from a read collection.
     * Aligns reference lengths by REFERENCE_ALIGNMENT bases for optimal loads of alignments
     * (references are stored in REFERENCE_ALIGNMENT bases chunks in SRA table)
     *
     * Is used to build RecordRangeInfo
     * @param run single opened read collection
     * @return list with references lengths
     * @throws ErrorMsg
     */
    public static List<Long> getReferencesLengthsAligned(ReadCollection run) throws ErrorMsg {
        ReferenceIterator refIt = run.getReferences();
        List<Long> lengths = new ArrayList<Long>();
        while (refIt.nextReference()) {
            long refLen = refIt.getLength();
            // lets optimize references so they always align in 5000 bases positions
            if (refLen % REFERENCE_ALIGNMENT != 0) {
                refLen += REFERENCE_ALIGNMENT - (refLen % REFERENCE_ALIGNMENT);
            }
            lengths.add(refLen);
        }
        return lengths;
    }
}
