package htsjdk.variant.variantcontext;

import htsjdk.variant.VariantBaseTest;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;

public class VariantContextBuilderTest extends VariantBaseTest {
    Allele Aref, C;

    String snpLoc = "chr1";
    String snpSource = "test";
    long snpLocStart = 10;
    long snpLocStop = 10;

    @BeforeTest
    public void before() {

        C = Allele.create("C");
        Aref = Allele.create("A", true);
    }

    @DataProvider(name = "trueFalse")
    public Object[][] testAttributesWorksTest() {
        return new Object[][]{{true}, {false}};
    }

    @Test(dataProvider = "trueFalse")
    public void testAttributeResettingWorks(final boolean leaveModifyableAsIs) {
        final VariantContextBuilder root1 = new VariantContextBuilder(snpSource, snpLoc, snpLocStart, snpLocStop, Arrays.asList(Aref, C));
        final VariantContextBuilder root2 = new VariantContextBuilder(snpSource, snpLoc, snpLocStart, snpLocStop, Arrays.asList(Aref, C));

        final VariantContext result1 = root1.attribute("AC", 1).make(leaveModifyableAsIs);

        //this is a red-herring and should not change anything, however, if leaveModifyableAsIs is true, it does change result1.
        final VariantContext ignored = root1.attribute("AC", 2).make(leaveModifyableAsIs);

        final VariantContext result2 = root2.attribute("AC", 1).make(leaveModifyableAsIs);

        if (leaveModifyableAsIs) {
            Assert.assertNotSame(result1.getAttribute("AC"), result2.getAttribute("AC"));
        } else {
            Assert.assertEquals(result1.getAttribute("AC"), result2.getAttribute("AC"));
        }
    }

    @Test()
    public void testAttributeResettingWorks() {
        final VariantContextBuilder root1 = new VariantContextBuilder(snpSource, snpLoc, snpLocStart, snpLocStop, Arrays.asList(Aref, C));
        final VariantContextBuilder root2 = new VariantContextBuilder(snpSource, snpLoc, snpLocStart, snpLocStop, Arrays.asList(Aref, C));

        final VariantContext result1 = root1.attribute("AC", 1).make();

        //this is a red-herring and should not change anything.
        final VariantContext ignored = root1.attribute("AC", 2).make();

        final VariantContext result2 = root2.attribute("AC", 1).make();

       Assert.assertEquals(result1.getAttribute("AC"), result2.getAttribute("AC"));

    }
}