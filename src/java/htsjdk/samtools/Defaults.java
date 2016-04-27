package htsjdk.samtools;

import htsjdk.samtools.util.Log;

import java.io.File;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Embodies defaults for global values that affect how the SAM JDK operates. Defaults are encoded in the class
 * and are also overridable using system properties.
 *
 * @author Tim Fennell
 */
public class Defaults {
    private static Log log = Log.getInstance(Defaults.class);
    
    /** Should BAM index files be created when writing out coordinate sorted BAM files?  Default = false. */
    public static final boolean CREATE_INDEX;

    /** Should MD5 files be created when writing out SAM and BAM files?  Default = false. */
    public static final boolean CREATE_MD5;

    /** Should asynchronous I/O be used where supported throughout all of htsjdk (one thread per file).
     *  Note: this option takes precedence over {@link #USE_ASYNC_IO_FOR_SAMTOOLS} and {@link #USE_ASYNC_IO_FOR_TRIBBLE}.
     *  Default = false.
     */
    public static final boolean USE_ASYNC_IO;

    /** Should asynchronous I/O be used where supported by the samtools package (one thread per file).
     *  Note: The {@link #USE_ASYNC_IO} option takes precedence over this option.
     *  Default = false.
     */
    public static final boolean USE_ASYNC_IO_FOR_SAMTOOLS;

    /** Should asynchronous I/O be used where supported by the tribble package (one thread per file).
     *  Note: performance may depend on the characteristics of the input file (eg number of samples in the VCF) and should be tested on a case-by-case basis.
     *  In particular, asynchronous reading of VCF files with few samples is known to perform worse than synchronous reading.
     *  Note: The {@link #USE_ASYNC_IO} option takes precedence over this option.
     *  Default = false.
     */
    public static final boolean USE_ASYNC_IO_FOR_TRIBBLE;

    /** Compresion level to be used for writing BAM and other block-compressed outputs.  Default = 5. */
    public static final int COMPRESSION_LEVEL;

    /** Buffer size, in bytes, used whenever reading/writing files or streams.  Default = 128k. */
    public static final int BUFFER_SIZE;

    /**
     * Even if BUFFER_SIZE is 0, this is guaranteed to be non-zero.  If BUFFER_SIZE is non-zero,
     * this == BUFFER_SIZE
     */
    public static final int NON_ZERO_BUFFER_SIZE;

    /** Should BlockCompressedOutputStream attempt to load libIntelDeflater? */
    public static final boolean TRY_USE_INTEL_DEFLATER;

    /**
     * Path to libIntelDeflater.so.  If this is not set, the library is looked for in the directory
     * where the executable jar lives.
     */
    public static final String INTEL_DEFLATER_SHARED_LIBRARY_PATH;

    /**
     * The reference FASTA file.  If this is not set, the file is null.  This file may be required for reading
     * writing SAM files (ex. CRAM).
     */
    public static final File REFERENCE_FASTA;

    /** Custom reader factory able to handle URL based resources like ga4gh.
     *  Expected format: <url prefix>,<fully qualified factory class name>[,<jar file name>]
     *  E.g. https://www.googleapis.com/genomics/v1beta/reads/,com.google.genomics.ReaderFactory
     *  OR https://www.googleapis.com/genomics/v1beta/reads/,com.google.genomics.ReaderFactory,/tmp/genomics.jar
     */
    public static final String CUSTOM_READER_FACTORY;

    /**
     * Boolean describing whether downloading a reference file is allowed (for CRAM files),
     * in case the reference file is not specified by the user
     * Enabling this is not necessarily a good idea, since this process often fails
     */
    public static final boolean USE_CRAM_REF_DOWNLOAD;

    /**
     * A mask (pattern) to use when building EBI reference service URL for a
     * given MD5 checksum. Must contain one and only one string placeholder.
     */
    public static final String EBI_REFERENCE_SEVICE_URL_MASK;


    static {
        CREATE_INDEX = getBooleanProperty("create_index", false);
        CREATE_MD5 = getBooleanProperty("create_md5", false);
        if (hasProperty("use_async_io")){
            USE_ASYNC_IO = getBooleanProperty("use_async_io", false);
            USE_ASYNC_IO_FOR_SAMTOOLS = USE_ASYNC_IO;
            USE_ASYNC_IO_FOR_TRIBBLE = USE_ASYNC_IO;
        } else {
            USE_ASYNC_IO = false;
            USE_ASYNC_IO_FOR_SAMTOOLS = getBooleanProperty("use_async_io_samtools", false);
            USE_ASYNC_IO_FOR_TRIBBLE = getBooleanProperty("use_async_io_tribble", false);
        }
        COMPRESSION_LEVEL = getIntProperty("compression_level", 5);
        BUFFER_SIZE = getIntProperty("buffer_size", 1024 * 128);
        TRY_USE_INTEL_DEFLATER = getBooleanProperty("try_use_intel_deflater", true);
        INTEL_DEFLATER_SHARED_LIBRARY_PATH = getStringProperty("intel_deflater_so_path", null);
        if (BUFFER_SIZE == 0) {
            NON_ZERO_BUFFER_SIZE = 1024 * 128;
        } else {
            NON_ZERO_BUFFER_SIZE = BUFFER_SIZE;
        }
        REFERENCE_FASTA = getFileProperty("reference_fasta", null);
        USE_CRAM_REF_DOWNLOAD = getBooleanProperty("use_cram_ref_download", false);
        EBI_REFERENCE_SEVICE_URL_MASK = "http://www.ebi.ac.uk/ena/cram/md5/%s";
        CUSTOM_READER_FACTORY = getStringProperty("custom_reader", "");
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
        result.put("USE_ASYNC_IO", USE_ASYNC_IO);
        result.put("USE_ASYNC_IO_FOR_SAMTOOLS", USE_ASYNC_IO_FOR_SAMTOOLS);
        result.put("USE_ASYNC_IO_FOR_TRIBBLE", USE_ASYNC_IO_FOR_TRIBBLE);
        result.put("COMPRESSION_LEVEL", COMPRESSION_LEVEL);
        result.put("BUFFER_SIZE", BUFFER_SIZE);
        result.put("TRY_USE_INTEL_DEFLATER", TRY_USE_INTEL_DEFLATER);
        result.put("INTEL_DEFLATER_SHARED_LIBRARY_PATH", INTEL_DEFLATER_SHARED_LIBRARY_PATH);
        result.put("NON_ZERO_BUFFER_SIZE", NON_ZERO_BUFFER_SIZE);
        result.put("REFERENCE_FASTA", REFERENCE_FASTA);
        result.put("USE_CRAM_REF_DOWNLOAD", USE_CRAM_REF_DOWNLOAD);
        result.put("EBI_REFERENCE_SEVICE_URL_MASK", EBI_REFERENCE_SEVICE_URL_MASK);
        result.put("CUSTOM_READER_FACTORY", CUSTOM_READER_FACTORY);
        return Collections.unmodifiableSortedMap(result);
    }

    /** Gets a string system property, prefixed with "samjdk." using the default 
     * if the property does not exist or if the java.security manager raises an exception for
     * applications started with  -Djava.security.manager  . */
    private static String getStringProperty(final String name, final String def) {
        try {
            return System.getProperty("samjdk." + name, def);
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
            return null != System.getProperty("samjdk." + name);
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

    /** Gets a File system property, prefixed with "samdjk." using the default if the property does not exist. */
    private static File getFileProperty(final String name, final String def) {
        final String value = getStringProperty(name, def);
        // TODO: assert that it is readable
        return (null == value) ? null : new File(value);
    }
}
