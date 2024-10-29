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

package htsjdk.samtools.reference;

import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleJSON;
import htsjdk.beta.io.bundle.BundleResource;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.plugin.IOUtils;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.SAMException;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.util.GZIIndex;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMTextHeaderCodec;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.BufferedLineReader;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.utils.ValidationUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Factory class for creating ReferenceSequenceFile instances for reading reference
 * sequences store in various formats.
 *
 * @author Tim Fennell
 */
public class ReferenceSequenceFileFactory {

    /**
     * @deprecated since June 2019 Use {@link FileExtensions#FASTA} instead.
     */
    @Deprecated
    public static final Set<String> FASTA_EXTENSIONS = FileExtensions.FASTA;

    /**
     * @deprecated since June 2019 Use {@link FileExtensions#FASTA_INDEX} instead.
     */
    @Deprecated
    public static final String FASTA_INDEX_EXTENSION = FileExtensions.FASTA_INDEX;

    /**
     * Attempts to determine the type of the reference file and return an instance
     * of ReferenceSequenceFile that is appropriate to read it.  Sequence names
     * will be truncated at first whitespace, if any.
     *
     * @param file the reference sequence file on disk
     */
    public static ReferenceSequenceFile getReferenceSequenceFile(final File file) {
        return getReferenceSequenceFile(file, true);
    }

    /**
     * Attempts to determine the type of the reference file and return an instance
     * of ReferenceSequenceFile that is appropriate to read it.
     *
     * @param file the reference sequence file on disk
     * @param truncateNamesAtWhitespace if true, only include the first word of the sequence name
     */
    public static ReferenceSequenceFile getReferenceSequenceFile(final File file, final boolean truncateNamesAtWhitespace) {
        return getReferenceSequenceFile(file, truncateNamesAtWhitespace, true);
    }

    /**
     * Attempts to determine the type of the reference file and return an instance
     * of ReferenceSequenceFile that is appropriate to read it.
     *
     * @param file the reference sequence file on disk
     * @param truncateNamesAtWhitespace if true, only include the first word of the sequence name
     * @param preferIndexed if true attempt to return an indexed reader that supports non-linear traversal, else return the non-indexed reader
     */
    public static ReferenceSequenceFile getReferenceSequenceFile(final File file, final boolean truncateNamesAtWhitespace, final boolean preferIndexed) {
        return getReferenceSequenceFile(IOUtil.toPath(file), HtsPath::new, truncateNamesAtWhitespace, preferIndexed);
    }

    /**
     * Attempts to determine the type of the reference file and return an instance
     * of ReferenceSequenceFile that is appropriate to read it.  Sequence names
     * will be truncated at first whitespace, if any.
     *
     * @param path the reference sequence file on disk
     */
    public static ReferenceSequenceFile getReferenceSequenceFile(final Path path) {
        return getReferenceSequenceFile(path, true);
    }

    /**
     * Attempts to determine the type of the reference file and return an instance
     * of ReferenceSequenceFile that is appropriate to read it.
     *
     * @param path the reference sequence file on disk
     * @param truncateNamesAtWhitespace if true, only include the first word of the sequence name
     */
    public static ReferenceSequenceFile getReferenceSequenceFile(final Path path, final boolean truncateNamesAtWhitespace) {
        return getReferenceSequenceFile(path, HtsPath::new, truncateNamesAtWhitespace, true);
    }

    /**
     * Attempts to determine the type of the reference file and return an instance
     * of ReferenceSequenceFile that is appropriate to read it. If the file represents
     * a Bundle file, the reference sequence file is created from the bundle.
     *
     * @param <T> the IOPath-derived type to use for IOPathResources
     * @param path the reference sequence file path
     * @param ioPathConstructor a function that takes a string and returns an IOPath-derived class of type <T>
     * @return a newly created {@link Bundle}
     * @param truncateNamesAtWhitespace if true, only include the first word of the sequence name
     * @param preferIndexed if true attempt to return an indexed reader that supports non-linear traversal, else return the non-indexed reader
     */
    public static <T extends IOPath> ReferenceSequenceFile getReferenceSequenceFile(
            final Path path,
            final Function<String, T> ioPathConstructor,
            final boolean truncateNamesAtWhitespace,
            final boolean preferIndexed) {
        final IOPath refIOPath = ioPathConstructor.apply(path.toUri().toString());
        if (refIOPath.hasExtension(BundleJSON.BUNDLE_EXTENSION)) {
            final Bundle referenceBundle = BundleJSON.toBundle(IOUtils.getStringFromPath(refIOPath), ioPathConstructor);
            return getReferenceSequenceFileFromBundle(referenceBundle, truncateNamesAtWhitespace, preferIndexed);
        }
        else {
            // this should throw an exception if the fasta file is not supported
            getFastaExtension(path);
            // Using faidx requires truncateNamesAtWhitespace
            if (truncateNamesAtWhitespace && preferIndexed && canCreateIndexedFastaReader(path)) {
                try {
                    return IOUtil.isBlockCompressed(path, true) ?
                            new BlockCompressedIndexedFastaSequenceFile(path) :
                            new IndexedFastaSequenceFile(path);
                } catch (final IOException e) {
                    throw new SAMException("Error opening FASTA: " + path, e);
                }
            } else {
                return new FastaSequenceFile(path, truncateNamesAtWhitespace);
            }
        }
    }

    /**
     * Attempts to determine the type of the reference file specified in the bundle, and return an instance
     * of ReferenceSequenceFile that is appropriate to use to read the reference.
     *
     * @param referenceBundle a Bundle containing resources for the reference file, index, and dictionary
     * @param truncateNamesAtWhitespace if true, only include the first word of the sequence name
     * @param preferIndexed if true attempt to return an indexed reader that supports non-linear traversal, else return the non-indexed reader
     */
    public static ReferenceSequenceFile getReferenceSequenceFileFromBundle(
            final Bundle referenceBundle,
            final boolean truncateNamesAtWhitespace,
            final boolean preferIndexed) {
        ValidationUtils.nonNull(referenceBundle, "reference bundle");

        // required fasta path
        final BundleResource fastaResource = referenceBundle.getOrThrow(BundleResourceType.CT_HAPLOID_REFERENCE);
        final IOPath fastaPath = fastaResource.getIOPath().orElseThrow(
                () -> new RuntimeException("The fasta bundle resource must contain a fasta resource that is backed by an IOPath."));
        if (!Files.exists(fastaPath.toPath())) {
            throw new RuntimeException(String.format("FASTA file %s does not exist", fastaPath));
        }

        // optional dictionary path
        IOPath dictPath = null;
        final Optional<BundleResource> dictPathResource = referenceBundle.get(BundleResourceType.CT_REFERENCE_DICTIONARY);
        if (dictPathResource.isPresent()) {
            final BundleResource dictResource = dictPathResource.get();
            final Optional<IOPath> optDictPath = dictResource.getIOPath();
            if (optDictPath.isPresent()) {
                dictPath = optDictPath.get();
                if (!Files.exists(dictPath.toPath())) {
                    throw new RuntimeException(String.format("Sequence dictionary file %s does not exist", dictPath));
                }
            }
        }

        // optional index. Using faidx requires truncateNamesAtWhitespace
        IOPath indexPath = null;
        IOPath gziIndexPath = null;
        if (preferIndexed) {
            if (!truncateNamesAtWhitespace) {
                throw new RuntimeException("preferIndexed option requires truncateNamesAtWhitespace");
            }
            final Optional<BundleResource> indexPathResource = referenceBundle.get(BundleResourceType.CT_REFERENCE_INDEX);
            if (indexPathResource.isPresent()) {
                final BundleResource indexResource = indexPathResource.get();
                final Optional<IOPath> optIndexIOPath = indexResource.getIOPath();
                if (optIndexIOPath.isPresent()) {
                    indexPath = optIndexIOPath.get();
                    if (preferIndexed && !Files.exists(indexPath.toPath())) {
                        throw new RuntimeException(String.format("FASTA index file %s does not exist", indexPath));
                    }
                }
            }

            final Optional<BundleResource> gziIndexPathResource = referenceBundle.get(BundleResourceType.CT_REFERENCE_INDEX_GZI);
            if (gziIndexPathResource.isPresent()) {
                final BundleResource gziIndexResource = gziIndexPathResource.get();
                final Optional<IOPath> optGziIndexPath = gziIndexResource.getIOPath();
                if (optGziIndexPath.isPresent()) {
                    gziIndexPath = optGziIndexPath.get();
                    if (!Files.exists(gziIndexPath.toPath())) {
                        throw new RuntimeException(String.format("GZI index file %s does not exist", gziIndexPath));
                    }
                }
            }
        }

        try {
            if (IOUtil.isBlockCompressed(fastaPath.toPath(), true) && preferIndexed && indexPath != null && gziIndexPath != null) {
                return new BlockCompressedIndexedFastaSequenceFile(
                        fastaPath,
                        dictPath,
                        new FastaSequenceIndex(indexPath.toPath()),
                        GZIIndex.loadIndex(gziIndexPath.toPath()));
            } else if (preferIndexed && indexPath != null) {
                return new IndexedFastaSequenceFile(fastaPath, dictPath, new FastaSequenceIndex(indexPath.toPath()));
            } else {
                return new FastaSequenceFile(fastaPath, dictPath, truncateNamesAtWhitespace);
            }
        } catch (final IOException e) {
            throw new SAMException("Error opening FASTA: " + fastaPath, e);
        }
    }

    /**
     * Checks if the provided FASTA file can be open as indexed.
     *
     * <p>For a FASTA file to be indexed, it requires to have:
     * <ul>
     *     <li>Associated .fai index ({@link FastaSequenceIndex}).</li>
     *     <li>Associated .gzi index if it is block-compressed ({@link GZIIndex}).</li>
     * </ul>
     *
     * @param fastaFile the reference sequence file path.
     * @return {@code true} if the file can be open as indexed; {@code false} otherwise.
     */
    public static boolean canCreateIndexedFastaReader(final Path fastaFile) {
        // this should thrown an exception if the fasta file is not supported
        getFastaExtension(fastaFile);

        // both the FASTA file should exists and the .fai index should exist
        if (Files.exists(fastaFile) && Files.exists(getFastaIndexFileName(fastaFile))) {
            // open the file for checking for block-compressed input
            try {
                // if it is bgzip, it requires the .gzi index
                return !IOUtil.isBlockCompressed(fastaFile, true) ||
                        Files.exists(GZIIndex.resolveIndexNameForBgzipFile(fastaFile));
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * Return an instance of ReferenceSequenceFile using the given fasta sequence file stream, optional index stream,
     * and no sequence dictionary
     *
     * @param source The named source of the reference file (used in error messages).
     * @param in The input stream to read the fasta file from.
     * @param index The index, or null to return a non-indexed reader.
     */
    public static ReferenceSequenceFile getReferenceSequenceFile(final String source, final SeekableStream in, final FastaSequenceIndex index) {
        return getReferenceSequenceFile(source, in, index, null, true);
    }

    /**
     * Return an instance of ReferenceSequenceFile using the given fasta sequence file stream and optional index stream
     * and sequence dictionary.
     *
     * @param source The named source of the reference file (used in error messages).
     * @param in The input stream to read the fasta file from.
     * @param index The index, or null to return a non-indexed reader.
     * @param dictionary The sequence dictionary, or null if there isn't one.
     * @param truncateNamesAtWhitespace if true, only include the first word of the sequence name
     */
    public static ReferenceSequenceFile getReferenceSequenceFile(final String source, final SeekableStream in, final FastaSequenceIndex index, final SAMSequenceDictionary dictionary, final boolean truncateNamesAtWhitespace) {
        if (truncateNamesAtWhitespace && index != null) {
            return new IndexedFastaSequenceFile(source, in, index, dictionary);
        }
        return new FastaSequenceFile(source, in, dictionary, truncateNamesAtWhitespace);
    }

    /**
     * Returns the default dictionary name for a FASTA file.
     *
     * @param file the reference sequence file on disk.
     */
    public static File getDefaultDictionaryForReferenceSequence(final File file) {
        return getDefaultDictionaryForReferenceSequence(IOUtil.toPath(file)).toFile();
    }

    /**
     * Returns the default dictionary name for a FASTA file.
     *
     * @param path the reference sequence file path.
     */
    public static Path getDefaultDictionaryForReferenceSequence(final Path path) {
        final String name = path.getFileName().toString();
        final int extensionIndex = name.length() - getFastaExtension(path).length();
        return path.resolveSibling(name.substring(0, extensionIndex) + FileExtensions.DICT);
    }

    /**
     * Loads the sequence dictionary from a FASTA file input stream.
     *
     * @param in the FASTA file input stream.
     * @return the sequence dictionary, or <code>null</code> if the header has no dictionary or it was empty.
     */
    public static SAMSequenceDictionary loadDictionary(final InputStream in) {
        final SAMTextHeaderCodec codec = new SAMTextHeaderCodec();
        final BufferedLineReader reader = new BufferedLineReader(in);
        final SAMFileHeader header = codec.decode(reader, null);
        if (header.getSequenceDictionary().isEmpty()) {
            return null;
        }
        return header.getSequenceDictionary();
    }

    /**
     * Returns the FASTA extension for the path.
     *
     * @param path the reference sequence file path.
     *
     * @throws IllegalArgumentException if the file is not a supported reference file.
     */
    public static String getFastaExtension(final Path path) {
        final String name = path.getFileName().toString();
        return FileExtensions.FASTA.stream().filter(name::endsWith).findFirst()
                .orElseGet(() -> {throw new IllegalArgumentException("File is not a supported reference file type: " + path.toAbsolutePath());});
    }

    /**
     * Returns the index name for a FASTA file.
     *
     * @param fastaFile the reference sequence file path.
     */
    public static Path getFastaIndexFileName(Path fastaFile) {
        return fastaFile.resolveSibling(fastaFile.getFileName() + FileExtensions.FASTA_INDEX);
    }
}
