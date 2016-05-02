package htsjdk.samtools.reference;

import htsjdk.samtools.SAMException;
import htsjdk.samtools.util.CloserUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;

/**
 * Created by farjoun on 2/14/14.
 */
public class ReferenceSequenceFileWalkerTest {


    @DataProvider(name = "TestReference")
    public Object[][] TestReference() {
        return new Object[][]{
                new Object[]{"src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.fasta", 0, 1},
                new Object[]{"src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.fasta", 1, 1},
                new Object[]{"src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.fasta", 0, 0},
        };
    }


    @Test(dataProvider = "TestReference")
    public void testGet(final String fileName, final int index1, final int index2) throws SAMException {
        final File refFile = new File(fileName);
        final ReferenceSequenceFileWalker refWalker = new ReferenceSequenceFileWalker(refFile);

        ReferenceSequence sequence = refWalker.get(index1);
        Assert.assertEquals(sequence.getContigIndex(), index1);

        sequence = refWalker.get(index2);
        Assert.assertEquals(sequence.getContigIndex(), index2);
        CloserUtil.close(refWalker);
    }


    @DataProvider(name = "TestFailReference")
    public Object[][] TestFailReference() {
        return new Object[][]{
                new Object[]{"src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.noindex.fasta", 1,3},  //fail because out of bounds
                new Object[]{"src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.noindex.fasta", 2,3},  //fail because out of bounds
                new Object[]{"src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.noindex.fasta", 1,0},  //fail because not allowed to look back
                new Object[]{"src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.noindex.fasta", -1,0},  //fail because out of bounds
                new Object[]{"src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.fasta", -1, 0},  //fail because out of bounds
                new Object[]{"src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.fasta", 1, -1},    //fail because out of bounds
                new Object[]{"src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.fasta", 2,3},  //fail because out of bounds
                new Object[]{"src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.fasta", 1,3},  //fail because out of bounds
                new Object[]{"src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.fasta", 1, 0} // fasta is indexed, but not allowed to look back
        };
    }


    @Test(expectedExceptions = {SAMException.class}, dataProvider = "TestFailReference")
    public void testFailGet(final String fileName, final int index1, final int index2) throws SAMException {
        final File refFile = new File(fileName);
        final ReferenceSequenceFileWalker refWalker = new ReferenceSequenceFileWalker(refFile);

        try {
            refWalker.get(index1);

            refWalker.get(index2);
        }
        finally {
            CloserUtil.close(refWalker);
        }
    }


}
