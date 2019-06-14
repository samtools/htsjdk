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
public final class FileExtensions {

    /** extensions for read files and related formats. */
    public static final Set<String> FASTA = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        ".fasta",
        ".fasta.gz",
        ".fa",
        ".fa.gz",
        ".fna",
        ".fna.gz",
        ".txt",
        ".txt.gz"
    )));
        
    public static final String FASTA_INDEX = ".fai";

    /** extensions for alignment files SAM, BAM, CRAM. */
    public static final String SAM = ".sam";
    public static final String BAM = ".bam";
    public static final String BAM_INDEX = ".bai";
    public static final String CRAM = ".cram";
    public static final String CRAM_INDEX = ".crai";
    
    public static final String BED = ".bed";
    public static final String TABIX_INDEX = ".tbi";
    public static final String TRIBBLE_INDEX = ".idx";

    /** extensions for VCF files and related formats. */
    public static final String VCF = ".vcf";
    public static final String VCF_INDEX = TRIBBLE_INDEX;
    public static final String BCF = ".bcf";
    public static final String COMPRESSED_VCF = ".vcf.gz";
    public static final String COMPRESSED_VCF_INDEX = ".tbi";
    public static final List<String> VCF_LIST = Collections.unmodifiableList(Arrays.asList(VCF, COMPRESSED_VCF, BCF));
    public static final String[] VCF_ARRAY = VCF_LIST.toArray(new String[0]);

    public static final String INTERVAL_LIST = ".interval_list";
    public static final String DICT = ".dict";
    public static final String GZI = ".gzi";
    public static final String SBI = ".sbi";
    public static final String CSI = ".csi";

    public static final Set<String> BLOCK_COMPRESSED = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(".gz", ".gzip", ".bgz", ".bgzf")));
}