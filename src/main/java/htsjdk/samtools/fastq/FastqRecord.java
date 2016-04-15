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
package htsjdk.samtools.fastq;

import htsjdk.samtools.SAMUtils;
import htsjdk.samtools.util.StringUtil;

import java.io.Serializable;

/**
 * Simple representation of a FASTQ record, without any conversion
 */
public class FastqRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String readName;
    private final String readString;
    private final String qualityHeader;
    private final String baseQualityString;

    /**
     * Default constructor
     *
     * @param readName      the read name (without {@link FastqConstants#SEQUENCE_HEADER})
     * @param readBases     the read sequence bases
     * @param qualityHeader the quality header (without {@link FastqConstants#SEQUENCE_HEADER})
     * @param baseQualities the base quality scores
     */
    public FastqRecord(final String readName, final String readBases, final String qualityHeader, final String baseQualities) {
        if (readName != null && !readName.isEmpty()) {
            this.readName = readName;
        } else {
            this.readName = null;
        }
        if (qualityHeader != null && !qualityHeader.isEmpty()) {
            this.qualityHeader = qualityHeader;
        } else {
            this.qualityHeader = null;
        }
        this.readString = readBases;
        this.baseQualityString = baseQualities;
    }

    /**
     * Constructor for byte[] arrays
     *
     * @param readName      the read name (without {@link FastqConstants#SEQUENCE_HEADER})
     * @param readBases     the read sequence bases as ASCII bytes ACGTN=.
     * @param qualityHeader the quality header (without {@link FastqConstants#SEQUENCE_HEADER})
     * @param baseQualities the base qualities as binary PHRED scores (not ASCII)
     */
    public FastqRecord(final String readName, final byte[] readBases, final String qualityHeader, final byte[] baseQualities) {
        this(readName, StringUtil.bytesToString(readBases), qualityHeader, SAMUtils.phredToFastq(baseQualities));
    }

    /**
     * Copy constructor
     *
     * @param other record to copy
     */
    public FastqRecord(final FastqRecord other) {
        if (other == null) {
            throw new IllegalArgumentException("new FastqRecord(null)");
        }
        this.readName = other.readName;
        this.readString = other.readString;
        this.qualityHeader = other.qualityHeader;
        this.baseQualityString = other.baseQualityString;
    }

    /**
     * @return the read name
     * @deprecated use {@link #getReadName()} instead
     */
    @Deprecated
    public String getReadHeader() {
        return getReadName();
    }

    /**
     * Get the read name
     *
     * @return the read name
     */
    public String getReadName() {
        return readName;
    }

    /**
     * Get the DNA sequence
     *
     * @return read sequence as a string of ACGTN=.
     */
    public String getReadString() {
        return readString;
    }

    /**
     * Get the DNA sequence.
     *
     * @return read sequence as ASCII bytes ACGTN=.
     */
    public byte[] getReadBases() {
        return StringUtil.stringToBytes(readString);
    }

    /**
     * Get the base qualities encoded as a FASTQ string
     *
     * @return the quality string
     */
    public String getBaseQualityString() {
        return baseQualityString;
    }

    /**
     * Get the base qualities as binary PHRED scores (not ASCII)
     *
     * @return the base quality
     */
    public byte[] getBaseQualities() {
        return SAMUtils.fastqToPhred(baseQualityString);
    }

    /**
     * Get the read length
     *
     * @return number of bases in the read
     */
    public int getReadLength() {
        return (readString == null) ? 0 : readString.length();
    }

    /**
     * Get the base quality header
     *
     * @return the base quality header
     */
    public String getBaseQualityHeader() {
        return qualityHeader;
    }

    /**
     * shortcut to getReadString().length()
     *
     * @deprecated use {@link #getReadLength()} instead
     */
    @Deprecated
    public int length() {
        return getReadLength();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime
                * result
                + ((qualityHeader == null) ? 0 : qualityHeader.hashCode());
        result = prime * result
                + ((baseQualityString == null) ? 0 : baseQualityString.hashCode());
        result = prime * result
                + ((readName == null) ? 0 : readName.hashCode());
        result = prime * result + ((readString == null) ? 0 : readString.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FastqRecord other = (FastqRecord) obj;
        if (readString == null) {
            if (other.readString != null)
                return false;
        } else if (!readString.equals(other.readString))
            return false;
        if (qualityHeader == null) {
            if (other.qualityHeader != null)
                return false;
        } else if (!qualityHeader.equals(other.qualityHeader))
            return false;
        if (baseQualityString == null) {
            if (other.baseQualityString != null)
                return false;
        } else if (!baseQualityString.equals(other.baseQualityString))
            return false;
        if (readName == null) {
            if (other.readName != null)
                return false;
        } else if (!readName.equals(other.readName))
            return false;

        return true;
    }

    /** Simple toString() that gives a read name and length */
    @Override
    public String toString() {
        return String.format("%s: %s bp", readName, getReadLength());
    }
}
