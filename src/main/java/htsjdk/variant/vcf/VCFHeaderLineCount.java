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

package htsjdk.variant.vcf;

/**
 * The ways the {@code Number} attribute of an INFO or FORMAT header line can say how many values a field holds.
 *
 * <p>{@link #P}, {@link #LA}, {@link #LR}, {@link #LG} and {@link #M} are defined for FORMAT fields only, and their
 * counts depend on the individual sample (see {@link #variesBySample()}). An INFO line that declares one of them
 * anyway keeps it, so an INFO line's count type may be any of these values.
 */
public enum VCFHeaderLineCount {
    /** A fixed number of values, which the header line gives as an integer. */
    INTEGER(null, false),
    /** One value per alternate allele. */
    A(VCFConstants.PER_ALTERNATE_COUNT, false),
    /** One value per allele, including the reference. */
    R(VCFConstants.PER_ALLELE_COUNT, false),
    /** One value per possible genotype. */
    G(VCFConstants.PER_GENOTYPE_COUNT, false),
    /** The number of values varies, is unknown or is unbounded. */
    UNBOUNDED(VCFConstants.UNBOUNDED_ENCODING_v4, false),
    /** One value per allele in the sample's GT (VCF 4.4). */
    P(VCFConstants.PER_GT_ALLELE_COUNT, true),
    /** As {@link #A}, counting only the alternate alleles the sample's LAA names (VCF 4.5). */
    LA(VCFConstants.PER_LOCAL_ALTERNATE_COUNT, true),
    /** As {@link #R}, counting only the alternate alleles the sample's LAA names (VCF 4.5). */
    LR(VCFConstants.PER_LOCAL_ALLELE_COUNT, true),
    /** As {@link #G}, counting only the alternate alleles the sample's LAA names (VCF 4.5). */
    LG(VCFConstants.PER_LOCAL_GENOTYPE_COUNT, true),
    /** One value per possible base modification in the alleles of the sample's GT (VCF 4.5). */
    M(VCFConstants.PER_BASE_MODIFICATION_COUNT, true);

    private final String numberText;
    private final boolean variesBySample;

    VCFHeaderLineCount(final String numberText, final boolean variesBySample) {
        this.numberText = numberText;
        this.variesBySample = variesBySample;
    }

    /**
     * @return what a header line writes as its {@code Number} for this count, or null for {@link #INTEGER}, which
     * writes the integer itself
     */
    public String getNumberText() {
        return numberText;
    }

    /**
     * @return true if the number of values depends on the individual sample (its GT or LAA), so that it cannot be
     * worked out from the record alone
     */
    public boolean variesBySample() {
        return variesBySample;
    }

    /**
     * @param numberText the value of a header line's {@code Number} attribute, not null
     * @return the count that writes {@code numberText}, or {@link #INTEGER} if no other count does
     */
    public static VCFHeaderLineCount fromNumberText(final String numberText) {
        for (final VCFHeaderLineCount count : values()) {
            if (numberText.equals(count.numberText)) {
                return count;
            }
        }
        return INTEGER;
    }
}
