package htsjdk.beta.codecs.variants.vcf;

import htsjdk.beta.plugin.HtsContentType;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.variants.VariantsCodec;
import htsjdk.beta.plugin.variants.VariantsFormats;
import htsjdk.samtools.util.FileExtensions;

import java.util.HashSet;
import java.util.Set;

/**
 * Base class for concrete implementations of {@link HtsContentType#VARIANT_CONTEXTS} codecs.
 */
public abstract class VCFCodec implements VariantsCodec {

    // FileExtensions.VCF_LIST includes BCF, which we don't want included here
    private static final Set<String> extensionMap = new HashSet<String>() {
        private static final long serialVersionUID = 1L;
        {
            add(FileExtensions.VCF);
            add(FileExtensions.COMPRESSED_VCF);
        }
    };

    @Override
    public String getFileFormat() { return VariantsFormats.VCF; }

    @Override
    public boolean canDecodeURI(final IOPath ioPath) {
        return extensionMap.stream().anyMatch(ext-> ioPath.hasExtension(ext));
    }

}
