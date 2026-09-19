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

import static htsjdk.samtools.SamReader.Type.*;

import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.CRAMEncodingStrategy;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.Md5CalculatingOutputStream;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.samtools.util.zip.DeflaterFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.Deflater;

/**
 * Create a writer for writing SAM, BAM, or CRAM files.
 */
public class SAMFileWriterFactory implements Cloneable {
    private static final Log log = Log.getInstance(SAMFileWriterFactory.class);
    private static boolean defaultCreateIndexWhileWriting = Defaults.CREATE_INDEX;
    private boolean createIndex = defaultCreateIndexWhileWriting;
    private static boolean defaultCreateMd5File = Defaults.CREATE_MD5;
    private boolean createMd5File = defaultCreateMd5File;
    private boolean useAsyncIo = Defaults.USE_ASYNC_IO_WRITE_FOR_SAMTOOLS;
    private int asyncOutputBufferSize = AsyncSAMFileWriter.DEFAULT_QUEUE_SIZE;
    private int bufferSize = Defaults.BUFFER_SIZE;
    private Path tmpDir;
    /** compression level 0: min 9:max */
    private int compressionLevel = BlockCompressedOutputStream.getDefaultCompressionLevel();

    private SamFlagField samFlagFieldOutput = SamFlagField.NONE;
    private Integer maxRecordsInRam = null;
    private DeflaterFactory deflaterFactory = BlockCompressedOutputStream.getDefaultDeflaterFactory();
    private CRAMEncodingStrategy cramEncodingStrategy = new CRAMEncodingStrategy();
    private boolean createBaiIndexForCram = false;
    private BamIndexType bamIndexType = BamIndexType.BAI;
    private BamIndexType samIndexType = BamIndexType.CSI;
    private int csiMinShift = BAMIndexer.DEFAULT_CSI_MIN_SHIFT;

    /** simple constructor */
    public SAMFileWriterFactory() {}

    /** copy constructor */
    public SAMFileWriterFactory(final SAMFileWriterFactory other) {
        if (other == null) throw new IllegalArgumentException("SAMFileWriterFactory(null)");
        this.createIndex = other.createIndex;
        this.createMd5File = other.createMd5File;
        this.useAsyncIo = other.useAsyncIo;
        this.asyncOutputBufferSize = other.asyncOutputBufferSize;
        this.bufferSize = other.bufferSize;
        this.tmpDir = other.tmpDir;
        this.compressionLevel = other.compressionLevel;
        this.maxRecordsInRam = other.maxRecordsInRam;
        this.cramEncodingStrategy = other.cramEncodingStrategy;
        this.createBaiIndexForCram = other.createBaiIndexForCram;
        this.deflaterFactory = other.deflaterFactory;
        this.samFlagFieldOutput = other.samFlagFieldOutput;
        this.bamIndexType = other.bamIndexType;
        this.samIndexType = other.samIndexType;
        this.csiMinShift = other.csiMinShift;
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
     * Gets the default for whether to create md5Files for BAM files this factory.
     * @see #setDefaultCreateMd5File(boolean)
     */
    public static boolean getDefaultCreateMd5File() {
        return defaultCreateMd5File;
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
    public SAMFileWriterFactory setDeflaterFactory(final DeflaterFactory deflaterFactory) {
        if (deflaterFactory == null) {
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
     * Sets the default for subsequent SAMFileWriterFactories that do not specify whether to create an index.
     * If a BAM, bgzipped SAM or CRAM file is created, the setting is true, and the file header specifies
     * coordinate order, then an index will be written along with the file.
     *
     * @param setting whether to attempt to create an index while creating the alignment file
     */
    public static void setDefaultCreateIndexWhileWriting(final boolean setting) {
        defaultCreateIndexWhileWriting = setting;
    }

    /**
     * Gets the default for subsequent SAMFileWriterFactories that do not specify whether to create an index.
     * @see #setDefaultCreateIndexWhileWriting
     */
    public static boolean getDefaultCreateIndexWhileWriting() {
        return defaultCreateIndexWhileWriting;
    }

    /**
     * Convenience method allowing newSAMFileWriterFactory().setCreateIndex(true);
     * Equivalent to SAMFileWriterFactory.setDefaultCreateIndexWhileWriting(true); newSAMFileWriterFactory();
     * If a BAM, bgzipped SAM or CRAM file is created, the setting is true, and the file header specifies
     * coordinate order, then an index will be written along with the file. The index kind is chosen by
     * {@link #setBamIndexType} for BAM, {@link #setSamIndexType} for bgzipped SAM, and is always a CRAI for
     * CRAM. Ignored for plain (uncompressed) SAM.
     *
     * @param setting whether to attempt to create an index while creating the alignment file.
     * @return this factory object
     */
    public SAMFileWriterFactory setCreateIndex(final boolean setting) {
        this.createIndex = setting;
        return this;
    }

    /**
     * Write a {@code .cram.bai} instead of the default {@code .cram.crai}. samtools cannot read a
     * BAI for a CRAM; use only for consumers that still require one.
     *
     * @param setting true to write a BAI instead of a CRAI. Ignored for non-CRAM output.
     * @return this factory object
     * @deprecated removed in the next major release along with BAI-for-CRAM writing; reading a
     *     BAI-indexed CRAM will still work.
     */
    @Deprecated
    public SAMFileWriterFactory setCreateBaiIndexForCram(final boolean setting) {
        this.createBaiIndexForCram = setting;
        return this;
    }

    /**
     * Sets the kind of index written beside a BAM when {@link #setCreateIndex index creation} is on. The default
     * is {@link BamIndexType#BAI}, written as {@code x.bai}; a CSI is written as {@code x.bam.csi}, as samtools
     * names it. A BAI cannot address positions beyond 2^29, so a BAM with a longer sequence needs
     * {@link BamIndexType#CSI}, or {@link BamIndexType#AUTO} to get a CSI only then. Ignored for bgzipped SAM
     * (which has {@link #setSamIndexType}) and CRAM.
     *
     * @return this factory object
     */
    public SAMFileWriterFactory setBamIndexType(final BamIndexType bamIndexType) {
        if (bamIndexType == null) {
            throw new IllegalArgumentException("null BAM index type");
        }
        this.bamIndexType = bamIndexType;
        return this;
    }

    /**
     * Sets the kind of index written beside a bgzipped SAM ({@code .sam.gz}) when {@link #setCreateIndex index
     * creation} is on. The default is {@link BamIndexType#CSI}, written as {@code x.sam.gz.csi}, as samtools
     * writes on the fly; {@link BamIndexType#BAI} is written as {@code x.sam.gz.bai}. {@link BamIndexType#AUTO}
     * resolves as for BAM: CSI only if a sequence is too long for a BAI. The CSI always carries a tabix header
     * naming every sequence of the header, so samtools and tabix both read it. Ignored for BAM, plain SAM and
     * CRAM.
     *
     * @return this factory object
     */
    public SAMFileWriterFactory setSamIndexType(final BamIndexType samIndexType) {
        if (samIndexType == null) {
            throw new IllegalArgumentException("null SAM index type");
        }
        this.samIndexType = samIndexType;
        return this;
    }

    /**
     * Sets log2 of the span of the smallest bins of a CSI index written beside a BAM or bgzipped SAM; the
     * default is 14, as for samtools. The rest of the binning scheme is chosen to reach the header's longest
     * sequence.
     *
     * @return this factory object
     */
    public SAMFileWriterFactory setCsiMinShift(final int csiMinShift) {
        if (csiMinShift < 1) {
            throw new IllegalArgumentException("CSI min shift must be at least 1, but was " + csiMinShift);
        }
        this.csiMinShift = csiMinShift;
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
     * Gets the maximum number of records held in RAM before spilling to disk during sorting.
     * @see #setMaxRecordsInRam(int)
     */
    public int getMaxRecordsInRam() {
        return maxRecordsInRam;
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
    public SAMFileWriterFactory setTempDirectory(final Path tmpDir) {
        this.tmpDir = tmpDir;
        return this;
    }

    /**
     * Gets the temporary directory that will be used when sorting data.
     * @see #setTempDirectory(Path)
     */
    public Path getTempDirectory() {
        return tmpDir;
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
     * Set the {@link CRAMEncodingStrategy} to use when creating CRAM writers. Controls the CRAM version,
     * compression profile, and per-data-series codec selection.
     *
     * <p>The default strategy uses the {@link htsjdk.samtools.cram.structure.CRAMCompressionProfile#NORMAL} profile.
     * To use a specific profile:
     * <pre>
     *   factory.setCRAMEncodingStrategy(CRAMCompressionProfile.ARCHIVE.toStrategy());
     * </pre>
     *
     * @param cramEncodingStrategy the encoding strategy to use for CRAM output
     * @return this factory for chaining
     */
    public SAMFileWriterFactory setCRAMEncodingStrategy(final CRAMEncodingStrategy cramEncodingStrategy) {
        if (cramEncodingStrategy == null) throw new IllegalArgumentException("CRAM encoding strategy was null");
        this.cramEncodingStrategy = cramEncodingStrategy;
        return this;
    }

    /** @return the current CRAM encoding strategy */
    public CRAMEncodingStrategy getCRAMEncodingStrategy() {
        return cramEncodingStrategy;
    }

    /**
     * Create a BAMFileWriter that is ready to receive SAMRecords.  Uses default compression level.
     *
     * @param header     entire header. Sort order is determined by the sortOrder property of this arg.
     * @param presorted  if true, SAMRecords must be added to the SAMFileWriter in order that agrees with header.sortOrder.
     * @param outputPath where to write the output.
     */
    public SAMFileWriter makeBAMWriter(final SAMFileHeader header, final boolean presorted, final Path outputPath) {
        return makeBAMWriter(header, presorted, outputPath, this.getCompressionLevel());
    }

    /**
     * Create a BAMFileWriter that is ready to receive SAMRecords from a URI.  Uses default compression level.
     * <p>
     * This is a convenience method that delegates to {@link #makeBAMWriter(SAMFileHeader, boolean, Path)} by converting
     * the URI to a Path via {@link IOUtil#getPath(URI)}. The URI must be supported by an available NIO filesystem provider.
     * </p>
     *
     * @param header     entire header. Sort order is determined by the sortOrder property of this arg.
     * @param presorted  if true, SAMRecords must be added to the SAMFileWriter in order that agrees with header.sortOrder.
     * @param outputUri  URI where to write the output (e.g., file:///path/to/file.bam)
     * @return a SAMFileWriter for the specified URI
     * @throws IOException if the URI cannot be converted to a Path
     */
    public SAMFileWriter makeBAMWriter(final SAMFileHeader header, final boolean presorted, final URI outputUri)
            throws IOException {
        return makeBAMWriter(header, presorted, IOUtil.getPath(outputUri));
    }

    /**
     * Create a BAMFileWriter that is ready to receive SAMRecords.
     *
     * @param header           entire header. Sort order is determined by the sortOrder property of this arg.
     * @param presorted        if true, SAMRecords must be added to the SAMFileWriter in order that agrees with header.sortOrder.
     * @param outputPath       where to write the output.
     * @param compressionLevel Override default compression level with the given value, between 0 (fastest) and 9 (smallest).
     */
    public SAMFileWriter makeBAMWriter(
            final SAMFileHeader header, final boolean presorted, final Path outputPath, final int compressionLevel) {
        try {
            final boolean createMd5File = this.createMd5File && IOUtil.isRegularPath(outputPath);
            if (this.createMd5File && !createMd5File) {
                log.warn("Cannot create MD5 file for BAM because output file is not a regular file: "
                        + outputPath.toUri());
            }
            OutputStream os = IOUtil.maybeBufferOutputStream(Files.newOutputStream(outputPath), bufferSize);
            if (createMd5File) os = new Md5CalculatingOutputStream(os, IOUtil.addExtension(outputPath, ".md5"));
            try {
                final BAMFileWriter ret =
                        new BAMFileWriter(os, outputPath.toUri().toString(), compressionLevel, deflaterFactory);
                final boolean createIndex = this.createIndex && IOUtil.isRegularPath(outputPath);
                if (this.createIndex && !createIndex) {
                    log.warn("Cannot create index for BAM because output file is not a regular file: "
                            + outputPath.toUri());
                }
                initializeBAMWriter(ret, header, presorted, createIndex);

                if (this.useAsyncIo) return new AsyncSAMFileWriter(ret, this.asyncOutputBufferSize);
                else return ret;
            } catch (final RuntimeException | Error e) {
                try {
                    os.close();
                } catch (final Exception suppressed) {
                    e.addSuppressed(suppressed);
                }
                throw e;
            }
        } catch (final IOException ioe) {
            throw new RuntimeIOException("Error opening file: " + outputPath.toUri(), ioe);
        }
    }

    private void initializeBAMWriter(
            final BAMFileWriter writer,
            final SAMFileHeader header,
            final boolean presorted,
            final boolean createIndex) {
        writer.setSortOrder(header.getSortOrder(), presorted);
        if (maxRecordsInRam != null) {
            writer.setMaxRecordsInRam(maxRecordsInRam);
        }
        if (this.tmpDir != null) writer.setTempDirectory(this.tmpDir);
        writer.setHeader(header);
        if (createIndex && writer.getSortOrder().equals(SAMFileHeader.SortOrder.coordinate)) {
            writer.enableBamIndexConstruction(bamIndexType, csiMinShift);
        }
    }

    /**
     * Create a SAMTextWriter that is ready to receive SAMRecords.
     *
     * @param header     entire header. Sort order is determined by the sortOrder property of this arg.
     * @param presorted  if true, SAMRecords must be added to the SAMFileWriter in order that agrees with header.sortOrder.
     * @param outputPath where to write the output. If it ends with one of {@link FileExtensions#BLOCK_COMPRESSED}
     *                   (for example {@code x.sam.gz}) the SAM text is BGZF-compressed, at this factory's
     *                   compression level; an MD5 file, if requested, is then of the compressed bytes. When
     *                   {@link #setCreateIndex index creation} is on and the output is block-compressed,
     *                   coordinate-sorted and a regular file, an index is written beside it; its kind is
     *                   chosen by {@link #setSamIndexType} (default CSI).
     */
    public SAMFileWriter makeSAMWriter(final SAMFileHeader header, final boolean presorted, final Path outputPath) {
        /**
         * Use the value specified from Defaults.SAM_FLAG_FIELD_FORMAT when samFlagFieldOutput value has not been set.  This should
         * be SamFlagField.DECIMAL when the user has not set Defaults.SAM_FLAG_FIELD_FORMAT.
         */
        if (samFlagFieldOutput == SamFlagField.NONE) {
            samFlagFieldOutput = Defaults.SAM_FLAG_FIELD_FORMAT;
        }
        try {
            OutputStream os = null == outputPath ? null : Files.newOutputStream(outputPath);
            if (this.createMd5File) {
                os = new Md5CalculatingOutputStream(os, IOUtil.addExtension(outputPath, ".md5"));
            }
            try {
                BlockCompressedOutputStream bgzfStream = null;
                if (outputPath != null && IOUtil.hasBlockCompressedExtension(outputPath)) {
                    // A BGZF stream makes several small writes per block, which uncompressed SAM's AsciiWriter does
                    // not. Closing the stream, as SAMTextWriter does when it finishes, writes the BGZF EOF block.
                    bgzfStream = new BlockCompressedOutputStream(
                            IOUtil.maybeBufferOutputStream(os, bufferSize),
                            outputPath,
                            compressionLevel,
                            deflaterFactory);
                    os = bgzfStream;
                }

                final SAMTextWriter writer = new SAMTextWriter(os, samFlagFieldOutput);
                if (bgzfStream != null && this.createIndex) {
                    final boolean canIndex = IOUtil.isRegularPath(outputPath);
                    if (!canIndex) {
                        log.warn("Cannot create index for SAM because output file is not a regular file: "
                                + outputPath.toUri());
                    }
                    if (canIndex && header.getSortOrder() == SAMFileHeader.SortOrder.coordinate) {
                        final BamIndexType resolved = samIndexType.resolve(header.getSequenceDictionary());
                        final Path indexPath;
                        if (resolved == BamIndexType.CSI) {
                            indexPath = IOUtil.addExtension(outputPath, FileExtensions.CSI);
                        } else {
                            indexPath = IOUtil.addExtension(outputPath, FileExtensions.BAI_INDEX);
                        }
                        writer.enableIndexConstruction(bgzfStream, indexPath, header, resolved, csiMinShift);
                    }
                }

                return initWriter(header, presorted, writer);
            } catch (final RuntimeException | Error e) {
                if (os != null) {
                    try {
                        os.close();
                    } catch (final Exception suppressed) {
                        e.addSuppressed(suppressed);
                    }
                }
                throw e;
            }
        } catch (final IOException ioe) {
            throw new RuntimeIOException("Error opening file: " + outputPath.toUri(), ioe);
        }
    }

    /**
     * Create a SAMTextWriter that is ready to receive SAMRecords from a URI.
     * <p>
     * This is a convenience method that delegates to {@link #makeSAMWriter(SAMFileHeader, boolean, Path)} by converting
     * the URI to a Path via {@link IOUtil#getPath(URI)}. The URI must be supported by an available NIO filesystem provider.
     * </p>
     *
     * @param header     entire header. Sort order is determined by the sortOrder property of this arg.
     * @param presorted  if true, SAMRecords must be added to the SAMFileWriter in order that agrees with header.sortOrder.
     * @param outputUri  URI where to write the output (e.g., file:///path/to/file.sam)
     * @return a SAMFileWriter for the specified URI
     * @throws IOException if the URI cannot be converted to a Path
     */
    public SAMFileWriter makeSAMWriter(final SAMFileHeader header, final boolean presorted, final URI outputUri)
            throws IOException {
        return makeSAMWriter(header, presorted, IOUtil.getPath(outputUri));
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
        return initWriter(header, presorted, new SAMTextWriter(stream, samFlagFieldOutput));
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
        return initWriter(
                header,
                presorted,
                new BAMFileWriter(stream, (String) null, this.getCompressionLevel(), this.deflaterFactory));
    }

    /**
     * Initialize SAMTextWriter or a BAMFileWriter and possibly wrap in AsyncSAMFileWriter
     * @param header    entire header. Sort order is determined by the sortOrder property of this arg.
     * @param presorted if true, SAMRecords must be added to the SAMFileWriter in order that agrees with header.sortOrder.
     * @param writer    SAM or BAM writer to initialize and maybe wrap.
     */
    private SAMFileWriter initWriter(
            final SAMFileHeader header, final boolean presorted, final SAMFileWriterImpl writer) {
        writer.setSortOrder(header.getSortOrder(), presorted);
        if (maxRecordsInRam != null) {
            writer.setMaxRecordsInRam(maxRecordsInRam);
        }
        if (this.tmpDir != null) writer.setTempDirectory(this.tmpDir);
        writer.setHeader(header);

        if (this.useAsyncIo) return new AsyncSAMFileWriter(writer, this.asyncOutputBufferSize);
        else return writer;
    }

    /**
     * Create either a SAM or a BAM writer based on examination of the outputPath extension.
     *
     * @param header     entire header. Sort order is determined by the sortOrder property of this arg.
     * @param presorted  presorted if true, SAMRecords must be added to the SAMFileWriter in order that agrees with header.sortOrder.
     * @param outputPath where to write the output.  Must end with .sam or .bam, or with .sam followed by one of
     *                   {@link FileExtensions#BLOCK_COMPRESSED} (for example {@code x.sam.gz}) for BGZF-compressed SAM.
     *                   SAM names are recognised in any case.
     * @return SAM or BAM writer based on file extension of outputPath.
     */
    public SAMFileWriter makeSAMOrBAMWriter(
            final SAMFileHeader header, final boolean presorted, final Path outputPath) {
        final String filename = outputPath.getFileName().toString();
        if (isSamName(filename)) {
            return makeSAMWriter(header, presorted, outputPath);
        } else {
            if (!BAM_TYPE.hasValidFileExtension(filename)) {
                log.info("Unknown file extension, assuming BAM format when writing file: "
                        + outputPath.toUri().toString());
            }
            return makeBAMWriter(header, presorted, outputPath);
        }
    }

    /** True for a SAM file name in any case, with or without a block-compression extension: {@code x.sam}, {@code x.SAM.gz}. */
    private static boolean isSamName(final String filename) {
        if (SAM_TYPE.hasValidFileExtension(filename)) {
            return true;
        }
        final String name = filename.toLowerCase(Locale.ROOT);
        for (final String extension : FileExtensions.BLOCK_COMPRESSED) {
            if (name.endsWith(FileExtensions.SAM + extension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create either a SAM or a BAM writer based on examination of the URI extension.
     * <p>
     * This is a convenience method that delegates to {@link #makeSAMOrBAMWriter(SAMFileHeader, boolean, Path)} by
     * converting the URI to a Path via {@link IOUtil#getPath(URI)}. The URI must be supported by an available NIO
     * filesystem provider.
     * </p>
     *
     * @param header     entire header. Sort order is determined by the sortOrder property of this arg.
     * @param presorted  presorted if true, SAMRecords must be added to the SAMFileWriter in order that agrees with header.sortOrder.
     * @param outputUri  URI where to write the output (e.g., file:///path/to/file.sam or file:///path/to/file.bam)
     * @return SAM or BAM writer based on file extension of outputUri
     * @throws IOException if the URI cannot be converted to a Path
     */
    public SAMFileWriter makeSAMOrBAMWriter(final SAMFileHeader header, final boolean presorted, final URI outputUri)
            throws IOException {
        return makeSAMOrBAMWriter(header, presorted, IOUtil.getPath(outputUri));
    }

    /**
     *
     * Create a SAM, BAM or CRAM writer based on examination of the outputPath extension.
     *
     * @param header header. Sort order is determined by the sortOrder property of this arg.
     * @param presorted if true, SAMRecords must be added to the SAMFileWriter in order that agrees with header.sortOrder.
     * @param outputPath where to write the output.  Must end with .sam, .bam or .cram, or name BGZF-compressed SAM as
     *                   {@link #makeSAMOrBAMWriter(SAMFileHeader, boolean, Path)} describes.
     * @param referenceFasta reference sequence file
     * @return SAMFileWriter appropriate for the file type specified in outputPath
     *
     */
    public SAMFileWriter makeWriter(
            final SAMFileHeader header, final boolean presorted, final Path outputPath, final Path referenceFasta) {
        final String filename = outputPath.getFileName().toString();
        if (CRAM_TYPE.hasValidFileExtension(filename)) {
            return makeCRAMWriter(header, presorted, outputPath, referenceFasta);
        } else {
            return makeSAMOrBAMWriter(header, presorted, outputPath);
        }
    }

    /**
     * Create a SAM, BAM or CRAM writer based on examination of the URI extension.
     * <p>
     * This is a convenience method that delegates to {@link #makeWriter(SAMFileHeader, boolean, Path, Path)} by
     * converting the URIs to Paths via {@link IOUtil#getPath(URI)}. The URIs must be supported by an available NIO
     * filesystem provider.
     * </p>
     *
     * @param header header. Sort order is determined by the sortOrder property of this arg.
     * @param presorted if true, SAMRecords must be added to the SAMFileWriter in order that agrees with header.sortOrder.
     * @param outputUri URI where to write the output (e.g., file:///path/to/file.sam, file:///path/to/file.bam, or file:///path/to/file.cram)
     * @param referenceUri URI of the reference sequence file, or null
     * @return SAMFileWriter appropriate for the file type specified in outputUri
     * @throws IOException if a URI cannot be converted to a Path
     */
    public SAMFileWriter makeWriter(
            final SAMFileHeader header, final boolean presorted, final URI outputUri, final URI referenceUri)
            throws IOException {
        final Path referencePath = referenceUri == null ? null : IOUtil.getPath(referenceUri);
        return makeWriter(header, presorted, IOUtil.getPath(outputUri), referencePath);
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
    public CRAMFileWriter makeCRAMWriter(
            final SAMFileHeader header, final OutputStream stream, final Path referenceFasta) {
        return new CRAMFileWriter(
                cramEncodingStrategy,
                stream,
                (OutputStream) null, // no index
                true, // presorted
                new ReferenceSource(referenceFasta),
                header,
                null);
    }

    /**
     * Create a CRAMFileWriter on an output file. Requires input record to be presorted to match the
     * sort order defined by the input header.
     *
     * Note: does not honor factory settings for USE_ASYNC_IO.
     *
     * @param header entire header. Sort order is determined by the sortOrder property of this arg.
     * @param presorted  if true, SAMRecords must be added to the SAMFileWriter in order that agrees with header.sortOrder.
     * @param output where to write the output.  Must end with .sam, .bam or .cram.
     * @param referenceFasta reference sequence file
     * @return CRAMFileWriter
     *
     */
    public CRAMFileWriter makeCRAMWriter(
            final SAMFileHeader header, final boolean presorted, final Path output, final Path referenceFasta) {
        return createCRAMWriterWithSettings(header, presorted, output, referenceFasta);
    }

    /**
     * Create a CRAMFileWriter on an output file from a URI.
     * <p>
     * This is a convenience method that delegates to {@link #makeCRAMWriter(SAMFileHeader, boolean, Path, Path)} by
     * converting the URIs to Paths via {@link IOUtil#getPath(URI)}. The URIs must be supported by an available NIO
     * filesystem provider.
     * </p>
     *
     * Note: does not honor factory setting for USE_ASYNC_IO.
     *
     * @param header entire header. Sort order is determined by the sortOrder property of this arg.
     * @param presorted  if true, SAMRecords must be added to the SAMFileWriter in order that agrees with header.sortOrder.
     * @param outputUri URI where to write the output (e.g., file:///path/to/file.cram)
     * @param referenceUri URI of the reference sequence file, or null
     * @return CRAMFileWriter
     * @throws IOException if a URI cannot be converted to a Path
     */
    public CRAMFileWriter makeCRAMWriter(
            final SAMFileHeader header, final boolean presorted, final URI outputUri, final URI referenceUri)
            throws IOException {
        final Path referencePath = referenceUri == null ? null : IOUtil.getPath(referenceUri);
        return makeCRAMWriter(header, presorted, IOUtil.getPath(outputUri), referencePath);
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
            final SAMFileHeader header, final boolean presorted, final Path outputFile, final Path referenceFasta) {

        final CRAMReferenceSource referenceSource;
        if (referenceFasta == null) {
            log.info("Reference fasta is not provided when writing CRAM file "
                    + outputFile.toUri().toString());
            log.info("Will attempt to use a default reference or download as set by defaults:");
            log.info("Default REFERENCE_FASTA (-Dsamjdk.reference_fasta): " + Defaults.REFERENCE_FASTA);
            log.info("Default USE_CRAM_REF_DOWNLOAD (-Dsamjdk.use_cram_ref_download): "
                    + Defaults.USE_CRAM_REF_DOWNLOAD);

            referenceSource = ReferenceSource.getDefaultCRAMReferenceSource();
        } else {
            referenceSource = new ReferenceSource(referenceFasta);
        }
        OutputStream cramOS = null;
        OutputStream indexOS = null;

        if (createIndex) {
            if (!IOUtil.isRegularPath(outputFile)) {
                log.warn("Cannot create index for CRAM because output file is not a regular file: "
                        + outputFile.toUri());
            } else {
                final Path indexPath = IOUtil.addExtension(
                        outputFile, createBaiIndexForCram ? FileExtensions.BAI_INDEX : FileExtensions.CRAM_INDEX);
                try {

                    indexOS = Files.newOutputStream(indexPath);
                } catch (final IOException ioe) {
                    throw new RuntimeIOException("Error creating index file for: " + indexPath.toUri(), ioe);
                }
            }
        }

        try {
            cramOS = IOUtil.maybeBufferOutputStream(Files.newOutputStream(outputFile), bufferSize);
        } catch (final IOException ioe) {
            throw new RuntimeIOException("Error creating CRAM file: " + outputFile.toUri(), ioe);
        }

        final CRAMIndexer indexer;
        if (indexOS == null) {
            indexer = null;
        } else {
            indexer =
                    createBaiIndexForCram ? new CRAMBAIIndexer(indexOS, header) : new CRAMCRAIIndexer(indexOS, header);
        }

        final Path md5Path = IOUtil.addExtension(outputFile, ".md5");
        final CRAMFileWriter writer = CRAMFileWriter.withIndexer(
                cramEncodingStrategy,
                createMd5File ? new Md5CalculatingOutputStream(cramOS, md5Path) : cramOS,
                indexer,
                presorted,
                referenceSource,
                header,
                outputFile.toUri().toString());

        return writer;
    }

    @Override
    public String toString() {
        return "SAMFileWriterFactory [createIndex=" + createIndex + ", createMd5File=" + createMd5File + ", useAsyncIo="
                + useAsyncIo + ", asyncOutputBufferSize=" + asyncOutputBufferSize + ", bufferSize=" + bufferSize
                + ", tmpDir=" + tmpDir + ", compressionLevel=" + compressionLevel + ", maxRecordsInRam="
                + maxRecordsInRam + ", createBaiIndexForCram=" + createBaiIndexForCram + ", bamIndexType="
                + bamIndexType + ", samIndexType=" + samIndexType + ", csiMinShift=" + csiMinShift + "]";
    }
}
