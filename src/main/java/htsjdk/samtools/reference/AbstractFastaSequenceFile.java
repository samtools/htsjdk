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
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMTextHeaderCodec;
import htsjdk.samtools.util.BufferedLineReader;
import htsjdk.samtools.util.IOUtil;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Provide core sequence dictionary functionality required by all fasta file readers.
 * @author Matt Hanna
 */
abstract class AbstractFastaSequenceFile implements ReferenceSequenceFile {
    private final Path path;
    protected SAMSequenceDictionary sequenceDictionary;

    /**
     * Finds and loads the sequence file dictionary.
     * @param file Fasta file to read.  Also acts as a prefix for supporting files.
     */
    AbstractFastaSequenceFile(final File file) {
        this(file == null ? null : file.toPath());
    }

    /**
     * Finds and loads the sequence file dictionary.
     * @param path Fasta file to read.  Also acts as a prefix for supporting files.
     */
    AbstractFastaSequenceFile(final Path path) {
        this.path = path;
        final Path dictionary = findSequenceDictionary(path);

        if (dictionary != null) {
            IOUtil.assertFileIsReadable(dictionary);

            try {
                final SAMTextHeaderCodec codec = new SAMTextHeaderCodec();
                final BufferedLineReader reader = new BufferedLineReader(Files.newInputStream(dictionary));
                final SAMFileHeader header = codec.decode(reader,
                        dictionary.toString());
                if (header.getSequenceDictionary() != null && !header.getSequenceDictionary().isEmpty()) {
                    this.sequenceDictionary = header.getSequenceDictionary();
                }
                reader.close();
            }
            catch (Exception e) {
                throw new SAMException("Could not open sequence dictionary file: " + dictionary, e);
            }
        }
    }

    protected static File findSequenceDictionary(final File file) {
        if (file == null) {
            return null;
        }
        Path dictionary = findSequenceDictionary(file.toPath());
        if (dictionary == null) {
            return null;
        }
        return dictionary.toFile();
    }

    protected static Path findSequenceDictionary(final Path path) {
        if (path == null) {
            return null;
        }
        // Try and locate the dictionary
        Path dictionary = path.toAbsolutePath();
        Path dictionaryExt = path.toAbsolutePath();
        boolean fileTypeSupported = false;
        for (final String extension : ReferenceSequenceFileFactory.FASTA_EXTENSIONS) {
            String filename = dictionary.getFileName().toString();
            if (filename.endsWith(extension)) {
                dictionaryExt = dictionary.resolveSibling(filename + IOUtil
                    .DICT_FILE_EXTENSION);
                String filenameNoExt = filename.substring(0, filename.lastIndexOf(extension));
                dictionary = dictionary.resolveSibling(filenameNoExt+ IOUtil.DICT_FILE_EXTENSION);
                fileTypeSupported = true;
                break;
            }
        }
        if (!fileTypeSupported)
            throw new IllegalArgumentException("File is not a supported reference file type: " + path.toAbsolutePath());

        if (Files.exists(dictionary))
            return dictionary;
        // try without removing the file extension
        if (Files.exists(dictionaryExt))
            return dictionaryExt;
        else return null;
    }

    /** Returns the path to the reference file. */
    protected Path getPath() {
        return path;
    }

    /**
     * Returns the list of sequence records associated with the reference sequence if found
     * otherwise null.
     */
    public SAMSequenceDictionary getSequenceDictionary() {
        return this.sequenceDictionary;
    }

    /** Returns the full path to the reference file. */
    protected String getAbsolutePath() {
        return path.toAbsolutePath().toString();
    }

    /** Returns the full path to the reference file. */
    public String toString() {
        return getAbsolutePath();
    }

    /** default implementation -- override if index is supported */
    public boolean isIndexed() {return false;}

    /** default implementation -- override if index is supported */
    public ReferenceSequence getSequence( String contig ) {
        throw new UnsupportedOperationException();
    }

    /** default implementation -- override if index is supported */
    public ReferenceSequence getSubsequenceAt( String contig, long start, long stop ) {
        throw new UnsupportedOperationException("Index does not appear to exist for " + getAbsolutePath() + ".  samtools faidx can be used to create an index");
    }

}
