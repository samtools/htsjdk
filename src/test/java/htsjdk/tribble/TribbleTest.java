package htsjdk.tribble;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.FileExtensions;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;


public class TribbleTest extends HtsjdkTest {

    @Test
    public void testStandardIndex() {

	final String vcf = "foo.vcf";
	final String expectedIndex = vcf + FileExtensions.TRIBBLE_INDEX;

	Assert.assertEquals(Tribble.indexFile(vcf), expectedIndex);
	Assert.assertEquals(Tribble.indexFile(new File(vcf).getAbsolutePath()), new File(expectedIndex).getAbsolutePath());
    }

    @Test
    public void testTabixIndex() {

	final String vcf = "foo.vcf.gz";
	final String expectedIndex = vcf + FileExtensions.TABIX_INDEX;

	Assert.assertEquals(Tribble.tabixIndexFile(vcf), expectedIndex);
	Assert.assertEquals(Tribble.tabixIndexFile(new File(vcf).getAbsolutePath()), new File(expectedIndex).getAbsolutePath());
    }
}
