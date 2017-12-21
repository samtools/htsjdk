package htsjdk.samtools.sra;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SRAFileReader;
import htsjdk.samtools.util.TestUtil;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests for SRA extension of SAMRecord objects which load fields on demand
 */
public class SRALazyRecordTest extends AbstractSRATest {
    private static final SRAAccession DEFAULT_ACCESSION = new SRAAccession("SRR2096940");

    @DataProvider(name = "serializationTestData")
    private Object[][] getSerializationTestData() {
        return new Object[][] {
                { DEFAULT_ACCESSION }
        };
    }

    @Test(dataProvider = "serializationTestData")
    public void testSerialization(final SRAAccession accession) throws Exception {
        final SRAFileReader reader = new SRAFileReader(accession);
        final SAMRecord initialSAMRecord = reader.getIterator().next();
        reader.close();

        final SAMRecord deserializedSAMRecord = TestUtil.serializeAndDeserialize(initialSAMRecord);

        Assert.assertEquals(deserializedSAMRecord, initialSAMRecord, "Deserialized SAMRecord not equal to original SAMRecord");
    }

    @Test(dataProvider = "serializationTestData")
    public void testCloneAndEquals(final SRAAccession accession) throws Exception {
        final SRAFileReader reader = new SRAFileReader(accession);
        final SAMRecord record = reader.getIterator().next();
        reader.close();

        final SAMRecord newRecord = (SAMRecord)record.clone();
        Assert.assertFalse(record == newRecord);
        Assert.assertNotSame(record, newRecord);
        Assert.assertEquals(record, newRecord);
        Assert.assertEquals(newRecord, record);

        newRecord.setAlignmentStart(record.getAlignmentStart() + 100);
        Assert.assertFalse(record.equals(newRecord));
        Assert.assertFalse(newRecord.equals(record));
    }
}
