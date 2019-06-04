/*
 * The MIT License
 *
 * Copyright (c) 2019 The Broad Institute
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
package htsjdk.samtools.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Contains file extension constants for read, alignment, and variant files
 */
public class IOExtensions {

    /** extensions for read files and related formats. */
    public static final Set<String> FASTA_EXTENSIONS = Collections.unmodifiableSet(new HashSet<String>() {{
        add(".fasta");
        add(".fasta.gz");
        add(".fa");
        add(".fa.gz");
        add(".fna");
        add(".fna.gz");
        add(".txt");
        add(".txt.gz");
    }});
    public static final String FASTA_INDEX_EXTENSION = ".fai";

    /** extensions for alignment files SAM, BAM, CRAM. */
    public static final String SAM_FILE_EXTENSION = ".sam";
    public static final String BAM_FILE_EXTENSION = ".bam";
    public static final String CRAM_FILE_EXTENSION = ".cram";
    
    public static final String BED_EXTENSION = ".bed";
    public static final String TABIX_STANDARD_INDEX_EXTENSION = ".tbi";
    public final static String TRIBBLE_STANDARD_INDEX_EXTENSION = ".idx";

    /** extensions for VCF files and related formats. */
    public static final String VCF_FILE_EXTENSION = ".vcf";
    public static final String VCF_INDEX_EXTENSION = TRIBBLE_STANDARD_INDEX_EXTENSION;
    public static final String BCF_FILE_EXTENSION = ".bcf";
    public static final String COMPRESSED_VCF_FILE_EXTENSION = ".vcf.gz";
    public static final String COMPRESSED_VCF_INDEX_EXTENSION = ".tbi";
    public static final List<String> VCF_EXTENSIONS_LIST = Collections.unmodifiableList(Arrays.asList(VCF_FILE_EXTENSION, COMPRESSED_VCF_FILE_EXTENSION, BCF_FILE_EXTENSION));

    public static final String INTERVAL_LIST_FILE_EXTENSION = ".interval_list";
    public static final String DICT_FILE_EXTENSION = ".dict";
    public static final String GZI_DEFAULT_EXTENSION = ".gzi";
    public static final String SBI_FILE_EXTENSION = ".sbi";

    public static final Set<String> BLOCK_COMPRESSED_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(".gz", ".gzip", ".bgz", ".bgzf")));
}