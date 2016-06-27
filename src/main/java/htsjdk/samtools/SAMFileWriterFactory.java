/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
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
package htsjdk.samtools;

import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.Md5CalculatingOutputStream;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.samtools.util.zip.DeflaterFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;

/**
 * Create a writer for writing SAM, BAM, or CRAM files.
 */
public class SAMFileWriterFactory implements Cloneable {
    private final static Log log = Log.getInstance(SAMFileWriterFactory.class);
    private static boolean defaultCreateIndexWhileWriting = Defaults.CREATE_INDEX;
    private boolean createIndex = defaultCreateIndexWhileWriting;
    private static boolean defaultCreateMd5File = Defaults.CREATE_MD5;
    private boolean createMd5File = defaultCreateMd5File;
    private boolean useAsyncIo = Defaults.USE_ASYNC_IO_WRITE_FOR_SAMTOOLS;
    private int asyncOutputBufferSize = AsyncSAMFileWriter.DEFAULT_QUEUE_SIZE;
    private int bufferSize = Defaults.BUFFER_SIZE;
    private File tmpDir;
    /** compression level 0: min 9:max */
    private int compressionLevel = BlockCompressedOutputStream.getDefaultCompressionLevel();
    private SamFlagField samFlagFieldOutput = SamFlagField.NONE;
    private Integer maxRecordsInRam = null;
    private DeflaterFactory deflaterFactory = BlockCompressedOutputStream.getDefaultDeflaterFactory();

    /** simple constructor */
    public SAMFileWriterFactory() {
    }
    
    /** copy constructor */
    public SAMFileWriterFactory( final SAMFileWriterFactory other) {
        if( other == null ) throw new IllegalArgumentException("SAMFileWriterFactory(null)");
        this.createIndex = other.createIndex;
        this.createMd5File = other.createMd5File;
        this.useAsyncIo = other.useAsyncIo;
        this.asyncOutputBufferSize = other.asyncOutputBufferSize;
        this.bufferSize = other.bufferSize;
        this.tmpDir = other.tmpDir;
        this.compressionLevel = other.compressionLevel;
        this.maxRecordsInRam = other.maxRecordsInRam;
    }
    
    @Override
    public SAMFileWriterFactory clone() {
        return new SAMFileWriterFactory(this);
    }

    /**
     * Sets the default for whether to create md5Files for BAM files this factory.
     */
    public static void setDefaultCreateMd5File(final boolean createMd5File) {
        defaultCreateMd5File = createMd5File;
    }

    /**
     * Sets whether to create md5Files for BAMs from this factory.
     */
    public SAMFileWriterFactory setCreateMd5File(final boolean createMd5File) {
        this.createMd5File = createMd5File;
        return this;
    }

    /**
     * Set the deflater factory used by BAM writers created by this writer factory. Must not be null.
     * If this method is not called, the default  {@link DeflaterFactory} is used which creates the default JDK {@link Deflater}.
     * This method returns the SAMFileWriterFactory itself. */
    public SAMFileWriterFactory setDeflaterFactory(final DeflaterFactory deflaterFactory){
        if (deflaterFactory == null){
            throw new IllegalArgumentException("null deflater factory");
        }
        this.deflaterFactory = deflaterFactory;
        return this;
    }

    /** set compression level 0!none 9: max */
    public SAMFileWriterFactory setCompressionLevel(final int compressionLevel) {
        this.compressionLevel = Math.min(9, Math.max(0, compressionLevel));
        return this;
    }
    
    public int getCompressionLevel() {
        return compressionLevel;
    }
    
    /**
     * Sets the default for subsequent SAMFileWriterFactories
     * that do not specify whether to create an index.
     * If a BAM (not SAM) file is created, the setting is true, and the file header specifies coordinate order,
     * then a BAM index file will be written along with the BAM file.
     *
     * @param setting whether to attempt to create a BAM index while creating the BAM file
     */
    public static void setDefaultCreateIndexWhileWriting(final boolean setting) {
        defaultCreateIndexWhileWriting = setting;
    }

    /**
     * Convenience method allowing newSAMFileWriterFactory().setCreateIndex(true);
     * Equivalent to SAMFileWriterFactory.setDefaultCreateIndexWhileWriting(true); newSAMFileWriterFactory();
     * If a BAM or CRAM (not SAM) file is created, the setting is true, and the file header specifies coordinate order,
     * then a BAM index file will be written along with the BAM file.
     *
     * @param setting whether to attempt to create a BAM index while creating the BAM file.
     * @return this factory object
     */
    public SAMFileWriterFactory setCreateIndex(final boolean setting) {
        this.createIndex = setting;
        return this;
    }

    /**
     * Before creating a writer that is not presorted, this method may be called in order to override
     * the default number of SAMRecords stored in RAM before spilling to disk
     * (c.f. SAMFileWriterImpl.MAX_RECORDS_IN_RAM).  When writing very large sorted SAM files, you may need
     * call this method in order to avoid running out of file handles.  The RAM available to the JVM may need
     * to be increased in order to hold the specified number of records in RAM.  This value affects the number
     * of records stored in subsequent calls to one of the make...() methods.
     *
     * @param maxRecordsInRam Number of records to store in RAM before spilling to temporary file when
     *                        creating a sorted SAM or BAM file.
     */
    public SAMFileWriterFactory setMaxRecordsInRam(final int maxRecordsInRam) {
        this.maxRecordsInRam = maxRecordsInRam;
        return this;
    }

    /**
     * Turn on or off the use of asynchronous IO for writing output SAM and BAM files.  If true then
     * each SAMFileWriter creates a dedicated thread which is used for compression and IO activities.
     */
    public SAMFileWriterFactory setUseAsyncIo(final boolean useAsyncIo) {
        this.useAsyncIo = useAsyncIo;
        return this;
    }

    /**
     * If and only if using asynchronous IO then sets the maximum number of records that can be buffered per
     * SAMFileWriter before producers will block when trying to write another SAMRecord.
     */
    public SAMFileWriterFactory setAsyncOutputBufferSize(final int asyncOutputBufferSize) {
        this.asyncOutputBufferSize = asyncOutputBufferSize;
        return this;
    }

    /**
     * Controls size of write buffer.
     * Default value: [[htsjdk.samtools.Defaults#BUFFER_SIZE]]
     */
    public SAMFileWriterFactory setBufferSize(final int bufferSize) {
        this.bufferSize = bufferSize;
        return this;
    }

    /**
     * Set the temporary directory to use when sort data.
     *
     * @param tmpDir Path to the temporary directory
     */
    public SAMFileWriterFactory setTempDirectory(final File tmpDir) {
        this.tmpDir = tmpDir;
        return this;
    }

    /**
     * Set the flag output format only when writing text.
     * Default value: [[htsjdk.samtools.SAMTextWriter.samFlagFieldOutput.DECIMAL]]
     */
    public SAMFileWriterFactory setSamFlagFieldOutput(final SamFlagField samFlagFieldOutput) {
        if (samFlagFieldOutput == null) throw new IllegalArgumentException("Sam flag field was null");
        this.samFlagFieldOutput = samFlagFieldOutput;
        return this;
    }

    /**
     * Create a BAMFileWriter that is ready to receive SAMRecords.  Uses default compression level.
     *
     * @param header     entire header. Sort order is determined by the sortOrder property of this arg.
     * @param presorted  if true, SAMRecords must be added to the SAMFileWriter in order that agrees with header.sortOrder.
     * @param outputFile where to write the output.
     */
    public SAMFileWriter makeBAMWriter(final SAMFileHeader header, final boolean presorted, final File outputFile) {
        return makeBAMWriter(header, presorted, outputFile, this.getCompressionLevel());
    }

    /**
     * Create a BAMFileWriter that is ready to receive SAMRecords.
     *
     * @param header           entire header. Sort order is determined by the sortOrder property of this arg.
     * @param presorted        if true, SAMRecords must be added to the SAMFileWriter in order that agrees with header.sortOrder.
     * @param outputFile       where to write the output.
     * @param compressionLevel Override default compression level with the given value, between 0 (fastest) and 9 (smallest).
     */
    public SAMFileWriter makeBAMWriter(final SAMFileHeader header, final boolean presorted, final File outputFile,
                                       final int compressionLevel) {
        try {
            final boolean createMd5File = this.createMd5File && IOUtil.isRegularPath(outputFile);
            if (this.createMd5File && !createMd5File) {
                log.warn("Cannot create MD5 file for BAM because output file is not a regular file: " + outputFile.getAbsolutePath());
            }
            OutputStream os = IOUtil.maybeBufferOutputStream(new FileOutputStream(outputFile, false), bufferSize);
            if (createMd5File) os = new Md5CalculatingOutputStream(os, new File(outputFile.getAbsolutePath() + ".md5"));
            final BAMFileWriter ret = new BAMFileWriter(os, outputFile, compressionLevel, deflaterFactory);
            final boolean createIndex = this.createIndex && IOUtil.isRegularPath(outputFile);
            if (this.createIndex && !createIndex) {
                log.warn("Cannot create index for BAM because output file is not a regular file: " + outputFile.getAbsolutePath());
            }
            if (this.tmpDir != null) ret.setTempDirectory(this.tmpDir);
            initializeBAMWriter(ret, header, presorted, createIndex);

            if (this.useAsyncIo) return new AsyncSAMFileWriter(ret, this.asyncOutputBufferSize);
            else return ret;
        } catch (final IOException ioe) {
            throw new RuntimeIOException("Error opening file: " + outputFile.getAbsolutePath());
        }
    }

    private void initializeBAMWriter(final BAMFileWriter writer, final SAMFileHeader header, final boolean presorted, final boolean createIndex) {
        writer.setSortOrder(header.getSortOrder(), presorted);
        if (maxRecordsInRam != null) {
            writer.setMaxRecordsInRam(maxRecordsInRam);
        }
        writer.setHeader(header);
        if (createIndex && writer.getSortOrder().equals(SAMFileHeader.SortOrder.coordinate)) {
            writer.enableBamIndexConstruction();
        }
    }

    /**
     * Create a SAMTextWriter that is ready to receive SAMRecords.
     *
     * @param header     entire header. Sort order is determined by the sortOrder property of this arg.
     * @param presorted  if true, SAMRecords must be added to the SAMFileWriter in order that agrees with header.sortOrder.
     * @param outputFile where to write the output.
     */
    public SAMFileWriter makeSAMWriter(final SAMFileHeader header, final boolean presorted, final File outputFile) {
        /**
         * Use the value specified from Defaults.SAM_FLAG_FIELD_FORMAT when samFlagFieldOutput value has not been set.  This should
         * be SamFlagField.DECIMAL when the user has not set Defaults.SAM_FLAG_FIELD_FORMAT.
         */
        if (samFlagFieldOutput == SamFlagField.NONE) {
            samFlagFieldOutput = Defaults.SAM_FLAG_FIELD_FORMAT;
        }
        try {
            final SAMTextWriter ret = this.createMd5File
                    ? new SAMTextWriter(new Md5CalculatingOutputStream(new FileOutputStream(outputFile, false),
                    new File(outputFile.getAbsolutePath() + ".md5")), samFlagFieldOutput)
                    : new SAMTextWriter(outputFile, samFlagFieldOutput);
            ret.setSortOrder(header.getSortOrder(), presorted);
            if (maxRecordsInRam != null) {
                ret.setMaxRecordsInRam(maxRecordsInRam);
            }
            ret.setHeader(header);

            if (this.useAsyncIo) return new AsyncSAMFileWriter(ret, this.asyncOutputBufferSize);
            else return ret;
        } catch (final IOException ioe) {
            throw new RuntimeIOException("Error opening file: " + outputFile.getAbsolutePath());
        }
    }

    /**
     * Create a SAMTextWriter for writing to a stream that is ready to receive SAMRecords.
     * This method does not support the creation of an MD5 file
     *
     * @param header    entire header. Sort order is determined by the sortOrder property of this arg.
     * @param presorted if true, SAMRecords must be added to the SAMFileWriter in order that agrees with header.sortOrder.
     * @param stream    the stream to write records to.  Note that this method does not buffer the stream, so the
     *                  caller must buffer if desired.  Note that PrintStream is buffered.
     */
    public SAMFileWriter makeSAMWriter(final SAMFileHeader header, final boolean presorted, final OutputStream stream) {
        /**
         * Use the value specified from Defaults.SAM_FLAG_FIELD_FORMAT when samFlagFieldOutput value has not been set.  This should
         * be samFlagFieldOutput.DECIMAL when the user has not set Defaults.SAM_FLAG_FIELD_FORMAT.
         */
        if (samFlagFieldOutput == SamFlagField.NONE) {
            samFlagFieldOutput = Defaults.SAM_FLAG_FIELD_FORMAT;
        }
        return initWriter(header, presorted, false, new SAMTextWriter(stream, samFlagFieldOutput));
    }

    /**
     * Create a BAMFileWriter for writing to a stream that is ready to receive SAMRecords.
     * This method does not support the creation of an MD5 file
     *
     * @param header    entire header. Sort order is determined by the sortOrder property of this arg.
     * @param presorted if true, SAMRecords must be added to the SAMFileWriter in order that agrees with header.sortOrder.
     * @param stream    the stream to write records to.  Note that this method does not buffer the stream, so the
     *                  caller must buffer if desired.  Note that PrintStream is buffered.
     */

    public SAMFileWriter makeBAMWriter(final SAMFileHeader header, final boolean presorted, final OutputStream stream) {
        return initWriter(header, presorted, true, new BAMFileWriter(stream, null, this.getCompressionLevel(), this.deflaterFactory));
    }

    /**
     * Initialize SAMTextWriter or a BAMFileWriter and possibly wrap in AsyncSAMFileWriter
     *
     * @param header    entire header. Sort order is determined by the sortOrder property of this arg.
     * @param presorted if true, SAMRecords must be added to the SAMFileWriter in order that agrees with header.sortOrder.
     * @param binary    do we want to generate a BAM or a SAM
     * @param writer    SAM or BAM writer to initialize and maybe wrap.
     */

    private SAMFileWriter initWriter(final SAMFileHeader header, final boolean presorted, final boolean binary,
                                     final SAMFileWriterImpl writer) {
        writer.setSortOrder(header.getSortOrder(), presorted);
        if (maxRecordsInRam != null) {
            writer.setMaxRecordsInRam(maxRecordsInRam);
        }
        writer.setHeader(header);

        if (this.useAsyncIo) return new AsyncSAMFileWriter(writer, this.asyncOutputBufferSize);
        else return writer;
    }

    /**
     * Create either a SAM or a BAM writer based on examination of the outputFile extension.
     *
     * @param header     entire header. Sort order is determined by the sortOrder property of this arg.
     * @param presorted  presorted if true, SAMRecords must be added to the SAMFileWriter in order that agrees with header.sortOrder.
     * @param outputFile where to write the output.  Must end with .sam or .bam.
     * @return SAM or BAM writer based on file extension of outputFile.
     */
    public SAMFileWriter makeSAMOrBAMWriter(final SAMFileHeader header, final boolean presorted, final File outputFile) {
        final String filename = outputFile.getName();
        if (filename.endsWith(BamFileIoUtils.BAM_FILE_EXTENSION)) {
            return makeBAMWriter(header, presorted, outputFile);
        }
        if (filename.endsWith(".sam")) {
            return makeSAMWriter(header, presorted, outputFile);
        }
        return makeBAMWriter(header, presorted, outputFile);
    }

    /**
     *
     * Create a SAM, BAM or CRAM writer based on examination of the outputFile extension.
     *
     * @param header header. Sort order is determined by the sortOrder property of this arg.
     * @param presorted if true, SAMRecords must be added to the SAMFileWriter in order that agrees with header.sortOrder.
     * @param outputFile where to write the output.  Must end with .sam, .bam or .cram.
     * @param referenceFasta reference sequence file
     * @return SAMFileWriter appropriate for the file type specified in outputFile
     *
     */
    public SAMFileWriter makeWriter(final SAMFileHeader header, final boolean presorted, final File outputFile, final File referenceFasta) {
        if (outputFile.getName().endsWith(SamReader.Type.CRAM_TYPE.fileExtension())) {
            return makeCRAMWriter(header, presorted, outputFile, referenceFasta);
        }
        else {
            return makeSAMOrBAMWriter(header, presorted, outputFile);
        }
    }

    /**
     * Create a CRAMFileWriter on an output stream. Requires the input to be presorted to match the sort order defined
     * by the input header.
     *
     * Note: does not honor factory settings for CREATE_MD5, CREATE_INDEX, USE_ASYNC_IO.
     *
     * @param header entire header. Sort order is determined by the sortOrder property of this arg.
     * @param stream where to write the output.
     * @param referenceFasta reference sequence file
     * @return CRAMFileWriter
     */
    public CRAMFileWriter makeCRAMWriter(final SAMFileHeader header, final OutputStream stream, final File referenceFasta) {
        // create the CRAMFileWriter directly without propagating factory settings
        final CRAMFileWriter writer = new CRAMFileWriter(stream, new ReferenceSource(referenceFasta), header, null);
        setCRAMWriterDefaults(writer);
        return writer;
    }

    /**
     * Create a CRAMFileWriter on an output file. Requires input record to be presorted to match the
     * sort order defined by the input header.
     *
     * Note: does not honor factory settings for USE_ASYNC_IO.
     *
     * @param header entire header. Sort order is determined by the sortOrder property of this arg.
     * @param outputFile where to write the output.  Must end with .sam, .bam or .cram.
     * @param referenceFasta reference sequence file
     * @return CRAMFileWriter
     *
     */
    public CRAMFileWriter makeCRAMWriter(final SAMFileHeader header, final File outputFile, final File referenceFasta) {
        return createCRAMWriterWithSettings(header, true, outputFile, referenceFasta);
    }

    /**
     * Create a CRAMFileWriter on an output file.
     *
     * Note: does not honor factory setting for USE_ASYNC_IO.
     *
     * @param header entire header. Sort order is determined by the sortOrder property of this arg.
     * @param presorted  if true, SAMRecords must be added to the SAMFileWriter in order that agrees with header.sortOrder.
     * @param outputFile where to write the output.  Must end with .sam, .bam or .cram.
     * @param referenceFasta reference sequence file
     * @return CRAMFileWriter
     *
     */
    public CRAMFileWriter makeCRAMWriter(final SAMFileHeader header, final boolean presorted, final File outputFile, final File referenceFasta) {
        return createCRAMWriterWithSettings(header, presorted, outputFile, referenceFasta);
    }

    /**
     * Create a CRAMFileWriter on an output file based on factory settings.
     *
     * Note: does not honor the factory setting for USE_ASYNC_IO.
     *
     * @param header entire header. Sort order is determined by the sortOrder property of this arg.
     * @param presorted  if true, SAMRecords must be added to the SAMFileWriter in order that agrees with header.sortOrder.
     * @param outputFile where to write the output.  Must end with .sam, .bam or .cram.
     * @param referenceFasta reference sequence file
     * @return CRAMFileWriter
     */
    private CRAMFileWriter createCRAMWriterWithSettings(
            final SAMFileHeader header,
            final boolean presorted,
            final File outputFile,
            final File referenceFasta) {
        OutputStream cramOS = null;
        OutputStream indexOS = null ;

        if (createIndex) {
            if (!IOUtil.isRegularPath(outputFile)) {
                log.warn("Cannot create index for CRAM because output file is not a regular file: " + outputFile.getAbsolutePath());
            }
            else {
                try {
                    final File indexFile = new File(outputFile.getAbsolutePath() + BAMIndex.BAMIndexSuffix) ;
                    indexOS = new FileOutputStream(indexFile) ;
                }
                catch (final IOException ioe) {
                    throw new RuntimeIOException("Error creating index file for: " + outputFile.getAbsolutePath()+ BAMIndex.BAMIndexSuffix);
                }
            }
        }

        try {
            cramOS = IOUtil.maybeBufferOutputStream(new FileOutputStream(outputFile, false), bufferSize);
        }
        catch (final IOException ioe) {
            throw new RuntimeIOException("Error creating CRAM file: " + outputFile.getAbsolutePath());
        }

        final CRAMFileWriter writer = new CRAMFileWriter(
                createMd5File ? new Md5CalculatingOutputStream(cramOS, new File(outputFile.getAbsolutePath() + ".md5")) : cramOS,
                indexOS,
                presorted,
                new ReferenceSource(referenceFasta),
                header,
                outputFile.getAbsolutePath());
        setCRAMWriterDefaults(writer);

        return writer;
    }

    // Set the default CRAM writer preservation parameters
    private void setCRAMWriterDefaults(final CRAMFileWriter writer) {
        writer.setPreserveReadNames(true);
        writer.setCaptureAllTags(true);
    }

    @Override
    public String toString() {
        return "SAMFileWriterFactory [createIndex=" + createIndex + ", createMd5File=" + createMd5File + ", useAsyncIo="
                + useAsyncIo + ", asyncOutputBufferSize=" + asyncOutputBufferSize + ", bufferSize=" + bufferSize
                + ", tmpDir=" + tmpDir + ", compressionLevel=" + compressionLevel + ", maxRecordsInRam="
                + maxRecordsInRam + "]";
    }

}
