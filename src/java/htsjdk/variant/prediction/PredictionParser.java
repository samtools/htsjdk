package htsjdk.variant.prediction;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import java.util.List;

/** parses a variant context  and return a list of Prediction */
public interface PredictionParser {
  /** get owner factory */
  public PredictionParserFactory getFactory();
  /** get available predictions for this variant */
  public List<Prediction> parse(final VariantContext ctx);
  }
