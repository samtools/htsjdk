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

package htsjdk.variant.variantcontext;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Some package private common utilities on Alleles.
 */
final class AlleleUtils {

    private static final Map<String, Allele> SYMBOLIC_BY_ID = new HashMap<>();

    private static final int MAX_PRINT_ASCII = 126; // ~

    /**
     * A position is {@code true} if the i-th ASCII code (0-based) is a valid
     * base/nucleotide character.
     */
    private static final boolean[] validBases = new boolean[MAX_PRINT_ASCII + 1];

    /**
     * A position is true if the i-th ASCII code (0-based) is a valid character as part of
     * a contig ID.
     * <p>
     *     Note that '*' and '=' are still not allowed in the first position of the ID.
     * </p>
     */
    private static final boolean[] validContigIDCharacters = new boolean[MAX_PRINT_ASCII + 1];

    private static final boolean[] validSymbolicIDCharacters = new boolean[MAX_PRINT_ASCII + 1];

    static {
        "aAcCgGtTnN".chars().forEach(ch -> validBases[ch] = true);

        Arrays.fill(validContigIDCharacters, '!',  validContigIDCharacters.length, true);
        "\\,\"()'`[]{}<>".chars().forEach(ch -> validContigIDCharacters[ch] = false);

        Arrays.fill(validSymbolicIDCharacters, '!', validSymbolicIDCharacters.length, true);
        validSymbolicIDCharacters['<'] = validSymbolicIDCharacters['>'] = false;

    }

    private AlleleUtils() {}

    /**
     * Checks whether a character is a valid base to appear in an allele encoding.
     * @param base the character to test.
     * @return {@code true} iff {@code base} is a valid.
     */
    private static boolean isValidBase(final char base) {
        return base <= MAX_PRINT_ASCII && validBases[base];
    }

    /**
     * Checks whether a byte is a valid base to appear in an allele encoding.
     * @param base the byte to test.
     * @return {@code true} iff {@code base} is a valid.
     */
    static boolean isValidBase(final byte base) {
        return base > 0 && base <= MAX_PRINT_ASCII && validBases[base];
    }

    /**
     * Validates and extracts the bases from an char sequence.
     * @param chars never {@code null}.
     * @return never {@code null}.
     * @throws NullPointerException if {@code chars} is {@code null}.
     * @throws AlleleEncodingException if {@code chars} contains non-valid base characters.
     */
    static byte[] extractBases(final CharSequence chars) {
        final int length = chars.length();
        final byte[] result = new byte[length];
        for (int i = 0; i < length; i++) {
            final char ch = chars.charAt(i);
            if (!isValidBase(ch)) {
                throw AlleleEncodingException.invalidBases(chars);
            }
            result[i] = (byte) ch;
        }
        return result;
    }

    /**
     * Checks the content of an char sequence for invalid base representations.
     * @param bases the sequence to test.
     * @return {@code true} iff {@code chars} only contains valid bases char representations.
     * @throws NullPointerException if {@code chars} is {@code null}.
     */
    static boolean areValidBases(final byte[] bases) {
        return areValidBases(bases, 0, bases.length);
    }

    /**
     * Checks whether a range in a byte array is exclusively composed of base characters
     * acceptable in an allele inline sequence.
     * <p>
     *     These include exclusively: a, c, g, t, n, A, C, G, T and N.
     * </p>
     *
     * @param source the target byte array containing the bases.
     * @param from the start index of the range to be assessed; 0-based index.
     * @param to the end index of the range to be assessed, the last position + 1 (so
     *           excluded from the range).
     * @return {@code true} iff all bases character in the range are acceptable.
     * @throws NullPointerException if {@code source} is {@code null} even if the
     * range is empty.
     * @throws IndexOutOfBoundsException if the index range provided isn't empty and points
     *  to positions outside the boundaries of the input {@code source} array.
     */
    static boolean areValidBases(final byte[] source, final int from, final int to) {
        if (to <= from) {
            Objects.requireNonNull(source);
            return true;
        } else {
            for (int i = from; i < to; i++) {
                if (!isValidBase(source[i])) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Checks whether an string is a valid contig id.
     * @param id the id to test.
     * @throws NullPointerException if {@code id} is {@code null}.
     * @return {@code true} iff {@code id} is valid.
     */
    static boolean isValidContigID(final String id) {
        if (id.isEmpty()) {
            return false;
        } else {
            final char first = id.charAt(0);
            if (first > MAX_PRINT_ASCII || !validContigIDCharacters[first] || first == '*'
                    || first == '=') {
                return false;
            }
            final int length = id.length();
            for (int i = 1; i < length; i++) {
                final char ch = id.charAt(i);
                if (ch > MAX_PRINT_ASCII || !validContigIDCharacters[ch]) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Checks whether a given ID is a valid symbolic allele ID.
     * @param id to test.
     * @return {@code true} iff {@code id} is a valid symbolic ID.
     */
    static boolean isValidSymbolicID(final String id) {
        if (id.isEmpty()) {
            return false;
        } else {
            final int length = id.length();
            for (int i = 0; i < length; i++) {
                final char ch = id.charAt(i);
                if (ch > MAX_PRINT_ASCII || !validSymbolicIDCharacters[ch]) {
                    return false;
                }
            }
            return true;
        }
    }

    static Allele registerSymbolic(final Allele allele) {
        if (!allele.isSymbolic()) {
            throw new IllegalArgumentException("the input allele must be symbolic");
        } else {
            final String id = allele.getSymbolicID();
            if (id == null) {
                throw new IllegalArgumentException("the input allele must have an ID");
            } else {
                final Allele previous = SYMBOLIC_BY_ID.get(id);
                if (previous == null) {
                    SYMBOLIC_BY_ID.put(id, allele);
                    return allele;
                } else if (!previous.equals(allele)) {
                    throw new IllegalArgumentException("trying to register two different symbolic alleles under the same ID: " + id);
                } else {
                    return previous;
                }
            }
        }
    }

    /**
     * Adds elements to the common symbolic alleles to the static list above.
     * <p>
     *     Whether these symbolic are structural and their structural variant type would be
     *     determine by their ID. See {@link StructuralVariantType#fromSymbolicID(String)} for
     *     details.
     * </p>
     * @param id the symbolic id for the new registered allele.
     * @param svType the desired SV type for this id if any; can be null.
     * @return if no such symbolic was registered, the new allele instead otherwise the old one.
     */
    static Allele registerSymbolic(final String id, final StructuralVariantType svType) {
        final Allele result = SYMBOLIC_BY_ID.computeIfAbsent(id, _id -> new PlainSymbolicAllele(_id, svType));
        if (result.getStructuralVariantType() != svType) {
            throw new IllegalArgumentException(String.format("colliding svType specs for symbolic ID '%s'; trying to register with %s when it has been already registered with %s",
                    id, svType == null ?"no SV type" : svType, result.getStructuralVariantType() == null ? "no SV type" : result.getStructuralVariantType()));
        }
        return result;
    }

    static Allele lookupSymbolic(final String id) {
        Objects.requireNonNull(id);
        return SYMBOLIC_BY_ID.get(id);
    }


    /**
     * Composes an allele given its encoding string and whether it is reference
     * or alternative.
     *
     * @param encoding        the specification string.
     * @param isReference whether the new allele is supposed to be a reference allele ({@code true}) or an alternative allele ({@code false}_.
     * @return never {@code null}
     * @throws IllegalArgumentException if the input spec and reference status combination results
     *                                  in an invalid allele.
     */
    static Allele decode(final CharSequence encoding, final boolean isReference) {
        return encoding.length() == 1 ? decode((byte) encoding.charAt(0), isReference)
                : decode(extractBytes(encoding), isReference, true);
    }

    /**
     * Transform a char sequence into a byte array.
     */
    private static byte[] extractBytes(final CharSequence encoding) {
        final int length = encoding.length();
        final byte[] result = new byte[length];
        for (int i = 0; i < length; i++) {
            result[i] = (byte) encoding.charAt(i);
        }
        return result;
    }

    /**
     * Decode a single byte Allele encoding.
     * @param encoding to decode into an {@link Allele} instance.
     * @param isReference whether the resulting allele must be reference or not.
     * @return never {@code null}.
     * @throws NullPointerException if {@code encoding} is {@code null}.
     * @throws AlleleEncodingException if {@code encoding} is invalid or it cannot be reference and
     * {@code isReference} is set to {@code true}
     */
    static Allele decode(final byte encoding, final boolean isReference) {
        if (encoding < 0 || encoding > MAX_PRINT_ASCII) {
            throw AlleleEncodingException.invalidEncoding(encoding);
        } else if (validBases[encoding]) {
            return decodeSingleBaseInline(encoding, isReference);
        } else {
            final Allele result;
            switch (encoding) {
                case '.': result = Allele.NO_CALL; break;
                case '*': result = Allele.SPAN_DEL; break;
                default:
                    throw AlleleEncodingException.invalidEncoding(encoding);
            }
            if (isReference) {
                throw AlleleEncodingException.cannotBeReference(encoding);
            }
            return result;
        }
    }

    static Allele decodeSingleBaseInline(byte encoding, boolean isReference) {
        switch (encoding) {
            case 'a': case 'A': return isReference ? Allele.REF_A : Allele.ALT_A;
            case 'c': case 'C': return isReference ? Allele.REF_C : Allele.ALT_C;
            case 'g': case 'G': return isReference ? Allele.REF_G : Allele.ALT_G;
            case 't': case 'T': return isReference ? Allele.REF_T : Allele.ALT_T;
            case 'n': case 'N': return isReference ? Allele.REF_N : Allele.ALT_N;
            default:
                throw new AlleleEncodingException("not a valid base '%s'", (char) encoding);
        }
    }

    /**
     * Decode a single byte Allele encoding.
     * @param encoding to decode into an {@link Allele} instance.
     * @param isReference whether the resulting allele must be reference or not.
     * @param canRepurposeEncoding whether is safe to reuse the input {@code encoding}
     *                             in the implementation of the returned allele.
     * @return never {@code null}.
     * @throws NullPointerException if {@code encoding} is {@code null}.
     * @throws AlleleEncodingException if {@code encoding} is invalid or it cannot be reference and
     * {@code isReference} is set to {@code true}
     */
    static Allele decode(final byte[] encoding, final boolean isReference,
                                 final boolean canRepurposeEncoding) {
        if (encoding.length == 0) {
            throw AlleleEncodingException.emptyEncoding();
        } else if (encoding.length == 1) {
            return decode(encoding[0], isReference);
        } else if (areValidBases(encoding)) {
            return new MultiBaseInLineAllele(canRepurposeEncoding ? encoding : encoding.clone(), isReference);
        } else {
            final Allele result;
            final byte firstByte = encoding[0];
            switch (firstByte) {
                case '<':
                    result = decodeSimpleSymbolic(encoding); break;
                case '[':  case ']': case '.':
                    result = Breakend.decode(encoding).asAllele(); break;
                default: {
                    final byte lastByte = encoding[encoding.length - 1];
                    switch (lastByte) {
                        case '>':
                            result = decodeContigInsertion(encoding); break;
                        case ']':  case '[': case '.':
                            result = Breakend.decode(encoding).asAllele(); break;
                        default:
                            throw AlleleEncodingException.invalidEncoding(encoding);
                    }
                }
            }
            if (isReference) {
                throw AlleleEncodingException.cannotBeReference(encoding);
            } else {
                return result;
            }
        }
    }

    // can assume that encode is not null and at least 2 bytes long and tha the last base is '>'
    // and it does not start with '<'.
    private static Allele decodeContigInsertion(final byte[] encode) {
        final int length = encode.length;
        if (length < 3) { // 2 at best would be a empty-name symbolic allele : "<>"
            throw new AlleleEncodingException("assembly contig insert encode must be at least 3 characters long: '%s'" + new String(encode));
        }
        final int left = ArrayUtils.indexOf(encode, (byte) '<', 1); // cannot start with '<' since that is a symbolic.
        if (left < 0) {
            throw new AlleleEncodingException("could not find open angle bracket '<' in assembly contig insert encode: '%s'", new String(encode));
        } else if (!areValidBases(encode, 0, left)) {
            throw new AlleleEncodingException("non-valid bases in assembly contig insert encode: '%s'" + new String(encode));
        } else {
            final byte[] bases = Arrays.copyOfRange(encode, 0, left);
            final String contigName = new String(encode, left + 1, length - left - 2);
            return new ContigInsertAllele(bases, contigName);
        }
    }

    // We can assume that its not null, starts with '<' and that at least is two byte long.
    private static Allele decodeSimpleSymbolic(final byte[] encode) {
        final int lastIndex = encode.length - 1;
        if (encode[lastIndex] != '>') {
            throw new AlleleEncodingException("opening '<' not close at the end: '%s'", new String(encode));
        } else {
            final String name = new String(encode, 1, encode.length - 2);
            final Allele registered = SYMBOLIC_BY_ID.get(name); // that must cover special ones like <*> or <NON_REF>
            return registered != null ? registered : new PlainSymbolicAllele(name, StructuralVariantType.fromSymbolicID(name));
        }
    }

    ///////////////////////
    // Deprecated methods:

}
