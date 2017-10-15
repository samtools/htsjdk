package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.IOUtil;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.file.Paths;

/**
 * Created by farjoun on 10/12/17.
 */
public class VCFPathReaderTest extends HtsjdkTest {

    @DataProvider(name = "pathsData")
    Object[][] pathsData() {
        return new Object[][]{
                // various ways to refer to a local file
                {"src/test/resources/htsjdk/variant/VCF4HeaderTest.vcf", false, true},
                {Paths.get("").toAbsolutePath().toString() + "/src/test/resources/htsjdk/variant/VCF4HeaderTest.vcf", false, true},
                {"file://" + Paths.get("").toAbsolutePath().toString() + "/src/test/resources/htsjdk/variant/VCF4HeaderTest.vcf", false, true},

                //testing GCS files:

                // this is almost a vcf, but not quite it's missing the #CHROM line and it has no content...
                {"gs://broad-references/hg38/v0/Homo_sapiens_assembly38.tile_db_header.vcf", false, false},

                // test that have indexes
                {"gs://broad-references/hg38/v0/Mills_and_1000G_gold_standard.indels.hg38.vcf.gz", true, true},
                {"gs://broad-references/hg38/v0/WholeGenomeShotgunContam.vcf", true, true},

                // testing a non-existent scheme:

                {"bogus://" + Paths.get("").toAbsolutePath().toString() + "/src/test/resources/htsjdk/variant/VCF4HeaderTest.vcf", false, false},

        };
    }

    @Test(dataProvider = "pathsData", timeOut = 60_000)
    public void testCanOpenVCFPathReader(final String uri, final boolean requiresIndex, final boolean shouldSucceed) throws Exception {

        // read an existing VCF
        try (final VCFPathReader reader = new VCFPathReader(IOUtil.getPath(uri), requiresIndex)) {
            final VCFHeader header = reader.getFileHeader();
        } catch (Exception e) {
            if (shouldSucceed) throw e;
        }
    }
}