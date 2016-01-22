package htsjdk.variant.prediction;

/** Sequence Ontology Term */
public interface SOTerm {
  public String getName();
  public String getAccession();
  public boolean isDescendantOf(final SOTerm parent);
}
