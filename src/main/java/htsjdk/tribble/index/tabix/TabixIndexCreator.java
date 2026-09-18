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
    private final TabixFormat formatSpec;
    private final BinningIndex.Builder indexBuilder =
            new BinningIndex.Builder(BinningIndex.BAI_MIN_SHIFT, BinningIndex.BAI_DEPTH);
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
     * @param sequenceDictionary is not required, but if present all features added must refer to sequences in the
     *                           dictionary.
     */
    public TabixIndexCreator(final SAMSequenceDictionary sequenceDictionary, final TabixFormat formatSpec) {
        this.sequenceDictionary = sequenceDictionary;
        this.formatSpec = formatSpec.clone();
    }

    public TabixIndexCreator(final TabixFormat formatSpec) {
        this(null, formatSpec);
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
        return new TabixIndex(formatSpec, sequenceNames, indexBuilder.build(sequenceNames.size()));
    }
}
