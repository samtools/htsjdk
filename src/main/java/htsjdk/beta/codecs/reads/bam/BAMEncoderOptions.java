package htsjdk.beta.codecs.reads.bam;

import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.Defaults;
import htsjdk.samtools.util.AbstractAsyncWriter;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.zip.DeflaterFactory;
import htsjdk.utils.ValidationUtils;

/**
 * Encoder options specific to BAM encoders.
 */
public class BAMEncoderOptions {
    public static final int DEAFULT_MAX_RECORDS_IN_RAM = 500000;

    private boolean useAsyncIo = Defaults.USE_ASYNC_IO_WRITE_FOR_SAMTOOLS;
    private int asyncOutputBufferSize = AbstractAsyncWriter.DEFAULT_QUEUE_SIZE;
    private int outputBufferSize = Defaults.BUFFER_SIZE;
    private IOPath tempDirPath = new HtsPath(IOUtil.getDefaultTmpDirPath().toString());
    private int compressionLevel = BlockCompressedOutputStream.getDefaultCompressionLevel();
    private Integer maxRecordsInRam = DEAFULT_MAX_RECORDS_IN_RAM;
    private DeflaterFactory deflaterFactory = BlockCompressedOutputStream.getDefaultDeflaterFactory();
    // SAM only ?:   private SamFlagField samFlagFieldOutput = SamFlagField.NONE;

    /**
     * Determine if async IO is enabled for these options. Defaults to
     * {@link Defaults#USE_ASYNC_IO_WRITE_FOR_SAMTOOLS}.
     *
     * @return true if async IO is enabled, otherwise false
     */
    public boolean isUseAsyncIo() {
        return useAsyncIo;
    }

    /**
     * Set whether to enable async IO for these options.
     *
     * @param useAsyncIo true to enable async IO, false to disable.
     * @return updated options
     */
    public BAMEncoderOptions setUseAsyncIo(final boolean useAsyncIo) {
        this.useAsyncIo = useAsyncIo;
        return this;
    }

    /**
     * Get the async output buffer size used for these options. If and only if using asynchronous IO
     * sets the maximum number of records that can be buffered per writer before producers will block
     * when trying to write another record. Defaults to {@link AbstractAsyncWriter#DEFAULT_QUEUE_SIZE}.
     *
     * @return async output buffer size used for these options
     */
    public int getAsyncOutputBufferSize() {
        return asyncOutputBufferSize;
    }

    /**
     * Get the async output buffer size used for these options. If and only if using asynchronous IO
     * sets the maximum number of records that can be buffered per writer before producers will block
     * when trying to write another record.
     *
     * @param asyncOutputBufferSize async output buffer size used for these options
     * @return updated options
     */
    public BAMEncoderOptions setAsyncOutputBufferSize(final int asyncOutputBufferSize) {
        this.asyncOutputBufferSize = asyncOutputBufferSize;
        return this;
    }

    /**
     * Get the output buffer size for these options. This determines the size of the output
     * stream buffer used when writing outputs. Default value is {@link Defaults#BUFFER_SIZE}.
     *
     * @return the buffer size for these options
     */
    public int getOutputBufferSize() {
        return outputBufferSize;
    }

    /**
     * Set the buffer size for these options. This determines the size of the output stream buffer used
     * when writing outputs. Defaults value is {@link Defaults#BUFFER_SIZE}.
     *
     * @param outputBufferSize the buffer size for these options
     * @return updated options
     */
    public BAMEncoderOptions setOutputBufferSize(final int outputBufferSize) {
        this.outputBufferSize = outputBufferSize;
        return this;
    }

    /**
     * Get the temporary directory path for these options. The temporary directory path is used for temporary
     * files created during output sorting operations. Defaults value is {@link IOUtil#getDefaultTmpDirPath()}.
     *
     * @return the temp directory path to be used for these options
     */
    public IOPath getTempDirPath() {
        return tempDirPath;
    }

    /**
     * Get the temporary directory path these options. The temporary directory path is used for temporary
     * files created during output sorting operations. Defaults value is  {@link IOUtil#getDefaultTmpDirPath()}.
     *
     * @param tempDirPath temporary directory path to use
     * @return updated options
     */
    public BAMEncoderOptions setTempDirPath(final IOPath tempDirPath) {
        ValidationUtils.nonNull(tempDirPath, "tempDirPath");
        this.tempDirPath = tempDirPath;
        return this;
    }

    /**
     * Set the compression level for these options. Defaults value is
     * {@link htsjdk.samtools.util.BlockCompressedStreamConstants#DEFAULT_COMPRESSION_LEVEL}.
     * See {@link htsjdk.samtools.util.BlockCompressedStreamConstants#DEFAULT_COMPRESSION_LEVEL}
     *
     * @return the compression level for these options, 1 <= compressionLevel <= 9
     */
    public int getCompressionLevel() {
        return compressionLevel;
    }

    /**
     * Set the compression level for these options. Defaults value is
     * {@link htsjdk.samtools.util.BlockCompressedStreamConstants#DEFAULT_COMPRESSION_LEVEL}.
     *
     * @param compressionLevel the compression level for these options, 1 <= compressionLevel <= 9
     * @return updated options
     */
    public BAMEncoderOptions setCompressionLevel(int compressionLevel) {
        this.compressionLevel = compressionLevel;
        return this;
    }

    /**
     * Set the maximum records kept in RAM before spilling to disk for these options. May be null. Default
     * value is
     *
     * Maximum records in RAM determines the amount of memory used by the writer when sorting output during writing.
     * When writing very large sorted SAM files, you may need use this option in order to avoid running out of
     * file handles. The RAM available to the JVM may need to be increased in order to hold the specified
     * number of records in RAM.
     *
     * Defaults value is {@link BAMEncoderOptions#DEAFULT_MAX_RECORDS_IN_RAM}.
     *
     * @return the maximum records kept in ram before spilling to disk for these options
     */
    public int getMaxRecordsInRam() {
        return maxRecordsInRam;
    }

    /**
     * Get the maximum records kept in ram before spilling to disk for these options. Maximum records in RAM
     * determines the amount of memory used by the writer when sorting output during writing.
     * When writing very large sorted SAM files, you may need use this option in order to avoid running out of
     * file handles. The RAM available to the JVM may need to be increased in order to hold the specified
     * number of records in RAM.
     *
     * Defaults value is {@link BAMEncoderOptions#DEAFULT_MAX_RECORDS_IN_RAM}.
     *
     * @param maxRecordsInRam the maximum records kept in ram before spilling to disk. may be null.
     * @return updated options
     */
    public BAMEncoderOptions setMaxRecordsInRam(int maxRecordsInRam) {
        this.maxRecordsInRam = maxRecordsInRam;
        return this;
    }

    /**
     * Get the {@link DeflaterFactory} for these options. Default value is
     * {@link BlockCompressedOutputStream#getDefaultDeflaterFactory()}.
     *
     * @return the {@link DeflaterFactory} for these options
     */
    public DeflaterFactory getDeflaterFactory() {
        return deflaterFactory;
    }

    /**
     * Set the {@link DeflaterFactory} for these options for these options.
     *
     *  Default value is {@link BlockCompressedOutputStream#getDefaultDeflaterFactory()}.
     *
     * @param deflaterFactory the {@link DeflaterFactory} for these options
     * @return updated options
     */
    public BAMEncoderOptions setDeflaterFactory(DeflaterFactory deflaterFactory) {
        this.deflaterFactory = deflaterFactory;
        return this;
    }

}
