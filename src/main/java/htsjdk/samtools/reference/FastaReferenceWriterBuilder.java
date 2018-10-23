/*
 * The MIT License
 *
 * Copyright (c) 2018 The Broad Institute
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

import htsjdk.utils.ValidationUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Buider for a {@link htsjdk.samtools.reference.FastaReferenceWriter}
 * <p>
 * You can set each of the three outputs (fasta, dictionary and index) to a file or a stream.
 * by default if you provide a file to the fasta an accompanying index and dictionary will be created.
 * This behaviour can be controlled by {@link #setMakeDictOutput(boolean)} and {@link #setMakeFaiOutput(boolean)}
 * <p>
 * The default bases-per-line is {@value FastaReferenceWriter#DEFAULT_BASES_PER_LINE}.
 * <p>
 * Setting a file or an output stream for any of the three outputs (fasta, index or dict) will invalidate the other
 * output type (i.e. setting a file output will invalidate a previous stream and vice-versa).
 * </p>
 */
public class FastaReferenceWriterBuilder {
    private Path fastaFile;
    private boolean makeFaiOutput = true;
    private boolean makeDictOutput = true;
    private int basesPerLine = FastaReferenceWriter.DEFAULT_BASES_PER_LINE;
    private Path indexFile;
    private Path dictFile;
    private OutputStream fastaOutput;
    private OutputStream indexOutput;
    private OutputStream dictOutput;

    private static Path defaultFaiFile(final boolean makeFaiFile, final Path fastaFile) {
        return makeFaiFile ? ReferenceSequenceFileFactory.getFastaIndexFileName(fastaFile) : null;
    }

    private static Path defaultDictFile(final boolean makeDictFile, final Path fastaFile) {
        return makeDictFile ? ReferenceSequenceFileFactory.getDefaultDictionaryForReferenceSequence(fastaFile) : null;
    }

    protected static int checkBasesPerLine(final int value) {
        ValidationUtils.validateArg(value > 0, "bases per line must be 1 or greater");
        return value;
    }

    /**
     * Set the output fasta file to write to. Doesn't (currently) support compressed filenames.
     * If the index file and output stream are both null and makeFaiOutput is true (default), a default index file will be created as well.
     * If the dictionary file and output stream are both null and makeDictOutput is true (default), a default dictionary file will be created as well.
     *
     * You can only provide a compressed stream to the fastaOutput, and only in the case that an index isn't written.
     *
     * @param fastaFile a {@link Path} to the output fasta file.
     * @return this builder
     */
    public FastaReferenceWriterBuilder setFastaFile(final Path fastaFile) {
        this.fastaFile = fastaFile;
        this.fastaOutput = null;
        return this;
    }

    /**
     * Sets whether to automatically generate an index file from the name of the fasta-file (assuming it is given
     * as a file). This can only happen if both the index file and output stream are null.
     *
     * @param makeFaiOutput a boolean flag
     * @return this builder
     */
    public FastaReferenceWriterBuilder setMakeFaiOutput(final boolean makeFaiOutput) {
        this.makeFaiOutput = makeFaiOutput;
        return this;
    }

    /**
     * Sets whether to automatically generate an dictionary file from the name of the fasta-file (assuming it is given
     * as a file). This can only happen if both the dictionary file and output stream are null.
     *
     * @param makeDictOutput a boolean flag
     * @return this builder
     */

    public FastaReferenceWriterBuilder setMakeDictOutput(final boolean makeDictOutput) {
        this.makeDictOutput = makeDictOutput;
        return this;
    }

    /**
     * Sets the number of bases each line of the fasta file will have.
     * the default is {@value FastaReferenceWriter#DEFAULT_BASES_PER_LINE}
     *
     * @param basesPerLine integer (must be positive, validated on {@link #build()}) indicating the number of bases per line in
     *                     the output
     * @return this builder
     */
    public FastaReferenceWriterBuilder setBasesPerLine(final int basesPerLine) {
        this.basesPerLine = basesPerLine;
        return this;
    }

    /**
     * Set the output index file to write to. Doesn't (currently) support compressed filenames.
     */
    public FastaReferenceWriterBuilder setIndexFile(final Path indexFile) {
        this.indexFile = indexFile;
        this.indexOutput = null;
        return this;
    }

    /**
     * Set the output dictionary file to write to.
     */
    public FastaReferenceWriterBuilder setDictFile(final Path dictFile) {
        this.dictFile = dictFile;
        this.dictOutput = null;
        return this;
    }

    /**
     * Set the output stream for writing the reference. Doesn't support compressed streams.
     *
     * @param fastaOutput a {@link OutputStream} for the output fasta file.
     * @return this builder
     */

    public FastaReferenceWriterBuilder setFastaOutput(final OutputStream fastaOutput) {
        this.fastaOutput = fastaOutput;
        this.fastaFile = null;
        return this;
    }

    /**
     * Set the output stream for writing the index. Doesn't support compressed streams.
     *
     * @param indexOutput a  {@link OutputStream} for the output index.
     * @return this builder
     */
    public FastaReferenceWriterBuilder setIndexOutput(final OutputStream indexOutput) {
        this.indexOutput = indexOutput;
        this.indexFile = null;
        return this;
    }

    /**
     * Set the output stream for writing the dictionary. Doesn't support compressed streams.
     *
     * @param dictOutput a {@link OutputStream} for the output dictionary.
     * @return this builder
     */
    public FastaReferenceWriterBuilder setDictOutput(final OutputStream dictOutput) {
        this.dictOutput = dictOutput;
        this.dictFile = null;
        return this;
    }

    /**
     * Create the {@link FastaReferenceWriter}. This is were all the validations happen:
     * <ld>
     * <li>
     * -One of fastaFile and fastaOutput must be non-null.
     * </li>
     * <li>
     * -the number of bases-per-line must be positive
     * </li>
     * </ld>
     *
     * @return a {@link FastaReferenceWriter}
     * @throws IOException if trouble opening files
     */
    public FastaReferenceWriter build() throws IOException {
        if (fastaFile == null && fastaOutput == null) {
            throw new IllegalArgumentException("Both fastaFile and fastaOutput were null. Please set one of them to be non-null.");
        }
        if(fastaFile != null) {
            if (indexFile == null && indexOutput == null) {
                indexFile = defaultFaiFile(makeFaiOutput, fastaFile);
            } else if (indexFile != null && indexOutput != null) {
                throw new IllegalArgumentException("Both indexFile and indexOutput were non-null. Please set one of them to be null.");
            }
            if (dictFile == null && dictOutput == null) {
                dictFile = defaultDictFile(makeDictOutput, fastaFile);
            } else if (dictFile != null && dictOutput != null) {
                throw new IllegalArgumentException("Both dictFile and dictOutput were non-null. Please set one of them to be null.");
            }
        }
        // checkout bases-perline first, so that files are not created if failure;
        checkBasesPerLine(basesPerLine);

        if (fastaFile != null) {
            fastaOutput = Files.newOutputStream(fastaFile);
        }
        if (indexFile != null) {
            indexOutput = Files.newOutputStream(indexFile);
        }
        if (dictFile != null) {
            dictOutput = Files.newOutputStream(dictFile);
        }

        return new FastaReferenceWriter(basesPerLine, fastaOutput, indexOutput, dictOutput);
    }
}
