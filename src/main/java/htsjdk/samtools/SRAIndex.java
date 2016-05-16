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

package htsjdk.samtools;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Emulates BAM index so that we can request chunks of records from SRAFileReader
 *
 * Here is how it works:
 *  SRA allows reading of alignments by Reference position fast, so we divide our "file" range for alignments as
 *  a length of all references. Reading unaligned reads is then fast if we use read positions for lookup and (internally)
 *  filter out aligned fragments.
 *
 *  Total SRA "file" range is calculated as sum of all reference lengths plus number of reads (both aligned and unaligned)
 *  in SRA archive.
 *
 *  Now, we can use Chunks to lookup for aligned and unaligned fragments.
 *
 *  We emulate BAM index bins by mapping SRA reference positions to bin numbers.
 *  And then we map from bin number to list of chunks, which represent SRA "file" positions (which are simply reference
 *  positions).
 *
 *  We only emulate last level of BAM index bins (and they refer to a portion of reference SRA_BIN_SIZE bases long).
 *  For all other bins RuntimeException will be returned (but since nobody else creates bins, except SRAIndex class
 *  that is fine).
 *
 *  But since the last level of bins was not meant to refer to fragments that only partially overlap bin reference
 *  positions, we also return chunk that goes 5000 bases left before beginning of the bin to assure fragments that
 *  start before the bin positions but still overlap with it can be retrieved by SRA reader.
 *  Later we will add support to NGS API to get a maximum number of bases that we need to go left to retrieve such fragments.
 *
 * Created by andrii.nikitiuk on 9/4/15.
 */
public class SRAIndex implements BrowseableBAMIndex {
    /**
     * Number of reference bases bins in last level can represent
     */
    public static final int SRA_BIN_SIZE = 16 * 1024;

    /**
     * Chunks of that size will be created when using SRA index
     */
    public static final int SRA_CHUNK_SIZE = 50000;

    /**
     * First bin number in last level
     */
    private static final int SRA_BIN_INDEX_OFFSET = GenomicIndexUtil.LEVEL_STARTS[GenomicIndexUtil.LEVEL_STARTS.length - 1];

    /**
     * How many bases should we go left on the reference to find all fragments that start before requested interval
     * but overlap with it
     */
    private static final int MAX_FRAGMENT_OVERLAP = 5000;

    private SAMFileHeader header;
    private SRAIterator.RecordRangeInfo recordRangeInfo;

    /**
     * @param header sam header
     * @param recordRangeInfo info about record ranges withing SRA archive
     */
    public SRAIndex(SAMFileHeader header, SRAIterator.RecordRangeInfo recordRangeInfo) {
        this.header = header;
        this.recordRangeInfo = recordRangeInfo;
    }

    /**
     * Gets the size (number of bins in) a given level of a BAM index.
     * @param levelNumber Level for which to inspect the size.
     * @return Size of the given level.
     */
    @Override
    public int getLevelSize(int levelNumber) {
        if (levelNumber == GenomicIndexUtil.LEVEL_STARTS.length - 1)
            return GenomicIndexUtil.MAX_BINS - GenomicIndexUtil.LEVEL_STARTS[levelNumber]-1;
        else
            return GenomicIndexUtil.LEVEL_STARTS[levelNumber+1] - GenomicIndexUtil.LEVEL_STARTS[levelNumber];
    }

    /**
     * SRA only operates on bins from last level
     * @param bin The bin  for which to determine the level.
     * @return bin level
     */
    @Override
    public int getLevelForBin(Bin bin) {
        if (bin.getBinNumber() < SRA_BIN_INDEX_OFFSET) {
            throw new RuntimeException("SRA only supports bins from the last level");
        }
        return GenomicIndexUtil.LEVEL_STARTS.length - 1;
    }

    /**
     * Gets the first locus that this bin can index into.
     * @param bin The bin to test.
     * @return first position that associated with given bin number
     */
    @Override
    public int getFirstLocusInBin(Bin bin) {
        if (bin.getBinNumber() < SRA_BIN_INDEX_OFFSET) {
            throw new RuntimeException("SRA only supports bins from the last level");
        }

        return (bin.getBinNumber() - SRA_BIN_INDEX_OFFSET) * SRA_BIN_SIZE + 1;
    }

    /**
     * Gets the last locus that this bin can index into.
     * @param bin The bin to test.
     * @return last position that associated with given bin number
     */
    @Override
    public int getLastLocusInBin(Bin bin) {
        if (bin.getBinNumber() < SRA_BIN_INDEX_OFFSET) {
            throw new RuntimeException("SRA only supports bins from the last level");
        }

        return (bin.getBinNumber() - SRA_BIN_INDEX_OFFSET + 1) * SRA_BIN_SIZE;
    }

    /**
     * Provides a list of bins that contain bases at requested positions
     * @param referenceIndex sequence of desired SAMRecords
     * @param startPos 1-based start of the desired interval, inclusive
     * @param endPos 1-based end of the desired interval, inclusive
     * @return a list of bins that contain relevant data
     */
    @Override
    public BinList getBinsOverlapping(int referenceIndex, int startPos, int endPos) {
        long refLength = recordRangeInfo.getReferenceLengthsAligned().get(referenceIndex);

        // convert to chunk address space within reference
        long refStartPos =  startPos - 1;
        long refEndPos = endPos;
        if (refEndPos >= refLength) {
            throw new RuntimeException("refEndPos is larger than reference length");
        }

        int firstBinNumber = (int)refStartPos / SRA_BIN_SIZE;
        int lastBinNumber = (int)(refEndPos - 1) / SRA_BIN_SIZE;

        int numberOfBins = ((int)refLength / SRA_BIN_SIZE) + 1;

        BitSet binBitSet = new BitSet();
        binBitSet.set(0, SRA_BIN_INDEX_OFFSET, false);
        if (firstBinNumber > 0) {
            binBitSet.set(SRA_BIN_INDEX_OFFSET, SRA_BIN_INDEX_OFFSET + firstBinNumber, false);
        }
        binBitSet.set(SRA_BIN_INDEX_OFFSET + firstBinNumber, SRA_BIN_INDEX_OFFSET + lastBinNumber + 1, true);
        if (lastBinNumber + 1 < numberOfBins) {
            binBitSet.set(SRA_BIN_INDEX_OFFSET + lastBinNumber + 1, SRA_BIN_INDEX_OFFSET + numberOfBins, false);
        }

        return new BinList(referenceIndex, binBitSet);
    }

    @Override
    public BAMFileSpan getSpanOverlapping(Bin bin) {
        return new BAMFileSpan(getBinChunks(bin));
    }

    @Override
    public BAMFileSpan getSpanOverlapping(int referenceIndex, int startPos, int endPos) {
        BinList binList = getBinsOverlapping(referenceIndex, startPos, endPos);
        BAMFileSpan result = new BAMFileSpan();
        Set<Chunk> savedChunks = new HashSet<Chunk>();
        for (Bin bin : binList) {
            List<Chunk> chunks = getSpanOverlapping(bin).getChunks();
            for (Chunk chunk : chunks) {
                if (!savedChunks.contains(chunk)) {
                    savedChunks.add(chunk);
                    result.add(chunk);
                }
            }
        }

        return result;
    }

    /**
     * @return a position where aligned fragments end
     */
    @Override
    public long getStartOfLastLinearBin() {
        int numberOfReferences = recordRangeInfo.getReferenceLengthsAligned().size();
        long refOffset = recordRangeInfo.getReferenceOffsets().get(numberOfReferences - 1);
        long lastChunkNumber = recordRangeInfo.getReferenceLengthsAligned().get(numberOfReferences - 1) / SRA_CHUNK_SIZE;
        return lastChunkNumber * SRA_CHUNK_SIZE + refOffset;
    }

    @Override
    public BAMIndexMetaData getMetaData(int reference) {
        throw new UnsupportedOperationException("Getting of BAM index metadata for SRA is not implemented");
    }

    @Override
    public void close() { }

    /**
     * @param bin Requested bin
     * @return chunks that represent all bases of requested bin
     */
    private List<Chunk> getBinChunks(Bin bin) {
        if (bin.containsChunks()) {
            return bin.getChunkList();
        }

        if (bin.getBinNumber() < SRA_BIN_INDEX_OFFSET) {
            throw new RuntimeException("SRA only supports bins from the last level");
        }
        int binNumber = bin.getBinNumber() - SRA_BIN_INDEX_OFFSET;
        long refOffset = recordRangeInfo.getReferenceOffsets().get(bin.getReferenceSequence());

        // move requested position MAX_FRAGMENT_OVERLAP bases behind, so that we take all the reads that overlap requested position
        int firstChunkCorrection = binNumber == 0 ? 0 : -MAX_FRAGMENT_OVERLAP;

        long binGlobalOffset = binNumber * SRA_BIN_SIZE + refOffset;
        long firstChunkNumber = (binGlobalOffset + firstChunkCorrection) / SRA_CHUNK_SIZE;
        long lastChunkNumber = (binGlobalOffset + SRA_BIN_SIZE - 1) / SRA_CHUNK_SIZE;
        List<Chunk> chunks = new ArrayList<Chunk>();
        for (long chunkNumber = firstChunkNumber; chunkNumber <= lastChunkNumber; chunkNumber++) {
            chunks.add(new Chunk(chunkNumber * SRA_CHUNK_SIZE, (chunkNumber + 1) * SRA_CHUNK_SIZE));
        }

        return chunks;
    }
}
