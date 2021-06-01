package htsjdk.beta.plugin.reads;

import htsjdk.beta.codecs.reads.bam.BAMDecoderOptions;
import htsjdk.beta.codecs.reads.cram.CRAMDecoderOptions;
import htsjdk.beta.plugin.HtsDecoderOptions;
import htsjdk.samtools.ValidationStringency;
import htsjdk.utils.ValidationUtils;

import java.nio.channels.SeekableByteChannel;
import java.util.Optional;
import java.util.function.Function;

//TODO:
// not carried forward from SamReaderFactory:
//  SAMRecordFactory (doesn't appear to ever ACTUALLY be used in htsjdk or gatk)
// use is* prefix for boolean getters ?
// finish cloud wrapper implementation

/**
 * ReadsDecoderOptions (shared/common).
 */
public class ReadsDecoderOptions implements HtsDecoderOptions {
    private ValidationStringency validationStringency   = ValidationStringency.STRICT;
    private boolean eagerlyDecode                       = false;
    private boolean cacheFileBasedIndexes               = false;  // honored by BAM and CRAM
    private boolean dontMemoryMapIndexes                = false;  // honored by BAM and CRAM
    //TODO: replace these with a prefetch size args, and use a local channel wrapper implementation
    private Function<SeekableByteChannel, SeekableByteChannel> dataWrapper;
    private Function<SeekableByteChannel, SeekableByteChannel> indexWrapper;

    private BAMDecoderOptions bamDecoderOptions         = new BAMDecoderOptions();
    private CRAMDecoderOptions cramDecoderOptions       = new CRAMDecoderOptions();

    // Public methods
    public ValidationStringency getValidationStringency() {
        return validationStringency;
    }

    public ReadsDecoderOptions setValidationStringency(final ValidationStringency validationStringency) {
        this.validationStringency = validationStringency;
        return this;
    }

    public boolean getEagerlyDecode() {
        return eagerlyDecode;
    }

    public ReadsDecoderOptions setEagerlyDecode(final boolean eagerlyDecode) {
        this.eagerlyDecode = eagerlyDecode;
        return this;
    }

    public boolean isCacheFileBasedIndexes() {
        return cacheFileBasedIndexes;
    }

    public ReadsDecoderOptions setCacheFileBasedIndexes(final boolean cacheFileBasedIndexes) {
        this.cacheFileBasedIndexes = cacheFileBasedIndexes;
        return this;
    }

    public boolean isDontMemoryMapIndexes() {
        return dontMemoryMapIndexes;
    }

    public ReadsDecoderOptions setDontMemoryMapIndexes(final boolean dontMemoryMapIndexes) {
        this.dontMemoryMapIndexes = dontMemoryMapIndexes;
        return this;
    }

    public Optional<Function<SeekableByteChannel, SeekableByteChannel>> getDataWrapper() {
        return Optional.ofNullable(dataWrapper);
    }

    // allow this to be bull
    public void setDataWrapper(Function<SeekableByteChannel, SeekableByteChannel> dataWrapper) {
        this.dataWrapper = dataWrapper;
    }

    public Optional<Function<SeekableByteChannel, SeekableByteChannel>> getIndexWrapper() {
        return Optional.ofNullable(indexWrapper);
    }

    // allow this to be bull
    public void setIndexWrapper(Function<SeekableByteChannel, SeekableByteChannel> indexWrapper) {
        this.indexWrapper = indexWrapper;
    }

    public BAMDecoderOptions getBAMDecoderOptions() { return bamDecoderOptions; }

    public ReadsDecoderOptions setBAMDecoderOptions(final BAMDecoderOptions bamDecoderOptions) {
        ValidationUtils.nonNull(bamDecoderOptions, "bamDecoderOptions");
        this.bamDecoderOptions = bamDecoderOptions;
        return this;
    }

    public CRAMDecoderOptions getCRAMDecoderOptions() { return cramDecoderOptions; }

    public ReadsDecoderOptions setCRAMDecoderOptions(final CRAMDecoderOptions cramDecoderOptions) {
        ValidationUtils.nonNull(cramDecoderOptions, "cramDecoderOptions");
        this.cramDecoderOptions = cramDecoderOptions;
        return this;
    }

}
