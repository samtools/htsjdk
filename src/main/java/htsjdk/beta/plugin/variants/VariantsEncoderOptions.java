package htsjdk.beta.plugin.variants;

import htsjdk.beta.io.bundle.IOPathResource;
import htsjdk.beta.io.bundle.OutputStreamResource;
import htsjdk.beta.plugin.HtsEncoderOptions;
import htsjdk.samtools.Defaults;

public class VariantsEncoderOptions implements HtsEncoderOptions {
    private boolean writeSitesOnly                  = false;
    private boolean writeFullFormatField            = false;
    private boolean allowFieldsMissingFromHeader    = false;
    private boolean isAsyncIO                       = false;
    private int bufferSize                          = Defaults.NON_ZERO_BUFFER_SIZE; // 128k


    /**
     * Get the buffer size used when writing to an {@link IOPathResource}. Defaults
     * to {@link Defaults#NON_ZERO_BUFFER_SIZE}.
     *
     * @return the buffer size used when writing to an IOPath
     */
    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * Set an output buffer size to use when writing to an {@link IOPathResource}.
     * Does not affect writing to OutputStreams supplied by the user as a
     * {@link OutputStreamResource}. Set to 0 for no buffering.
     * Defaults to {@link Defaults#NON_ZERO_BUFFER_SIZE}.
     *
     * @param bufferSize the buffer size to use
     * @return updated {@link VariantsEncoderOptions}
     */
    public VariantsEncoderOptions setBuffer(final int bufferSize) {
        this.bufferSize = bufferSize;
        return this;
    }

    /**
     * Determine if sites-only writing is enabled (suppresses writing of genotypes). Defaults to false.
     *
     * @return true if writeSitesOnly is enabled
     */
    public boolean isWriteSitesOnly() {
        return writeSitesOnly;
    }

    /**
     * Set whether only sites are written, without genotypes suppressed. Defaults to false.
     *
     * @param writeSitesOnly true if only sites should be written; or false to include genotypes
     */
    public VariantsEncoderOptions setWriteSitesOnly(boolean writeSitesOnly) {
        this.writeSitesOnly = writeSitesOnly;
        return this;
    }

    /**
     * Determine if writing fields that are missing from the header is allowed. Defaults to false.
     *
     * @return true if writing fields that are missing from the header is allowed, otherwise false
     */
    public boolean isAllowFieldsMissingFromHeader() {
        return allowFieldsMissingFromHeader;
    }

    /**
     * Set whether writing fields that are missing from the header is allowed. Defaults to false.
     *
     * @param allowFieldsMissingFromHeader true to allow missing fields to be written, otherwise false
     */
    public VariantsEncoderOptions setAllowFieldsMissingFromHeader(boolean allowFieldsMissingFromHeader) {
        this.allowFieldsMissingFromHeader = allowFieldsMissingFromHeader;
        return this;
    }

    /**
     * Determine if async IO is enabled for these options. Defaults to false.
     * {@link Defaults#USE_ASYNC_IO_WRITE_FOR_SAMTOOLS}.
     *
     * @return true if async IO is enabled, otherwise false
     */
    public boolean isAsyncIO() {
        return isAsyncIO;
    }

    /**
     * Set whether to enable async IO for these options. Defaults to false.
     *
     * @param asyncIO true to enable async IO, false to disable.
     * @return updated options
     */
    public VariantsEncoderOptions setAsyncIO(final boolean asyncIO) {
        this.isAsyncIO = asyncIO;
        return this;
    }

    /**
     * True if only full format fields should always be written (suppress trimming of trailing missing values).
     * Defaults to false.
     *
     * @return true if only full format fields should always be written
     */
    public boolean isWriteFullFormatField() {
        return writeFullFormatField;
    }

    /** Set whether full format fields should always be written (suppress trimming of trailing missing values).
     * Defaults to false.
     *
     * @param writeFullFormatField true if full format fields should always be written
     */
    public VariantsEncoderOptions setWriteFullFormatField(boolean writeFullFormatField) {
        this.writeFullFormatField = writeFullFormatField;
        return this;
    }

}
