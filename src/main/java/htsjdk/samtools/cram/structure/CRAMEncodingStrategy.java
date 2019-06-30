package htsjdk.samtools.cram.structure;

import htsjdk.samtools.Defaults;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.utils.ValidationUtils;

import com.google.gson.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Parameters that can be set to control encoding strategy used on write.
 */
public class CRAMEncodingStrategy {
    private final int version = 1;
    private final String strategyName = "default";

    // encoding strategies
    private String customCompressionMapPath = "";

    //TODO: should this have separate values for tags (separate from CRAMRecord data) ?
    private int gzipCompressionLevel = Defaults.COMPRESSION_LEVEL;
    private int readsPerSlice = 10000;
    private int slicesPerContainer = 1;

    // should these preservation policies be stored independently of encoding strategy ?
    private boolean preserveReadNames = true;
    private String readNamePrefix = "";          // only if preserveReadNames = false
    private boolean retainMD = true;
    private boolean embedReference = false; // embed reference
    private boolean embedBases = true;      // embed bases rather than doing reference compression

    public CRAMEncodingStrategy() {
        // use defaults;
    }

    public void setEncodingMap(final Path encodingMap) {
        this.customCompressionMapPath = encodingMap.toAbsolutePath().toString();
    }


    public CRAMEncodingStrategy setReadsPerSlice(final int readsPerSlice) {
        ValidationUtils.validateArg(readsPerSlice > 0, "Reads per slice must be > 1");
        this.readsPerSlice = readsPerSlice;
        return this;
    }

    public void setGZIPCompressionLevel(final int compressionLevel) {
        ValidationUtils.validateArg(compressionLevel >=0 && compressionLevel <= 10, "cram gzip compression level must be > 0 and <= 10");
        this.gzipCompressionLevel = compressionLevel;
    }

    public void setSlicesPerContainer(final int slicesPerContainer) {
        ValidationUtils.validateArg(slicesPerContainer >=0, "slicesPerContainer must be > 0");
        this.slicesPerContainer = slicesPerContainer;
    }

    public String getCustomCompressionMapPath() { return customCompressionMapPath; }
    public int getGZIPCompressionLevel() { return gzipCompressionLevel; }
    public int getRecordsPerSlice() { return readsPerSlice; }
    public int getSlicesPerContainer() { return slicesPerContainer; }

    public void writeToPath(final Path outputPath) {
        try (final BufferedWriter fileWriter = Files.newBufferedWriter(outputPath)) {
            final Gson gson = new GsonBuilder().setPrettyPrinting().create();
            final String jsonEncodingString = gson.toJson(this);
            fileWriter.write(jsonEncodingString);
        } catch (final IOException e) {
            throw new RuntimeIOException("Failed creating json file for encoding strategy", e);
        }
    }

    public static CRAMEncodingStrategy readFromPath(final Path outputPath) {
        try (final BufferedReader fileReader = Files.newBufferedReader(outputPath)) {
            final Gson gson = new Gson();
            return gson.fromJson(fileReader, CRAMEncodingStrategy.class);
        } catch (final IOException e) {
            throw new RuntimeIOException("Failed opening encoding strategy json file", e);
        }
    }

    @Override
    public String toString() {
        return "CRAMEncodingStrategy{" +
                "strategyName='" + strategyName + '\'' +
                ", version=" + version +
                ", gzipCompressionLevel=" + gzipCompressionLevel +
                ", readsPerSlice=" + readsPerSlice +
                ", slicesPerContainer=" + slicesPerContainer +
                ", customCompressionMapPath='" + customCompressionMapPath + '\'' +
                ", preserveReadNames=" + preserveReadNames +
                ", readNamePrefix='" + readNamePrefix + '\'' +
                ", retainMD=" + retainMD +
                ", embedReference=" + embedReference +
                ", embedBases=" + embedBases +
                '}';
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
