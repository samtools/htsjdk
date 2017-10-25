/*
 * The MIT License
 *
 * Copyright (c) 2014 The Broad Institute
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

package htsjdk.variant.utils;

import htsjdk.samtools.BamFileIoUtils;
import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMTextHeaderCodec;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.reference.ReferenceSequenceFileFactory;
import htsjdk.samtools.util.BufferedLineReader;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.CollectionUtil;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.IntervalList;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFPathReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

/**
 * Tiny class for automatically loading a SAMSequenceDictionary given a file
 * @author farjoun on 2/25/2014
 */
public class SAMSequenceDictionaryExtractor {

    enum TYPE {
        FASTA(ReferenceSequenceFileFactory.FASTA_EXTENSIONS) {
            @Override
            SAMSequenceDictionary extractDictionary(final File reference) {
                final SAMSequenceDictionary dict = ReferenceSequenceFileFactory.getReferenceSequenceFile(reference).getSequenceDictionary();
                if (dict == null)
                    throw new SAMException("Could not find dictionary next to reference file " + reference.getAbsoluteFile());
                return dict;
            }

            @Override
            SAMSequenceDictionary extractDictionary(Path reference) {
                final SAMSequenceDictionary dict = ReferenceSequenceFileFactory.getReferenceSequenceFile(reference).getSequenceDictionary();
                if (dict == null)
                    throw new SAMException("Could not find dictionary next to reference file " + reference.toUri().toString());
                return dict;
            }
        },
        DICTIONARY(IOUtil.DICT_FILE_EXTENSION) {
            @Override
            SAMSequenceDictionary extractDictionary(final File dictionary) {
                BufferedLineReader bufferedLineReader = null;
                try {
                    bufferedLineReader = new BufferedLineReader(new FileInputStream(dictionary));
                    final SAMTextHeaderCodec codec = new SAMTextHeaderCodec();
                    final SAMFileHeader header = codec.decode(bufferedLineReader, dictionary.toString());
                    return header.getSequenceDictionary();
                } catch (final FileNotFoundException e) {
                    throw new SAMException("Could not open sequence dictionary file: " + dictionary, e);
                } finally {
                    CloserUtil.close(bufferedLineReader);
                }
            }

            @Override
            SAMSequenceDictionary extractDictionary(Path dictionary) {
                return extractDictionary(dictionary.toFile());
            }
        },
        SAM(IOUtil.SAM_FILE_EXTENSION, BamFileIoUtils.BAM_FILE_EXTENSION) {
            @Override
            SAMSequenceDictionary extractDictionary(final File sam) {
                return SamReaderFactory.makeDefault().getFileHeader(sam).getSequenceDictionary();
            }

            @Override
            SAMSequenceDictionary extractDictionary(Path sam) {
                return SamReaderFactory.makeDefault().getFileHeader(sam).getSequenceDictionary();
            }
        },
        VCF(IOUtil.VCF_EXTENSIONS) {
            @Override
            SAMSequenceDictionary extractDictionary(final File vcf) {
                VCFFileReader vcfFileReader = null;
                try {
                    vcfFileReader = new VCFFileReader(vcf, false);
                    return vcfFileReader.getFileHeader().getSequenceDictionary();
                } finally {
                    CloserUtil.close(vcfFileReader);
                }
            }

            @Override
            SAMSequenceDictionary extractDictionary(Path vcf) {
                try(VCFPathReader vcfPathReader = new VCFPathReader(vcf, false)){
                    return vcfPathReader.getFileHeader().getSequenceDictionary();
                }
            }

     },
        INTERVAL_LIST(IOUtil.INTERVAL_LIST_FILE_EXTENSION) {
            @Override
            SAMSequenceDictionary extractDictionary(final File intervalList) {
                return IntervalList.fromFile(intervalList).getHeader().getSequenceDictionary();
            }

            @Override
            SAMSequenceDictionary extractDictionary(Path intervalList) {
                return IntervalList.fromPath(intervalList).getHeader().getSequenceDictionary();
            }
        };

        final Collection<String> applicableExtensions;

        TYPE(final String... s) {
            applicableExtensions = CollectionUtil.makeSet(s);
        }

        TYPE(final Collection<String> extensions) {
            applicableExtensions = extensions;
        }

        abstract SAMSequenceDictionary extractDictionary(final File file);
        abstract SAMSequenceDictionary extractDictionary(final Path file);

        static TYPE forFile(final File dictionaryExtractable) {
            for (final TYPE type : TYPE.values()) {
                for (final String s : type.applicableExtensions) {
                    if (dictionaryExtractable.getName().endsWith(s)) {
                        return type;
                    }
                }
            }
            throw new SAMException("Cannot figure out type of file " + dictionaryExtractable.getAbsolutePath() + " from extension. Current implementation understands the following types: " + Arrays.toString(TYPE.values()));
        }

        static TYPE forPath(final Path dictionaryExtractable) {
            for (final TYPE type : TYPE.values()) {
                for (final String s : type.applicableExtensions) {
                    if (dictionaryExtractable.toUri().toString().endsWith(s)) {
                        return type;
                    }
                }
            }
            throw new SAMException("Cannot figure out type of file " + dictionaryExtractable.toUri().toString() + " from extension. Current implementation understands the following types: " + Arrays.toString(TYPE.values()));
        }

        @Override
        public String toString() {
            return super.toString() + ": " + applicableExtensions.toString();
        }
    }

    public static SAMSequenceDictionary extractDictionary(final File file) {
        return TYPE.forFile(file).extractDictionary(file);
    }

    public static SAMSequenceDictionary extractDictionary(final Path path) {
        return TYPE.forPath(path).extractDictionary(path);
    }

}
