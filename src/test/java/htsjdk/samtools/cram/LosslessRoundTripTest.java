package htsjdk.samtools.cram;

import htsjdk.samtools.*;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A set of tests to ensure certain features are not lost during roundtrip tests
 */
public class LosslessRoundTripTest {
    private SAMRecordSetBuilder samRecordSetBuilder;
    private SAMFileHeader samFileHeader;
    private ReferenceSource referenceSource;
    private ReferenceSource emptyReferenceSource;

    @BeforeTest
    public void beforeTest() {
        int refSequenceLength = 100;
        samRecordSetBuilder = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.coordinate, true, refSequenceLength);
        samFileHeader = samRecordSetBuilder.getHeader();

        InMemoryReferenceSequenceFile rsFile = new InMemoryReferenceSequenceFile();
        // fill out the sequence with ACGTN repeated pattern:
        byte[] ref = new byte[refSequenceLength];
        for (int i = 0; i < refSequenceLength; i++) {
            ref[i] = "ACGTN".getBytes()[i % 5];
        }

        rsFile.add(samFileHeader.getSequence(0).getSequenceName(), ref);
        referenceSource = new ReferenceSource(rsFile);
        emptyReferenceSource = new ReferenceSource(new InMemoryReferenceSequenceFile());
    }

    @BeforeMethod
    public void beforeMethod() {
        if (samRecordSetBuilder != null)
            samRecordSetBuilder.getRecords().clear();
    }

    /**
     * Test that NM and MD tags make it through CRAM conversion unchanged.
     *
     * @throws IOException
     */
    @Test
    public void test_MD_NM() throws IOException {
        // a dumb unmapped read:
        SAMRecord record = samRecordSetBuilder.addFrag("read1", SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX,
                SAMRecord.NO_ALIGNMENT_START, false, true, SAMRecord.NO_ALIGNMENT_CIGAR, null, 0);

        // setting some unrealistic values to provoke test failure if the values are auto-restored while reading CRAM:
        record.setAttribute("MD", "nonsense");
        record.setAttribute("NM", 123);

        SAMRecord roundTripRecord = roundtripSingleRecord(samRecordSetBuilder, emptyReferenceSource, CramVersions.CRAM_v3);

        Assert.assertNotNull(roundTripRecord);
        Assert.assertEquals(roundTripRecord, record);
    }

    /**
     * For unmapped reads mapping quality score is not preserved in CRAM.
     */
    @Test
    public void testMappingScoreInUnmappedReadRoundtrip() {
        int mappingScore = 1;
        Assert.assertNotEquals(mappingScore, SAMRecord.NO_MAPPING_QUALITY);
        samRecordSetBuilder.addFrag("read1", SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, SAMRecord.NO_ALIGNMENT_START,
                false, true, SAMRecord.NO_ALIGNMENT_CIGAR, null, 0).setMappingQuality(mappingScore);

        SAMRecord roundtripRecord = roundtripSingleRecord(samRecordSetBuilder, emptyReferenceSource, CramVersions.CRAM_v2_1);
        Assert.assertEquals(roundtripRecord.getMappingQuality(), SAMRecord.NO_MAPPING_QUALITY);

        roundtripRecord = roundtripSingleRecord(samRecordSetBuilder, emptyReferenceSource, CramVersions.CRAM_v3);
        Assert.assertEquals(roundtripRecord.getMappingQuality(), SAMRecord.NO_MAPPING_QUALITY);
    }

    /**
     * Test various insert size expectations for CRAM transformations
     *
     * @throws IOException
     */
    @Test
    public void test_InsertSize() throws IOException {
        roundTripInsertSizes(15, -15);
        roundTripInsertSizes(-99, 123456);
    }

    private void roundTripInsertSizes(int tlen1, int tlen2) {
        SAMRecordBuilder.Pair pair = new SAMRecordBuilder.Pair(samRecordSetBuilder.getHeader());
        pair.first().name("a1").start(1).flags(67).ref(0).mapq(1).cigar("10M").tlen(tlen1).bases("ACGTNACGTN").scores();
        pair.last().name("a1").start(6).flags(3).ref(0).mapq(1).cigar("10M").tlen(tlen2).bases("ACGTNTTTTT").scores();
        pair.mate();

        Assert.assertEquals(pair.first().create().getInferredInsertSize(), tlen1);
        Assert.assertEquals(pair.last().create().getInferredInsertSize(), tlen2);

        Iterator<SAMRecord> iterator = roundtrip(pair.iterator(), samRecordSetBuilder.getHeader(), referenceSource, CramVersions.CRAM_v2_1);
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next().getInferredInsertSize(), tlen1);
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next().getInferredInsertSize(), tlen2);
        Assert.assertFalse(iterator.hasNext());

        iterator = roundtrip(pair.iterator(), samRecordSetBuilder.getHeader(), referenceSource, CramVersions.CRAM_v3);
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next().getInferredInsertSize(), tlen1);
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next().getInferredInsertSize(), tlen2);
        Assert.assertFalse(iterator.hasNext());
    }

    /**
     * In terms of CIGAR the expectations for CRAM round trip are:
     * <ul>
     * <li>M instead of = and X.</li>
     * <li>empty (zero-len) ops removed.</li>
     * <li>adjacent ops merged.</li>
     * <li>cigar with no read bases is set to *.</li>
     * </ul>
     * <p>
     * And more formally:
     * <p>
     * Let A denote a set of all cigar operators {ISM=XDSHN}, see {@link CigarOperator}.
     * <p>
     * Let R denote a set of cigar operators that consume read bases {ISM=X}, see {@link CigarOperator#consumesReadBases()}.
     * <p>
     * Let ̅x denote operator x∈R after transformation of {=X} into {M}: if x∈R then ̅x∈{ISM}.
     * <p>
     * Let * (star) denote a cigar for unmapped read, see {@link SAMRecord#NO_ALIGNMENT_CIGAR}.
     * <p>
     * Let ≈ denote CRAM round trip transformation of a cigar string.
     * <p>
     * Let a cigar element be defined as a pair of length L and operator x and denoted as (L, x), see {@link CigarElement}.
     * <p>
     * A cigar consisting of 2 elements is represented as sum of the elements: (L, x) + (K, y) and so on, see {@link Cigar}.
     * <p>
     * The following statements must be true:
     * <ul>
     * <li>∀x∈A: (0, x) ≈ *</li>
     * <li>∀L>0 and x∉R: (L>0, x) ≈ *</li>
     * <li>∀L>0 and x∈R: (L, x) ≈ (L, ̅x)</li>
     * <li>∀L≥0, ∀K≥0 and ∀x∈R: (L, x) + (K, x) ≈ (L+K, ̅x)</li>
     * <li>∀L,K>0 and ∀x∉R: (L, x) + (K, x) ≈ *</li>
     * <li>∀L,K>0 and ∀x∈R: (L, x) + (K, x) ≈ (L+K, ̅x)</li>
     * <li>∀L>0 and ∀x∈R and ∀y∉R: (L, x) + (0, y) ≈ (L, ̅x)</li>
     * <li>∀K>0 and ∀x∉R and ∀y∈R: (0, x) + (K, y) ≈ (K, ̅y)</li>
     * <li>∀L>0 and ∀K>0 and ∀x∈R and ∀y∈A: (L, x) + (0, y) + (K, x) ≈ (L+K, ̅x)</li>
     * <li>∀K>0 and ∀x,z∉R and ∀y∈R: (0, x) + (K, y) + (0, z) ≈ (K, ̅y)</li>
     * </ul>
     */
    @DataProvider(name = "cigarExpectations")
    public Object[][] getCigarExpectations() {
        List<Object[]> list = new ArrayList<>();
        for (CigarOperator op1 : CigarOperator.values()) {
            // cigar element of length=0 and operator x should roundtrip to *:
            // (0, x) = *
            list.add(new String[]{"0" + (char) CigarOperator.enumToCharacter(op1), SAMRecord.NO_ALIGNMENT_CIGAR});

            int len1 = 1;
            String ce1 = String.format("%d%c", len1, (char) CigarOperator.enumToCharacter(op1));
            String zero1 = String.format("0%c", (char) CigarOperator.enumToCharacter(op1));
            if (op1.consumesReadBases()) {
                // (L>0, x∈R) = (L, ̅x)
                list.add(new String[]{ce1, generalizeMatchMismatchInCigarString(ce1)});
            } else {
                // (L>0, x∉R) = *
                list.add(new String[]{ce1, SAMRecord.NO_ALIGNMENT_CIGAR});
            }

            int len2 = 2;
            for (CigarOperator op2 : CigarOperator.values()) {
                String ce2 = String.format("%d%c", len2, (char) CigarOperator.enumToCharacter(op2));
                String ce12 = String.format("%d%c", len1 + len2, (char) CigarOperator.enumToCharacter(op2));
                String ce11 = String.format("%d%c", len1 + len1, (char) CigarOperator.enumToCharacter(op1));
                String zero2 = String.format("0%c", (char) CigarOperator.enumToCharacter(op2));
                if (op1 == op2) {
                    if (op1.consumesReadBases())
                        // (L>0, x∈R) + (K>0, x∈R) = (L+K, ̅x)
                        list.add(new String[]{ce1 + ce2, generalizeMatchMismatchInCigarString(ce12)});
                    else {
                        // (L>0, x∉R) + ((K>0, x∉R)) = *
                        list.add(new String[]{ce1 + ce2, SAMRecord.NO_ALIGNMENT_CIGAR});
                    }
                }
                if (op1.consumesReadBases() && !op2.consumesReadBases()) {
                    // (L>0, x∈R) + (0, y∉R) = (L, ̅x)
                    list.add(new String[]{ce1 + zero2, generalizeMatchMismatchInCigarString(ce1)});
                    // (0, x∈R) + (0, y∉R) = *
                    list.add(new String[]{zero1 + zero2, SAMRecord.NO_ALIGNMENT_CIGAR});
                    // (L>0, x∈R) + (0, y∉R) + (L>0, x∈R) = (2L, ̅x)
                    list.add(new String[]{ce1 + zero2 + ce1, generalizeMatchMismatchInCigarString(ce11)});
                }

                if (!op1.consumesReadBases() && op2.consumesReadBases()) {
                    // (0, x∉R) + (K>0, y∈R) = (K, ̅y)
                    list.add(new String[]{zero1 + ce2, generalizeMatchMismatchInCigarString(ce2)});
                    // (L>0, x∉R) + (0, y∈R) = *
                    list.add(new String[]{ce1 + zero2, SAMRecord.NO_ALIGNMENT_CIGAR});
                    // (0, x∉R) + (K>0, y∈R) + (0, x∉R) = (K, ̅y)
                    list.add(new String[]{zero1 + ce2 + zero1, generalizeMatchMismatchInCigarString(ce2)});
                }
                // (0, x∈R) + (0, y∉R) = *
                list.add(new String[]{zero1 + zero2, SAMRecord.NO_ALIGNMENT_CIGAR});
                // (0, x∉R) + (0, y∈R) = *
                list.add(new String[]{zero2 + zero1, SAMRecord.NO_ALIGNMENT_CIGAR});
            }
        }
        return list.toArray(new Object[list.size()][]);
    }

    /**
     * Replace {@link CigarOperator#EQ} and {@link CigarOperator#X} cigar operators with {@link CigarOperator#M}.
     *
     * @param cigarString a cigar string
     * @return a new cigar string with EQ and X replaced with M
     */
    private static String generalizeMatchMismatchInCigarString(String cigarString) {
        Cigar cigar = TextCigarCodec.decode(cigarString);
        List<CigarElement> newList = new ArrayList<>(cigar.getCigarElements().size());
        for (CigarElement ce : cigar.getCigarElements()) {
            switch (ce.getOperator()) {
                case EQ:
                case X:
                    newList.add(new CigarElement(ce.getLength(), CigarOperator.M));
                    break;
                default:
                    newList.add(ce);
                    break;
            }
        }
        return new Cigar(newList).toString();
    }

    @Test(dataProvider = "cigarExpectations")
    public void testRoundtripCigar(String originalCigarString, String expectedRoundTrippedCigarString) {
        SAMRecordSetBuilder samRecordSetBuilder = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.coordinate);
        samRecordSetBuilder.getHeader().getSequence(0).setSequenceLength(100);

        samRecordSetBuilder.addFrag("read1", 0, 1, false, false, originalCigarString, null, 0).setReadBases(SAMRecord.NULL_SEQUENCE);

        SAMRecord roundtripRecord = roundtripSingleRecord(samRecordSetBuilder, referenceSource, CramVersions.CRAM_v2_1);
        Assert.assertEquals(roundtripRecord.getCigarString(), expectedRoundTrippedCigarString);

        roundtripRecord = roundtripSingleRecord(samRecordSetBuilder, referenceSource, CramVersions.CRAM_v3);
        Assert.assertEquals(roundtripRecord.getCigarString(), expectedRoundTrippedCigarString);
    }

    private SAMRecord roundtripSingleRecord(SAMRecordSetBuilder builder, ReferenceSource referenceSource, Version cramVersion) {
        Assert.assertEquals(builder.getRecords().size(), 1);
        Iterator<SAMRecord> iterator = roundtrip(builder, referenceSource, cramVersion);
        Assert.assertTrue(iterator.hasNext());
        return iterator.next();
    }

    private Iterator<SAMRecord> roundtrip(SAMRecordSetBuilder builder, ReferenceSource referenceSource, Version cramVersion) {
        return roundtrip(builder.iterator(), builder.getHeader(), referenceSource, cramVersion);
    }

    private Iterator<SAMRecord> roundtrip(Iterator<SAMRecord> iterator, SAMFileHeader header, ReferenceSource referenceSource, Version cramVersion) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (referenceSource == null) {
            InMemoryReferenceSequenceFile rsFile = new InMemoryReferenceSequenceFile();
            referenceSource = new ReferenceSource(rsFile);
        }
        CRAMFileWriter cramFileWriter = new MyCRAMFileWriter(baos, false, referenceSource, header, cramVersion);

        iterator.forEachRemaining(cramFileWriter::addAlignment);
        cramFileWriter.close();

        SamReaderFactory f = SamReaderFactory.make().referenceSource(referenceSource).validationStringency(ValidationStringency.SILENT);
        SamReader reader = f.open(SamInputResource.of(new ByteArrayInputStream(baos.toByteArray())));
        return reader.iterator();
    }

    private static class MyCRAMFileWriter extends CRAMFileWriter {
        public MyCRAMFileWriter(OutputStream outputStream, boolean presorted,
                                CRAMReferenceSource referenceSource, SAMFileHeader samFileHeader,
                                Version version) {
            super(outputStream, null, presorted, referenceSource, samFileHeader, null, version);
        }
    }
}
