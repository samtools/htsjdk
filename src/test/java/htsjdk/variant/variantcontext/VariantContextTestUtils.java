package htsjdk.variant.variantcontext;

import htsjdk.samtools.util.Tuple;
import htsjdk.utils.ValidationUtils;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class VariantContextTestUtils {
    /**
     * Reads an entire VCF into memory, returning both its VCFHeader and all VariantContext records in
     * the vcf.
     *
     * For unit/integration testing purposes only! Do not call this method from actual tools!
     *
     * @param vcfPath Path of file to be loaded
     * @return A Tuple with the VCFHeader as the first element, and a List of all VariantContexts from the VCF
     *         as the second element
     */
    public static Tuple<VCFHeader, List<VariantContext>> readEntireVCFIntoMemory(final Path vcfPath) {
        ValidationUtils.nonNull(vcfPath);

        try ( final VCFFileReader vcfReader = new VCFFileReader(vcfPath, false) ) {
            final Object header = vcfReader.getFileHeader();
            if ( ! (header instanceof VCFHeader) ) {
                throw new IllegalArgumentException(vcfPath + " does not have a valid VCF header");
            }

            final List<VariantContext> variantContexts = new ArrayList<>();
            for ( final VariantContext vcfRecord : vcfReader ) {
                variantContexts.add(vcfRecord);
            }

            return new Tuple(header, variantContexts);
        }
    }

}
