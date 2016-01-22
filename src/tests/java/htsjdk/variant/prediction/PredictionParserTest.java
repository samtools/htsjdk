package htsjdk.variant.prediction;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import java.io.File;
import java.util.Map;
import java.util.List;
import java.util.ServiceLoader;
import org.testng.Assert;
import org.testng.annotations.Test;


public class PredictionParserTest {
    @Test
    public void testDefaultPredctionParser() {
	boolean foundOneParser=false;
        final ServiceLoader<PredictionParserFactory> loader = ServiceLoader.load(PredictionParserFactory.class);
        for(final PredictionParserFactory parserFactory:loader) {
	  final File inputFileVcf = new File("testdata/htsjdk/tribble/tabix/testTabixIndex.vcf");
	  
	  final VCFFileReader reader = new VCFFileReader(inputFileVcf, false);
	  PredictionParser parser = parserFactory.createParser(reader.getFileHeader());
	  for(final VariantContext ctx:reader) {
	  parser.parse(ctx);
	  }
	  reader.close();
	  foundOneParser = true;
        }
        Assert.assertTrue(foundOneParser);
        
    }


}