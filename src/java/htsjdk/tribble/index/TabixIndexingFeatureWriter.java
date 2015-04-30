/*
 * The MIT License
 *
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.tribble.index;

import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.LocationAware;
import htsjdk.tribble.Feature;
import htsjdk.tribble.index.tabix.TabixIndexCreator;
import htsjdk.tribble.writers.LineEncoder;
import htsjdk.samtools.util.PositionalOutputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 *
 * @author shenkers
 * @param <T> the type of the feature to be written
 */
public class TabixIndexingFeatureWriter<T extends Feature> {

    // File to which features will be written
    File featureFile;
    // OutputStream for the feature file to be written
    OutputStream featureFileOutputStream;
    // holds a pointer to the current offset position in the file being written
    LocationAware filePosition;
    IndexCreator indexCreator;
    // function to encode features as lines of text
    LineEncoder<T> lineEncoder;

    /*
     * IndexingTabixWriter writer uses an internal Writer, based by the ByteArrayOutputStream lineBuffer,
     * to temp. buffer the header and per-site output before flushing the per line output
     * in one go to the super.getOutputStream.  This results in high-performance, proper encoding,
     * and allows us to avoid flushing explicitly the output stream getOutputStream, which
     * allows us to properly compress vcfs in gz format without breaking indexing on the fly
     * for uncompressed streams.
     */
    private static final int INITIAL_BUFFER_SIZE = 1024 * 16;
    private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream(INITIAL_BUFFER_SIZE);
    /* Wrapping in a {@link BufferedWriter} avoids frequent conversions with individual writes to OutputStreamWriter. */
    private Writer writer=null;

    // an indicator of how the output feature file will be compressed
    public enum Compression {
        BGZF, NONE
    }

    public TabixIndexingFeatureWriter(File featureFile, Compression compression, LineEncoder<T> lineEncoder, TabixIndexCreator indexCreator) throws FileNotFoundException {
        this.featureFile = featureFile;
        
        switch (compression) {
            case BGZF: {
                BlockCompressedOutputStream bcos = new BlockCompressedOutputStream(lineBuffer, null);
                filePosition = bcos;
                writer = new BufferedWriter(new OutputStreamWriter(bcos));
                break;
            }
            case NONE: {
                PositionalOutputStream pos = new PositionalOutputStream(lineBuffer);
                filePosition = pos;
                writer = new BufferedWriter(new OutputStreamWriter(pos));
                break;
            }
        }

        this.featureFileOutputStream = new BufferedOutputStream(new FileOutputStream(featureFile));
        this.indexCreator = indexCreator;
        this.lineEncoder = lineEncoder;
    }

    /*
     * Write a feature line to the output stream and updates the indexCreator
     * 
     * Features must be added in coordinate-sorted order
     *
     * @param s the string to write
     * @throws IOException
     */
    public void add(T feature) throws IOException {
        indexCreator.addFeature(feature, filePosition.getPosition());
        writer.write(lineEncoder.encode(feature));
        writer.write("\n");

        //Actually write the line buffer contents to the destination output 
        //stream.After calling this function the line buffer is reset so the 
        //contents of the buffer can be reused 
        writer.flush();
        featureFileOutputStream.write(lineBuffer.toByteArray());
        lineBuffer.reset();
    }
    
    /*
     * Closes the feature output stream and writes the index to disk at the 
     * conventional location
     */
    public Index close() throws IOException {
        featureFileOutputStream.close();

        final Index index = indexCreator.finalizeIndex(filePosition.getPosition());
        index.writeBasedOnFeatureFile(featureFile);
        return index;
    }

}
