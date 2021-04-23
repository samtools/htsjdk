/*
* Copyright (c) 2012 The Broad Institute
* 
* Permission is hereby granted, free of charge, to any person
* obtaining a copy of this software and associated documentation
* files (the "Software"), to deal in the Software without
* restriction, including without limitation the rights to use,
* copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the
* Software is furnished to do so, subject to the following
* conditions:
* 
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package htsjdk.variant.bcf2;

import htsjdk.samtools.util.FileExtensions;
import htsjdk.tribble.TribbleException;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Common utilities for working with BCF2 files
 * <p>
 * Includes convenience methods for encoding, decoding BCF2 type descriptors (size + type)
 *
 * @author depristo
 * @since 5/12
 */
public final class BCF2Utils {

    public static final int OVERFLOW_ELEMENT_MARKER = 15;
    public static final int MAX_INLINE_ELEMENTS = 14;

    public final static BCF2Type[] INTEGER_TYPES_BY_SIZE = new BCF2Type[]{BCF2Type.INT8, BCF2Type.INT16, BCF2Type.INT32};
    public final static BCF2Type[] ID_TO_ENUM;

    static {
        int maxID = -1;
        for (final BCF2Type v : BCF2Type.values()) maxID = Math.max(v.getID(), maxID);
        ID_TO_ENUM = new BCF2Type[maxID + 1];
        for (final BCF2Type v : BCF2Type.values()) ID_TO_ENUM[v.getID()] = v;
    }

    private BCF2Utils() {
    }

    public static byte encodeTypeDescriptor(final int nElements, final BCF2Type type) {
        return (byte) ((0x0F & nElements) << 4 | (type.getID() & 0x0F));
    }

    public static int decodeSize(final byte typeDescriptor) {
        return (0xF0 & typeDescriptor) >> 4;
    }

    public static int decodeTypeID(final byte typeDescriptor) {
        return typeDescriptor & 0x0F;
    }

    public static BCF2Type decodeType(final byte typeDescriptor) {
        return ID_TO_ENUM[decodeTypeID(typeDescriptor)];
    }

    public static boolean sizeIsOverflow(final byte typeDescriptor) {
        return decodeSize(typeDescriptor) == OVERFLOW_ELEMENT_MARKER;
    }

    /**
     * Returns a good name for a shadow BCF file for vcfFile.
     * <p>
     * foo.vcf =&gt; foo.bcf
     * foo.xxx =&gt; foo.xxx.bcf
     * <p>
     * If the resulting BCF file cannot be written, return null.  Happens
     * when vcfFile = /dev/null for example
     *
     * @param vcfFile
     * @return the BCF
     */
    public static final File shadowBCF(final File vcfFile) {
        final String path = vcfFile.getAbsolutePath();
        if (path.contains(FileExtensions.VCF))
            return new File(path.replace(FileExtensions.VCF, FileExtensions.BCF));
        else {
            final File bcf = new File(path + FileExtensions.BCF);
            if (bcf.canRead())
                return bcf;
            else {
                try {
                    // this is the only way to robustly decide if we could actually write to BCF
                    final FileOutputStream o = new FileOutputStream(bcf);
                    o.close();
                    bcf.delete();
                    return bcf;
                } catch (final IOException e) {
                    return null;
                }
            }
        }
    }

    public static BCF2Type determineIntegerType(final int value) {
        for (final BCF2Type potentialType : INTEGER_TYPES_BY_SIZE) {
            if (potentialType.withinRange(value))
                return potentialType;
        }

        throw new TribbleException("Integer cannot be encoded in allowable range of even INT32: " + value);
    }

    public static BCF2Type determineIntegerType(final int[] values) {
        // find the min and max values in the array
        int max = 0, min = 0;
        for (final int v : values) {
            if (v > max) max = v;
            if (v < min) min = v;
        }

        final BCF2Type maxType = determineIntegerType(max);
        final BCF2Type minType = determineIntegerType(min);

        // INT8 < INT16 < INT32 so this returns the larger of the two
        return maxType.compareTo(minType) >= 0 ? maxType : minType;
    }

    /**
     * Returns the maximum BCF2 integer size of t1 and t2
     * <p>
     * For example, if t1 == INT8 and t2 == INT16 returns INT16
     *
     * @param t1
     * @param t2
     * @return
     */
    public static BCF2Type maxIntegerType(final BCF2Type t1, final BCF2Type t2) {
        switch (t1) {
            case INT8:
                return t2;
            case INT16:
                return t2 == BCF2Type.INT32 ? t2 : t1;
            case INT32:
                return t1;
            default:
                throw new TribbleException("BUG: unexpected BCF2Type " + t1);
        }
    }

    public static BCF2Type determineIntegerType(final List<Integer> values) {
        BCF2Type maxType = BCF2Type.INT8;
        for (final Integer value : values) {
            if (value == null) continue;
            final BCF2Type type1 = determineIntegerType(value);
            switch (type1) {
                case INT8:
                    break;
                case INT16:
                    maxType = BCF2Type.INT16;
                    break;
                case INT32:
                    return BCF2Type.INT32; // fast path for largest possible value
                default:
                    throw new TribbleException("Unexpected integer type " + type1);
            }
        }
        return maxType;
    }

    /**
     * Are the elements and their order in the output and input headers consistent so that
     * we can write out the raw genotypes block without decoding and recoding it?
     * <p>
     * If the order of INFO, FILTER, or contig elements in the output header is different than
     * in the input header we must decode the blocks using the input header and then recode them
     * based on the new output order.
     * <p>
     * If they are consistent, we can simply pass through the raw genotypes block bytes, which is
     * a *huge* performance win for large blocks.
     * <p>
     * Many common operations on BCF2 files (merging them for -nt, selecting a subset of records, etc)
     * don't modify the ordering of the header fields and so can safely pass through the genotypes
     * undecoded.  Some operations -- those at add filters or info fields -- can change the ordering
     * of the header fields and so produce invalid BCF2 files if the genotypes aren't decoded
     */
    public static boolean headerLinesAreOrderedConsistently(final VCFHeader outputHeader, final VCFHeader genotypesBlockHeader) {
        // first, we have to have the same samples in the same order
        if (!nullAsEmpty(outputHeader.getSampleNamesInOrder()).equals(nullAsEmpty(genotypesBlockHeader.getSampleNamesInOrder())))
            return false;

        final Iterator<? extends VCFHeaderLine> outputLinesIt = outputHeader.getIDHeaderLines().iterator();

        for (final VCFHeaderLine headerLine : genotypesBlockHeader.getIDHeaderLines()) {
            if (!outputLinesIt.hasNext()) // missing lines in output
                return false;

            final VCFHeaderLine outputLine = outputLinesIt.next();
            if (!headerLine.getClass().equals(outputLine.getClass()) || !headerLine.getID().equals(outputLine.getID()))
                return false;
        }

        return true;
    }

    private static <T> List<T> nullAsEmpty(final List<T> l) {
        if (l == null)
            return Collections.emptyList();
        else
            return l;
    }
}
