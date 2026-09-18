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
import htsjdk.utils.ValidationUtils;
import java.util.List;
import java.util.stream.Collectors;

/**
 * IndexCreator for Tabix.
 * Features are expected to be 1-based, inclusive.
 *
 * This differs from {@link TabixIndexCreator} in that sequence
 * names are populated from the header, not from the ones that are seen. This
 * is needed to support index merging.
 */
public class AllRefsTabixIndexCreator implements IndexCreator {
    private final TabixFormat formatSpec;
    private final BinningIndex.Builder indexBuilder =
            new BinningIndex.Builder(BinningIndex.BAI_MIN_SHIFT, BinningIndex.BAI_DEPTH);
    private final SAMSequenceDictionary sequenceDictionary;

    // A feature can't be added to the index until the next feature is added because the next feature
    // defines the location of the end of the previous feature in the output file.
    private PendingFeature previousFeature = null;

    public AllRefsTabixIndexCreator(final SAMSequenceDictionary sequenceDictionary, final TabixFormat formatSpec) {
        ValidationUtils.nonNull(sequenceDictionary);
        this.sequenceDictionary = sequenceDictionary;
        this.formatSpec = formatSpec.clone();
    }

    @Override
    public void addFeature(final Feature feature, final long filePosition) {
        final int referenceIndex = sequenceDictionary.getSequenceIndex(feature.getContig());
        if (referenceIndex == -1) {
            throw new IllegalArgumentException(
                    "Sequence " + feature.getContig() + " is not in the sequence dictionary provided for indexing");
        }
        final PendingFeature thisFeature =
                new PendingFeature(referenceIndex, feature.getStart(), feature.getEnd(), filePosition);
        if (previousFeature != null) {
            previousFeature.addTo(indexBuilder, thisFeature);
        }
        previousFeature = thisFeature;
    }

    @Override
    public Index finalizeIndex(final long finalFilePosition) {
        if (previousFeature != null) {
            previousFeature.addTo(indexBuilder, finalFilePosition);
        }
        final List<String> sequenceNames = sequenceDictionary.getSequences().stream()
                .map(SAMSequenceRecord::getSequenceName)
                .collect(Collectors.toList());
        return new TabixIndex(formatSpec, sequenceNames, indexBuilder.build(sequenceNames.size()));
    }
}
