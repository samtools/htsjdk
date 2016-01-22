package htsjdk.variant.prediction;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** default prediction parser factory */
public class DefaultPredictionParserFactory implements PredictionParserFactory {
    @Override
    public String getName() { return "Default";}
    @Override
    public String getVersion() { return "1.0";}
    @Override
    public String getDescription() { return "Default Prediction Parser for htsjdk. Does nothing.";}
    @Override
    public PredictionParser createParser(final VCFHeader header) {
      return new PredictionParserImpl();
    }
    @Override
    public Map<String,String> getProperties() {
      return Collections.emptyMap();
    }
    
    
    private class PredictionParserImpl implements PredictionParser {
      @Override
      public PredictionParserFactory getFactory() {
      return DefaultPredictionParserFactory.this;
      }
      /** get available predictions for this variant */
      @Override
      public List<Prediction> parse(final VariantContext ctx) {
	return Collections.emptyList();
      }
    }
  }