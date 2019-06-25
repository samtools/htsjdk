package htsjdk.samtools.cram.structure;

import htsjdk.samtools.Defaults;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.utils.ValidationUtils;

import com.google.gson.*;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Parameters that can be set to control encoding strategy used on write
 */
public class CRAMEncodingStrategy {
    private final int version = 1;
    private final String strategyName = "default";

    // encoding strategies
    private String customCompressionMapPath = "";
    private int gzipCompressionLevel = Defaults.COMPRESSION_LEVEL;
    private int readsPerSlice = 10000; // use to replace CRAMContainerStreamWriter.DEFAULT_RECORDS_PER_SLICE.;
    private int slicesPerContainer = 1;

    // should these preservation policies be stored independentlyof encoding strategy ?
    private boolean preserveReadNames = true;
    private String readNamePrefix = "";          // only if preserveReadNames = false
    private boolean retainMD = true;
    private boolean embedReference = false; // embed reference
    private boolean embedBases = true;      // embed bases rather than doing reference compression

    //TODO: should there be a DEFAULT_RECORDS_PER_SLICE ?

    public CRAMEncodingStrategy() {
        // use defaults;
    }

    public CRAMEncodingStrategy setReadsPerSlice(final int readsPerSlice) {
        ValidationUtils.validateArg(readsPerSlice > 0, "Reads per slice must be > 1");
        this.readsPerSlice = readsPerSlice;
        return this;
    }


    public String getCustomCompressionMapPath() { return customCompressionMapPath; }
    public int getGZIPCompressionLevel() { return gzipCompressionLevel; }
    public int getRecordsPerSlice() { return readsPerSlice; }
    public int getSlicesPerContainer() { return slicesPerContainer; }

    public void writeToPath(final Path outputPath) {
        //TODO: replace FilerWriter with something path friendly
        try (final FileWriter fw = new FileWriter(outputPath.toFile())) {
            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.setPrettyPrinting();
            final Gson gson = gsonBuilder.create();
            final String jsonEncodingString = gson.toJson(this);
            fw.write(jsonEncodingString);
        } catch (final IOException e) {
            throw new RuntimeIOException("Failed creating json file for encoding strategy", e);
        }
    }

    public static CRAMEncodingStrategy readFromPath(final Path outputPath) {
        //TODO: replace FileReader with something path friendly
        try (final FileReader fr = new FileReader(outputPath.toFile())) {
            final Gson gson = new Gson();
            return gson.fromJson(fr, CRAMEncodingStrategy.class);
        } catch (final IOException e) {
            throw new RuntimeIOException("Failed opening encoding strategy json file", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CRAMEncodingStrategy that = (CRAMEncodingStrategy) o;

        if (version != that.version) return false;
        if (gzipCompressionLevel != that.gzipCompressionLevel) return false;
        if (readsPerSlice != that.readsPerSlice) return false;
        if (getSlicesPerContainer() != that.getSlicesPerContainer()) return false;
        if (preserveReadNames != that.preserveReadNames) return false;
        if (retainMD != that.retainMD) return false;
        if (embedReference != that.embedReference) return false;
        if (embedBases != that.embedBases) return false;
        return readNamePrefix != null ? readNamePrefix.equals(that.readNamePrefix) : that.readNamePrefix == null;

    }

    @Override
    public int hashCode() {
        int result = version;
        result = 31 * result + gzipCompressionLevel;
        result = 31 * result + readsPerSlice;
        result = 31 * result + getSlicesPerContainer();
        result = 31 * result + (preserveReadNames ? 1 : 0);
        result = 31 * result + (readNamePrefix != null ? readNamePrefix.hashCode() : 0);
        result = 31 * result + (retainMD ? 1 : 0);
        result = 31 * result + (embedReference ? 1 : 0);
        result = 31 * result + (embedBases ? 1 : 0);
        return result;
    }
}
