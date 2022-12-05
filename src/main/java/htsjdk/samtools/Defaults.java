package htsjdk.samtools;

import htsjdk.samtools.util.Log;

import java.io.File;
import java.util.Collections;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Embodies defaults for global values that affect how the SAM JDK operates. Defaults are encoded in the class
 * and are also overridable using system properties.
 *
 * @author Tim Fennell
 */
public class Defaults {
      private static final Log log = Log.getInstance(Defaults.class);
    
    /** Should BAM index files be created when writing out coordinate sorted BAM files?  Default = false. */
    public static final boolean CREATE_INDEX;

    /** Should MD5 files be created when writing out SAM and BAM files?  Default = false. */
    public static final boolean CREATE_MD5;

    /** Should asynchronous read I/O be used where supported by the samtools package (one thread per file).
     *  Default = false.
     */
    public static final boolean USE_ASYNC_IO_READ_FOR_SAMTOOLS;

    /** Should asynchronous write I/O be used where supported by the samtools package (one thread per file).
     *  Default = false.
     */
    public static final boolean USE_ASYNC_IO_WRITE_FOR_SAMTOOLS;

    /** Should asynchronous write I/O be used where supported by the tribble package (one thread per file).
     *  Default = false.
     */
    public static final boolean USE_ASYNC_IO_WRITE_FOR_TRIBBLE;

    /** Compression level to be used for writing BAM and other block-compressed outputs.  Default = 5. */
    public static final int COMPRESSION_LEVEL;

    /** Buffer size, in bytes, used whenever reading/writing files or streams.  Default = 128k. */
    public static final int BUFFER_SIZE;

    /** The output format of the flag field when writing SAM text.  Ignored for reading SAM text.  Default = DECIMAL */
    public static final SamFlagField SAM_FLAG_FIELD_FORMAT;

    /**
     * The extension to assume a sam file has when the actual file doesn't have an extension, useful
     * for when outputing to /dev/stdout, for example.
     */
    public static final String DEFAULT_SAM_EXTENSION;

    /**
     * The extension to assume a vcf has when the actual file doesn't have an extension, useful
     * for when outputing to /dev/stdout, for example.
     */
    public static final String DEFAULT_VCF_EXTENSION;

    /**
     * Even if BUFFER_SIZE is 0, this is guaranteed to be non-zero.  If BUFFER_SIZE is non-zero,
     * this == BUFFER_SIZE (Default = 128k).
     */
    public static final int NON_ZERO_BUFFER_SIZE;

    /**
     * The reference FASTA file.  If this is not set, the file is null.  This file may be required for reading
     * writing SAM files (ex. CRAM).  Default = null.
     */
    public static final File REFERENCE_FASTA;

    /** Custom reader factory able to handle URL based resources like ga4gh.
     *  Expected format: <url prefix>,<fully qualified factory class name>[,<jar file name>]
     *  E.g. https://www.googleapis.com/genomics/v1beta/reads/,com.google.genomics.ReaderFactory
     *  OR https://www.googleapis.com/genomics/v1beta/reads/,com.google.genomics.ReaderFactory,/tmp/genomics.jar
     *  Default = "".
     */
    public static final String CUSTOM_READER_FACTORY;

    /**
     * The name of the system property which contains the location of a reference cache.
     */
    public static final String REF_CACHE_PROPERTY_NAME = "ref_cache";

    /**
     * Pathname to a local disk directory housing a cache of reference files.
     * The pathname can be constructed using %nums and %s notation, consuming num characters of the MD5sum.
     * For example /local/ref_cache/%2s/%2s/%s will create 2 nested subdirectories with the filenames in
     * the deepest directory being the last 28 characters of the md5sum.
     * The system property corresponds to the REF_CACHE environment variable implemented by samtools
     * (see http://www.htslib.org/doc/samtools.html#ENVIRONMENT_VARIABLES).
     */
    public static final String REF_CACHE;

    /**
     * Boolean describing whether downloading a reference file is allowed (for CRAM files),
     * in case the reference file is not specified by the user
     * Enabling this is not necessarily a good idea, since this process often fails.  Default = false.
     */
    public static final boolean USE_CRAM_REF_DOWNLOAD;

    /**
     * A mask (pattern) to use when building EBI reference service URL for a
     * given MD5 checksum. Must contain one and only one string placeholder.
     * Default = "https://www.ebi.ac.uk/ena/cram/md5/%s".
     */
    public static final String EBI_REFERENCE_SERVICE_URL_MASK;

    /**
     * Boolean describing whether downloading of SRA native libraries is allowed,
     * in case such native libraries are not found locally.  Default = false.
     */
    public static final boolean SRA_LIBRARIES_DOWNLOAD;


    /**
     * The name of the system property that disables snappy.  Default = "snappy.disable".
     */
    public static final String DISABLE_SNAPPY_PROPERTY_NAME = "snappy.disable";

    /**
     * Disable use of the Snappy compressor.  Default = false.
     */
    public static final boolean DISABLE_SNAPPY_COMPRESSOR;


    public static final String SAMJDK_PREFIX = "samjdk.";
    static {
        CREATE_INDEX = getBooleanProperty("create_index", false);
        CREATE_MD5 = getBooleanProperty("create_md5", false);
        USE_ASYNC_IO_READ_FOR_SAMTOOLS = getBooleanProperty("use_async_io_read_samtools", false);
        USE_ASYNC_IO_WRITE_FOR_SAMTOOLS = getBooleanProperty("use_async_io_write_samtools", false);
        USE_ASYNC_IO_WRITE_FOR_TRIBBLE = getBooleanProperty("use_async_io_write_tribble", false);
        COMPRESSION_LEVEL = getIntProperty("compression_level", 5);
        DEFAULT_SAM_EXTENSION = getStringProperty("default_sam_type", "bam");
        DEFAULT_VCF_EXTENSION = getStringProperty("default_vcf_type", "vcf");
        BUFFER_SIZE = getIntProperty("buffer_size", 1024 * 128);
        if (BUFFER_SIZE == 0) {
            NON_ZERO_BUFFER_SIZE = 1024 * 128;
        } else {
            NON_ZERO_BUFFER_SIZE = BUFFER_SIZE;
        }
        REFERENCE_FASTA = getFileProperty("reference_fasta", null);
        REF_CACHE = getStringProperty(REF_CACHE_PROPERTY_NAME, "");
        USE_CRAM_REF_DOWNLOAD = getBooleanProperty("use_cram_ref_download", false);
        EBI_REFERENCE_SERVICE_URL_MASK = "https://www.ebi.ac.uk/ena/cram/md5/%s";
        CUSTOM_READER_FACTORY = getStringProperty("custom_reader", "");
        SAM_FLAG_FIELD_FORMAT = SamFlagField.valueOf(getStringProperty("sam_flag_field_format", SamFlagField.DECIMAL.name()));
        SRA_LIBRARIES_DOWNLOAD = getBooleanProperty("sra_libraries_download", false);
        DISABLE_SNAPPY_COMPRESSOR = getBooleanProperty(DISABLE_SNAPPY_PROPERTY_NAME, false);
    }

    /**
     * Returns a map of all default values (keys are names), lexicographically sorted by keys.
     * The returned map is unmodifiable.
     * This function is useful for example when logging all defaults.
     */
    public static SortedMap<String, Object> allDefaults(){
        final SortedMap<String, Object> result = new TreeMap<>();
        result.put("CREATE_INDEX", CREATE_INDEX);
        result.put("CREATE_MD5", CREATE_MD5);
        result.put("USE_ASYNC_IO_READ_FOR_SAMTOOLS", USE_ASYNC_IO_READ_FOR_SAMTOOLS);
        result.put("USE_ASYNC_IO_WRITE_FOR_SAMTOOLS", USE_ASYNC_IO_WRITE_FOR_SAMTOOLS);
        result.put("USE_ASYNC_IO_WRITE_FOR_TRIBBLE", USE_ASYNC_IO_WRITE_FOR_TRIBBLE);
        result.put("COMPRESSION_LEVEL", COMPRESSION_LEVEL);
        result.put("BUFFER_SIZE", BUFFER_SIZE);
        result.put("NON_ZERO_BUFFER_SIZE", NON_ZERO_BUFFER_SIZE);
        result.put("REFERENCE_FASTA", REFERENCE_FASTA);
        result.put("REF_CACHE", REF_CACHE);
        result.put("USE_CRAM_REF_DOWNLOAD", USE_CRAM_REF_DOWNLOAD);
        result.put("EBI_REFERENCE_SERVICE_URL_MASK", EBI_REFERENCE_SERVICE_URL_MASK);
        result.put("CUSTOM_READER_FACTORY", CUSTOM_READER_FACTORY);
        result.put("SAM_FLAG_FIELD_FORMAT", SAM_FLAG_FIELD_FORMAT);
        result.put("DISABLE_SNAPPY_COMPRESSOR", DISABLE_SNAPPY_COMPRESSOR);
        return Collections.unmodifiableSortedMap(result);
    }

    /** Gets a string system property, prefixed with "samjdk." using the default 
     * if the property does not exist or if the java.security manager raises an exception for
     * applications started with  -Djava.security.manager  . */
    private static String getStringProperty(final String name, final String def) {
        try {
            return System.getProperty(Defaults.SAMJDK_PREFIX + name, def);
        } catch (final java.security.AccessControlException error) {
            log.warn(error,"java Security Manager forbids 'System.getProperty(\"" + name + "\")' , returning default value: " + def );
            return def;
        }
    }

    /** Checks whether a string system property, prefixed with "samjdk.", exists.
     * If the property does not exist or if the java.security manager raises an exception for
     * applications started with  -Djava.security.manager  this method returns false. */
    private static boolean hasProperty(final String name){
        try {
            return null != System.getProperty(Defaults.SAMJDK_PREFIX + name);
        } catch (final java.security.AccessControlException error) {
            log.warn(error,"java Security Manager forbids 'System.getProperty(\"" + name + "\")' , returning false");
            return false;
        }
    }

    /** Gets a boolean system property, prefixed with "samjdk." using the default if the property does not exist. */
    private static boolean getBooleanProperty(final String name, final boolean def) {
        final String value = getStringProperty(name, Boolean.toString(def));
        return Boolean.parseBoolean(value);
    }

    /** Gets an int system property, prefixed with "samjdk." using the default if the property does not exist. */
    private static int getIntProperty(final String name, final int def) {
        final String value = getStringProperty(name, Integer.toString(def));
        return Integer.parseInt(value);
    }

    /** Gets a File system property, prefixed with "samjdk." using the default if the property does not exist. */
    private static File getFileProperty(final String name, final String def) {
        final String value = getStringProperty(name, def);
        Optional<File> maybeFile = Optional.ofNullable(value).map(File::new);
        maybeFile.ifPresent(f -> {
            if (!f.exists()) {
                log.warn(String.format("File property for %s has value %s. However file %s doesn't exist.", SAMJDK_PREFIX + name, value, f.getAbsolutePath()));
            } else {
                log.info(String.format("Found file for property %s: %s ", SAMJDK_PREFIX + name, f.getAbsolutePath()));
            }
        });
        return maybeFile.orElse(null);
    }
}
