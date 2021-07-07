package htsjdk.beta.codecs.reads.bam;

import htsjdk.io.IOPath;
import htsjdk.samtools.Defaults;
import htsjdk.samtools.util.AbstractAsyncWriter;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.zip.DeflaterFactory;

/**
 * Encoder options for BAM encoders.
 */
public class BAMEncoderOptions {
    // SAM ?:   private SamFlagField samFlagFieldOutput = SamFlagField.NONE;
    private boolean createOutputIndex = Defaults.CREATE_INDEX;
    private boolean createMD5File = Defaults.CREATE_MD5;
    private boolean useAsyncIo = Defaults.USE_ASYNC_IO_WRITE_FOR_SAMTOOLS;
    private int asyncOutputBufferSize = AbstractAsyncWriter.DEFAULT_QUEUE_SIZE;
    private int bufferSize = Defaults.BUFFER_SIZE;
    private IOPath tempDirPath;
    private int compressionLevel = BlockCompressedOutputStream.getDefaultCompressionLevel();
    private Integer maxRecordsInRam = null;
    private DeflaterFactory deflaterFactory = BlockCompressedOutputStream.getDefaultDeflaterFactory();

    public boolean isCreateOutputIndex() {
        return createOutputIndex;
    }

    public void setCreateOutputIndex(boolean createOutputIndex) {
        this.createOutputIndex = createOutputIndex;
    }

    public boolean isCreateMD5File() {
        return createMD5File;
    }

    public void setCreateMD5File(boolean createMD5File) {
        this.createMD5File = createMD5File;
    }

    public boolean isUseAsyncIo() {
        return useAsyncIo;
    }

    public void setUseAsyncIo(boolean useAsyncIo) {
        this.useAsyncIo = useAsyncIo;
    }

    public int getAsyncOutputBufferSize() {
        return asyncOutputBufferSize;
    }

    public void setAsyncOutputBufferSize(int asyncOutputBufferSize) {
        this.asyncOutputBufferSize = asyncOutputBufferSize;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public IOPath getTempDirPath() {
        return tempDirPath;
    }

    public void setTempDirPath(IOPath tempDirPath) {
        this.tempDirPath = tempDirPath;
    }

    public int getCompressionLevel() {
        return compressionLevel;
    }

    public void setCompressionLevel(int compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    public Integer getMaxRecordsInRam() {
        return maxRecordsInRam;
    }

    public void setMaxRecordsInRam(Integer maxRecordsInRam) {
        this.maxRecordsInRam = maxRecordsInRam;
    }

    public DeflaterFactory getDeflaterFactory() {
        return deflaterFactory;
    }

    public void setDeflaterFactory(DeflaterFactory deflaterFactory) {
        this.deflaterFactory = deflaterFactory;
    }

}
