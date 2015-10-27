package htsjdk.samtools.cram.structure;

import org.testng.Assert;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import java.util.Arrays;

/**
 * Created by Vadim on 12/03/2015.
 */
public class SubstitutionMatrixTest {

    SubstitutionMatrix m;
    long[][] freqs;

    @BeforeGroups(groups = "matrix2code")
    public void m() {
        m = new SubstitutionMatrix(new byte[]{27, (byte) 228, 27, 27, 27});
    }

    @BeforeGroups(groups = "freqs2matrix")
    public void b() {
        freqs = new long[255][255];
        for (int r = 0; r < SubstitutionMatrix.BASES.length; r++) {
            for (int b = 0; b < SubstitutionMatrix.BASES.length; b++) {
                if (r == b)
                    continue;
                freqs[SubstitutionMatrix.BASES[r]][SubstitutionMatrix.BASES[b]] = b;
            }
        }
        m = new SubstitutionMatrix(freqs);
    }


    @Test(dataProvider = "matrix2code", groups = "matrix2code")
    public void testMatrix2code(char refBase, char base, int code) {
        if (refBase == base)
            return;
        Assert.assertEquals(m.code((byte) refBase, (byte) base), code);
        Assert.assertEquals(m.base((byte) refBase, (byte) code), base);
    }

    @Test(dataProvider = "freqs2matrix", groups = "freqs2matrix")
    public void testFreqs2matrix(char refBase, char base, int code) {
        if (refBase == base)
            return;
        Assert.assertEquals(m.code((byte) refBase, (byte) base), code);
        Assert.assertEquals(m.base((byte) refBase, (byte) code), base);
    }

    @DataProvider(name = "matrix2code")
    public Object[][] provider1() {
        return new Object[][]{
                {'A', 'C', 0},
                {'A', 'G', 1},
                {'A', 'T', 2},
                {'A', 'N', 3},
                {'C', 'A', 3},
                {'C', 'G', 2},
                {'C', 'T', 1},
                {'C', 'N', 0},
                {'G', 'A', 0},
                {'G', 'C', 1},
                {'G', 'T', 2},
                {'G', 'N', 3},
                {'T', 'A', 0},
                {'T', 'C', 1},
                {'T', 'G', 2},
                {'T', 'N', 3},
                {'N', 'A', 0},
                {'N', 'C', 1},
                {'N', 'G', 2},
                {'N', 'T', 3},
        };
    }

    @DataProvider(name = "freqs2matrix")
    public Object[][] provider2() {
        return new Object[][]{
                {'A', 'C', 3},
                {'A', 'G', 2},
                {'A', 'T', 1},
                {'A', 'N', 0},
                {'C', 'A', 3},
                {'C', 'G', 2},
                {'C', 'T', 1},
                {'C', 'N', 0},
                {'G', 'A', 3},
                {'G', 'C', 2},
                {'G', 'T', 1},
                {'G', 'N', 0},
                {'T', 'A', 3},
                {'T', 'C', 2},
                {'T', 'G', 1},
                {'T', 'N', 0},
                {'N', 'A', 3},
                {'N', 'C', 2},
                {'N', 'G', 1},
                {'N', 'T', 0},
        };
    }
}
