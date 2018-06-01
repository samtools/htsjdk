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

import htsjdk.samtools.SAMException;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.GZIIndex;
import htsjdk.samtools.util.IOUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Factory class for creating ReferenceSequenceFile instances for reading reference
 * sequences store in various formats.
 *
 * @author Tim Fennell
 */
public class ReferenceSequenceFileFactory {
    public static final Set<String> FASTA_EXTENSIONS = new HashSet<String>() {{
        add(".fasta");
        add(".fasta.gz");
        add(".fa");
        add(".fa.gz");
        add(".fna");
        add(".fna.gz");
        add(".txt");
        add(".txt.gz");
    }};

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
        return getReferenceSequenceFile(file.toPath(), truncateNamesAtWhitespace, preferIndexed);
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
        return getReferenceSequenceFile(path, truncateNamesAtWhitespace, true);
    }

    /**
     * Attempts to determine the type of the reference file and return an instance
     * of ReferenceSequenceFile that is appropriate to read it.
     *
     * @param path the reference sequence file path
     * @param truncateNamesAtWhitespace if true, only include the first word of the sequence name
     * @param preferIndexed if true attempt to return an indexed reader that supports non-linear traversal, else return the non-indexed reader
     */
    public static ReferenceSequenceFile getReferenceSequenceFile(final Path path, final boolean truncateNamesAtWhitespace, final boolean preferIndexed) {
        // this should thrown an exception if the fasta file is not supported
        getFastaExtension(path);
        // Using faidx requires truncateNamesAtWhitespace
        if (truncateNamesAtWhitespace && preferIndexed && canCreateIndexedFastaReader(path)) {
            try {
                return IOUtil.isBlockCompressed(path, true) ? new BlockCompressedIndexedFastaSequenceFile(path) : new IndexedFastaSequenceFile(path);
            } catch (final IOException e) {
                throw new SAMException("Error opening FASTA: " + path, e);
            }
        } else {
            return new FastaSequenceFile(path, truncateNamesAtWhitespace);
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
     * Returns the default dictionary name for a FASTA file.
     *
     * @param file the reference sequence file on disk.
     */
    public static File getDefaultDictionaryForReferenceSequence(final File file) {
        return getDefaultDictionaryForReferenceSequence(file.toPath()).toFile();
    }

    /**
     * Returns the default dictionary name for a FASTA file.
     *
     * @param path the reference sequence file path.
     */
    public static Path getDefaultDictionaryForReferenceSequence(final Path path) {
        final String name = path.getFileName().toString();
        final int extensionIndex = name.length() - getFastaExtension(path).length();
        return path.resolveSibling(name.substring(0, extensionIndex) + IOUtil.DICT_FILE_EXTENSION);
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
        return FASTA_EXTENSIONS.stream().filter(name::endsWith).findFirst()
                .orElseGet(() -> {throw new IllegalArgumentException("File is not a supported reference file type: " + path.toAbsolutePath());});
    }

    /**
     * Returns the index name for a FASTA file.
     *
     * @param fastaFile the reference sequence file path.
     */
    public static Path getFastaIndexFileName(Path fastaFile) {
        return fastaFile.resolveSibling(fastaFile.getFileName() + ".fai");
    }

}
