package htsjdk.variant.prediction;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import java.util.Optional;
import java.util.Map;
import java.util.Set;

/** One Prediction */
public interface Prediction {
  /** the variant for this prediction */
  public VariantContext getVariantContext();
  /** alt allele for this prediction */
  public Optional<Allele> getAlt();
  /** Sequence Ontology terms */
  public Set<SOTerm> getSOTerms();
  
    
  public String getGeneName();
  public String getGeneAccession();
  public String getGeneId();
  public String getTranscriptName();
  public String getTranscriptAccession();
  public String getTranscriptId();
  public String getProteinName();
  public String getProteinAccession();
  public String getProteinId();
  public Integer getPositionInCDna();
  public Integer getPositionProtein();
  public String getRefCodon();
  public String getAlCodon();
  public Map<String,Object> getProperties();
  }
