package htsjdk.samtools.util;


import htsjdk.HtsjdkTest;
import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class CigarElementUnitTest extends HtsjdkTest {

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNegativeLengthCheck(){
        final CigarElement element = new CigarElement(-1, CigarOperator.M);
    }


    @DataProvider
    public Object[][] elementsForEquals() {
        final CigarElement mElement = new CigarElement(10, CigarOperator.M);
        return new Object[][] {
                // same object
                {mElement, mElement, true},
                // different equal objects
                {mElement, new CigarElement(mElement.getLength(), mElement.getOperator()), true},
                // different lengths
                {mElement, new CigarElement(mElement.getLength() + 1, mElement.getOperator()), false},
                // different operators
                {mElement, new CigarElement(mElement.getLength(), CigarOperator.X), false},
                // different class
                {mElement, mElement.toString(), false}
        };
    }

    @Test(dataProvider = "elementsForEquals")
    public void testEqualsAndHashCode(final CigarElement element, final Object other, final boolean isEquals) {
        Assert.assertEquals(element.equals(other), isEquals);
        Assert.assertEquals(element.hashCode() == other.hashCode(), isEquals);
    }
}
