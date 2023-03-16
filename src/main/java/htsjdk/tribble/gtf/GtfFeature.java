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

import htsjdk.tribble.Feature;
import htsjdk.tribble.annotation.Strand;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * describes a GTF (Gene transfer format ) record See
 * https://en.wikipedia.org/wiki/Gene_transfer_format The code was partially copied from the gff
 * package
 * 
 * @author Pierre Lindenbaum
 */
public interface GtfFeature extends Feature {

    @Override
    public String getContig();

    @Override
    public int getStart();

    @Override
    public int getEnd();

    public String getSource();

    public Strand getStrand();

    public OptionalInt getPhase();

    public String getType();

    public OptionalDouble getScore();

    public Map<String, List<String>> getAttributes();

    /**
     * @param key the searched key
     * @return true if the GTF feature has this attribute
     */
    public default boolean hasAttribute(final String key) {
        return getAttributes().containsKey(key);
    }

    /**
     * @param key the attribute name
     * @return the value for this attribute or null if there is none
     * @throws IllegalArgumentException if there is more than one value for this attribute
     */
    public default String getAttribute(final String key) {
        final List<String> values = getAttributes(key);
        if (values == null || values.isEmpty()) {
            return null;
        }

        if (values.size() != 1) {
            throw new IllegalArgumentException(
                    "Attribute '" + key + "' has multiple values when only one expected");
        }
        return values.get(0);
    }

    /**
     * @param key the attribute name
     * @return the values for this attribute or an empty List if there is none
     */
    public default List<String> getAttributes(final String key) {
        return getAttributes().getOrDefault(key, Collections.emptyList());
    }

    /**
     * @return true if this feature type is a gene
     */
    public default boolean isGene() {
        return this.getType().equals("gene");
    }

    /**
     * @return true if this feature type is a transcript
     */
    public default boolean isTranscript() {
        return this.getType().equals("transcript");
    }

    /**
     * @return true if this feature type is an exon
     */
    public default boolean isExon() {
        return this.getType().equals("exon");
    }

    /**
     * @return true if this feature type is a CDS
     */
    public default boolean isCDS() {
        return this.getType().equals("CDS");
    }

    /**
     * shortcut to <code>getAttribute("gene_name")</code>
     * 
     * @return the gene name or null
     */
    public default String getGeneName() {
        return getAttribute("gene_name");
    }

    /**
     * shortcut to <code>getAttribute(GtfConstants.GENE_ID)</code>
     * 
     * @return the gene ID or null
     */
    public default String getGeneId() {
        return getAttribute(GtfConstants.GENE_ID);
    }

    /**
     * shortcut to <code>getAttribute(GtfConstants.TRANSCRIPT_ID)</code>
     * 
     * @return the transcript ID or null
     */
    public default String getTranscriptId() {
        return getAttribute(GtfConstants.TRANSCRIPT_ID);
    }
}
