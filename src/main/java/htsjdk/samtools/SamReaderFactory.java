/*
 * The MIT License
 *
 * Copyright (c) 2016 The Broad Institute
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

import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.sra.SRAAccession;
import htsjdk.samtools.util.*;
import htsjdk.samtools.util.zip.InflaterFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

/**
 * <p>Describes the functionality for producing {@link SamReader}, and offers a
 * handful of static generators.</p>
 * <pre>
 *     SamReaderFactory.makeDefault().open(new File("/my/bam.bam");
 * </pre>
 * <p>Example: Configure a factory</p>
 * <pre>
 *      final {@link SamReaderFactory} factory =
 *          SamReaderFactory.makeDefault()
 *              .enable({@link Option#INCLUDE_SOURCE_IN_RECORDS}, {@link Option#VALIDATE_CRC_CHECKSUMS})
 *              .validationStringency({@link ValidationStringency#SILENT});
 *
 * </pre>
 * <p>Example: Open two bam files from different sources, using different options</p>
 * <pre>
 *     final {@link SamReaderFactory} factory =
 *          SamReaderFactory.makeDefault()
 *              .enable({@link Option#INCLUDE_SOURCE_IN_RECORDS}, {@link Option#VALIDATE_CRC_CHECKSUMS})
 *              .validationStringency({@link ValidationStringency#SILENT});
 *
 *     // File-based bam
 *     final {@link SamReader} fileReader = factory.open(new File("/my/bam.bam"));
 *
 *     // HTTP-hosted BAM with index from an arbitrary stream
 *     final SeekableStream myBamIndexStream = ...
 *     final {@link SamInputResource} resource =
 *          {@link SamInputResource}.of(new URL("http://example.com/data.bam")).index(myBamIndexStream);
 *     final {@link SamReader} complicatedReader = factory.open(resource);
 * </pre>
 *
 * @author mccowan
 */
public abstract class SamReaderFactory {

    private static ValidationStringency defaultValidationStringency = ValidationStringency.DEFAULT_STRINGENCY;

    abstract public SamReader open(final File file);

    /**
     * Open the specified path (without using any wrappers).
     *
     * @param path the SAM or BAM file to open.
     */
    public SamReader open(final Path path) {
        return open(path, null, null);
    }

    /**
     * Open the specified path, using the specified wrappers for prefetching/caching.
     *
     * @param path the SAM or BAM file to open
     * @param dataWrapper the wrapper for the data (or null for none)
     * @param indexWrapper the wrapper for the index (or null for none)
     */
    public SamReader open(final Path path,
            Function<SeekableByteChannel, SeekableByteChannel> dataWrapper,
            Function<SeekableByteChannel, SeekableByteChannel> indexWrapper) {
        final SamInputResource r = SamInputResource.of(path, dataWrapper);
        final Path indexMaybe = SamFiles.findIndex(path);
        if (indexMaybe != null) r.index(indexMaybe, indexWrapper);
        return open(r);
    }

    abstract public SamReader open(final SamInputResource resource);

    abstract public ValidationStringency validationStringency();

    abstract public CRAMReferenceSource referenceSource();

    /** Set this factory's {@link htsjdk.samtools.SAMRecordFactory} to the provided one, then returns itself. */
    abstract public SamReaderFactory samRecordFactory(final SAMRecordFactory samRecordFactory);

    /**
     * Set this factory's {@link htsjdk.samtools.util.zip.InflaterFactory} to the provided one, then returns itself.
     * Note: The inflaterFactory provided here is only used for BAM decompression implemented with {@link BAMFileReader},
     * it is not used for CRAM or other formats like a gzipped SAM file.
     */
    abstract public SamReaderFactory inflaterFactory(final InflaterFactory inflaterFactory);

    /** Enables the provided {@link Option}s, then returns itself. */
    abstract public SamReaderFactory enable(final Option... options);

    /** Disables the provided {@link Option}s, then returns itself. */
    abstract public SamReaderFactory disable(final Option... options);

    /** Sets a specific Option to a boolean value. * */
    abstract public SamReaderFactory setOption(final Option option, boolean value);

    /** Sets the specified reference sequence * */
    abstract public SamReaderFactory referenceSequence(File referenceSequence);

    /** Sets the specified reference sequence. */
    abstract public SamReaderFactory referenceSequence(Path referenceSequence);

    /** Sets the specified reference sequence * */
    abstract public SamReaderFactory referenceSource(CRAMReferenceSource referenceSequence);

    /** Utility method to open the file get the header and close the file */
    abstract public SAMFileHeader getFileHeader(File samFile);

    /** Utility method to open the file get the header and close the file */
    abstract public SAMFileHeader getFileHeader(Path samFile);

    /** Reapplies any changed options to the reader * */
    abstract public void reapplyOptions(SamReader reader);

    /** Set this factory's {@link ValidationStringency} to the provided one, then returns itself. */
    abstract public SamReaderFactory validationStringency(final ValidationStringency validationStringency);

    /** Set whether readers created by this factory will use asynchronous IO.
     * If this methods is not called, this flag will default to the value of {@link Defaults#USE_ASYNC_IO_READ_FOR_SAMTOOLS}.
     * Note that this option may not be applicable to all readers returned from this factory.
     * Returns the factory itself. */
    abstract public SamReaderFactory setUseAsyncIo(final boolean asynchronousIO);

    private static SamReaderFactoryImpl DEFAULT =
            new SamReaderFactoryImpl(Option.DEFAULTS, defaultValidationStringency,
                    DefaultSAMRecordFactory.getInstance(), BlockGunzipper.getDefaultInflaterFactory());

    public static void setDefaultValidationStringency(final ValidationStringency defaultValidationStringency) {
        SamReaderFactory.defaultValidationStringency = defaultValidationStringency;
        // The default may have changed, so reset the default SamReader
        DEFAULT = new SamReaderFactoryImpl(Option.DEFAULTS, defaultValidationStringency,
                DefaultSAMRecordFactory.getInstance(), BlockGunzipper.getDefaultInflaterFactory());
    }

    /** Creates a copy of the default {@link SamReaderFactory}. */
    public static SamReaderFactory makeDefault() {
        return SamReaderFactoryImpl.copyOf(DEFAULT);
    }

    /**
     * Creates an "empty" factory with no enabled {@link Option}s, {@link ValidationStringency#DEFAULT_STRINGENCY},
     * no path wrapper, and {@link htsjdk.samtools.DefaultSAMRecordFactory}.
     */
    public static SamReaderFactory make() {
        return new SamReaderFactoryImpl(EnumSet.noneOf(Option.class), ValidationStringency.DEFAULT_STRINGENCY,
                DefaultSAMRecordFactory.getInstance(), BlockGunzipper.getDefaultInflaterFactory());
    }

    private static class SamReaderFactoryImpl extends SamReaderFactory {
        private final static Log LOG = Log.getInstance(SamReaderFactory.class);
        private final EnumSet<Option> enabledOptions;
        private ValidationStringency validationStringency;
        private boolean asynchronousIO = Defaults.USE_ASYNC_IO_READ_FOR_SAMTOOLS;
        private SAMRecordFactory samRecordFactory;
        private CustomReaderFactory customReaderFactory;
        private CRAMReferenceSource referenceSource;
        private InflaterFactory inflaterFactory;

        private SamReaderFactoryImpl(final EnumSet<Option> enabledOptions, final ValidationStringency validationStringency, final SAMRecordFactory samRecordFactory, final InflaterFactory inflaterFactory) {
            this.enabledOptions = EnumSet.copyOf(enabledOptions);
            this.samRecordFactory = samRecordFactory;
            this.validationStringency = validationStringency;
            this.customReaderFactory = CustomReaderFactory.getInstance();
            this.inflaterFactory = inflaterFactory;
        }
   
        @Override
        public SamReader open(final File file) {
            final SamInputResource r = SamInputResource.of(file);
            final File indexMaybe = SamFiles.findIndex(file);
            if (indexMaybe != null) r.index(indexMaybe);
            return open(r);
        }


        @Override
        public ValidationStringency validationStringency() {
            return validationStringency;
        }

        @Override
        public CRAMReferenceSource referenceSource() {
            return referenceSource;
        }

        @Override
        public SamReaderFactory samRecordFactory(final SAMRecordFactory samRecordFactory) {
            this.samRecordFactory = samRecordFactory;
            return this;
        }

        @Override
        public SamReaderFactory inflaterFactory(final InflaterFactory inflaterFactory) {
            this.inflaterFactory = inflaterFactory;
            return this;
        }

        @Override
        public SamReaderFactory enable(final Option... options) {
            Collections.addAll(this.enabledOptions, options);
            return this;
        }

        @Override
        public SamReaderFactory disable(final Option... options) {
            for (final Option option : options) {
                this.enabledOptions.remove(option);
            }
            return this;
        }

        @Override
        public SamReaderFactory setOption(final Option option, final boolean value) {
            if (value) {
                return enable(option);
            } else {
                return disable(option);
            }
        }

        @Override
        public SamReaderFactory referenceSequence(final File referenceSequence) {
            this.referenceSource = new ReferenceSource(referenceSequence);
            return this;
        }

        @Override
        public SamReaderFactory referenceSequence(final Path referenceSequence) {
            this.referenceSource = new ReferenceSource(referenceSequence);
            return this;
        }

        @Override
        public SamReaderFactory referenceSource(final CRAMReferenceSource referenceSource) {
            this.referenceSource = referenceSource;
            return this;
        }

        @Override
        public SAMFileHeader getFileHeader(final File samFile) {
            final SamReader reader = open(samFile);
            final SAMFileHeader header = reader.getFileHeader();
            CloserUtil.close(reader);
            return header;
        }

        @Override
        public SAMFileHeader getFileHeader(final Path samFile) {
            final SamReader reader = open(samFile);
            final SAMFileHeader header = reader.getFileHeader();
            CloserUtil.close(reader);
            return header;
        }

        @Override
        public void reapplyOptions(final SamReader reader) {
            for (final Option option : enabledOptions) {
                option.applyTo((SamReader.PrimitiveSamReaderToSamReaderAdapter) reader);
            }
        }

        @Override
        public SamReaderFactory validationStringency(final ValidationStringency validationStringency) {
            this.validationStringency = validationStringency;
            return this;
        }

        @Override
        public SamReaderFactory setUseAsyncIo(final boolean asynchronousIO){
            this.asynchronousIO = asynchronousIO;
            return this;
        }

        @Override
        public SamReader open(final SamInputResource resource) {
            final SamReader.PrimitiveSamReader primitiveSamReader;
            try {
                final InputResource data = resource.data();
                final InputResource indexMaybe = resource.indexMaybe();
                final boolean indexDefined = indexMaybe != null;

                final InputResource.Type type = data.type();
                if (type == InputResource.Type.URL) {
                  SamReader reader = customReaderFactory.maybeOpen(
                      data.asUrl());
                  if (reader != null) {
                    return reader;
                  }
                }
                if (type == InputResource.Type.SEEKABLE_STREAM || type == InputResource.Type.URL) {
                    if (SamStreams.sourceLikeBam(data.asUnbufferedSeekableStream())) {
                        final SeekableStream bufferedIndexStream;
                        if (indexDefined && indexMaybe.asUnbufferedSeekableStream() != null) {
                            bufferedIndexStream = IOUtil.maybeBufferedSeekableStream(indexMaybe.asUnbufferedSeekableStream());
                        } else {
                            // TODO: Throw an exception here?  An index _may_ have been provided, but we're ignoring it
                            bufferedIndexStream = null;
                        }

                        primitiveSamReader = new BAMFileReader(
                                IOUtil.maybeBufferedSeekableStream(data.asUnbufferedSeekableStream()),
                                bufferedIndexStream,
                                false,
                                asynchronousIO,
                                validationStringency,
                                this.samRecordFactory,
                                this.inflaterFactory
                        );
                    } else if (SamStreams.sourceLikeCram(data.asUnbufferedSeekableStream())) {
                        if (referenceSource == null) {
                            referenceSource = ReferenceSource.getDefaultCRAMReferenceSource();
                        }
                        SeekableStream bufferedIndexStream = indexDefined ?
                                IOUtil.maybeBufferedSeekableStream(indexMaybe.asUnbufferedSeekableStream()) :
                                null;
                        primitiveSamReader = new CRAMFileReader(
                                IOUtil.maybeBufferedSeekableStream(data.asUnbufferedSeekableStream()),
                                bufferedIndexStream, referenceSource, validationStringency);
                    } else {
                        // assume its a SAM file/no index
                        LOG.warn("Unable to detect file format from input URL or stream, assuming SAM format.");
                        primitiveSamReader = new SAMTextReader(
                                IOUtil.toBufferedStream(data.asUnbufferedInputStream()),
                                validationStringency, this.samRecordFactory);
                    }
                } else if (type == InputResource.Type.SRA_ACCESSION) {
                    primitiveSamReader = new SRAFileReader(data.asSRAAccession());
                } else {
                    InputStream bufferedStream =
                            IOUtil.maybeBufferInputStream(
                                    data.asUnbufferedInputStream(),
                                    Math.max(Defaults.BUFFER_SIZE, BlockCompressedStreamConstants.MAX_COMPRESSED_BLOCK_SIZE)
                            );
                    File sourceFile = data.asFile();
                    // calling asFile is safe even if indexMaybe is a Google Cloud Storage bucket
                    // (in that case we just get null)
                    final File indexFile = indexMaybe == null ? null : indexMaybe.asFile();
                    if (SamStreams.isBAMFile(bufferedStream)) {
                        if (sourceFile == null || !sourceFile.isFile()) {
                            // check whether we can seek
                            final SeekableStream indexSeekable = indexMaybe == null ? null : indexMaybe.asUnbufferedSeekableStream();
                            // do not close bufferedStream, it's the same stream we're getting here.
                            SeekableStream sourceSeekable = data.asUnbufferedSeekableStream();
                            if (null == sourceSeekable || null == indexSeekable) {
                                // not seekable.
                                // it's OK that we consumed a bit of the stream already, this ctor expects it.
                                primitiveSamReader = new BAMFileReader(bufferedStream, indexFile, false, asynchronousIO,
                                        validationStringency, this.samRecordFactory, this.inflaterFactory);
                            } else {
                                // seekable.
                                // need to return to the beginning because it's the same stream we used earlier
                                // and read a bit from, and that form of the ctor expects the stream to start at 0.
                                sourceSeekable.seek(0);
                                primitiveSamReader = new BAMFileReader(
                                        sourceSeekable, indexSeekable, false, asynchronousIO, validationStringency,
                                        this.samRecordFactory, this.inflaterFactory);
                            }
                        } else {
                            bufferedStream.close();
                            primitiveSamReader = new BAMFileReader(
                                sourceFile, indexFile, false, asynchronousIO,
                                validationStringency, this.samRecordFactory, this.inflaterFactory);
                        }
                    } else if (BlockCompressedInputStream.isValidFile(bufferedStream)) {
                        primitiveSamReader = new SAMTextReader(new BlockCompressedInputStream(bufferedStream), validationStringency, this.samRecordFactory);
                    } else if (SamStreams.isGzippedSAMFile(bufferedStream)) {
                        primitiveSamReader = new SAMTextReader(new GZIPInputStream(bufferedStream), validationStringency, this.samRecordFactory);
                    } else if (SamStreams.isCRAMFile(bufferedStream)) {
                        if (referenceSource == null) {
                            referenceSource = ReferenceSource.getDefaultCRAMReferenceSource();
                        }
                        if (sourceFile == null || !sourceFile.isFile()) {
                            primitiveSamReader = new CRAMFileReader(bufferedStream, indexFile, referenceSource, validationStringency);
                        } else {
                            bufferedStream.close();
                            primitiveSamReader = new CRAMFileReader(sourceFile, indexFile, referenceSource, validationStringency);
                        }
                    } else if (sourceFile != null && isSra(sourceFile)) {
                        if (bufferedStream != null) {
                            bufferedStream.close();
                        }
                        primitiveSamReader = new SRAFileReader(new SRAAccession(sourceFile.getPath()));
                    } else {
                        if (indexDefined) {
                            bufferedStream.close();
                            throw new RuntimeException("Cannot use index file with textual SAM file");
                        }
                        primitiveSamReader = new SAMTextReader(bufferedStream, sourceFile, validationStringency, this.samRecordFactory);
                    }
                }

                // Apply the options defined by this factory to this reader
                final SamReader.PrimitiveSamReaderToSamReaderAdapter reader =
                        new SamReader.PrimitiveSamReaderToSamReaderAdapter(primitiveSamReader, resource);

                for (final Option option : enabledOptions) {
                    option.applyTo(reader);
                }

                return reader;
            } catch (final IOException e) {
                throw new RuntimeIOException(e);
            }
        }

        /** Attempts to detect whether the file is an SRA accessioned file. If SRA support is not available, returns false. */
        private boolean isSra(final File sourceFile) {
            try {
                // if SRA fails to initialize (the most common reason is a failure to find/load native libraries),
                // it will throw a subclass of java.lang.Error and here we only catch subclasses of java.lang.Exception
                //
                // Note: SRA initialization errors should not be ignored, but rather shown to user
                return SRAAccession.isValid(sourceFile.getPath());
            } catch (final Exception e) {
                return false;
            }
        }

        public static SamReaderFactory copyOf(final SamReaderFactoryImpl target) {
            return new SamReaderFactoryImpl(target.enabledOptions, target.validationStringency, target.samRecordFactory, target.inflaterFactory);
        }
    }

    /** A collection of binary {@link SamReaderFactory} options. */
    public enum Option {
        /**
         * The factory's {@link SamReader}s will produce populated (non-null) values when calling {@link SAMRecord#getFileSource()}.
         * <p/>
         * This option increases memory footprint slightly per {@link htsjdk.samtools.SAMRecord}.
         */
        INCLUDE_SOURCE_IN_RECORDS {
            @Override
            void applyTo(final BAMFileReader underlyingReader, final SamReader reader) {
                underlyingReader.enableFileSource(reader, true);
            }

            @Override
            void applyTo(final SAMTextReader underlyingReader, final SamReader reader) {
                underlyingReader.enableFileSource(reader, true);
            }

            @Override
            void applyTo(final CRAMFileReader underlyingReader, final SamReader reader) {
                underlyingReader.enableFileSource(reader, true);
            }

            @Override
            void applyTo(final SRAFileReader underlyingReader, final SamReader reader) {
                underlyingReader.enableFileSource(reader, true);
            }
        },

        /**
         * The factory's {@link SamReader}s' {@link SamReader#indexing()}'s calls to {@link SamReader.Indexing#getIndex()} will produce
         * {@link BAMIndex}es that do some caching in memory instead of reading the index from the disk for each query operation.
         *
         * @see SamReader#indexing()
         * @see htsjdk.samtools.SamReader.Indexing#getIndex()
         */
        CACHE_FILE_BASED_INDEXES {
            @Override
            void applyTo(final BAMFileReader underlyingReader, final SamReader reader) {
                underlyingReader.enableIndexCaching(true);
            }

            @Override
            void applyTo(final SAMTextReader underlyingReader, final SamReader reader) {
                logDebugIgnoringOption(reader, this);
            }

            @Override
            void applyTo(final CRAMFileReader underlyingReader, final SamReader reader) {
                underlyingReader.enableIndexCaching(true);
            }

            @Override
            void applyTo(final SRAFileReader underlyingReader, final SamReader reader) {
                underlyingReader.enableIndexCaching(true);
            }
        },

        /**
         * The factory's {@link SamReader}s' will not use memory mapping for accessing index files (which is used by default).  This is
         * slower but more scalable when accessing large numbers of BAM files sequentially.
         *
         * @see SamReader#indexing()
         * @see htsjdk.samtools.SamReader.Indexing#getIndex()
         */
        DONT_MEMORY_MAP_INDEX {
            @Override
            void applyTo(final BAMFileReader underlyingReader, final SamReader reader) {
                underlyingReader.enableIndexMemoryMapping(false);
            }

            @Override
            void applyTo(final SAMTextReader underlyingReader, final SamReader reader) {
                logDebugIgnoringOption(reader, this);
            }

            @Override
            void applyTo(final CRAMFileReader underlyingReader, final SamReader reader) {
                underlyingReader.enableIndexMemoryMapping(false);
            }

            @Override
            void applyTo(final SRAFileReader underlyingReader, final SamReader reader) {
                underlyingReader.enableIndexMemoryMapping(false);
            }
        },

        /**
         * Eagerly decode {@link htsjdk.samtools.SamReader}'s {@link htsjdk.samtools.SAMRecord}s, which can reduce memory footprint if many
         * fields are being read per record, or if fields are going to be updated.
         */
        EAGERLY_DECODE {
            @Override
            void applyTo(final BAMFileReader underlyingReader, final SamReader reader) {
                underlyingReader.setEagerDecode(true);
            }

            @Override
            void applyTo(final SAMTextReader underlyingReader, final SamReader reader) {
                logDebugIgnoringOption(reader, this);
            }

            @Override
            void applyTo(final CRAMFileReader underlyingReader, final SamReader reader) {
                logDebugIgnoringOption(reader, this);
            }

            @Override
            void applyTo(final SRAFileReader underlyingReader, final SamReader reader) {
                logDebugIgnoringOption(reader, this);
            }
        },

        /**
         * For {@link htsjdk.samtools.SamReader}s backed by block-compressed streams, enable CRC validation of those streams.  This is an
         * expensive operation, but serves to ensure validity of the stream.
         */
        VALIDATE_CRC_CHECKSUMS {
            @Override
            void applyTo(final BAMFileReader underlyingReader, final SamReader reader) {
                underlyingReader.enableCrcChecking(true);
            }

            @Override
            void applyTo(final SAMTextReader underlyingReader, final SamReader reader) {
                logDebugIgnoringOption(reader, this);
            }

            @Override
            void applyTo(final CRAMFileReader underlyingReader, final SamReader reader) {
                logDebugIgnoringOption(reader, this);
            }

            @Override
            void applyTo(final SRAFileReader underlyingReader, final SamReader reader) {
                logDebugIgnoringOption(reader, this);
            }

        };

        public static EnumSet<Option> DEFAULTS = EnumSet.noneOf(Option.class);

        /** Applies this option to the provided reader, if applicable. */
        void applyTo(final SamReader.PrimitiveSamReaderToSamReaderAdapter reader) {
            final SamReader.PrimitiveSamReader underlyingReader = reader.underlyingReader();
            if (underlyingReader instanceof BAMFileReader) {
                applyTo((BAMFileReader) underlyingReader, reader);
            } else if (underlyingReader instanceof SAMTextReader) {
                applyTo((SAMTextReader) underlyingReader, reader);
            } else if (underlyingReader instanceof CRAMFileReader) {
                applyTo((CRAMFileReader) underlyingReader, reader);
            } else if (underlyingReader instanceof SRAFileReader) {
                applyTo((SRAFileReader) underlyingReader, reader);
            } else {
                throw new IllegalArgumentException(String.format("Unrecognized reader type: %s.", underlyingReader.getClass()));
            }

        }

        private static void logDebugIgnoringOption(final SamReader r, final Option option) {
            LOG.debug(String.format("Ignoring %s option; does not apply to %s readers.", option, r.getClass().getSimpleName()));
        }

        private final static Log LOG = Log.getInstance(Option.class);

        abstract void applyTo(final BAMFileReader underlyingReader, final SamReader reader);

        abstract void applyTo(final SAMTextReader underlyingReader, final SamReader reader);

        abstract void applyTo(final CRAMFileReader underlyingReader, final SamReader reader);

        abstract void applyTo(final SRAFileReader underlyingReader, final SamReader reader);
    }
}
