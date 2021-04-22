package htsjdk.variant.vcf;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import htsjdk.samtools.Defaults;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.samtools.util.StringUtil;

/**
 * 
 * @author Pierre Lindenbaum
 * A Factory opening {@link VCFReader}
 *
 */
public abstract class VCFReaderFactory {
    private final static Log LOG = Log.getInstance(VCFReaderFactory.class);

    private static final VCFReaderFactory DEFAULT_FACTORY;
    private static VCFReaderFactory currentFactory;

    static {
        DEFAULT_FACTORY = createDefaultVcfReaderFactory();
        currentFactory = DEFAULT_FACTORY;
    }

    /** set the current instance of VCFReaderFactory */
    public static void setInstance(final VCFReaderFactory factory) {
        currentFactory = factory;
    }

    /** get the current instance of VCFReaderFactory */
    public static VCFReaderFactory getInstance() {
        return currentFactory;
    }

    /** get the default instance of VCFReaderFactory */
    public static VCFReaderFactory getDefaultInstance() {
        return DEFAULT_FACTORY;
    }

    /**
     * Open VCFReader that will or will not assert the presence of an index as
     * desired
     */
    public abstract VCFReader open(final Path vcfPath, boolean requireIndex);

    /**
     * Open VCFReader that will or will not assert the presence of an index as
     * desired
     */
    public VCFReader open(final File vcfFile, boolean requireIndex) {
        return open(IOUtil.toPath(vcfFile), requireIndex);
    }

    /**
     * Open VCFReader that will or will not assert the presence of an index as
     * desired
     */
    public VCFReader open(final String vcfUri, boolean requireIndex) {
        if (IOUtil.isUrl(vcfUri)) {
            throw new RuntimeIOException("VCFReaderFactory(" + this.getClass()
                    + ") cannot open URI: " + vcfUri);
        }
        return open(Paths.get(vcfUri), requireIndex);
    }

    /**
     * creates the default instance of VCFReaderFactory A new instance from a
     * specific class it's name is defined by the java property
     * {@link Defaults}.CUSTOM_VCF_READER_FACTORY . Otherwise, we return the
     * default instance of VCFReaderFactory
     */
    private static VCFReaderFactory createDefaultVcfReaderFactory() {
        final String factoryClassName = Defaults.CUSTOM_VCF_READER_FACTORY;
        if (!StringUtil.isBlank(factoryClassName)) {
            try {
                LOG.info("Attempting to load factory class " + factoryClassName);
                final Class<?> clazz = Class.forName(factoryClassName);
                return VCFReaderFactory.class.cast(clazz.newInstance());
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot load " + factoryClassName, e);
            }
        }
        return new DefaultVCFReaderFactory();
    }

    /**
     * default instance of a VCFReaderFactory. It returns a
     * {@link VCFFileReader}
     */
    private static class DefaultVCFReaderFactory extends VCFReaderFactory {
        @Override
        public VCFReader open(final Path vcfPath, boolean requireIndex) {
            return new VCFFileReader(vcfPath, requireIndex);
        }
    }
}
