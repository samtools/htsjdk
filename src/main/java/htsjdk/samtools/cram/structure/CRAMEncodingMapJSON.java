/*
 * Copyright (c) 2019 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.compression.GZIPExternalCompressor;
import htsjdk.samtools.cram.compression.RANSExternalCompressor;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * Bridge class for JSON serialization of CompressionHeaderEncodingMap objects. This allows
 * CompressionHeaderEncodingMap objects to be serialized and used as input for testing or running
 * with various encoding map strategy combinations.
 */
class CRAMEncodingMapJSON {
    // Include a map version and a CRAM that can be validated after round-tripping (these names appear in
    // the json file).
    final private long htsjdkCRAMEncodingMapVersion;
    final private Version targetCRAMVersion;

    // the actual list of entries that are serialized
    final List<CRAMEncodingMapJSONEntry> entries = new ArrayList<>();

    public CRAMEncodingMapJSON(final long serialVersionUID, final Version targetCRAMVersion) {
        this.htsjdkCRAMEncodingMapVersion = serialVersionUID;
        this.targetCRAMVersion = targetCRAMVersion;
    }

    /**
     * Add an CRAMEncodingMapJSONEntry to be serialized
     * @param entry entry to be serialized
     */
    public void addEncodingMapEntry(final CRAMEncodingMapJSONEntry entry) { entries.add(entry); }

    /**
     * @return the target CompressionHeaderEncodingMap version this map represents
     */
    public List<CRAMEncodingMapJSONEntry> getEncodingMapEntries() { return entries; }

    /*
     * @return the target CRAM version for which this map is intended
     */
    public Version getTargetCRAMVersion() { return targetCRAMVersion; }

    /**
     * @return the target CompressionHeaderEncodingMap version this map represents
     */
    public long getEncodingMapVersion() { return htsjdkCRAMEncodingMapVersion; }

    /**
     * Container for entries that are serialized.
     */
    public static class CRAMEncodingMapJSONEntry {
        final static int NO_COMPRESSION_ARG = -1;

        public final Integer contentID;
        public final DataSeries dataSeries;
        public final EncodingDescriptor encodingDescriptor;
        public final BlockCompressionMethod compressionMethod;
        public final int compressorSpecificArg; // RANS order or GZIP compression level, otherwise NO_COMPRESSION_ARG

        // TODO: contentID and dataSeries are redundant ?
        // Note: also, compressor will be unused if we ever use a DataSeries encoding that is not External
        public CRAMEncodingMapJSONEntry(final Integer contentID,
                                        final DataSeries dataSeries,
                                        final EncodingDescriptor encodingDescriptor,
                                        final ExternalCompressor compressor) {
            this.contentID = contentID;
            this.dataSeries = dataSeries;
            this.encodingDescriptor = encodingDescriptor;
            if (compressor != null) {
                this.compressionMethod = compressor.getMethod();
                if (this.compressionMethod == BlockCompressionMethod.RANS) {
                    this.compressorSpecificArg = ((RANSExternalCompressor) compressor).getOrder().ordinal();
                } else if (this.compressionMethod == BlockCompressionMethod.GZIP) {
                    this.compressorSpecificArg = ((GZIPExternalCompressor) compressor).getWriteCompressionLevel();
                } else {
                    this.compressorSpecificArg = NO_COMPRESSION_ARG;
                }
            } else {
                this.compressionMethod = null;
                this.compressorSpecificArg = NO_COMPRESSION_ARG;
            }
        }
    }

}

