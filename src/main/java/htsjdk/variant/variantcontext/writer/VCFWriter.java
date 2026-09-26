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
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * this class writes VCF files
 */
class VCFWriter extends IndexingVariantContextWriter {

    // Initialized when the header is written to the output stream
    private VCFEncoder vcfEncoder = null;

    // the VCF header we're storing
    protected VCFHeader mHeader = null;

    private final boolean allowMissingFieldsInHeader;

    // should we write genotypes or just sites?
    private final boolean doNotWriteGenotypes;

    // should we always output a complete format record, even if we could drop trailing fields?
    private final boolean writeFullFormatField;

    // Explicit output version set by the builder, or null for resolution from the header
    private final VCFHeaderVersion explicitVersion;

    // The resolved output version, set once the header is written
    private VCFHeaderVersion outputVersion;

    // is the header or body written to the output stream?
    private boolean outputHasBeenWritten;

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

    public VCFWriter(
            final Path location,
            final OutputStream output,
            final SAMSequenceDictionary refDict,
            final boolean enableOnTheFlyIndexing,
            final boolean doNotWriteGenotypes,
            final boolean allowMissingFieldsInHeader,
            final boolean writeFullFormatField,
            final VCFHeaderVersion explicitVersion) {
        super(writerName(location, output), location, output, refDict, enableOnTheFlyIndexing);
        this.doNotWriteGenotypes = doNotWriteGenotypes;
        this.allowMissingFieldsInHeader = allowMissingFieldsInHeader;
        this.writeFullFormatField = writeFullFormatField;
        this.explicitVersion = explicitVersion;
    }

    public VCFWriter(
            final Path location,
            final OutputStream output,
            final SAMSequenceDictionary refDict,
            final IndexCreator indexCreator,
            final boolean enableOnTheFlyIndexing,
            final boolean doNotWriteGenotypes,
            final boolean allowMissingFieldsInHeader,
            final boolean writeFullFormatField,
            final VCFHeaderVersion explicitVersion) {
        super(writerName(location, output), location, output, refDict, enableOnTheFlyIndexing, indexCreator);
        this.doNotWriteGenotypes = doNotWriteGenotypes;
        this.allowMissingFieldsInHeader = allowMissingFieldsInHeader;
        this.writeFullFormatField = writeFullFormatField;
        this.explicitVersion = explicitVersion;
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
        lineBuffer.writeTo(getOutputStream());
        lineBuffer.reset();
    }

    @Override
    public void writeHeader(final VCFHeader header) {

        // note we need to update the mHeader object after this call because they header
        // may have genotypes trimmed out of it, if doNotWriteGenotypes is true
        setHeader(header);
        try {
            writeHeader(this.mHeader, writer, makeVersionLine(outputVersion), getStreamName());
            writeAndResetBuffer();
            outputHasBeenWritten = true;
        } catch (IOException e) {
            throw new RuntimeIOException("Couldn't write file " + getStreamName(), e);
        }
    }

    /** The {@code ##fileformat} line that declares a version. */
    static String makeVersionLine(final VCFHeaderVersion version) {
        return VCFHeader.METADATA_INDICATOR + version.getFormatString() + "=" + version.getVersionString();
    }

    /**
     * The version a writer labels its output with: the one the caller asked for if any, else the header's with a
     * floor of 4.2, else 4.2.
     */
    static VCFHeaderVersion resolveOutputVersion(final VCFHeader header, final VCFHeaderVersion explicitVersion) {
        return explicitVersion != null ? explicitVersion : VCFEncoder.resolveVersion(header);
    }

    /**
     * Checks that the output version can express every line of the header, by each line's
     * {@link VCFHeaderLine#minimumVersion}.
     *
     * @throws IllegalStateException quoting each line the output version cannot express and the version that can
     */
    static void checkHeaderCompatibility(final VCFHeader header, final VCFHeaderVersion outputVersion) {
        final List<String> violations = new ArrayList<>();
        VCFHeaderVersion needed = outputVersion;
        for (final VCFHeaderLine line : header.getMetaDataInInputOrder()) {
            final VCFHeaderVersion required = line.minimumVersion();
            if (outputVersion.isOlderThan(required)) {
                violations.add(line + " requires " + required.getVersionString() + " or later");
                if (required.isAtLeastAsRecentAs(needed)) needed = required;
            }
        }
        if (!violations.isEmpty()) {
            throw new IllegalStateException("The header cannot be written as " + outputVersion.getVersionString()
                    + ": " + String.join("; ", violations) + "; call VariantContextWriterBuilder.setVCFVersion("
                    + needed.name() + ") or higher");
        }
    }

    public static VCFHeader writeHeader(
            VCFHeader header, final Writer writer, final String versionLine, final String streamNameForError) {

        try {
            // the file format field needs to be written first
            writer.write(versionLine + "\n");

            for (final VCFHeaderLine line : header.getMetaDataInSortedOrder()) {
                if (VCFHeaderVersion.isFormatString(line.getKey())) continue;

                writer.write(VCFHeader.METADATA_INDICATOR);
                writer.write(line.toString());
                writer.write("\n");
            }

            // write out the column line
            writer.write(VCFHeader.HEADER_INDICATOR);
            boolean isFirst = true;
            for (final VCFHeader.HEADER_FIELDS field : header.getHeaderFields()) {
                if (isFirst) isFirst = false; // don't write out a field separator
                else writer.write(VCFConstants.FIELD_SEPARATOR);
                writer.write(field.toString());
            }

            if (header.hasGenotypingData()) {
                writer.write(VCFConstants.FIELD_SEPARATOR);
                writer.write("FORMAT");
                for (final String sample : header.getGenotypeSamples()) {
                    writer.write(VCFConstants.FIELD_SEPARATOR);
                    writer.write(sample);
                }
            }

            writer.write("\n");
            writer.flush(); // necessary so that writing to an output stream will work
        } catch (IOException e) {
            throw new RuntimeIOException("IOException writing the VCF header to " + streamNameForError, e);
        }

        return header;
    }

    /**
     * attempt to close the VCF file; a second call does nothing
     */
    @Override
    public void close() {
        if (isClosed()) return;
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
            if (this.mHeader == null) {
                throw new IllegalStateException(
                        "Unable to write the VCF: header is missing, " + "try to call writeHeader or setHeader first.");
            }
            try {
                if (this.doNotWriteGenotypes) {
                    this.vcfEncoder.write(
                            this.writer,
                            new VariantContextBuilder(context).noGenotypes().make());
                } else {
                    this.vcfEncoder.write(this.writer, context);
                }
                write("\n");
                // only now, so that a refused record is not indexed; still before any of its bytes reach the output
                super.add(context);
            } catch (final RuntimeException e) {
                // A record can be refused part way through, by the encoder or by the indexer, and a caller may carry
                // on with the next one: what was encoded of this one must not be left in the buffer to lead it.
                writer.flush();
                lineBuffer.reset();
                throw e;
            }
            writeAndResetBuffer();
            outputHasBeenWritten = true;
        } catch (IOException e) {
            throw new RuntimeIOException("Unable to write the VCF object to " + getStreamName(), e);
        }
    }

    @Override
    public void setHeader(final VCFHeader header) {
        if (outputHasBeenWritten) {
            throw new IllegalStateException(
                    "The header cannot be modified after the header or variants have been written to the output stream.");
        }
        // The writer works on its own copy, labelled with the output version, so that the encoder's header and the
        // written ##fileformat line agree while the caller's header keeps whatever version it declares.
        this.outputVersion = resolveOutputVersion(header, this.explicitVersion);
        this.mHeader = doNotWriteGenotypes ? new VCFHeader(header.getMetaDataInSortedOrder()) : new VCFHeader(header);
        this.mHeader.setVCFHeaderVersion(this.outputVersion);
        checkHeaderCompatibility(this.mHeader, this.outputVersion);
        this.vcfEncoder = new VCFEncoder(
                this.mHeader, this.allowMissingFieldsInHeader, this.writeFullFormatField, this.outputVersion);
    }
}
