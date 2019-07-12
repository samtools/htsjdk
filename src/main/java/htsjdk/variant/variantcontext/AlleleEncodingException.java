package htsjdk.variant.variantcontext;

import htsjdk.samtools.util.SequenceUtil;

/**
 * Indicates an error in the encoding for an {@link Allele}, that is typically
 * result of a bad-formed data-file (e.g. VCF)
 */
public class AlleleEncodingException extends RuntimeException {
    public AlleleEncodingException(final String message, Object ... args) {
        super(String.format(message, args));
    }

    public static AlleleEncodingException cannotBeReference(final byte base) {
        throw new AlleleEncodingException(String.format("cannot be reference: '%s'", (char) base));
    }

    public static AlleleEncodingException cannotBeReference(final byte[] encoding) {
        throw new AlleleEncodingException(String.format("cannot be reference: '%s'", encoding));
    }

    public static AlleleEncodingException invalidEncoding(final byte base) {
        if (SequenceUtil.isValidIUPAC(base)) {
            throw new AlleleEncodingException("only a,t,c,g and n are valid IUPAC code in alleles: '" + (char) base + "'");
        } else {
            throw new AlleleEncodingException(String.format("invalid allele encoding: '%s'", (char) base));
        }
    }

    public static AlleleEncodingException invalidEncoding(final byte[] encoding) {
        throw new AlleleEncodingException(String.format("invalid allele encoding: '%s'", new String(encoding)));
    }

    public static AlleleEncodingException emptyEncoding() {
        throw new AlleleEncodingException("empty encoding is invalid");
    }

    public static AlleleEncodingException invalidContigID(final String contig) {
        throw new AlleleEncodingException("invalid contig id: '%s'", contig);
    }

    public static AlleleEncodingException invalidBases(final CharSequence chars) {
        return new AlleleEncodingException("invalid bases provided: '%s'", chars);
    }

    public static AlleleEncodingException invalidBases(final byte[] bases) {
        throw new AlleleEncodingException("invalid bases provided: '%s'", new String(bases));
    }
}
