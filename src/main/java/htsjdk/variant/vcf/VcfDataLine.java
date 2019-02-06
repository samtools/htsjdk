package htsjdk.variant.vcf;

import htsjdk.samtools.util.QualityUtil;
import htsjdk.tribble.Feature;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypesContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface VcfDataLine extends Feature {
    String getID();

    boolean isVariant();

    List<Allele> getAlleles();
    Allele getReference();
    List<Allele> getAlternateAlleles();
    Allele getAlternateAllele(int index);
    int getNAlleles();

    boolean hasLog10PError();
    double getLog10PError();

    default double getPhredScaledQual(){
        return QualityUtil.convertLog10PErrorToPhredScale(getLog10PError());
    }

    Map<String, Object> getAttributes();

    boolean isFiltered();
    Set<String> getFilters();
    boolean filtersWereApplied();

    GenotypesContext getGenotypes();
    Genotype getGenotype(String sample);
    List<String> calcVCFGenotypeKeys(VCFHeader header);

    int getMaxPloidy(int i);
}
