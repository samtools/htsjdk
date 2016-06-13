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
import htsjdk.samtools.BrowseableBAMIndex;
import htsjdk.samtools.Chunk;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.SAMUtils;
import htsjdk.samtools.SAMValidationError;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Integration tests for SRA functionality
 *
 * Created by andrii.nikitiuk on 8/24/15.
 */
public class SRATest extends AbstractSRATest {

    @DataProvider(name = "testCounts")
    private Object[][] createDataForCounts() {
        return new Object[][] {
            {"SRR2096940", 10591, 498},
            {"SRR000123", 0, 4583}
        };
    }

    @Test(dataProvider = "testCounts")
    public void testCounts(String acc, int expectedNumMapped, int expectedNumUnmapped) {
        SamReader reader = SamReaderFactory.make().validationStringency(ValidationStringency.SILENT).open(
                SamInputResource.of(new SRAAccession(acc))
        );

        final SAMRecordIterator samRecordIterator = reader.iterator();

        assertCorrectCountsOfMappedAndUnmappedRecords(samRecordIterator, expectedNumMapped, expectedNumUnmapped);
    }

    @DataProvider(name = "testCountsBySpan")
    private Object[][] createDataForCountsBySpan() {
        return new Object[][] {
            {"SRR2096940", Arrays.asList(new Chunk(0, 59128983), new Chunk(59128983, 59141089)), 10591, 498},
            {"SRR2096940", Arrays.asList(new Chunk(0, 29128983), new Chunk(29128983, 59141089)), 10591, 498},
            {"SRR2096940", Arrays.asList(new Chunk(0, 59134983), new Chunk(59134983, 59141089)), 10591, 498},
            {"SRR2096940", Arrays.asList(new Chunk(0, 59130000)),                                10591, 0},
            {"SRR2096940", Arrays.asList(new Chunk(0, 59140889)),                                10591, 298}
        };
    }

    @Test(dataProvider = "testCountsBySpan")
    public void testCountsBySpan(String acc, List<Chunk> chunks, int expectedNumMapped, int expectedNumUnmapped) {
        SamReader reader = SamReaderFactory.make().validationStringency(ValidationStringency.SILENT).open(
                SamInputResource.of(new SRAAccession(acc))
        );

        final SAMRecordIterator samRecordIterator = ((SamReader.Indexing) reader).iterator(new BAMFileSpan(chunks));

        assertCorrectCountsOfMappedAndUnmappedRecords(samRecordIterator, expectedNumMapped, expectedNumUnmapped);
    }

    @DataProvider(name = "testGroups")
    private Object[][] createDataForGroups() {
        return new Object[][] {
            {"SRR822962", new TreeSet<>(Arrays.asList(
                    "GS54389-FS3-L08", "GS57511-FS3-L08", "GS54387-FS3-L02", "GS54387-FS3-L01",
                    "GS57510-FS3-L01", "GS57510-FS3-L03", "GS54389-FS3-L07", "GS54389-FS3-L05",
                    "GS54389-FS3-L06", "GS57510-FS3-L02", "GS57510-FS3-L04", "GS54387-FS3-L03",
                    "GS46253-FS3-L03"))
            },
            {"SRR2096940", new HashSet<>(Arrays.asList("SRR2096940"))}
        };
    }

    @Test(dataProvider = "testGroups")
    public void testGroups(String acc, Set<String> groups) {
        SamReader reader = SamReaderFactory.make().validationStringency(ValidationStringency.SILENT).open(
                SamInputResource.of(new SRAAccession(acc))
        );

        final SAMRecordIterator samRecordIterator = reader.iterator();

        SAMFileHeader header = reader.getFileHeader();
        Set<String> headerGroups = new TreeSet<>();
        for (SAMReadGroupRecord group : header.getReadGroups()) {
            Assert.assertEquals(group.getReadGroupId(), group.getId());
            headerGroups.add(group.getReadGroupId());
        }

        Assert.assertEquals(groups, headerGroups);

        Set<String> foundGroups = new TreeSet<>();

        for (int i = 0; i < 10000; i++) {
            if (!samRecordIterator.hasNext()) {
                break;
            }
            SAMRecord record = samRecordIterator.next();
            String groupName = (String)record.getAttribute("RG");

            foundGroups.add(groupName);
        }

        // please note that some groups may be introduced after 10k records, which is not an error
        Assert.assertEquals(groups, foundGroups);
    }

    @DataProvider(name = "testReferences")
    private Object[][] createDataForReferences() {
        return new Object[][] {
            // primary alignment only
            {"SRR1063272", 1,
                    Arrays.asList("supercont2.1", "supercont2.2", "supercont2.3", "supercont2.4",
                                  "supercont2.5", "supercont2.6", "supercont2.7", "supercont2.8",
                                  "supercont2.9", "supercont2.10", "supercont2.11", "supercont2.12",
                                  "supercont2.13", "supercont2.14"),
                    Arrays.asList(2291499, 1621675, 1575141, 1084805,
                                  1814975, 1422463, 1399503, 1398693,
                                  1186808, 1059964, 1561994, 774062,
                                  756744, 926563)},
        };
    }

    @Test(dataProvider = "testReferences")
    public void testReferences(String acc, int numberFirstReferenceFound, List<String> references, List<Integer> refLengths) {
        SamReader reader = SamReaderFactory.make().validationStringency(ValidationStringency.SILENT).open(
                SamInputResource.of(new SRAAccession(acc))
        );

        final SAMRecordIterator samRecordIterator = reader.iterator();

        SAMFileHeader header = reader.getFileHeader();
        Set<String> headerRefNames = new TreeSet<>();

        for (SAMSequenceRecord ref : header.getSequenceDictionary().getSequences()) {
            String refName = ref.getSequenceName();

            int refIndex = references.indexOf(refName);
            Assert.assertTrue(refIndex != -1, "Unexpected reference: " + refName);

            Assert.assertEquals(refLengths.get(refIndex), (Integer) ref.getSequenceLength(), "Reference length is incorrect");

            headerRefNames.add(refName);
        }

        Assert.assertEquals(new TreeSet<>(references), headerRefNames);

        Set<String> foundRefNames = new TreeSet<>();
        for (int i = 0; i < 10000; i++) {
            if (!samRecordIterator.hasNext()) {
                break;
            }
            SAMRecord record = samRecordIterator.next();

            if (record.getReferenceIndex().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX)) {
                continue;
            }

            String refName = record.getReferenceName();
            Assert.assertNotNull(refName);

            foundRefNames.add(refName);
        }

        Assert.assertEquals(new TreeSet<>(references.subList(0, numberFirstReferenceFound)), foundRefNames);
    }

    @DataProvider(name = "testRows")
    private Object[][] createDataForRowsTest() {
        return new Object[][] {
            // primary alignment only
            {"SRR1063272", 0, 99, "SRR1063272.R.1",
                    "ACTCGACATTCTGCCTTCGACCTATCTTTCTCCTCTCCCAGTCATCGCCCAGTAGAATTACCAGGCAATGAACCAGGGCCTTCCATCCCAACGGCACAGCA",
                    "@@CDDBDFFBFHFIEEFGIGGHIEHIGIGGFGEGAFDHIIIIIGGGDFHII;=BF@FEHGIEEH?AHHFHFFFFDC5'5=?CC?ADCD@AC??9BDDCDB<",
                    86, "101M", "supercont2.1", 60, true, false},

            // small SRA archive
            {"SRR2096940", 1, 16, "SRR2096940.R.3",
                    "GTGTGTCACCAGATAAGGAATCTGCCTAACAGGAGGTGTGGGTTAGACCCAATATCAGGAGACCAGGAAGGAGGAGGCCTAAGGATGGGGCTTTTCTGTCACCAATCCTGTCCCTAGTGGCCCCACTGTGGGGTGGAGGGGACAGATAAAAGTACCCAGAACCAGAG",
                    "AAAABFFFFFFFGGGGGGGGIIIIIIIIIIIIIIIIIIIIIIIIIIIIII7IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIGGGGGFGFFDFFFFFC",
                    55627016, "167M", "CM000681.1", 42, false, false},

            {"SRR2096940", 10591, 4, "SRR2096940.R.10592",
                    "CTCTGGTTCTGGGTACTTTTATCTGTCCCCTCCACCCCACAGTGGCGAGCCAGATTCCTTATCTGGTGACACAC",
                    "IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII",
                    -1, null, null, -1, false, false},

            // primary and secondary alignments
            {"SRR833251", 81, 393, "SRR833251.R.51",
                    "ATGCAAATCCGAATGGGCTATTTGTGGGTACTTGGGCAGGTAAGTAGCTGGCAATCTTGGTCGGTAAACCAATACCCAAGTTCACATAGGCACCATCGGGA",
                    "CCCFFFFFHHHHHIJJJIJJJJJIIJJJGIJIJIIJIJJJDGIGIIJIJIHIJJJJJJGIGHIHEDFFFFDDEEEDDDDDCDEEDDDDDDDDDDDDDBBDB",
                    1787186, "38M63S", "gi|169794206|ref|NC_010410.1|", 11, true, true},

            // local SRA file
            {"src/test/resources/htsjdk/samtools/sra/test_archive.sra", 1, 99, "test_archive.R.2",
                    "TGTCGATGCTGAAAGTGTCTGCGGTGAACCACTTCATGCACAGCGCACACTGCAGCTCCACTTCACCCAGCTGACGGCCGTTCTCATCGTCTCCAGAGCCCGTCTGAGCGTCCGCTGCTTCAGAACTGTCCCCGGCTGTATCCTGAAGAC",
                    "BBAABBBFAFFFGGGGGGGGGGGGEEFHHHHGHHHHHFHHGHFDGGGGGHHGHHHHHHHHHHHHFHHHGHHHHHHGGGGGGGHGGHHHHHHHHHGHHHHHGGGGHGHHHGGGGGGGGGHHHHEHHHHHHHHHHGCGGGHHHHHHGBFFGF",
                    2811570, "150M", "NC_007121.5", 60, true, false}
        };
    }

    @Test(dataProvider = "testRows")
    public void testRows(String acc, int recordIndex, int flags, String readName, String bases, String quals, int refStart, String cigar,
                         String refName, int mapQ, boolean hasMate, boolean isSecondaryAlignment) {
        SAMRecord record = getRecordByIndex(acc, recordIndex, false);

        checkSAMRecord(record, flags, readName, bases, quals, refStart, cigar, refName, mapQ, hasMate, isSecondaryAlignment);
    }

    @Test(dataProvider = "testRows")
    public void testRowsAfterIteratorDetach(String acc, int recordIndex, int flags, String readName, String bases, String quals,
                                            int refStart, String cigar, String refName, int mapQ, boolean hasMate,
                                            boolean isSecondaryAlignment) {
        SAMRecord record = getRecordByIndex(acc, recordIndex, true);

        checkSAMRecord(record, flags, readName, bases, quals, refStart, cigar, refName, mapQ, hasMate, isSecondaryAlignment);
    }

    @Test(dataProvider = "testRows")
    public void testRowsOverrideValues(String acc, int recordIndex, int flags, String readName, String bases, String quals,
                                       int refStart, String cigar, String refName, int mapQ, boolean hasMate,
                                       boolean isSecondaryAlignment) {
        SAMRecord record = getRecordByIndex(acc, recordIndex, true);
        SAMFileHeader header = record.getHeader();


        record.setFlags(0);
        record.setReadUnmappedFlag(refStart == -1);
        record.setReadBases("C".getBytes());
        record.setBaseQualities(SAMUtils.fastqToPhred("A"));
        if (refStart == -1) {
            checkSAMRecord(record, 4, readName, "C", "A", refStart, "1M", refName, mapQ, false, false);
        } else {
            int sequenceIndex = header.getSequenceIndex(refName);
            Assert.assertFalse(sequenceIndex == -1);

            if (sequenceIndex == 0) {
                if (header.getSequenceDictionary().getSequences().size() > 1) {
                    sequenceIndex++;
                }
            } else {
                sequenceIndex--;
            }

            refName = header.getSequence(sequenceIndex).getSequenceName();

            record.setAlignmentStart(refStart - 100);
            record.setCigarString("1M");
            record.setMappingQuality(mapQ - 1);
            record.setReferenceIndex(sequenceIndex);

            checkSAMRecord(record, 0, readName, "C", "A", refStart - 100, "1M", refName, mapQ - 1, false, false);
        }
    }

    @Test(dataProvider = "testRows")
    public void testRowsBySpan(String acc, int recordIndex, int flags, String readName, String bases, String quals,
                                            int refStart, String cigar, String refName, int mapQ, boolean hasMate,
                                            boolean isSecondaryAlignment) {
        SamReader reader = SamReaderFactory.make().validationStringency(ValidationStringency.SILENT).open(
                SamInputResource.of(new SRAAccession(acc))
        );

        SAMFileHeader header = reader.getFileHeader();

        Chunk chunk;
        if (refStart != -1) {
            long refOffset = 0;
            int refIndex = header.getSequenceDictionary().getSequence(refName).getSequenceIndex();
            for (SAMSequenceRecord sequenceRecord : header.getSequenceDictionary().getSequences()) {
                if (sequenceRecord.getSequenceIndex() <  refIndex) {
                    refOffset += sequenceRecord.getSequenceLength();
                }
            }

            chunk = new Chunk(refOffset + refStart - 1, refOffset + refStart);
        } else {
            long totalRefLength = header.getSequenceDictionary().getReferenceLength();
            long totalRecordRange = ((BAMFileSpan)reader.indexing().getFilePointerSpanningReads()).toCoordinateArray()[1];
            chunk = new Chunk(totalRefLength, totalRecordRange);
        }

        final SAMRecordIterator samRecordIterator = ((SamReader.Indexing) reader).iterator(new BAMFileSpan(chunk));

        SAMRecord record = null;
        while (samRecordIterator.hasNext()) {
            SAMRecord currentRecord = samRecordIterator.next();
            if (currentRecord.getReadName().equals(readName)) {
                record = currentRecord;
                break;
            }
        }

        checkSAMRecord(record, flags, readName, bases, quals, refStart, cigar, refName, mapQ, hasMate, isSecondaryAlignment);
    }

    @Test(dataProvider = "testRows")
    public void testRowsByIndex(String acc, int recordIndex, int flags, String readName, String bases, String quals,
                                int refStart, String cigar, String refName, int mapQ, boolean hasMate,
                                boolean isSecondaryAlignment) {
        SamReader reader = SamReaderFactory.make().validationStringency(ValidationStringency.SILENT).open(
                SamInputResource.of(new SRAAccession(acc))
        );

        Assert.assertTrue(reader.hasIndex());
        Assert.assertTrue(reader.indexing().hasBrowseableIndex());

        SAMFileHeader header = reader.getFileHeader();
        BrowseableBAMIndex index = reader.indexing().getBrowseableIndex();

        BAMFileSpan span;
        if (refStart != -1) {
            int refIndex = header.getSequenceDictionary().getSequence(refName).getSequenceIndex();
            span = index.getSpanOverlapping(refIndex, refStart, refStart + 1);
        } else {
            long chunkStart = index.getStartOfLastLinearBin();
            long totalRecordRange = ((BAMFileSpan) reader.indexing().getFilePointerSpanningReads()).toCoordinateArray()[1];
            span = new BAMFileSpan(new Chunk(chunkStart, totalRecordRange));
        }

        final SAMRecordIterator samRecordIterator = ((SamReader.Indexing) reader).iterator(span);

        SAMRecord record = null;
        while (samRecordIterator.hasNext()) {
            SAMRecord currentRecord = samRecordIterator.next();
            if (refStart != -1 && currentRecord.getAlignmentStart() + currentRecord.getReadLength() < refStart) {
                continue;
            }

            if (currentRecord.getReadName().equals(readName)) {
                record = currentRecord;
                break;
            }
        }

        checkSAMRecord(record, flags, readName, bases, quals, refStart, cigar, refName, mapQ, hasMate, isSecondaryAlignment);
    }

    private SAMRecord getRecordByIndex(String acc, int recordIndex, boolean detach) {
        SamReader reader = SamReaderFactory.make().validationStringency(ValidationStringency.SILENT).open(
                SamInputResource.of(new SRAAccession(acc))
        );

        final SAMRecordIterator samRecordIterator = reader.iterator();

        while (recordIndex != 0) {
            Assert.assertTrue(samRecordIterator.hasNext(), "Record set is too small");

            samRecordIterator.next();
            recordIndex--;
        }
        Assert.assertTrue(samRecordIterator.hasNext(), "Record set is too small");

        SAMRecord record = samRecordIterator.next();

        if (detach) {
            samRecordIterator.next();
        }

        return record;
    }

    private void checkSAMRecord(SAMRecord record, int flags, String readName, String bases, String quals,
                                int refStart, String cigar, String refName, int mapQ, boolean hasMate,
                                boolean isSecondaryAlignment) {

        Assert.assertNotNull(record, "Record with read id: " + readName + " was not found by span created from index");

        List<SAMValidationError> validationErrors = record.isValid();
        Assert.assertNull(validationErrors, "SRA Lazy record is invalid. List of errors: " +
                (validationErrors != null ? validationErrors.toString() : ""));

        Assert.assertEquals(new String(record.getReadBases()), bases);
        Assert.assertEquals(record.getBaseQualityString(), quals);
        Assert.assertEquals(record.getReadPairedFlag(), hasMate);
        Assert.assertEquals(record.getFlags(), flags);
        Assert.assertEquals(record.getNotPrimaryAlignmentFlag(), isSecondaryAlignment);
        if (refStart == -1) {
            Assert.assertEquals(record.getReadUnmappedFlag(), true);
            Assert.assertEquals(record.getAlignmentStart(), 0);
            Assert.assertEquals(record.getCigarString(), "*");
            Assert.assertEquals(record.getReferenceName(), "*");
            Assert.assertEquals(record.getMappingQuality(), 0);
        } else {
            Assert.assertEquals(record.getReadUnmappedFlag(), false);
            Assert.assertEquals(record.getAlignmentStart(), refStart);
            Assert.assertEquals(record.getCigarString(), cigar);
            Assert.assertEquals(record.getReferenceName(), refName);
            Assert.assertEquals(record.getMappingQuality(), mapQ);
        }
    }

}
