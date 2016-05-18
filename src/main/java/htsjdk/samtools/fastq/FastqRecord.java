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

import java.io.Serializable;

/**
 * Represents a fastq record, fairly literally, i.e. without any conversion.
 */
public class FastqRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String seqHeaderPrefix;
    private final String seqLine;
    private final String qualHeaderPrefix;
    private final String qualLine;

    public FastqRecord(final String seqHeaderPrefix, final String seqLine, final String qualHeaderPrefix, final String qualLine) {
        if (seqHeaderPrefix != null && !seqHeaderPrefix.isEmpty()) this.seqHeaderPrefix = seqHeaderPrefix;
        else this.seqHeaderPrefix = null;
        if (qualHeaderPrefix != null && !qualHeaderPrefix.isEmpty()) this.qualHeaderPrefix = qualHeaderPrefix;
        else this.qualHeaderPrefix = null;
        this.seqLine = seqLine ;
        this.qualLine = qualLine ;
    }
    
    /** copy constructor */
    public FastqRecord(final FastqRecord other) {
        if( other == null ) throw new IllegalArgumentException("new FastqRecord(null)");
        this.seqHeaderPrefix = other.seqHeaderPrefix;
        this.seqLine = other.seqLine;
        this.qualHeaderPrefix = other.qualHeaderPrefix;
        this.qualLine = other.qualLine;
    }

    /** @return the read name */
    public String getReadHeader() { return seqHeaderPrefix; }
    /** @return the read DNA sequence */
    public String getReadString() { return seqLine; }
    /** @return the quality header */
    public String getBaseQualityHeader() { return qualHeaderPrefix; }
    /** @return the quality string */
    public String getBaseQualityString() { return qualLine; }
    /** shortcut to getReadString().length() */
    public int length() { return this.seqLine==null?0:this.seqLine.length();}
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime
                * result
                + ((qualHeaderPrefix == null) ? 0 : qualHeaderPrefix.hashCode());
        result = prime * result
                + ((qualLine == null) ? 0 : qualLine.hashCode());
        result = prime * result
                + ((seqHeaderPrefix == null) ? 0 : seqHeaderPrefix.hashCode());
        result = prime * result + ((seqLine == null) ? 0 : seqLine.hashCode());
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
        if (seqLine == null) {
            if (other.seqLine != null)
                return false;
        } else if (!seqLine.equals(other.seqLine))
            return false;
        if (qualHeaderPrefix == null) {
            if (other.qualHeaderPrefix != null)
                return false;
        } else if (!qualHeaderPrefix.equals(other.qualHeaderPrefix))
            return false;
        if (qualLine == null) {
            if (other.qualLine != null)
                return false;
        } else if (!qualLine.equals(other.qualLine))
            return false;
        if (seqHeaderPrefix == null) {
            if (other.seqHeaderPrefix != null)
                return false;
        } else if (!seqHeaderPrefix.equals(other.seqHeaderPrefix))
            return false;
        
        return true;
    }
    
    @Override
    public String toString() {
        return new StringBuilder().
                append(FastqConstants.SEQUENCE_HEADER).append(this.seqHeaderPrefix==null?"":this.seqHeaderPrefix).append('\n').
                append(this.seqLine==null?"":this.seqLine).append('\n').
                append(FastqConstants.QUALITY_HEADER).append(this.qualHeaderPrefix==null?"":this.qualHeaderPrefix).append('\n').
                append(this.qualLine==null?"":this.qualLine).
                toString();
        }
}
