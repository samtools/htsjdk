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
package htsjdk.tribble.index.tabix;

import htsjdk.index.BinningIndex;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.tribble.Feature;
import htsjdk.tribble.index.Index;
import htsjdk.tribble.index.IndexCreator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * IndexCreator for Tabix.
 * Features are expected to be 1-based, inclusive.
 */
public class TabixIndexCreator implements IndexCreator {
    /** The {@code minShift} tabix uses for a CSI index unless told otherwise: 16 kb smallest bins, as in TBI. */
    public static final int DEFAULT_CSI_MIN_SHIFT = BinningIndex.BAI_MIN_SHIFT;

    private final TabixFormat formatSpec;
    private final TabixIndexType indexType;
    private final BinningIndex.Builder indexBuilder;
    private final List<String> sequenceNames = new ArrayList<String>();
    // Merely a faster way to ensure that features are added in a specific sequence name order
    private final Set<String> sequenceNamesSeen = new HashSet<String>();
    // A sequence dictionary is not required, but if it is provided all sequences names must be present in it.
    private final SAMSequenceDictionary sequenceDictionary;

    private String currentSequenceName = null;
    // A feature can't be added to the index until the next feature is added because the next feature
    // defines the location of the end of the previous feature in the output file.
    private PendingFeature previousFeature = null;

    /**
     * Creates a TBI index.
     *
     * @param sequenceDictionary is not required, but if present all features added must refer to sequences in the
     *                           dictionary.
     */
    public TabixIndexCreator(final SAMSequenceDictionary sequenceDictionary, final TabixFormat formatSpec) {
        this(sequenceDictionary, formatSpec, TabixIndexType.TBI);
    }

    /** Creates a TBI index. */
    public TabixIndexCreator(final TabixFormat formatSpec) {
        this(null, formatSpec);
    }

    /**
     * @param sequenceDictionary is not required, but if present all features added must refer to sequences in the
     *                           dictionary. For a CSI index its longest sequence also decides the binning scheme,
     *                           as it does for tabix; without it a deep scheme is used.
     * @param indexType the format to produce
     */
    public TabixIndexCreator(
            final SAMSequenceDictionary sequenceDictionary,
            final TabixFormat formatSpec,
            final TabixIndexType indexType) {
        this(sequenceDictionary, formatSpec, indexType, DEFAULT_CSI_MIN_SHIFT);
    }

    /**
     * @param sequenceDictionary is not required, but if present all features added must refer to sequences in the
     *                           dictionary. For a CSI index its longest sequence also decides the binning scheme,
     *                           as it does for tabix; without it a deep scheme is used.
     * @param indexType the format to produce
     * @param csiMinShift for a CSI index, log2 of the span of the smallest bins (tabix's {@code -m}); must be
     *                    {@link #DEFAULT_CSI_MIN_SHIFT} for TBI, whose scheme is fixed
     */
    public TabixIndexCreator(
            final SAMSequenceDictionary sequenceDictionary,
            final TabixFormat formatSpec,
            final TabixIndexType indexType,
            final int csiMinShift) {
        this.sequenceDictionary = sequenceDictionary;
        this.formatSpec = formatSpec.clone();
        this.indexType = indexType;
        this.indexBuilder = newBuilder(sequenceDictionary, indexType, csiMinShift);
    }

    /**
     * A builder for the binning scheme an index type calls for: TBI's fixed scheme, or for CSI the one tabix would
     * choose for the longest sequence in the dictionary. Either way it keeps the record counts tabix writes: each
     * sequence's in a metadata pseudo-bin, and a count of records without a position, which is always 0 here.
     */
    static BinningIndex.Builder newBuilder(
            final SAMSequenceDictionary sequenceDictionary, final TabixIndexType indexType, final int csiMinShift) {
        if (indexType == TabixIndexType.TBI) {
            if (csiMinShift != DEFAULT_CSI_MIN_SHIFT) {
                throw new IllegalArgumentException("A TBI index has a fixed binning scheme; minShift cannot be set");
            }
            return new BinningIndex.Builder(BinningIndex.BAI_MIN_SHIFT, BinningIndex.BAI_DEPTH).reportingRecordCounts();
        }
        final long longestSequence = sequenceDictionary == null
                ? 0
                : sequenceDictionary.getSequences().stream()
                        .mapToLong(SAMSequenceRecord::getSequenceLength)
                        .max()
                        .orElse(0);
        final BinningIndex.Geometry geometry = BinningIndex.csiGeometry(csiMinShift, longestSequence);
        return new BinningIndex.Builder(geometry.minShift(), geometry.depth(), true).reportingRecordCounts();
    }

    @Override
    public void addFeature(final Feature feature, final long filePosition) {
        final String sequenceName = feature.getContig();
        final int referenceIndex;
        if (sequenceName.equals(currentSequenceName)) {
            referenceIndex = sequenceNames.size() - 1;
        } else {
            referenceIndex = sequenceNames.size();
            if (currentSequenceName != null && sequenceNamesSeen.contains(sequenceName)) {
                throw new IllegalArgumentException("Sequence " + feature + " added out sequence of order");
            }
        }
        final PendingFeature thisFeature =
                new PendingFeature(referenceIndex, feature.getStart(), feature.getEnd(), filePosition);
        if (previousFeature != null) {
            previousFeature.addTo(indexBuilder, thisFeature);
        }
        previousFeature = thisFeature;
        if (referenceIndex == sequenceNames.size()) {
            advanceToReference(sequenceName);
        }
    }

    /** Starts a new sequence, which becomes the next reference ordinal in the index. */
    private void advanceToReference(final String sequenceName) {
        if (sequenceDictionary != null && sequenceDictionary.getSequence(sequenceName) == null) {
            throw new IllegalArgumentException(
                    "Sequence " + sequenceName + " is not in the sequence dictionary provided for indexing");
        }
        sequenceNames.add(sequenceName);
        currentSequenceName = sequenceName;
        sequenceNamesSeen.add(sequenceName);
    }

    @Override
    public Index finalizeIndex(final long finalFilePosition) {
        if (previousFeature != null) {
            previousFeature.addTo(indexBuilder, finalFilePosition);
        }
        // Only sequences that have features are listed, in the order they were seen.
        return new TabixIndex(formatSpec, sequenceNames, indexBuilder.build(sequenceNames.size()), indexType);
    }
}
