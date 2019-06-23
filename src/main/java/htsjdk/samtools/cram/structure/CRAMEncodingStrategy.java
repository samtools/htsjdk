package htsjdk.samtools.cram.structure;

import htsjdk.samtools.Defaults;
import htsjdk.utils.ValidationUtils;

/**
 * Parameters that can be set to control encoding strategy used on write
 */
public class CRAMEncodingStrategy {
    private final int version = 1;

    // encoding strategies
    //private CompressionMap compressionMap;
    private int gzipCompressionLevel = Defaults.COMPRESSION_LEVEL;
    private int readsPerSlice = 10000; // use to replace CRAMContainerStreamWriter.DEFAULT_RECORDS_PER_SLICE.;
    private int slicesPerContainer = 1;

    // preservation policies ?
    private boolean preserveReadNames = true;
    private String readNamePrefix; // only if preserveReadNames = false
    private boolean retainMD = true;
    private boolean embedReference = false; // embed reference
    private boolean embedBases = true;     // embed bases rather than doing reference compression

    public CRAMEncodingStrategy() {
        // use defaults;
    }

    public CRAMEncodingStrategy setReadsPerSlice(final int readsPerSlice) {
        ValidationUtils.validateArg(readsPerSlice > 0, "Reads must be > 1");
        this.readsPerSlice = readsPerSlice;
        return this;
    }

    public int getGZIPCompressionLevel() { return gzipCompressionLevel; }
    public int getRecordsPerSlice() { return readsPerSlice; }
    public int getSlicesPerContainer() { return slicesPerContainer; }
}
