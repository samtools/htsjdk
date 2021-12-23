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

import htsjdk.samtools.Defaults;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
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
import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * this class writes VCF files
 */
class VCFWriter extends IndexingVariantContextWriter {
    protected final static Log logger = Log.getInstance(VCFWriter.class);

	// Initialized when the header is written to the output stream
	private VCFEncoder vcfEncoder = null;

	// the VCF header we're storing
	protected VCFHeader mHeader = null;

	private final boolean allowMissingFieldsInHeader;

	// should we write genotypes or just sites?
	private final boolean doNotWriteGenotypes;

    // should we always output a complete format record, even if we could drop trailing fields?
    private final boolean writeFullFormatField;

    // is the header or body written to the output stream?
    private boolean outputHasBeenWritten;

    private VCFVersionUpgradePolicy policy = Defaults.VCF_VERSION_TRANSITION_POLICY;

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
        this(IOUtil.toPath(location), output, refDict, enableOnTheFlyIndexing, doNotWriteGenotypes,
            allowMissingFieldsInHeader,writeFullFormatField);
    }

    public VCFWriter(final Path location, final OutputStream output, final SAMSequenceDictionary refDict,
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
        this(IOUtil.toPath(location), output, refDict, indexCreator, enableOnTheFlyIndexing,
            doNotWriteGenotypes, allowMissingFieldsInHeader, writeFullFormatField);
    }

    public VCFWriter(final Path location, final OutputStream output, final SAMSequenceDictionary refDict,
        final IndexCreator indexCreator, final boolean enableOnTheFlyIndexing,
        final boolean doNotWriteGenotypes, final boolean allowMissingFieldsInHeader,
        final boolean writeFullFormatField) {
        super(writerName(location, output), location, output, refDict, enableOnTheFlyIndexing, indexCreator);
        this.doNotWriteGenotypes = doNotWriteGenotypes;
        this.allowMissingFieldsInHeader = allowMissingFieldsInHeader;
        this.writeFullFormatField = writeFullFormatField;
    }

    /**
     * See {@link VCFVersionUpgradePolicy}. Controls how the writer will handle headers and variant contexts
     * from versions of VCF before the current version. The writer will call {@link VCFHeader#upgradeVersion} on headers
     * passed in by {@link VCFWriter#writeHeader} and {@link VCFWriter#setHeader} before writing the header out.
     * This has no effect on the header written out by {@link BCF2Writer}, which writes its VCF header using the static
     * method {@link VCFWriter#writeHeader(VCFHeader, Writer, String)}, which writes the header verbatim.
     * @param policy the policy to use
     */
    public void setVersionUpgradePolicy(final VCFVersionUpgradePolicy policy) {
        this.policy = policy;
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
        // note we need to update the mHeader object after this call because the header
        // may have genotypes trimmed out of it, if doNotWriteGenotypes is true
        setHeader(header);
        try {
            writeHeader(this.mHeader, writer, getStreamName());
            writeAndResetBuffer();
            outputHasBeenWritten = true;
        } catch ( IOException e ) {
            throw new RuntimeIOException("Couldn't write file " + getStreamName(), e);
        }
    }

    @Deprecated // starting after version 2.24.1
    public static VCFHeader writeHeader(final VCFHeader header,
                                        final Writer writer,
                                        final String versionLine,
                                        final String streamNameForError) {
        // Determine requested version from versionLine
        final VCFHeaderVersion requestedVersion = VCFHeaderVersion.fromHeaderVersionLine(versionLine);
        final VCFHeaderLine requestedVersionLine = VCFHeader.makeHeaderVersionLine(requestedVersion);
        // Set version inside header and validate lines
        header.addMetaDataLine(requestedVersionLine);
        return writeHeader(header, writer, streamNameForError);
    }

    public static VCFHeader writeHeader(final VCFHeader header,
                                        final Writer writer,
                                        final String streamNameForError) {
        try {
            // The file format field needs to be written first; below any file format lines
            // embedded in the header will be removed
            writer.write(header.getVCFHeaderVersion().toHeaderVersionLine() + "\n");

            for (final VCFHeaderLine line : header.getMetaDataInSortedOrder() ) {
                // Remove the fileformat header lines
                if ( VCFHeaderVersion.isFormatString(line.getKey()) ) {
                    continue;
                }

                writer.write(VCFHeader.METADATA_INDICATOR);
                writer.write(line.toString());
                writer.write("\n");
            }

            // write out the column line
            writer.write(VCFHeader.HEADER_INDICATOR);
            writer.write(header.getHeaderFields().stream()
                .map(Enum::name)
                .collect(Collectors.joining(VCFConstants.FIELD_SEPARATOR)));

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
            // If this context came from a version of VCF different from that of the header which we wrote,
            // we need to make sure it is encoded in a way which is compatible with the version of the header.
            final VariantContext versionCompatibleContext = this.mHeader == null || context.getVersion() == this.mHeader.getVCFHeaderVersion()
                ? context
                : context.makeCompatibleWithHeaderVersion(this.mHeader);
            super.add(versionCompatibleContext);
            if (this.mHeader == null) {
                throw new IllegalStateException("Unable to write the VCF: header is missing, " +
                                                   "try to call writeHeader or setHeader first.");
            }
            if (this.doNotWriteGenotypes) {
                this.vcfEncoder.write(this.writer, new VariantContextBuilder(versionCompatibleContext).noGenotypes().make());
            } else {
                this.vcfEncoder.write(this.writer, versionCompatibleContext);
            }
            write("\n");

            writeAndResetBuffer();
            outputHasBeenWritten = true;
        } catch (IOException e) {
            throw new RuntimeIOException("Unable to write the VCF object to " + getStreamName(), e);
        }
    }

    @Override
    public void setHeader(final VCFHeader header) {
        if (outputHasBeenWritten) {
            throw new IllegalStateException("The header cannot be modified after the header or variants have been written to the output stream.");
        }
        final VCFHeader upgradedHeader = header.upgradeVersion(this.policy);
        this.mHeader = doNotWriteGenotypes ? new VCFHeader(upgradedHeader.getMetaDataInSortedOrder()) : upgradedHeader;
        this.vcfEncoder = new VCFEncoder(this.mHeader, this.allowMissingFieldsInHeader, this.writeFullFormatField);
    }
}
