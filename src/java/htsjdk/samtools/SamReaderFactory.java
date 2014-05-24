package htsjdk.samtools;

import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.BlockCompressedStreamConstants;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.EnumSet;
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

    abstract public SamReader open(final File file);

    abstract public SamReader open(final SamInputResource resource);

    abstract public ValidationStringency validationStringency();

    /** Set this factory's {@link htsjdk.samtools.SAMRecordFactory} to the provided one, then returns itself. */
    abstract public SamReaderFactory samRecordFactory(final SAMRecordFactory samRecordFactory);

    /** Enables the provided {@link Option}s, then returns itself. */
    abstract public SamReaderFactory enable(final Option... options);

    /** Disables the provided {@link Option}s, then returns itself. */
    abstract public SamReaderFactory disable(final Option... options);

    /** Set this factory's {@link ValidationStringency} to the provided one, then returns itself. */
    abstract public SamReaderFactory validationStringency(final ValidationStringency validationStringency);

    private static final SamReaderFactoryImpl DEFAULT =
            new SamReaderFactoryImpl(Option.DEFAULTS, ValidationStringency.DEFAULT_STRINGENCY, DefaultSAMRecordFactory.getInstance());

    /** Creates a copy of the default {@link SamReaderFactory}. */
    public static SamReaderFactory makeDefault() {
        return SamReaderFactoryImpl.copyOf(DEFAULT);
    }

    /**
     * Creates an "empty" factory with no enabled {@link Option}s, {@link ValidationStringency#DEFAULT_STRINGENCY}, and 
     * {@link htsjdk.samtools.DefaultSAMRecordFactory}.
     */
    public static SamReaderFactory make() {
        return new SamReaderFactoryImpl(EnumSet.noneOf(Option.class), ValidationStringency.DEFAULT_STRINGENCY, DefaultSAMRecordFactory.getInstance());
    }

    private static class SamReaderFactoryImpl extends SamReaderFactory {
        private final EnumSet<Option> enabledOptions;
        private ValidationStringency validationStringency;
        private SAMRecordFactory samRecordFactory;

        private SamReaderFactoryImpl(final EnumSet<Option> enabledOptions, final ValidationStringency validationStringency, final SAMRecordFactory samRecordFactory) {
            this.enabledOptions = EnumSet.copyOf(enabledOptions);
            this.samRecordFactory = samRecordFactory;
            this.validationStringency = validationStringency;
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
        public SamReaderFactory samRecordFactory(final SAMRecordFactory samRecordFactory) {
            this.samRecordFactory = samRecordFactory;
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
        public SamReaderFactory validationStringency(final ValidationStringency validationStringency) {
            this.validationStringency = validationStringency;
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
                                validationStringency,
                                this.samRecordFactory
                        );
                    } else {
                        throw new SAMFormatException("Unrecognized file format: " + data.asUnbufferedSeekableStream());
                    }
                } else {
                    final InputStream bufferedStream =
                            IOUtil.maybeBufferInputStream(
                                    data.asUnbufferedInputStream(),
                                    Math.max(Defaults.BUFFER_SIZE, BlockCompressedStreamConstants.MAX_COMPRESSED_BLOCK_SIZE)
                            );
                    final File sourceFile = data.asFile();
                    final File indexFile = indexMaybe == null ? null : indexMaybe.asFile();
                    if (SamStreams.isBAMFile(bufferedStream)) {
                        if (sourceFile == null || !sourceFile.isFile()) {
                            // Handle case in which file is a named pipe, e.g. /dev/stdin or created by mkfifo
                            primitiveSamReader = new BAMFileReader(bufferedStream, indexFile, false, validationStringency, this.samRecordFactory);
                        } else {
                            bufferedStream.close();
                            primitiveSamReader = new BAMFileReader(sourceFile, indexFile, false, validationStringency, this.samRecordFactory);
                        }
                    } else if (BlockCompressedInputStream.isValidFile(bufferedStream)) {
                        primitiveSamReader = new SAMTextReader(new BlockCompressedInputStream(bufferedStream), validationStringency, this.samRecordFactory);
                    } else if (SamStreams.isGzippedSAMFile(bufferedStream)) {
                        primitiveSamReader = new SAMTextReader(new GZIPInputStream(bufferedStream), validationStringency, this.samRecordFactory);
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
                        new SamReader.PrimitiveSamReaderToSamReaderAdapter(primitiveSamReader);

                for (final Option option : enabledOptions) {
                    option.applyTo(reader);
                }

                return reader;
            } catch (final IOException e) {
                throw new RuntimeIOException(e);
            }
        }

        public static SamReaderFactory copyOf(final SamReaderFactoryImpl target) {
            return new SamReaderFactoryImpl(target.enabledOptions, target.validationStringency, target.samRecordFactory);
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
        };

        public static EnumSet<Option> DEFAULTS = EnumSet.noneOf(Option.class);

        /** Applies this option to the provided reader, if applicable. */
        void applyTo(final SamReader.PrimitiveSamReaderToSamReaderAdapter reader) {
            final SamReader.PrimitiveSamReader underlyingReader = reader.underlyingReader();
            if (underlyingReader instanceof BAMFileReader) {
                applyTo((BAMFileReader) underlyingReader, reader);
            } else if (underlyingReader instanceof SAMTextReader) {
                applyTo((SAMTextReader) underlyingReader, reader);
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
    }
}
