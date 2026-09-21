/*
 * Copyright (c) 2026 The Broad Institute
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

import htsjdk.tribble.TribbleException;
import htsjdk.variant.vcf.VCFCompoundHeaderLine;
import htsjdk.variant.vcf.VCFConstants;
import htsjdk.variant.vcf.VCFContigHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFIDHeaderLine;
import htsjdk.variant.vcf.VCFSimpleHeaderLine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One of the two dictionaries a BCF record's integer indices refer to: the FILTER, INFO and FORMAT IDs, or the
 * contigs.
 *
 * <p>Indices are assigned as htslib assigns them ({@code bcf_hdr_set_idx}): {@code PASS} is 0 in the ID dictionary,
 * then each header line in turn takes the index its {@code IDX} attribute names, or the next index after the highest
 * assigned so far when it has none. So a header whose lines all carry {@code IDX} (as htslib writes) is read by those
 * values, gaps included, and a header without any (as htsjdk has written) is read in line order. An ID seen before
 * keeps its first index, which is how {@code INFO/DP} and {@code FORMAT/DP} share one. Two IDs naming the same index
 * are an error.
 */
final class BCFDictionary {
    static final String IDX_ATTRIBUTE = "IDX";

    /**
     * The maximum accepted value for an IDX attribute. A dictionary index above sixteen million cannot come from
     * any real header (even a header naming every scaffold of a highly fragmented assembly has far fewer lines),
     * so the bound only limits what a malformed IDX can make the reader allocate; htslib has no bound at all.
     */
    static final int MAX_IDX = 1 << 24;

    private final String[] indexToString;

    private BCFDictionary(final String[] indexToString) {
        this.indexToString = indexToString;
    }

    /**
     * @return the ID at {@code index}
     * @throws TribbleException if {@code index} is out of range or a gap between the header's {@code IDX} values
     */
    String getString(final int index) {
        if (index < 0 || index >= indexToString.length || indexToString[index] == null) {
            throw new TribbleException("BCF dictionary index " + index + " is out of range (dictionary size "
                    + indexToString.length + ")");
        }
        return indexToString[index];
    }

    /** @return one more than the highest index */
    int size() {
        return indexToString.length;
    }

    /** The dictionary of the header's FILTER, INFO and FORMAT IDs, with {@code PASS} at 0. */
    static BCFDictionary forIDs(final VCFHeader header) {
        final Builder builder = new Builder();
        builder.add(VCFConstants.PASSES_FILTERS_v4, null);
        for (final VCFHeaderLine line : header.getMetaDataInInputOrder()) {
            if (line.shouldBeAddedToDictionary()) {
                builder.add(((VCFIDHeaderLine) line).getID(), idxAttribute(line));
            }
        }
        return builder.build();
    }

    /** The dictionary of the header's contigs. */
    static BCFDictionary forContigs(final VCFHeader header) {
        final Builder builder = new Builder();
        for (final VCFContigHeaderLine contig : header.getContigLines()) {
            builder.add(contig.getID(), idxAttribute(contig));
        }
        return builder.build();
    }

    /** @return the line's {@code IDX} attribute, or null if it has none */
    static String idxAttribute(final VCFHeaderLine line) {
        if (line instanceof VCFCompoundHeaderLine) {
            return ((VCFCompoundHeaderLine) line).getGenericFieldValue(IDX_ATTRIBUTE);
        }
        if (line instanceof VCFSimpleHeaderLine) {
            return ((VCFSimpleHeaderLine) line).getGenericFieldValue(IDX_ATTRIBUTE);
        }
        return null;
    }

    /** Assigns indices to IDs in the order they are added. */
    private static final class Builder {
        private final List<String> indexToString = new ArrayList<>();
        private final Map<String, Integer> stringToIndex = new HashMap<>();

        void add(final String id, final String idxAttribute) {
            if (stringToIndex.containsKey(id)) {
                return;
            }
            final int index = idxAttribute == null ? indexToString.size() : parseIdx(id, idxAttribute);
            while (indexToString.size() <= index) {
                indexToString.add(null);
            }
            final String occupant = indexToString.get(index);
            if (occupant != null) {
                throw new TribbleException(
                        "Conflicting IDX=" + index + " in the BCF header dictionary: " + occupant + " and " + id);
            }
            indexToString.set(index, id);
            stringToIndex.put(id, index);
        }

        BCFDictionary build() {
            return new BCFDictionary(indexToString.toArray(new String[0]));
        }

        private static int parseIdx(final String id, final String idxAttribute) {
            final int idx;
            try {
                idx = Integer.parseInt(idxAttribute);
            } catch (final NumberFormatException e) {
                throw new TribbleException(
                        "The IDX attribute of header line " + id + " is not an integer: " + idxAttribute, e);
            }
            if (idx < 0) {
                throw new TribbleException("The IDX attribute of header line " + id + " is negative: " + idx);
            }
            if (idx >= MAX_IDX) {
                throw new TribbleException("The IDX attribute of header line " + id + " is " + idx
                        + ", which is at or above the maximum of " + MAX_IDX);
            }
            return idx;
        }
    }
}
