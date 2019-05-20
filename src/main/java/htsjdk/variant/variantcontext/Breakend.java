package htsjdk.variant.variantcontext;

import htsjdk.samtools.util.*;
import org.apache.commons.lang3.ArrayUtils;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Represents the information about a breakend representable in an VCF allele spec.
 */
public abstract class Breakend implements Serializable, BaseSequence {

    protected final BreakendType type;

    private Breakend(final BreakendType type) {
        this.type = type;
    }

    /**
     * Checks whether an allele spec byte sequence is likely to be a break-end spec.
     * <p>
     *     In order to keep the code efficient, this does not make a full check but
     *     if it return true most likely a call to  {@link Breakend#decode} won't fail on the same array, if we assume
     *     that such spec came from a well-formed VCF.
     * </p>
     * @param spec the allele representation bases as a byte array.
     * @return {@code true} iff the input {@code spec} looks like a valid breakend.
     * @throws NullPointerException if {@code spec} is {@code null}.
     */
    static boolean looksLikeBreakend(final byte[] spec) {
        final int length = spec.length;
        if (length < 2) {
            return false;
        }
        final byte first = spec[0];
        final byte last = spec[length - 1];
        if (first == '.' && last != '.') {
            return true;
        } else if (last == '.' && first != '.') {
            return true;
        } else if ((first == '[' || first == ']') && last != '[' && last != ']') {
            return true;
        } else {
            return first != '[' && first != ']' && (last == '[' || last == ']');
        }
    }

    /**
     * Constructs a single breakend.
     * @param type the single type breakend. Only single types are allowed.
     * @param base the reference aligned base character.
     * @return never {@code null}.
     *
     * @throws NullPointerException if {@code type} is {@code null}.
     * @throws IllegalArgumentException if {@code type} is not a single type.
     * @throws AlleleEncodingException if the {@code base} provided is not a valid
     * base character.
     */
    public static Breakend single(final BreakendType type, final byte base) {
        if (type == null || !type.isSingle()) {
            throw new IllegalArgumentException("bad type");
        }

        if (AlleleUtils.isValidBase(base)) {
            throw AlleleEncodingException.invalidBases(new byte[] { base });
        }
        return new SingleBaseSingleBreakend(type, base);
    }

    public static Breakend single(final BreakendType type, final byte[] bases) {
        switch (bases.length) {
            case 0: throw new AlleleEncodingException("single breakend must have at least one base");
            case 1: return single(type, bases[0]);
            default :
                if (!AlleleUtils.areValidBases(bases)) {
                    throw AlleleEncodingException.invalidBases(bases);
                } else {
                    return new MultiBaseSingleBreakend(type, bases);
                }
        }
    }

    /**
     * Creates a paired breakend given its type properties.
     *
     * <p>
     *     Notice that only valid allele bases are allowed for {@code base} ('a', 'c', 't', 'g', 'n') and
     *     so it is no possible to instanciate a <em>before-contig-start</em> right-forward breakend
     *     whose encoding starts with {@code '.'}.
     * </p>
     * <p>
     *     To create one of these you need to call {@link #beforeContigStart} instead.
     * </p>
     * @param type the paired breakend type. Cannot be a single one nor {@code null}.
     * @param base the single reference aligned base.
     * @param mateContig the location contig for the mate breakend.
     * @param matePosition the location positio for the mate breakend.
     * @param mateContigIsInAssembly whether the mate's contig is in assembly ({@code true}) or reference ({@code false}).
     * @return never {@code null}.
     * @throws NullPointerException if any of {@code type}, {@code bases} or {@code mateContig} is {@code null}.
     * @throws IllegalArgumentException if {@code type} is not paired or {@code matePosition} less or equal to 0.
     * @throws AlleleEncodingException if {@code bases} contain non-valid bases codes
     */
    public static Breakend paired(final BreakendType type, final byte base, final String mateContig, final int matePosition, final boolean mateContigIsInAssembly) {
        if (!AlleleUtils.isValidContigID(mateContig)) {
            throw AlleleEncodingException.invalidContigID(mateContig);
        } else if (type.isSingle()) {
            throw new IllegalArgumentException("bad type cannot be single: " + type);
        } else if (matePosition <= 0) {
            throw new IllegalArgumentException("mate position cannot be negative or 0");
        } else if (!AlleleUtils.isValidBase(base)) {
            if (base == '.') {
                throw new IllegalArgumentException("cannot use base '.' here, please call beforeContigStart(...) instead");
            } else {
                throw AlleleEncodingException.invalidBases(new byte[] { base });
            }
        } else {
            return new SingleBasePairedBreakend(type, base, mateContig, matePosition, mateContigIsInAssembly);
        }
    }

    public static Breakend paired(final BreakendType type, final byte[] bases, final String mateContig, final int matePosition, final boolean mateContigIsInAssembly) {
        switch (bases.length) {
            case 0:
                if (type == BreakendType.RIGHT_FORWARD) {
                    return beforeContigStart(mateContig, matePosition, mateContigIsInAssembly);
                } else {
                    throw new AlleleEncodingException("bad breakend-type '%s'; no bases requires '%s'", type, BreakendType.RIGHT_FORWARD);
                }
            case 1:
                return paired(type, bases[0], mateContig, matePosition, mateContigIsInAssembly);
            default:
                if (!AlleleUtils.isValidContigID(mateContig)) {
                    throw AlleleEncodingException.invalidContigID(mateContig);
                } else if (matePosition <= 0) {
                    throw new AlleleEncodingException("the mate-position must be greater than 0: " + matePosition);
                } else if (!AlleleUtils.areValidBases(bases)) {
                    throw AlleleEncodingException.invalidBases(bases);
                } else {
                    return new MultiBasePairedBreakend(type, bases, mateContig, matePosition, mateContigIsInAssembly);
                }
        }
    }

    /**
     * Creates a brekaned that represent the insertion of sequence before the begining of a
     * reference contig.
     * @param mateContig the mate's breakend contig ID.
     * @param matePosition the mate's breakend position.
     * @param mateContigIsInAssembly whether {@code mateContig} refers to a reference or assemblu
     *
     * @return never {@code null}.
     *
     * @throws NullPointerException if {@code mateContig} is {@code null}.
     * @throws IllegalArgumentException if {@code matePosition} is 0 or negative.
     * @throws AlleleEncodingException if {@code mateContig} is not a valid contig-id.
     */
    public static Breakend beforeContigStart(final String mateContig, final int matePosition, final boolean mateContigIsInAssembly) {
        if (!AlleleUtils.isValidContigID(mateContig)) {
            throw AlleleEncodingException.invalidContigID(mateContig);
        } else if (matePosition <= 0) {
            throw new IllegalArgumentException("mate position cannot be negative or 0");
        } else {
            return new BeforeContigInsertBreakend(mateContig, matePosition, mateContigIsInAssembly);
        }
    }

    /**
     * Returns the allele representation of a breakend.
     * @return never {@code null}.
     */
    public Allele asAllele() {
        return new BreakendAllele(this);
    }

    /**
     * Decodes/parses a breakend from its character/string representation.
     * @param chars the source char sequence.
     * @return never {@code null}.
     * @throws NullPointerException if {@code chars} is {@code null}.
     * @throws AlleleEncodingException if the encoding provided in {@code chars} is not
     * a valid encoding for a breakend.
     */
    public static Breakend decode(final CharSequence chars) {
        final int length = chars.length();
        final byte[] encoding = new byte[length];
        for (int i = 0; i < length; i++) {
            encoding[i] = (byte) chars.charAt(i);
        }
        return decode(encoding);
    }

    /**
     * Decodes/parses a breakend from its byte array representation.
     * @param encoding the source byte array.
     * @return never {@code null}.
     * @throws NullPointerException if {@code encoding} is {@code null}.
     * @throws AlleleEncodingException if the encoding provided in {@code encoding} is not
     * a valid encoding for a breakend.
     */
    public static Breakend decode(final byte[] encoding) {

        final int length = encoding.length;
        if (length < 2) {
            throw new AlleleEncodingException("not a breakend encoding; too short: '%s'", new String(encoding));
        } else if (length == 2) {
            return decodeSingle(encoding);
        } else {
            for (final byte b : encoding) {
                if (b == '[' || b == ']') {
                    return decodePaired(encoding);
                }
            }
            return decodeSingle(encoding);
        }
    }

    /**
     * Proceeds decoding assuming that this is in fact a single typed breakend.
     * <p>
     *     It assumes that the source encoding is of length 2 at least.
     * </p>
     * @param encoding
     * @return never {@code null}.
     * @throws AlleleEncodingException if combination of bytes provided is not a
     * valid encoding for a single typed breakend.
     */
    private static Breakend decodeSingle(final byte[] encoding) {
        final BreakendType type;
        final int length = encoding.length;
        final int first = encoding[0];
        final int last = encoding[length - 1];
        final int basesFrom;
        final int basesTo;
        if (first == '.') {
            type = BreakendType.SINGLE_JOIN;
            basesFrom = 1;
            basesTo = length;
        } else if (last == '.') {
            type = BreakendType.SINGLE_FORK;
            basesFrom = 0;
            basesTo = length -1;
        } else {
            throw AlleleEncodingException.invalidEncoding(encoding);
        }
        if (encoding.length == 2) {
            final byte base = encoding[basesFrom];
            if (!AlleleUtils.isValidBase(base)) {
                throw AlleleEncodingException.invalidEncoding(encoding);
            } else {
                return new SingleBaseSingleBreakend(type, base);
            }
        } else {
            if (!AlleleUtils.areValidBases(encoding, basesFrom, basesTo)) {
                throw AlleleEncodingException.invalidEncoding(encoding);
            } else {
                return new MultiBaseSingleBreakend(type, Arrays.copyOfRange(encoding, basesFrom, basesTo));
            }
        }
    }

    /**
     * Proceeds assuming the spec is a mated (non-single) break-end.
     * It is provided the correct location for the first braket and its value.
     * @param encoding the full String spec for the breakend.
     * @return never {@code null}.
     */
    private static Breakend decodePaired(final byte[] encoding) {
        final int length = encoding.length;
        final byte first = encoding[0];
        final byte last = encoding[length - 1];
        final byte bracket;
        final int left;
        final int right;
        if (first == '[' || first == ']') {
            bracket = first;
            left = 0;
            if ((right = ArrayUtils.lastIndexOf(encoding, bracket)) <= left) {
                throw new AlleleEncodingException("bad paired break-end encoding missing right bracket (%s): '%s'", bracket, new String(encoding));
            }
        } else if (last == '[' || last == ']') {
            bracket = last;
            right = length - 1;
            left = ArrayUtils.indexOf(encoding, bracket);
            if ((left <= 0 || left == right)) {
                throw new AlleleEncodingException("bad paired break-end encoding missing left bracket (%s): '%s'", bracket, new String(encoding));
            }
        } else {
            throw new AlleleEncodingException("bad paired break-end encoding; first or last byte must be a bracket ('[' or ']'): '%s'", new String(encoding));
        }
        int colon = ArrayUtils.lastIndexOf(encoding, (byte) ':', right - 1);
        if (colon < 0) {
            throw new AlleleEncodingException("missing colon in mate location: '%s'", new String(encoding));
        } else if (colon <= left) {
            throw new AlleleEncodingException("bad paired break-end encoding; found colon (:) before left bracket: '%s'", new String(encoding));
        }
        final boolean mateContigIsOnAssembly = colon - left >= 2 && encoding[left + 1] == '<' && encoding[colon - 1] == '>';
        final String contig = mateContigIsOnAssembly ? new String(encoding, left + 2, colon - left - 3)
                : new String(encoding, left + 1, colon - left - 1);
        if (!AlleleUtils.isValidContigID(contig)) {
            throw new AlleleEncodingException("bad mate contig name (%s): '%s'", contig, new String(encoding));
        }
        final int position = parseUnsigedPosition(encoding, colon + 1, right);
        final boolean isLeftBreakend = bracket == ']';
        final boolean isForwardBreakend = (bracket == '[' && left > 0) || (bracket == ']' && left == 0);
        final BreakendType type = BreakendType.paired(isLeftBreakend, isForwardBreakend);
        final int numberOfBases = length - (right - left + 1);
        switch (numberOfBases) {
            case 0: throw new AlleleEncodingException("no bases in encoding: '%s'", new String(encoding));
            case 1:
                final byte base = type.startsWithBase() ? first : last;
                if (type == BreakendType.RIGHT_FORWARD && base == '.') {
                    return new BeforeContigInsertBreakend(contig, position, mateContigIsOnAssembly);
                } else if (!AlleleUtils.isValidBase(base)) {
                    throw AlleleEncodingException.invalidEncoding(encoding);
                } else {
                    return new SingleBasePairedBreakend(type, base, contig, position, mateContigIsOnAssembly);
                }
            default:
                final byte[] bases = type.startsWithBase()
                        ? Arrays.copyOfRange(encoding, 0, left)
                        : Arrays.copyOfRange(encoding, right + 1, length);
                if (!AlleleUtils.areValidBases(bases)) {
                    throw AlleleEncodingException.invalidBases(bases);
                } else {
                    return new MultiBasePairedBreakend(type, bases, contig, position, mateContigIsOnAssembly);
                }
        }
    }

    private static int parseUnsigedPosition(final byte[] spec, final int from, final int to) {
        if (from >= to) {
            throw new AlleleEncodingException("bad paired-breakend encode; mate contig position has length 0: '%s'", new String(spec));
        } else {
            int result = 0;
            for (int i = from; i < to; i++) {
                byte b = spec[i];
                if (b < '0' || b > '9') {
                    throw new AlleleEncodingException("bad paired-breakend encode; mate contig position contain non-digit characters (%s): '%s'", (char) b, new String(spec));
                } else {
                    result = result * 10 + (b - '0');
                }
            }
            return result;
        }
    }

    /**
     * Access this breakend type.
     * @return never {@code null}.
     */
    public BreakendType getType() {
        return type;
    }


    /**
     * Checks whether this breakend is a single type breakend.
     * @return {@code true} iff this is a single type breakend.
     */
    public abstract boolean isSingle();

    /**
     * Checks whether this breakend is a paired type breakend.
     * @return {@code true} iff this is a paired type breakend
     */
    public abstract boolean isPaired();

    /**
     * Returns the contig for the mate break-end if known.
     * <p>Otherwise it return {@code null}, for example if this is a
     * single typed breakend.
     * </p>
     *
     * @return might be {@code null}
     */
    public abstract String getMateContig();

    /**
     * Encodes the breakend back into a string.
     * @return never {@code null}.
     */
    public abstract String encodeAsString();

    /**
     * Checks whether the mate-contig it is an assembly contig/sequence.
     * <p>
     *     As per the VCF spec, assembly contigs are specified by enclosing their names in
     *     angled brackets.
     * </p>
     * <p>
     *     For single breakends that do not have a mate, this method will return {@code false}
     * </p>
     * <p>
     *     For example:
     * </p>
     * <code>
     *     Breakend.of("A[<seq1>:124912[").mateIsOnAssemblyContig() == true
     *     Breakend.of("A[13:312451[").mateIsOnAssemblyContig() == false
     *     Breakend.of("A.").mateIsOnAssemblyContig() == false
     * </code>
     *
     * @return {@code true} iff the breakend is a paired one and the mate contig belongs
     * to an assembly file.
     */
    public abstract boolean mateIsOnAssemblyContig();

    /**
     * Position of the mate break-end using 1-based indexing.
     * <p>
     *     When there is no mate this will return -1.
     * </p>
     * @return -1 or 1 or greater.
     */
    public abstract int getMatePosition();

    /**
     * Returns a 1-bp sized locatable indicating the contig and position of the mate-break end.
     * @return never {@code null}.
     */
    public Locatable getMateLocation() {
        if (isPaired()) {
            return new Locatable() {

                @Override
                public String getContig() {
                    return getMateContig();
                }

                @Override
                public int getStart() {
                    return getMatePosition();
                }

                @Override
                public int getEnd() {
                    return getMatePosition();
                }
            };
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return encodeAsString();
    }

    private static abstract class AbstractPairedBreakend extends Breakend {

        private static final long serialVersionUID = 1;

        final String mateContig;
        final int matePosition;
        final boolean mateContigIsInAssembly;

        private AbstractPairedBreakend(final BreakendType type, final String mateContig,
                                       final int matePosition, final boolean mateContigIsInAssembly) {
            super(type);
            this.mateContig = mateContig;
            this.matePosition = matePosition;
            this.mateContigIsInAssembly = mateContigIsInAssembly;
        }

        abstract StringBuilder appendBases(final StringBuilder builder);

        @Override
        public String getMateContig() {
            return mateContig;
        }

        @Override
        public boolean mateIsOnAssemblyContig() {
            return mateContigIsInAssembly;
        }

        @Override
        public int getMatePosition() {
            return matePosition;
        }

        @Override
        public String encodeAsString() {
            // 14 = [ + ] + .? + : + <>? + max_digits_int (10)
            final StringBuilder builder = new StringBuilder( + mateContig.length() + 17);
            final char bracket = type.isRightSide() ? '[' : ']';
            final boolean startWithBase = type.startsWithBase();
            if (startWithBase) {
                appendBases(builder);
            }
            builder.append(bracket);
            if (mateContigIsInAssembly) {
                builder.append('<').append(mateContig).append('>');
            } else {
                builder.append(mateContig);
            }
            builder.append(':').append(matePosition).append(bracket);
            if (!startWithBase) {
                appendBases(builder);
            }
            return builder.toString();
        }

        @Override
        public boolean isSingle() {
            return false;
        }

        @Override
        public boolean isPaired() {
            return true;
        }
    }

    private static final class MultiBasePairedBreakend extends AbstractPairedBreakend {

        private static final long serialVersionUID = 1;

        private final byte[] bases;

        private MultiBasePairedBreakend(final BreakendType type, final byte[] bases, final String mateContig, final int matePosition, final boolean mateContigIsInAssembly) {
            super(type, mateContig, matePosition, mateContigIsInAssembly);
            this.bases = bases;
        }

        @Override
        StringBuilder appendBases(final StringBuilder builder) {
            for (int i = 0; i < bases.length; i++) {
                builder.append((char) bases[i]);
            }
            return builder;
        }
        @Override
        public int numberOfBases() {
            return bases.length;
        }

        @Override
        public byte baseAt(final int index) {
            return bases[index];
        }

        @Override
        public boolean equals(final Object other) {
            return this == other || (other instanceof MultiBasePairedBreakend && equals((MultiBasePairedBreakend) other));
        }

        @Override
        public int hashCode() {
            return (((((SequenceUtil.hashCode(bases) * 31) + type.hashCode()) * 31)
                    + mateContig.hashCode()) * 31 + matePosition * 31);
        }

        private boolean equals(final MultiBasePairedBreakend other) {
            return other == this || (other.type == type && SequenceUtil.equals(other.bases, bases) && mateContigIsInAssembly == other.mateContigIsInAssembly && mateContig.equals(other.mateContig) && matePosition == other.matePosition);
        }
    }

    private static final class BeforeContigInsertBreakend extends AbstractPairedBreakend {

        private BeforeContigInsertBreakend(String mateContig, int matePosition, boolean mateContigIsInAssembly) {
            super(BreakendType.RIGHT_FORWARD, mateContig, matePosition, mateContigIsInAssembly);
        }

        @Override
        StringBuilder appendBases(final StringBuilder builder) {
            return builder.append('.');
        }

        @Override
        public int numberOfBases() {
            return 0;
        }

        @Override
        public byte baseAt(int index) {
            throw new IndexOutOfBoundsException();
        }

        @Override
        public boolean equals(final Object other) {
            return other == null || (other instanceof BeforeContigInsertBreakend && equals((BeforeContigInsertBreakend) other));
        }

        @Override
        public int hashCode() {
            return (( (mateContig.hashCode() * 31) + matePosition ) * 31 + Boolean.hashCode(mateContigIsInAssembly)) * 31;
        }

        private boolean equals(final BeforeContigInsertBreakend other) {
            return other.type == type &&
                    other.mateContig.equals(this.mateContig) &&
                    other.matePosition == this.matePosition &&
                    other.mateContigIsInAssembly == this.mateContigIsInAssembly;
        }
    }

    private static final class SingleBasePairedBreakend extends AbstractPairedBreakend {

        private static final long serialVersionUID = 1L;
        private final byte base;

        private SingleBasePairedBreakend(final BreakendType type, final byte base, final String mateContig,
                                         final int matePosition, final boolean mateContigIsInAssembly) {
            super(type, mateContig, matePosition, mateContigIsInAssembly);
            this.base = base;
        }

        @Override
        StringBuilder appendBases(final StringBuilder builder) {
            return builder.append((char) base);
        }

        @Override
        public int hashCode() {
            return (((((SequenceUtil.hashCode(base) * 31) + type.hashCode()) * 31)
                    + mateContig.hashCode()) * 31 + matePosition * 31);
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof SingleBasePairedBreakend && equals((SingleBasePairedBreakend) other);
        }

        private boolean equals(final SingleBasePairedBreakend other) {
            return SequenceUtil.basesEqual(base, other.base) && type == other.type
                    && mateContigIsInAssembly == other.mateContigIsInAssembly
                    && mateContig.equals(other.mateContig)
                    && matePosition == other.matePosition;
        }

        @Override
        public int numberOfBases() {
            return 1;
        }

        @Override
        public byte baseAt(final int index) {
            if (index != 0) {
                throw new IndexOutOfBoundsException();
            }
            return base;
        }
    }

    private abstract static class AbstractSingleBreakend extends Breakend {

        private static final long serialVersionUID = 1;

        private AbstractSingleBreakend(final BreakendType type) {
            super(type);
        }

        @Override
        public String getMateContig() {
            return null;
        }

        @Override
        public boolean mateIsOnAssemblyContig() {
            return false;
        }

        @Override
        public int getMatePosition() {
            return -1;
        }

        @Override
        public boolean isSingle() {
            return true;
        }

        @Override
        public boolean isPaired() {
            return false;
        }


    }

    private final static class SingleBaseSingleBreakend extends AbstractSingleBreakend {

        private static final long serialVersionUID = 1;
        private final byte base;

        private SingleBaseSingleBreakend(final BreakendType type, final byte base) {
            super(type);
            this.base = base;
        }

        @Override
        public int numberOfBases() {
            return 1;
        }

        @Override
        public byte baseAt(final int index) {
            if (index != 0) {
                throw new IndexOutOfBoundsException();
            }
            return base;
        }

        @Override
        public int hashCode() {
            return (SequenceUtil.hashCode(base) * 31 + type.hashCode()) * 31;
        }

        @Override
        public boolean equals(final Object other) {
            return other == this || other instanceof SingleBaseSingleBreakend && equals((SingleBaseSingleBreakend) other);
        }

        private boolean equals(final SingleBaseSingleBreakend other) {
            return other == this || (other.type == type && SequenceUtil.basesEqual(other.base, base));
        }

        @Override
        public String encodeAsString() {
            final char[] chars;
            if (type.startsWithBase()) {
                chars = new char[] { (char) base, '.' };
            } else {
                chars = new char[] { '.', (char) base };
            }
            return new String(chars);
        }

    }

    private final static class MultiBaseSingleBreakend extends AbstractSingleBreakend {

        private static final long serialVersionUID = 1;
        private final byte[] bases;

        private MultiBaseSingleBreakend(final BreakendType type, final byte[] bases) {
            super(type);
            this.bases = bases;
        }

        @Override
        public int numberOfBases() {
            return bases.length;
        }

        @Override
        public byte baseAt(final int index) {
            return bases[index];
        }

        @Override
        public int hashCode() {
            return (SequenceUtil.hashCode(bases) * 31 + type.hashCode()) * 31;
        }

        @Override
        public boolean equals(final Object other) {
            return other == this || other instanceof MultiBaseSingleBreakend && equals((MultiBaseSingleBreakend) other);
        }

        private boolean equals(final MultiBaseSingleBreakend other) {
            return other == this || (other.type == type && SequenceUtil.equals(other.bases, bases));
        }

        @Override
        public String encodeAsString() {
            final char[] chars = new char[bases.length + 1];
            if (type.startsWithBase()) {
                for (int i = 0; i < bases.length; i++) {
                    chars[i] = (char) bases[i];
                }
                chars[chars.length - 1] = '.';
            } else {
                for (int i = chars.length - 1; i > 0;) {
                    chars[i] = (char) bases[--i];
                }
                chars[0] = '.';
            }
            return new String(chars);
        }
    }

}

