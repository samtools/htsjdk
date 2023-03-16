/*
 * The MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package htsjdk.tribble.gtf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import htsjdk.tribble.annotation.Strand;
import htsjdk.tribble.gff.Gff3Constants;

/**
 * default implementation of GtfFeature. The code was partially copied from the gff package
 * 
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

    GtfFeatureImpl(final String contig, final String source, final String type, final int start,
            final int end, final OptionalDouble score, final Strand strand, final OptionalInt phase,
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

    private static Map<String, List<String>> copyAttributesSafely(
            final Map<String, List<String>> attributes) {
        final Map<String, List<String>> modifiableDeepMap = new LinkedHashMap<>(attributes.size());

        for (final Map.Entry<String, List<String>> entry : attributes.entrySet()) {
            final List<String> unmodifiableDeepList =
                    Collections.unmodifiableList(new ArrayList<>(entry.getValue()));
            modifiableDeepMap.put(entry.getKey(), unmodifiableDeepList);
        }

        return Collections.unmodifiableMap(modifiableDeepMap);
    }

    @Override
    public boolean equals(final Object other) {
        if (other == this) {
            return true;
        }
        if (other == null || !(other instanceof GtfFeatureImpl)) {
            return false;
        }

        final GtfFeatureImpl otherBaseData = (GtfFeatureImpl) other;
        return otherBaseData.getContig().equals(getContig())
                && otherBaseData.getSource().equals(getSource())
                && otherBaseData.getType().equals(getType())
                && otherBaseData.getStart() == getStart() 
                && otherBaseData.getEnd() == getEnd()
                && otherBaseData.getScore().equals(getScore())
                && otherBaseData.getPhase().equals(getPhase())
                && otherBaseData.getStrand().equals(getStrand())
                && otherBaseData.getAttributes().equals(getAttributes());

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

    @Override
    public String getContig() {
        return contig;
    }

    @Override
    public String getSource() {
        return source;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public int getStart() {
        return start;
    }

    @Override
    public int getEnd() {
        return end;
    }

    @Override
    public OptionalDouble getScore() {
        return score;
    }

    @Override
    public Strand getStrand() {
        return strand;
    }

    @Override
    public OptionalInt getPhase() {
        return phase;
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        return attributes;
    }
    
    @Override
    public String toString() {
        return new StringBuilder().
                append(this.getContig()).
                append(GtfConstants.FIELD_DELIMITER).
                append(this.getSource()).
                append(GtfConstants.FIELD_DELIMITER).
                append(this.getType()).
                append(GtfConstants.FIELD_DELIMITER).
                append(this.getStart()).
                append(GtfConstants.FIELD_DELIMITER).
                append(this.getEnd()).
                append(GtfConstants.FIELD_DELIMITER).
                append(this.getScore().isPresent() ? String.valueOf(this.getScore().getAsDouble()) : Gff3Constants.UNDEFINED_FIELD_VALUE ).
                append(GtfConstants.FIELD_DELIMITER).
                append(this.getStrand().toString()).
                append(GtfConstants.FIELD_DELIMITER).
                append(this.getPhase().isPresent() ? String.valueOf(this.getPhase().getAsInt()) : Gff3Constants.UNDEFINED_FIELD_VALUE).
                append(GtfConstants.FIELD_DELIMITER).
                append(getAttributes().isEmpty() ?
                        GtfConstants.UNDEFINED_FIELD_VALUE:
                        getAttributes().entrySet().stream().
                            flatMap(KV-> KV.getValue().stream().map(S->KV.getKey()+" \""+S+"\"")). 
                            collect(Collectors.joining("; "))
                        ).
                toString();
    }
}
