/*
* Copyright (c) 2012 The Broad Institute
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

package htsjdk.variant.variantcontext.writer;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.tribble.index.IndexCreator;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.vcf.VCFConstants;
import htsjdk.variant.vcf.VCFEncoder;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFHeaderVersion;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * this class writes VCF files
 */
class VCFWriter extends IndexingVariantContextWriter {

    private static final String VERSION_LINE =
            VCFHeader.METADATA_INDICATOR + VCFHeaderVersion.VCF4_2.getFormatString() + "=" + VCFHeaderVersion.VCF4_2.getVersionString();

	// Initialized when the header is written to the output stream
	private VCFEncoder vcfEncoder = null;

	// the VCF header we're storing
	protected VCFHeader mHeader = null;

	private final boolean allowMissingFieldsInHeader;

	// should we write genotypes or just sites?
	private final boolean doNotWriteGenotypes;

    // should we always output a complete format record, even if we could drop trailing fields?
    private final boolean writeFullFormatField;

    /*
     * The VCF writer uses an internal Writer, based by the ByteArrayOutputStream lineBuffer,
     * to temp. buffer the header and per-site output before flushing the per line output
     * in one go to the super.getOutputStream.  This results in high-performance, proper encoding,
     * and allows us to avoid flushing explicitly the output stream getOutputStream, which
     * allows us to properly compress vcfs in gz format without breaking indexing on the fly
     * for uncompressed streams.
     */
    private static final int INITIAL_BUFFER_SIZE = 1024 * 16;
    private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream(INITIAL_BUFFER_SIZE);
    /* Wrapping in a {@link BufferedWriter} avoids frequent conversions with individual writes to OutputStreamWriter. */
    private final Writer writer = new BufferedWriter(new OutputStreamWriter(lineBuffer, VCFEncoder.VCF_CHARSET));

    public VCFWriter(final File location, final OutputStream output, final SAMSequenceDictionary refDict,
                     final boolean enableOnTheFlyIndexing,
                     final boolean doNotWriteGenotypes, final boolean allowMissingFieldsInHeader,
                     final boolean writeFullFormatField) {
        super(writerName(location, output), location, output, refDict, enableOnTheFlyIndexing);
        this.doNotWriteGenotypes = doNotWriteGenotypes;
        this.allowMissingFieldsInHeader = allowMissingFieldsInHeader;
        this.writeFullFormatField = writeFullFormatField;
    }

    public VCFWriter(final File location, final OutputStream output, final SAMSequenceDictionary refDict,
                     final IndexCreator indexCreator, final boolean enableOnTheFlyIndexing,
                     final boolean doNotWriteGenotypes, final boolean allowMissingFieldsInHeader,
                     final boolean writeFullFormatField) {
        super(writerName(location, output), location, output, refDict, enableOnTheFlyIndexing, indexCreator);
        this.doNotWriteGenotypes = doNotWriteGenotypes;
        this.allowMissingFieldsInHeader = allowMissingFieldsInHeader;
        this.writeFullFormatField = writeFullFormatField;
    }
    // --------------------------------------------------------------------------------
    //
    // VCFWriter interface functions
    //
    // --------------------------------------------------------------------------------

    /*
     * Write String s to the internal buffered writer.
     *
     * writeAndResetBuffer() must be called to actually write the data to the true output stream.
     *
     * @param s the string to write
     * @throws IOException
     */
    private void write(final String s) throws IOException {
        writer.write(s);
    }

    /*
     * Actually write the line buffer contents to the destination output stream. After calling this function
     * the line buffer is reset so the contents of the buffer can be reused
     */
    private void writeAndResetBuffer() throws IOException {
        writer.flush();
        getOutputStream().write(lineBuffer.toByteArray());
        lineBuffer.reset();
    }

    @Override
    public void writeHeader(final VCFHeader header) {
        // note we need to update the mHeader object after this call because they header
        // may have genotypes trimmed out of it, if doNotWriteGenotypes is true
        try {
            this.mHeader = writeHeader(header, writer, doNotWriteGenotypes, getVersionLine(), getStreamName());
            this.vcfEncoder = new VCFEncoder(this.mHeader, this.allowMissingFieldsInHeader, this.writeFullFormatField);
            writeAndResetBuffer();

        } catch ( IOException e ) {
            throw new RuntimeIOException("Couldn't write file " + getStreamName(), e);
        }
    }

    public static String getVersionLine() {
        return VERSION_LINE;
    }

    public static VCFHeader writeHeader(VCFHeader header,
                                        final Writer writer,
                                        final boolean doNotWriteGenotypes,
                                        final String versionLine,
                                        final String streamNameForError) {
        header = doNotWriteGenotypes ? new VCFHeader(header.getMetaDataInSortedOrder()) : header;
        
        try {
            // the file format field needs to be written first
            writer.write(versionLine + "\n");

            for (final VCFHeaderLine line : header.getMetaDataInSortedOrder() ) {
                if ( VCFHeaderVersion.isFormatString(line.getKey()) )
                    continue;

                writer.write(VCFHeader.METADATA_INDICATOR);
                writer.write(line.toString());
                writer.write("\n");
            }

            // write out the column line
            writer.write(VCFHeader.HEADER_INDICATOR);
            boolean isFirst = true;
            for (final VCFHeader.HEADER_FIELDS field : header.getHeaderFields() ) {
                if ( isFirst )
                    isFirst = false; // don't write out a field separator
                else
                    writer.write(VCFConstants.FIELD_SEPARATOR);
                writer.write(field.toString());
            }

            if ( header.hasGenotypingData() ) {
                writer.write(VCFConstants.FIELD_SEPARATOR);
                writer.write("FORMAT");
                for (final String sample : header.getGenotypeSamples() ) {
                    writer.write(VCFConstants.FIELD_SEPARATOR);
                    writer.write(sample);
                }
            }

            writer.write("\n");
            writer.flush();  // necessary so that writing to an output stream will work
        }
        catch (IOException e) {
            throw new RuntimeIOException("IOException writing the VCF header to " + streamNameForError, e);
        }

        return header;
    }

    /**
     * attempt to close the VCF file
     */
    @Override
    public void close() {
        // try to close the vcf stream
        try {
            // TODO -- would it be useful to null out the line buffer so we don't have it around unnecessarily?
            writer.close();
        } catch (IOException e) {
            throw new RuntimeIOException("Unable to close " + getStreamName(), e);
        }

        super.close();
    }

    /**
     * Add a record to the file
     */
    @Override
    public void add(final VariantContext context) {
        try {
            super.add(context);

            if (this.doNotWriteGenotypes) write(this.vcfEncoder.encode(new VariantContextBuilder(context).noGenotypes().make()));
            else write(this.vcfEncoder.encode(context));
            write("\n");

            writeAndResetBuffer();

        } catch (IOException e) {
            throw new RuntimeIOException("Unable to write the VCF object to " + getStreamName(), e);
        }
    }
}
