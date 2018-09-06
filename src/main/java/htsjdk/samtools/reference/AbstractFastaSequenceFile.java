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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Provide core sequence dictionary functionality required by all fasta file readers.
 * @author Matt Hanna
 */
abstract class AbstractFastaSequenceFile implements ReferenceSequenceFile {
    private final Path path;
    private final String source;
    protected SAMSequenceDictionary sequenceDictionary;

    /**
     * Finds and loads the sequence file dictionary.
     * @param file Fasta file to read.  Also acts as a prefix for supporting files.
     */
    AbstractFastaSequenceFile(final File file) {
        this(IOUtil.toPath(file));
    }

    /**
     * Finds and loads the sequence file dictionary.
     * @param path Fasta file to read.  Also acts as a prefix for supporting files.
     */
    AbstractFastaSequenceFile(final Path path) {
        this.path = path;
        this.source = path == null ? "unknown" : path.toAbsolutePath().toString();
        final Path dictionary = findSequenceDictionary(path);

        if (dictionary != null) {
            IOUtil.assertFileIsReadable(dictionary);
            try (InputStream dictionaryIn = Files.newInputStream(dictionary)) {
                this.sequenceDictionary = ReferenceSequenceFileFactory.loadDictionary(dictionaryIn);
            }
            catch (Exception e) {
                throw new SAMException("Could not open sequence dictionary file: " + dictionary, e);
            }
        }
    }

    /**
     * Constructs an {@link AbstractFastaSequenceFile} with an optional sequence dictionary.
     * @param path Fasta file to read.  Also acts as a prefix for supporting files.
     * @param source Named source used for error messages.
     * @param sequenceDictionary The sequence dictionary, or null if there isn't one.
     */
    AbstractFastaSequenceFile(final Path path, final String source, final SAMSequenceDictionary sequenceDictionary) {
        this.path = path;
        this.source = source;
        this.sequenceDictionary = sequenceDictionary;
    }

    protected static File findSequenceDictionary(final File file) {
        final Path dictionary = findSequenceDictionary(IOUtil.toPath(file));
        if (dictionary == null) {
            return null;
        }
        return dictionary.toFile();
    }

    protected static Path findSequenceDictionary(final Path path) {
        if (path == null) {
            return null;
        }
        // Try and locate the dictionary with the default method
        final Path dictionary = ReferenceSequenceFileFactory.getDefaultDictionaryForReferenceSequence(path); path.toAbsolutePath();
        if (Files.exists(dictionary)) {
            return dictionary;
        }
        // try without removing the file extension
        final Path dictionaryExt = path.resolveSibling(path.getFileName().toString() + IOUtil.DICT_FILE_EXTENSION);
        if (Files.exists(dictionaryExt)) {
            return dictionaryExt;
        }
        else return null;
    }

    /** Returns the path to the reference file. */
    protected Path getPath() {
        return path;
    }

    /** Returns the named source of the reference file. */
    protected String getSource() {
        return source;
    }

    /**
     * Returns the list of sequence records associated with the reference sequence if found
     * otherwise null.
     */
    @Override
    public SAMSequenceDictionary getSequenceDictionary() {
        return this.sequenceDictionary;
    }

    /** Returns the full path to the reference file. */
    protected String getAbsolutePath() {
        if (path == null) {
            return null;
        }
        return path.toAbsolutePath().toString();
    }

    /** Returns the full path to the reference file, or the source if no path was specified. */
    public String toString() {
        return source;
    }

    /** default implementation -- override if index is supported */
    @Override
    public boolean isIndexed() {return false;}

    /** default implementation -- override if index is supported */
    @Override
    public ReferenceSequence getSequence( String contig ) {
        throw new UnsupportedOperationException("Index does not appear to exist for " + getSource() + ".  samtools faidx can be used to create an index");
    }

    /** default implementation -- override if index is supported */
    @Override
    public ReferenceSequence getSubsequenceAt( String contig, long start, long stop ) {
        throw new UnsupportedOperationException("Index does not appear to exist for " + getSource() + ".  samtools faidx can be used to create an index");
    }

}
