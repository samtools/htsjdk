package htsjdk.variant.prediction;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import java.util.Map;
import java.util.List;


/** parses a variant context  and return a list of Prediction */
public interface PredictionParserFactory {
  /** factory name : e.g. VEP */
  public String getName();
  /** factory version */
  public String getVersion();
  /** factory version */
  public String getDescription();
  /** get a description of the associated properties in a prediction */
  public Map<String,String> getProperties();
  /** create a prediction parser from the VCF header */
  public PredictionParser createParser(final VCFHeader header);
  }
