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

import htsjdk.samtools.BAMFileSpan;
import htsjdk.samtools.Bin;
import htsjdk.samtools.GenomicIndexUtil;
import htsjdk.samtools.SRAFileReader;
import htsjdk.samtools.SRAIndex;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Unit tests for SRAIndex
 *
 * Created by andrii.nikitiuk on 10/28/15.
 */
public class SRAIndexTest extends AbstractSRATest {
    private static final SRAAccession DEFAULT_ACCESSION = new SRAAccession("SRR1298981");
    private static final int LAST_BIN_LEVEL = GenomicIndexUtil.LEVEL_STARTS.length - 1;
    private static final int SRA_BIN_OFFSET = GenomicIndexUtil.LEVEL_STARTS[LAST_BIN_LEVEL];

    @Test
    public void testLevelSize() {
        final SRAIndex index = getIndex(DEFAULT_ACCESSION);
        Assert.assertEquals(index.getLevelSize(0), GenomicIndexUtil.LEVEL_STARTS[1] - GenomicIndexUtil.LEVEL_STARTS[0]);

        Assert.assertEquals(index.getLevelSize(LAST_BIN_LEVEL), GenomicIndexUtil.MAX_BINS - GenomicIndexUtil.LEVEL_STARTS[LAST_BIN_LEVEL] - 1);
    }

    @Test
    public void testLevelForBin() {
        final SRAIndex index = getIndex(DEFAULT_ACCESSION);
        final Bin bin = new Bin(0, SRA_BIN_OFFSET);
        Assert.assertEquals(index.getLevelForBin(bin), LAST_BIN_LEVEL);
    }

    @DataProvider(name = "testBinLocuses")
    private Object[][] createDataForBinLocuses() {
        return new Object[][] {
                {DEFAULT_ACCESSION, 0, 0, 1, SRAIndex.SRA_BIN_SIZE},
                {DEFAULT_ACCESSION, 0, 1, SRAIndex.SRA_BIN_SIZE + 1, SRAIndex.SRA_BIN_SIZE * 2}
        };
    }

    @Test(dataProvider = "testBinLocuses")
    public void testBinLocuses(SRAAccession acc, int reference, int binIndex, int firstLocus, int lastLocus) {
        final SRAIndex index = getIndex(acc);
        final Bin bin = new Bin(reference, SRA_BIN_OFFSET + binIndex);

        Assert.assertEquals(index.getFirstLocusInBin(bin), firstLocus);
        Assert.assertEquals(index.getLastLocusInBin(bin), lastLocus);
    }

    @DataProvider(name = "testBinOverlappings")
    private Object[][] createDataForBinOverlappings() {
        return new Object[][] {
                {DEFAULT_ACCESSION, 0, 1, SRAIndex.SRA_BIN_SIZE, new HashSet<>(Arrays.asList(0))},
                {DEFAULT_ACCESSION, 0, SRAIndex.SRA_BIN_SIZE + 1, SRAIndex.SRA_BIN_SIZE * 2, new HashSet<>(Arrays.asList(1))},
                {DEFAULT_ACCESSION, 0, SRAIndex.SRA_BIN_SIZE + 1, SRAIndex.SRA_BIN_SIZE * 3, new HashSet<>(Arrays.asList(1, 2))},
                {DEFAULT_ACCESSION, 0, SRAIndex.SRA_BIN_SIZE * 2, SRAIndex.SRA_BIN_SIZE * 2 + 1, new HashSet<>(Arrays.asList(1, 2))}
        };
    }


    @Test(dataProvider = "testBinOverlappings")
    public void testBinOverlappings(SRAAccession acc, int reference, int firstLocus, int lastLocus, Set<Integer> binNumbers) {
        final SRAIndex index = getIndex(acc);
        final Iterator<Bin> binIterator = index.getBinsOverlapping(reference, firstLocus, lastLocus).iterator();
        final Set<Integer> binNumbersFromIndex = new HashSet<>();
        while (binIterator.hasNext()) {
            final Bin bin = binIterator.next();
            binNumbersFromIndex.add(bin.getBinNumber() - SRA_BIN_OFFSET);
        }

        Assert.assertEquals(binNumbers, binNumbersFromIndex);
    }

    @DataProvider(name = "testSpanOverlappings")
    private Object[][] createDataForSpanOverlappings() {
        return new Object[][] {
                {DEFAULT_ACCESSION, 0, 1, SRAIndex.SRA_BIN_SIZE, new long[] {0, SRAIndex.SRA_CHUNK_SIZE} },
                {DEFAULT_ACCESSION, 0, SRAIndex.SRA_BIN_SIZE * 2, SRAIndex.SRA_BIN_SIZE * 2 + 1, new long[]{0, SRAIndex.SRA_CHUNK_SIZE} },
                {DEFAULT_ACCESSION, 0, SRAIndex.SRA_CHUNK_SIZE, SRAIndex.SRA_CHUNK_SIZE + 1, new long[]{0, SRAIndex.SRA_CHUNK_SIZE, SRAIndex.SRA_CHUNK_SIZE, SRAIndex.SRA_CHUNK_SIZE * 2} },
        };
    }

    @Test(dataProvider = "testSpanOverlappings")
    public void testSpanOverlappings(SRAAccession acc, int reference, int firstLocus, int lastLocus, long[] spanCoordinates) {
        final SRAIndex index = getIndex(acc);
        final BAMFileSpan span = index.getSpanOverlapping(reference, firstLocus, lastLocus);

        long[] coordinatesFromIndex = span.toCoordinateArray();

        Assert.assertTrue(Arrays.equals(coordinatesFromIndex, spanCoordinates),
                "Coordinates mismatch. Expected: " + Arrays.toString(spanCoordinates) +
                " but was : " + Arrays.toString(coordinatesFromIndex));
    }

    private SRAIndex getIndex(SRAAccession acc) {
        final SRAFileReader reader = new SRAFileReader(acc);
        return (SRAIndex) reader.getIndex();
    }
}
