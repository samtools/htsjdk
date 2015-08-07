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

import htsjdk.samtools.util.hdfs.FileHadoop;

import java.io.File;
import java.io.FileNotFoundException;
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
        final String name = file.getName();
        for (final String ext : FASTA_EXTENSIONS) {
            if (name.endsWith(ext)) {
                // Using faidx requires truncateNamesAtWhitespace
                if (truncateNamesAtWhitespace && preferIndexed && IndexedFastaSequenceFile.canCreateIndexedFastaReader(file)) {
                    try {
                    	if (file instanceof FileHadoop) {
					return new IndexedFastaSequenceFileHadoop(file);
				} else {
					 return new IndexedFastaSequenceFile(file);
				}
                    } catch (FileNotFoundException e) {
                        throw new IllegalStateException("Should never happen, because existence of files has been checked.", e);
                    }
                }
                else {
                    return new FastaSequenceFile(file, truncateNamesAtWhitespace);
                }
            }
        }

        throw new IllegalArgumentException("File is not a supported reference file type: " + file.getAbsolutePath());
    }
}
