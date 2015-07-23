package htsjdk.tribble;

import htsjdk.tribble.util.TabixUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;


public class TribbleTest {

    @Test
    public void testStandardIndex() {

	final String vcf = "foo.vcf";
	final String expectedIndex = vcf + Tribble.STANDARD_INDEX_EXTENSION;

	Assert.assertEquals(Tribble.indexFile(vcf), expectedIndex);
	Assert.assertEquals(Tribble.indexFile(new File(vcf).getAbsolutePath()), new File(expectedIndex).getAbsolutePath());
    }

    @Test
    public void testTabixIndex() {

	final String vcf = "foo.vcf.gz";
	final String expectedIndex = vcf + TabixUtils.STANDARD_INDEX_EXTENSION;

	Assert.assertEquals(Tribble.tabixIndexFile(vcf), expectedIndex);
	Assert.assertEquals(Tribble.tabixIndexFile(new File(vcf).getAbsolutePath()), new File(expectedIndex).getAbsolutePath());
    }
}
