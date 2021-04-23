package htsjdk.variant.variantcontext.writer.BCF2FieldWriter;

import htsjdk.tribble.TribbleException;
import htsjdk.variant.bcf2.BCF2Type;
import htsjdk.variant.bcf2.BCF2Utils;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.BCF2Encoder;
import htsjdk.variant.vcf.VCFCompoundHeaderLine;
import htsjdk.variant.vcf.VCFConstants;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeaderLineCount;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

// TODO in the genotype writers, a missing genotype (one where variantContext.getGenotype(sampleName) == null)
//  is treated like one where all its attributes/inline fields are missing, this matches the behavior
//  of the old writer, which previously created a new empty Genotype object for each missing genotypes, is this right?
//  For example, should the FT string of a missing genotype be PASS or a padded empty string

/**
 * INFO and FORMAT writers
 */
public class BCF2FieldWriter {

    final VCFCompoundHeaderLine headerLine;
    final int dictionaryOffset;
    final BCF2Type dictionaryOffsetType;
    final String key;
    final BCF2Encoder encoder;

    BCF2FieldWriter(final VCFCompoundHeaderLine headerLine, final int dictionaryOffset, final BCF2Encoder encoder) {
        this.headerLine = headerLine;
        this.dictionaryOffset = dictionaryOffset;
        this.dictionaryOffsetType = BCF2Utils.determineIntegerType(dictionaryOffset);
        this.key = headerLine.getID();
        this.encoder = encoder;
    }

    /**
     * This should be called before encoding every VariantContext in both INFO and FORMAT writers
     */
    public void start() throws IOException {
        // Write key
        encoder.encodeTypedInt(dictionaryOffset, dictionaryOffsetType);
    }


    //////////////////////////////////////////////////
    // Factory Methods                              //
    //////////////////////////////////////////////////
    public static SiteWriter createSiteWriter(
        final VCFInfoHeaderLine line,
        final int offset,
        final BCF2Encoder encoder
    ) {
        return line.getType() == VCFHeaderLineType.Flag
            ? new SiteFlagWriter(line, offset, encoder)
            : new SiteAttributeWriter(line, offset, encoder);
    }

    public static GenotypeWriter createGenotypeWriter(
        final VCFFormatHeaderLine line,
        final int offset,
        final BCF2Encoder encoder
    ) {
        // Specialized writers for fields stored inline in the Genotype and not in its attributes map
        switch (line.getID()) {
            case VCFConstants.GENOTYPE_KEY:
                return new GTWriter(line, offset, encoder);
            case VCFConstants.GENOTYPE_FILTER_KEY:
                return new FTWriter(line, offset, encoder);
            case VCFConstants.DEPTH_KEY:
                return new DPWriter(line, offset, encoder);
            case VCFConstants.GENOTYPE_QUALITY_KEY:
                return new GQWriter(line, offset, encoder);
            case VCFConstants.GENOTYPE_ALLELE_DEPTHS:
                return new ADWriter(line, offset, encoder);
            case VCFConstants.GENOTYPE_PL_KEY:
                return new PLWriter(line, offset, encoder);
        }

        if (line.getType() == VCFHeaderLineType.Flag) {
            throw new TribbleException("Format lines cannot have type Flag");
        } else {
            return new GenotypeAttributeWriter(line, offset, encoder);
        }
    }

    private static BCF2FieldEncoder getEncoder(final VCFCompoundHeaderLine line, final BCF2Encoder encoder) {
        switch (line.getType()) {
            case Integer:
                return line.isFixedCount() && line.getCount() == 1
                    ? new BCF2FieldEncoder.AtomicIntFieldEncoder(encoder)
                    : new BCF2FieldEncoder.VecIntFieldEncoder(encoder);
            case Float:
                return line.isFixedCount() && line.getCount() == 1
                    ? new BCF2FieldEncoder.AtomicFloatFieldEncoder(encoder)
                    : new BCF2FieldEncoder.VecFloatFieldEncoder(encoder);
            case String:
                return new BCF2FieldEncoder.StringFieldEncoder(encoder);
            case Character:
                return new BCF2FieldEncoder.CharFieldEncoder(encoder);
            default:
                throw new TribbleException("Unrecognized line type: " + line.getType());
        }
    }


    //////////////////////////////////////////////////
    // Info Writers                                 //
    //////////////////////////////////////////////////
    public static abstract class SiteWriter extends BCF2FieldWriter {

        SiteWriter(final VCFInfoHeaderLine headerLine, final int dictionaryOffset, final BCF2Encoder encoder) {
            super(headerLine, dictionaryOffset, encoder);
        }

        public abstract void encode(final VariantContext vc) throws IOException;
    }

    /**
     * INFO writer that accesses variant context fields stored in the VC's attributes map
     */
    static class SiteAttributeWriter extends SiteWriter {

        private final BCF2FieldEncoder siteEncoder;
        private final boolean boundedNonAtomic;

        SiteAttributeWriter(final VCFInfoHeaderLine headerLine, final int dictionaryOffset, final BCF2Encoder encoder) {
            super(headerLine, dictionaryOffset, encoder);
            this.siteEncoder = BCF2FieldWriter.getEncoder(headerLine, encoder);

            // If this line's count is unbounded, or the inner encoder is one of the atomic specializations,
            // the inner encoder can always figure out the correct number of BCF2 values to write out by itself.
            // Otherwise we need to inspect the context to determine the number of values to encode
            // and possibly error if too many values were provided
            this.boundedNonAtomic = headerLine.getCountType() != VCFHeaderLineCount.UNBOUNDED && !(
                siteEncoder instanceof BCF2FieldEncoder.AtomicIntFieldEncoder || siteEncoder instanceof BCF2FieldEncoder.AtomicFloatFieldEncoder
            );
        }

        @Override
        public void encode(final VariantContext vc) throws IOException {
            siteEncoder.start();

            final Object o = vc.getAttribute(key);
            if (o == null) {
                encoder.encodeTypedMissing(siteEncoder.type);
            } else {
                siteEncoder.load(o);
                if (boundedNonAtomic) {
                    final int headerLength = headerLine.getCount(vc);
                    if (siteEncoder.nValues > headerLength)
                        throw BCF2FieldWriter.tooManyValues(siteEncoder.nValues, headerLength, key, vc);
                    siteEncoder.nValues = headerLength;
                }

                siteEncoder.encodeType();
                siteEncoder.encode();
            }
        }
    }

    /**
     * INFO writer that accesses Flags stored in the VC's attributes map
     */
    static class SiteFlagWriter extends SiteWriter {

        SiteFlagWriter(final VCFInfoHeaderLine headerLine, final int dictionaryOffset, final BCF2Encoder encoder) {
            super(headerLine, dictionaryOffset, encoder);
        }

        @Override
        public void encode(final VariantContext vc) throws IOException {
            final Object o;
            if ((o = vc.getAttribute(key)) != null && o instanceof Boolean && (Boolean) o) {
                encoder.encodeTypedInt(1, BCF2Type.INT8);
            } else {
                encoder.encodeTypedMissing(BCF2Type.INT8);
            }
        }
    }


    //////////////////////////////////////////////////
    // Genotype Writers                             //
    //////////////////////////////////////////////////
    public static abstract class GenotypeWriter extends BCF2FieldWriter {

        GenotypeWriter(final VCFFormatHeaderLine headerLine, final int dictionaryOffset, final BCF2Encoder encoder) {
            super(headerLine, dictionaryOffset, encoder);
        }

        public abstract void encode(final VariantContext vc, final String[] sampleNames) throws IOException;
    }

    /**
     * FORMAT writer that accesses genotype fields stored in the Genotype object's attributes map
     */
    static class GenotypeAttributeWriter extends GenotypeWriter {

        private final BCF2FieldEncoder siteEncoder;
        private final boolean boundedNonAtomic;

        GenotypeAttributeWriter(final VCFFormatHeaderLine headerLine, final int dictionaryOffset, final BCF2Encoder encoder) {
            super(headerLine, dictionaryOffset, encoder);
            this.siteEncoder = BCF2FieldWriter.getEncoder(headerLine, encoder);

            // If this line's count is unbounded, or the inner encoder is one of the atomic specializations,
            // the inner encoder can always figure out the correct number of BCF2 values to write out by itself.
            // Otherwise we need to inspect the context to determine the number of values to encode
            // and possibly error if too many values were provided
            this.boundedNonAtomic = headerLine.getCountType() != VCFHeaderLineCount.UNBOUNDED && !(
                siteEncoder instanceof BCF2FieldEncoder.AtomicIntFieldEncoder || siteEncoder instanceof BCF2FieldEncoder.AtomicFloatFieldEncoder
            );
        }

        @Override
        public void encode(final VariantContext vc, final String[] sampleNames) throws IOException {
            siteEncoder.start();
            for (final String s : sampleNames) {
                final Genotype g = vc.getGenotype(s);
                siteEncoder.load(g == null ? null : g.getExtendedAttribute(key));
            }

            if (boundedNonAtomic) {
                final int headerLength = headerLine.getCount(vc);
                if (siteEncoder.nValues > headerLength)
                    throw BCF2FieldWriter.tooManyValues(siteEncoder.nValues, headerLength, key, vc);
                siteEncoder.nValues = headerLength;
            }

            siteEncoder.encodeType();
            siteEncoder.encode();
        }
    }

    /**
     * Base class for FORMAT writers that access genotype fields stored directly
     * as int fields in the Genotype object as opposed to the attributes map.
     */
    static abstract class GenotypeInlineAtomicIntWriter extends GenotypeWriter {

        // Used to store values to write out to avoid boxing
        private int[] vs;

        GenotypeInlineAtomicIntWriter(final VCFFormatHeaderLine headerLine, final int dictionaryOffset, final BCF2Encoder encoder) {
            super(headerLine, dictionaryOffset, encoder);
        }

        @Override
        public void encode(final VariantContext vc, final String[] sampleNames) throws IOException {
            if (vs == null || vs.length < sampleNames.length) {
                vs = new int[sampleNames.length];
            }

            BCF2Type type = BCF2Type.INT8;
            int i = 0;

            for (final String s : sampleNames) {
                final Genotype g = vc.getGenotype(s);
                final int v = g == null ? -1 : get(g);
                if (v != -1) {
                    type = BCF2Utils.maxIntegerType(type, BCF2Utils.determineIntegerType(v));
                }
                vs[i++] = v;
            }

            encoder.encodeType(1, type);

            for (int j = 0; j < i; j++) {
                final int v = vs[j];
                if (v == -1) {
                    encoder.encodeRawMissingValue(type);
                } else {
                    encoder.encodeRawInt(v, type);
                }
            }
        }

        protected abstract int get(final Genotype g);
    }

    static class DPWriter extends GenotypeInlineAtomicIntWriter {

        DPWriter(final VCFFormatHeaderLine headerLine, final int dictionaryOffset, final BCF2Encoder encoder) {
            super(headerLine, dictionaryOffset, encoder);
        }

        @Override
        protected int get(final Genotype g) {
            return g.getDP();
        }
    }

    static class GQWriter extends GenotypeInlineAtomicIntWriter {

        GQWriter(final VCFFormatHeaderLine headerLine, final int dictionaryOffset, final BCF2Encoder encoder) {
            super(headerLine, dictionaryOffset, encoder);
        }

        @Override
        protected int get(final Genotype g) {
            return g.getGQ();
        }
    }

    /**
     * Base class for FORMAT writers that access genotype fields stored directly
     * as int[] fields in the Genotype object as opposed to the attributes map.
     */
    static abstract class GenotypeInlineVecIntWriter extends GenotypeWriter {

        private final List<int[]> vs = new ArrayList<>();

        GenotypeInlineVecIntWriter(final VCFFormatHeaderLine headerLine, final int dictionaryOffset, final BCF2Encoder encoder) {
            super(headerLine, dictionaryOffset, encoder);
        }

        @Override
        public void encode(final VariantContext vc, final String[] sampleNames) throws IOException {
            BCF2Type type = BCF2Type.INT8;

            // For both vector of int types represented as inline fields by htsjdk (AD and PL),
            // the count type can be determined by inspecting the header
            final int nValues = headerLine.getCount(vc);

            // Find narrowest integer type that fits all values
            for (final String s : sampleNames) {
                final Genotype g = vc.getGenotype(s);
                final int[] v = g == null ? null : get(g);
                vs.add(v);

                if (v == null) continue;
                if (v.length > nValues)
                    throw BCF2FieldWriter.tooManyValues(v.length, nValues, key, vc);

                if (type == BCF2Type.INT32) continue;
                type = BCF2Utils.maxIntegerType(type, BCF2Utils.determineIntegerType(v));
            }

            encoder.encodeType(nValues, type);

            for (final int[] vs : vs) {
                if (vs == null) {
                    encoder.encodePaddingValues(nValues, type);
                } else {
                    encoder.encodeRawVecInt(vs, nValues, type);
                }
            }
            vs.clear();
        }

        abstract int[] get(final Genotype g);
    }

    static class ADWriter extends GenotypeInlineVecIntWriter {

        ADWriter(final VCFFormatHeaderLine headerLine, final int dictionaryOffset, final BCF2Encoder encoder) {
            super(headerLine, dictionaryOffset, encoder);
        }

        @Override
        int[] get(final Genotype g) {
            return g.getAD();
        }
    }

    static class PLWriter extends GenotypeInlineVecIntWriter {

        PLWriter(final VCFFormatHeaderLine headerLine, final int dictionaryOffset, final BCF2Encoder encoder) {
            super(headerLine, dictionaryOffset, encoder);
        }

        @Override
        int[] get(final Genotype g) {
            return g.getPL();
        }
    }

    /**
     * Writer for the FT or filter field. This is a special case of the String writer
     * where the type of the value is known to be String (and not List<String>)
     * and null values must be specially handled by encoding them as PASS.
     */
    static class FTWriter extends GenotypeWriter {

        private static final byte[] PASS = "PASS".getBytes(StandardCharsets.US_ASCII);

        private final List<byte[]> vs = new ArrayList<>();

        FTWriter(final VCFFormatHeaderLine headerLine, final int dictionaryOffset, final BCF2Encoder encoder) {
            super(headerLine, dictionaryOffset, encoder);
        }

        @Override
        public void encode(final VariantContext vc, final String[] sampleNames) throws IOException {
            int nValues = 0;

            for (final String s : sampleNames) {
                final Genotype g = vc.getGenotype(s);
                final String f;
                final byte[] v;
                if (g == null || (f = g.getFilters()) == null) {
                    v = FTWriter.PASS;
                } else {
                    v = f.getBytes(StandardCharsets.UTF_8);
                }
                nValues = Math.max(nValues, v.length);
                vs.add(v);
            }

            encoder.encodeType(nValues, BCF2Type.CHAR);
            for (final byte[] v : vs) {
                encoder.encodeRawString(v, nValues);
            }
            vs.clear();
        }
    }

    /**
     * Specialized writer for GT field.
     */
    static class GTWriter extends GenotypeWriter {

        private final HashMap<Allele, Integer> alleleMapForTriPlus = new HashMap<>(5);
        private Allele ref, alt1;

        GTWriter(final VCFFormatHeaderLine headerLine, final int dictionaryOffset, final BCF2Encoder encoder) {
            super(headerLine, dictionaryOffset, encoder);
        }

        @Override
        public void encode(final VariantContext vc, final String[] sampleNames) throws IOException {
            buildAlleleMap(vc);
            final int nValues = vc.getMaxPloidy(2);
            // Offsets should always fit into a signed 8-bit integer but do this check anyway for spec compliance
            final BCF2Type type = BCF2Utils.determineIntegerType(vc.getNAlleles() << 1);

            encoder.encodeType(nValues, type);

            for (final String s : sampleNames) {
                final Genotype g = vc.getGenotype(s);
                if (g != null) {
                    boolean notFirst = false;
                    for (final Allele a : g.getAlleles()) {
                        // TODO Genotype and Allele classes can't properly store phasing information for plodiy > 2
                        //  Currently all non ref alleles are assumed to have the same phasing
                        final int encoded = encodeAlleleWithoutPhasing(a) | ((g.isPhased() && notFirst) ? 0x01 : 0x00);
                        encoder.encodeRawInt(encoded, type);
                        notFirst = true;
                    }
                    // Pad with missing values if sample ploidy is less than maximum
                    final int padding = nValues - g.getPloidy();
                    if (padding > 0) {
                        encoder.encodePaddingValues(padding, type);
                    }
                } else {
                    // Entirely missing genotype, which we encode as vector of no calls, or 0
                    for (int i = 0; i < nValues; i++) {
                        encoder.encodeRawInt(0, type);
                    }
                }
            }
        }

        /**
         * Fast path code to encode an allele without phasing information.
         * Inline tests for == against ref (most common, first test)
         * == alt1 (second most common, second test)
         * == NO_CALL (third)
         * and finally in the map from allele => offset for all alt 2+ alleles
         *
         * @param a the allele we want to encode
         * @return the encoded allele without phasing information
         */
        private int encodeAlleleWithoutPhasing(final Allele a) {
            if (a == ref) return 2;                 // ( 0 + 1) << 1
            else if (a == alt1) return 4;           // ( 1 + 1) << 1
            else if (a == Allele.NO_CALL) return 0; // (-1 + 1) << 1
            else {
                final Integer i = alleleMapForTriPlus.get(a);
                if (i == null) throw new IllegalStateException("BUG: Couldn't find allele offset for allele " + a);
                return i;
            }
        }

        private void buildAlleleMap(final VariantContext vc) {
            // ref and alt1 are handled by a fast path when determining the offset
            // so they do not need to be placed in the map
            final int nAlleles = vc.getNAlleles();
            ref = vc.getReference();
            alt1 = nAlleles > 1 ? vc.getAlternateAllele(0) : null;

            if (nAlleles > 2) {
                // for multi-allelics we need to clear the map, and add additional looks
                alleleMapForTriPlus.clear();
                final List<Allele> alleles = vc.getAlleles();
                for (int i = 2; i < alleles.size(); i++) {
                    // Perform encoding here so we only do it once instead of after every lookup
                    alleleMapForTriPlus.put(alleles.get(i), (i + 1) << 1);
                }
            }
        }
    }


    //////////////////////////////////////////////////
    // Exception utilities                          //
    //////////////////////////////////////////////////
    private static TribbleException tooManyValues(final int observed, final int expected, final String key, final VariantContext vc) {
        final String error = "Observed number of values: %d exceeds expected number: %d for attribute: %s in VariantContext: %s";
        return new TribbleException(String.format(error, observed, expected, key, vc));
    }
}
