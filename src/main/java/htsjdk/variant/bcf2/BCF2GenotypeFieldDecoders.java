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

import htsjdk.tribble.TribbleException;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.vcf.VCFConstants;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderVersion;
import htsjdk.variant.vcf.VCFPassThruTextTransformer;
import htsjdk.variant.vcf.VCFPercentEncodedTextTransformer;
import htsjdk.variant.vcf.VCFTextTransformer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * An efficient scheme for building and obtaining specialized
 * genotype field decoders.  Used by the BCFCodec to parse
 * with little overhead the fields from BCF2 encoded genotype
 * records
 *
 * <p>The decoders hold no per-record state, so one set serves every record of a file, decoded on any thread.
 *
 * @author Mark DePristo
 * @since 6/12
 */
public class BCF2GenotypeFieldDecoders {
    private static final boolean ENABLE_FASTPATH_GT = true;
    private static final int MIN_SAMPLES_FOR_FASTPATH_GENOTYPES = 0;

    // initialized once per writer to allow parallel writers to work
    private final HashMap<String, Decoder> genotypeFieldDecoder = new HashMap<String, Decoder>();
    private final Decoder defaultDecoder;

    /**
     * @param header the header of the file being read; its version decides whether the first allele's phase bit is
     *     honoured (VCF 4.4 and later) and whether String values are percent-decoded (VCF 4.3 and later)
     */
    public BCF2GenotypeFieldDecoders(final VCFHeader header) {
        final VCFHeaderVersion version = header.getVCFHeaderVersion();
        final boolean phaseBitIsLiteral = version != null && version.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_4);
        final VCFTextTransformer textTransformer =
                version != null && version.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3)
                        ? new VCFPercentEncodedTextTransformer()
                        : new VCFPassThruTextTransformer();
        defaultDecoder = new GenericDecoder(textTransformer);
        genotypeFieldDecoder.put(VCFConstants.FORMAT.GENOTYPE, new GTDecoder(phaseBitIsLiteral));
        genotypeFieldDecoder.put(VCFConstants.FORMAT.GENOTYPE_FILTER, new FTDecoder(textTransformer));
        genotypeFieldDecoder.put(VCFConstants.FORMAT.READ_DEPTH, new DPDecoder());
        genotypeFieldDecoder.put(VCFConstants.FORMAT.ALLELE_DEPTHS, new ADDecoder());
        genotypeFieldDecoder.put(VCFConstants.FORMAT.PHRED_SCALED_GENOTYPE_LIKELIHOODS, new PLDecoder());
        genotypeFieldDecoder.put(VCFConstants.FORMAT.GENOTYPE_QUALITY, new GQDecoder());
    }

    // -----------------------------------------------------------------
    //
    // Genotype field decoder
    //
    // -----------------------------------------------------------------

    /**
     * Return decoder appropriate for field, or the generic decoder if no
     * specialized one is bound
     * @param field the GT field to decode
     * @return a non-null decoder
     */
    public Decoder getDecoder(final String field) {
        final Decoder d = genotypeFieldDecoder.get(field);
        return d == null ? defaultDecoder : d;
    }

    /**
     * Decoder a field into a set of GenotypeBuilders
     *
     * The way this works is that this decode method
     * iterates over the builders, decoding a genotype field
     * in BCF2 for each sample from decoder.
     *
     * This system allows us to easily use specialized
     * decoders for specific genotype field values. For example,
     * we use a special decoder to directly read the BCF2 data for
     * the PL field into a int[] rather than the generic List of Integer
     */
    public interface Decoder {
        public void decode(
                final List<Allele> siteAlleles,
                final String field,
                final BCF2Decoder decoder,
                final byte typeDescriptor,
                final int numElements,
                final GenotypeBuilder[] gbs)
                throws IOException;
    }

    /**
     * Decodes GT, an integer-typed field, so that MISSING and END_OF_VECTOR are its type's two lowest values and one
     * compare against END_OF_VECTOR finds either. Each allele carries the phase of the separator before it in its low
     * bit; the first allele has no separator, and whether its bit means anything depends on the header version: below
     * VCF 4.4 it is ignored and the phase is inferred from the other alleles, as the VCF text reader does for a GT
     * without a leading indicator; from 4.4 it is the leading indicator ({@code |0/1}). A haploid call is always
     * unphased, as the VCF text reader reads {@code 1}.
     */
    private class GTDecoder implements Decoder {
        private final boolean phaseBitIsLiteral;

        GTDecoder(final boolean phaseBitIsLiteral) {
            this.phaseBitIsLiteral = phaseBitIsLiteral;
        }

        @Override
        public void decode(
                final List<Allele> siteAlleles,
                final String field,
                final BCF2Decoder decoder,
                final byte typeDescriptor,
                final int numElements,
                final GenotypeBuilder[] gbs)
                throws IOException {
            if (ENABLE_FASTPATH_GT
                    && siteAlleles.size() == 2
                    && numElements == 2
                    && gbs.length >= MIN_SAMPLES_FOR_FASTPATH_GENOTYPES)
                fastBiallelicDiploidDecode(siteAlleles, decoder, typeDescriptor, gbs);
            else {
                generalDecode(siteAlleles, numElements, decoder, typeDescriptor, gbs);
            }
        }

        /**
         * fast path for many samples with diploid genotypes
         *
         * The way this would work is simple.  Create a List<Allele> diploidGenotypes[] object
         * After decoding the offset, if that sample is diploid compute the
         * offset into the alleles vector which is simply offset = allele0 * nAlleles + allele1
         * if there's a value at diploidGenotypes[offset], use it, otherwise create the genotype
         * cache it and use that
         *
         * Some notes.  If there are nAlleles at the site, there are implicitly actually
         * n + 1 options including
         */
        @SuppressWarnings({"unchecked"})
        private void fastBiallelicDiploidDecode(
                final List<Allele> siteAlleles,
                final BCF2Decoder decoder,
                final byte typeDescriptor,
                final GenotypeBuilder[] gbs)
                throws IOException {
            final BCF2Type type = BCF2Utils.decodeType(typeDescriptor);
            final int missing = type.getMissingBytes();
            final int endOfVector = type.getVectorEndBytes();

            final int nPossibleGenotypes = 3 * 3;
            final Object allGenotypes[] = new Object[nPossibleGenotypes];

            for (final GenotypeBuilder gb : gbs) {
                final int a1 = decoder.decodeInt(type);
                final int a2 = decoder.decodeInt(type);

                if (a1 <= endOfVector) {
                    if (a1 != missing) {
                        throw new TribbleException("END_OF_VECTOR in the first allele of a GT field");
                    }
                    // ploidy 0: no GT for this sample
                    gb.alleles(null);
                    gb.phased(false);
                } else if (a2 <= endOfVector) {
                    // haploid: the second slot is padding (MISSING from htsjdk, END_OF_VECTOR from htslib)
                    gb.alleles(Arrays.asList(getAlleleFromEncoded(siteAlleles, a1)));
                    setHaploidPhasing(gb);
                } else {
                    // downshift to remove phase
                    final int offset = (a1 >> 1) * 3 + (a2 >> 1);
                    assert offset < allGenotypes.length;

                    List<Allele> gt = (List<Allele>) allGenotypes[offset];
                    if (gt == null) {
                        final Allele allele1 = getAlleleFromEncoded(siteAlleles, a1);
                        final Allele allele2 = getAlleleFromEncoded(siteAlleles, a2);
                        gt = Arrays.asList(allele1, allele2);
                        allGenotypes[offset] = gt;
                    }

                    gb.alleles(gt);
                    final boolean secondPhased = (a2 & 0x01) == 1;
                    if (phaseBitIsLiteral && ((a1 & 0x01) == 1) != secondPhased) {
                        gb.allelePhasing(new boolean[] {!secondPhased, secondPhased});
                    } else {
                        gb.phased(secondPhased);
                    }
                }
            }
        }

        private void generalDecode(
                final List<Allele> siteAlleles,
                final int ploidy,
                final BCF2Decoder decoder,
                final byte typeDescriptor,
                final GenotypeBuilder[] gbs)
                throws IOException {
            final BCF2Type type = BCF2Utils.decodeType(typeDescriptor);
            final int missing = type.getMissingBytes();
            final int endOfVector = type.getVectorEndBytes();

            // a single cache for the encoded genotypes, since we don't actually need this vector
            final int[] tmp = new int[ploidy];

            for (final GenotypeBuilder gb : gbs) {
                // a sentinel ends the sample's alleles: a shorter genotype is padded with MISSING (htsjdk) or
                // END_OF_VECTOR (htslib) up to the site's ploidy
                int actualPloidy = 0;
                for (int i = 0; i < ploidy; i++) {
                    final int v = decoder.decodeInt(type);
                    if (v <= endOfVector) {
                        if (i == 0 && v != missing) {
                            throw new TribbleException("END_OF_VECTOR in the first allele of a GT field");
                        }
                        for (int j = i + 1; j < ploidy; j++) decoder.decodeInt(type);
                        break;
                    }
                    tmp[actualPloidy++] = v;
                }

                if (actualPloidy == 0) {
                    gb.alleles(null);
                    gb.phased(false);
                } else {
                    final List<Allele> gt = new ArrayList<Allele>(actualPloidy);
                    for (int i = 0; i < actualPloidy; i++) {
                        gt.add(getAlleleFromEncoded(siteAlleles, tmp[i]));
                    }
                    gb.alleles(gt);
                    if (actualPloidy == 1) {
                        setHaploidPhasing(gb);
                    } else {
                        setPhasing(gb, tmp, actualPloidy);
                    }
                }
            }
        }

        /**
         * A haploid call is always unphased, as the VCF text reader reads {@code 1}. Both readers will be revisited
         * together for VCF 4.4 haploid semantics.
         */
        private void setHaploidPhasing(final GenotypeBuilder gb) {
            gb.phased(false);
        }

        /**
         * Sets a genotype of two or more alleles its phasing from their low bits. One flag serves unless the
         * separators are mixed ({@code 0/1|2}) or, from VCF 4.4, the first allele's bit is not the one the
         * separators imply ({@code |0/1}).
         */
        private void setPhasing(final GenotypeBuilder gb, final int[] encoded, final int ploidy) {
            final boolean firstSeparatorPhased = (encoded[1] & 0x01) == 1;
            boolean allSeparatorsPhased = firstSeparatorPhased;
            boolean anySeparatorPhased = firstSeparatorPhased;
            for (int i = 2; i < ploidy; i++) {
                final boolean phased = (encoded[i] & 0x01) == 1;
                allSeparatorsPhased &= phased;
                anySeparatorPhased |= phased;
            }
            // without a leading indicator the first allele is unphased if any separator is, and phased otherwise
            final boolean firstAllelePhased = phaseBitIsLiteral ? (encoded[0] & 0x01) == 1 : allSeparatorsPhased;
            final boolean mixed = anySeparatorPhased && !allSeparatorsPhased;
            if (!mixed && firstAllelePhased == allSeparatorsPhased) {
                gb.phased(firstSeparatorPhased);
                return;
            }
            final boolean[] allelePhasing = new boolean[ploidy];
            allelePhasing[0] = firstAllelePhased;
            for (int i = 1; i < ploidy; i++) {
                allelePhasing[i] = (encoded[i] & 0x01) == 1;
            }
            gb.allelePhasing(allelePhasing);
        }

        private Allele getAlleleFromEncoded(final List<Allele> siteAlleles, final int encode) {
            final int offset = encode >> 1;
            return offset == 0 ? Allele.NO_CALL : siteAlleles.get(offset - 1);
        }
    }

    private class DPDecoder implements Decoder {
        @Override
        public void decode(
                final List<Allele> siteAlleles,
                final String field,
                final BCF2Decoder decoder,
                final byte typeDescriptor,
                final int numElements,
                final GenotypeBuilder[] gbs)
                throws IOException {
            for (final GenotypeBuilder gb : gbs) {
                // the -1 is for missing
                gb.DP(decoder.decodeInt(typeDescriptor, -1));
            }
        }
    }

    private class GQDecoder implements Decoder {
        @Override
        public void decode(
                final List<Allele> siteAlleles,
                final String field,
                final BCF2Decoder decoder,
                final byte typeDescriptor,
                final int numElements,
                final GenotypeBuilder[] gbs)
                throws IOException {
            for (final GenotypeBuilder gb : gbs) {
                // the -1 is for missing
                gb.GQ(decoder.decodeInt(typeDescriptor, -1));
            }
        }
    }

    private class ADDecoder implements Decoder {
        @Override
        public void decode(
                final List<Allele> siteAlleles,
                final String field,
                final BCF2Decoder decoder,
                final byte typeDescriptor,
                final int numElements,
                final GenotypeBuilder[] gbs)
                throws IOException {
            for (final GenotypeBuilder gb : gbs) {
                gb.AD(decoder.decodeIntArray(typeDescriptor, numElements));
            }
        }
    }

    private class PLDecoder implements Decoder {
        @Override
        public void decode(
                final List<Allele> siteAlleles,
                final String field,
                final BCF2Decoder decoder,
                final byte typeDescriptor,
                final int numElements,
                final GenotypeBuilder[] gbs)
                throws IOException {
            for (final GenotypeBuilder gb : gbs) {
                gb.PL(decoder.decodeIntArray(typeDescriptor, numElements));
            }
        }
    }

    /**
     * A FORMAT String value as the VCF text reader would hold it: {@code .} is a missing value, and from VCF 4.3 the
     * text is percent-decoded. A value is a String, or a List of Strings for an htsjdk-style collapsed list.
     */
    @SuppressWarnings("unchecked")
    private static Object formatStringValue(final Object value, final VCFTextTransformer textTransformer) {
        if (value instanceof String) {
            final String s = (String) value;
            return VCFConstants.MISSING_VALUE_v4.equals(s) ? null : textTransformer.decodeText(s);
        }
        return textTransformer.decodeText((List<String>) value);
    }

    private class GenericDecoder implements Decoder {
        private final VCFTextTransformer textTransformer;

        GenericDecoder(final VCFTextTransformer textTransformer) {
            this.textTransformer = textTransformer;
        }

        @Override
        public void decode(
                final List<Allele> siteAlleles,
                final String field,
                final BCF2Decoder decoder,
                final byte typeDescriptor,
                final int numElements,
                final GenotypeBuilder[] gbs)
                throws IOException {
            final boolean isString = BCF2Utils.decodeType(typeDescriptor) == BCF2Type.CHAR;
            for (final GenotypeBuilder gb : gbs) {
                Object value = decoder.decodeTypedValue(typeDescriptor, numElements);
                if (value == null) continue; // don't add missing values
                if (isString) {
                    value = formatStringValue(value, textTransformer);
                    if (value == null) continue;
                } else if (value instanceof List && ((List) value).size() == 1) {
                    // a vector pruned down to one value is returned as that value, not a vector of size 1
                    value = ((List) value).get(0);
                }
                gb.attribute(field, value);
            }
        }
    }

    private class FTDecoder implements Decoder {
        private final VCFTextTransformer textTransformer;

        FTDecoder(final VCFTextTransformer textTransformer) {
            this.textTransformer = textTransformer;
        }

        @Override
        public void decode(
                final List<Allele> siteAlleles,
                final String field,
                final BCF2Decoder decoder,
                final byte typeDescriptor,
                final int numElements,
                final GenotypeBuilder[] gbs)
                throws IOException {
            for (final GenotypeBuilder gb : gbs) {
                Object value = decoder.decodeTypedValue(typeDescriptor, numElements);
                if (value != null) value = formatStringValue(value, textTransformer);
                assert value == null || value instanceof String;
                gb.filter((String) value);
            }
        }
    }
}
