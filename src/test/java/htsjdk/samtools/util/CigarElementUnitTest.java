package htsjdk.samtools.util;


import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import org.testng.annotations.Test;

public class CigarElementUnitTest {

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNegativeLengthCheck(){
        final CigarElement element = new CigarElement(-1, CigarOperator.M);
    }
}
