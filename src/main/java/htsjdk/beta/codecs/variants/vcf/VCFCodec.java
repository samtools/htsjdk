package htsjdk.beta.codecs.variants.vcf;

import htsjdk.io.IOPath;
import htsjdk.beta.plugin.variants.VariantsCodec;
import htsjdk.beta.plugin.variants.VariantsFormat;
import htsjdk.samtools.util.FileExtensions;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public abstract class VCFCodec implements VariantsCodec {

    //TODO: FileExtensions.VCF_LIST includes BCF!
    private final Set<String> extensionMap = new HashSet(FileExtensions.VCF_LIST);

    @Override
    public VariantsFormat getFileFormat() { return VariantsFormat.VCF; }

    @Override
    public boolean canDecodeURI(final IOPath ioPath) {
        return extensionMap.stream().anyMatch(ext-> ioPath.hasExtension(ext));
    }

}
