/*
 * The MIT License
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
package htsjdk.tribble.gtf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import htsjdk.tribble.annotation.Strand;

/**
 * default implementation of GtfFeature.
 * The code was partially copied from the gff package
 * @author Pierre Lindenbaum
 *
 */
class GtfFeatureImpl implements GtfFeature {
    private final String contig;
    private final String source;
    private final String type;
    private final int start;
    private final int end;
    private final OptionalDouble score;
    private final Strand strand;
    private final OptionalInt phase;
    private final Map<String, List<String>> attributes;
    private final int hashCode;

    GtfFeatureImpl(final String contig, final String source, final String type,
                 final int start, final int end, final OptionalDouble score, final Strand strand, final OptionalInt phase,
                 final Map<String, List<String>> attributes) {
        this.contig = contig;
        this.source = source;
        this.type = type;
        this.start = start;
        this.end = end;
        this.score = score;
        this.phase = phase;
        this.strand = strand;
        this.attributes = copyAttributesSafely(attributes);
        this.hashCode = computeHashCode();
    }

    private static Map<String, List<String>> copyAttributesSafely(final Map<String, List<String>> attributes) {
        final Map<String, List<String>> modifiableDeepMap = new LinkedHashMap<>();

        for (final Map.Entry<String, List<String>> entry : attributes.entrySet()) {
            final List<String> unmodifiableDeepList = Collections.unmodifiableList(new ArrayList<>(entry.getValue()));
            modifiableDeepMap.put(entry.getKey(), unmodifiableDeepList);
        }

        return Collections.unmodifiableMap(modifiableDeepMap);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if(other==null || !(other instanceof GtfFeatureImpl)) {
            return false;
        }

        final GtfFeatureImpl otherBaseData = (GtfFeatureImpl) other;
       return otherBaseData.getContig().equals(getContig()) &&
                otherBaseData.getSource().equals(getSource()) &&
                otherBaseData.getType().equals(getType()) &&
                otherBaseData.getStart() == getStart() &&
                otherBaseData.getEnd() == getEnd() &&
                otherBaseData.getScore().equals(getScore()) &&
                otherBaseData.getPhase().equals(getPhase()) &&
                otherBaseData.getStrand().equals(getStrand()) &&
                otherBaseData.getAttributes().equals(getAttributes());
       
    	}

    @Override
    public int hashCode() {
        return hashCode;
    }

    private int computeHashCode() {
        int hash = getContig().hashCode();
        hash = 31 * hash + getSource().hashCode();
        hash = 31 * hash + getType().hashCode();
        hash = 31 * hash + Integer.hashCode(getStart());
        hash = 31 * hash + Integer.hashCode(getEnd());
        hash = 31 * hash + getScore().hashCode();
        hash = 31 * hash + getPhase().hashCode();
        hash = 31 * hash + getStrand().hashCode();
        hash = 31 * hash + getAttributes().hashCode();

        return hash;
    }

    public String getContig() {
        return contig;
    }

    public String getSource() {
        return source;
    }

    public String getType() {
        return type;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public OptionalDouble getScore() {
        return score;
    }

    public Strand getStrand() {
        return strand;
    }

    public OptionalInt getPhase() {
        return phase;
    }

    public Map<String, List<String>> getAttributes() {
        return attributes;
    }



}