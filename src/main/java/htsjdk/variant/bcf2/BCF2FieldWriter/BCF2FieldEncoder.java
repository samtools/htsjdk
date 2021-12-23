package htsjdk.variant.bcf2.BCF2FieldWriter;

import htsjdk.tribble.TribbleException;
import htsjdk.variant.bcf2.BCF2Encoder;
import htsjdk.variant.bcf2.BCF2Type;
import htsjdk.variant.bcf2.BCF2Utils;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFCompoundHeaderLine;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

abstract class BCF2FieldEncoder {

    final BCF2Encoder encoder;

    BCF2Type type;

    /*
    The number of VCF values this encoder has seen, taking the maximum over all objects loaded.
    This value is not identical to either the number of Java objects loaded or the BCF2 typing byte length
    but is primarily useful for checking that the number of VCF values matches the header's declared count.

    For example, for a writer of type Character having loaded the String "abc", nValues is 3 matching its typing byte,
    while for a writer of type String having loaded the String "abc", nValues is 1, but its typing byte length is 3.
     */
    int nValues;

    BCF2FieldEncoder(final BCF2Encoder encoder) {
        this.encoder = encoder;
    }

    abstract void load(final Object o);

    void encodeType() throws IOException {
        encoder.encodeType(nValues, type);
    }

    void checkNValues(final VCFCompoundHeaderLine headerLine, final VariantContext vc) {
        final int expectedValues = headerLine.getCount(vc);
        if (nValues > expectedValues)
            throw BCF2FieldWriter.tooManyValues(nValues, expectedValues, headerLine.getKey(), vc);
        nValues = expectedValues;
    }

    abstract void encode() throws IOException;


    static class AtomicIntFieldEncoder extends BCF2FieldEncoder {

        private final List<Integer> vs = new ArrayList<>();

        AtomicIntFieldEncoder(final BCF2Encoder encoder) {
            super(encoder);
            type = BCF2Type.INT8;
            nValues = 1;
        }

        @Override
        void load(final Object o) {
            if (o == null) {
                vs.add(null);
            } else if (o instanceof Integer) {
                final Integer v = (Integer) o;
                type = BCF2Utils.maxIntegerType(type, BCF2Utils.determineIntegerType(v));
                vs.add(v);
            } else {
                throw BCF2FieldEncoder.incompatibleType(o, type);
            }
        }

        @Override
        void encode() throws IOException {
            encoder.encodeRawVecInt(vs, type);
            vs.clear();
            type = BCF2Type.INT8;
        }
    }

    static class AtomicFloatFieldEncoder extends BCF2FieldEncoder {

        private final List<Double> vs = new ArrayList<>();

        AtomicFloatFieldEncoder(final BCF2Encoder encoder) {
            super(encoder);
            type = BCF2Type.FLOAT;
            nValues = 1;
        }

        @Override
        void load(final Object o) {
            if (o == null) {
                vs.add(null);
            } else if (o instanceof Double) {
                vs.add((Double) o);
            } else {
                throw BCF2FieldEncoder.incompatibleType(o, type);
            }
        }

        @Override
        void encode() throws IOException {
            encoder.encodeRawVecFloat(vs);
            vs.clear();
        }
    }

    static class CharFieldEncoder extends BCF2FieldEncoder {

        // TODO see https://github.com/samtools/hts-specs/issues/618
        // private static final byte[] MISSING = new byte[] {(byte) BCF2Type.CHAR.getMissingBytes()};
        private static final byte[] EMPTY = new byte[0];

        private final List<byte[]> vs = new ArrayList<>();

        CharFieldEncoder(final BCF2Encoder encoder) {
            super(encoder);
            type = BCF2Type.CHAR;
            nValues = 0;
        }

        @Override
        void load(final Object o) {
            if (o == null) {
                vs.add(EMPTY);
            } else if (o instanceof String) {
                final byte[] b = ((String) o).getBytes(StandardCharsets.UTF_8);
                nValues = Math.max(nValues, b.length);
                vs.add(b);
            } else if (o instanceof List) {
                final List<String> strings = (List<String>) o;
                nValues = Math.max(nValues, strings.size());
                final ByteBuffer buff = ByteBuffer.allocate(strings.size());
                for (final String s : strings) {
                    if (s == null) {
                        buff.put((byte) type.getMissingBytes());
                    } else if (s.length() > 1) {
                        throw new TribbleException("Value of VCF type Character is a string with more than 1 character: " + s);
                    } else {
                        buff.put(s.getBytes(StandardCharsets.UTF_8)[0]);
                    }
                }
                vs.add(buff.array());
            } else {
                throw BCF2FieldEncoder.incompatibleType(o, type);
            }
        }

        @Override
        void encode() {
            for (final byte[] v : vs) {
                encoder.encodeRawString(v, nValues);
            }
            vs.clear();
            nValues = 0;
        }
    }

    static class StringFieldEncoder extends BCF2FieldEncoder {

        private static final byte[] EMPTY = new byte[0];

        private final List<byte[]> vs = new ArrayList<>();
        private int charLength;

        StringFieldEncoder(final BCF2Encoder encoder) {
            super(encoder);
            type = BCF2Type.CHAR;
            nValues = 0;
            charLength = 0;
        }

        @Override
        void load(final Object o) {
            if (o == null) {
                vs.add(EMPTY);
            } else {
                final byte[] v;
                final int stringsSeen;
                if (o instanceof String) {
                    v = ((String) o).getBytes(StandardCharsets.UTF_8);
                    stringsSeen = 1;
                } else if (o instanceof List) {
                    final List<String> strings = (List<String>) o;
                    v = encoder.compactStrings(strings);
                    stringsSeen = strings.size();
                } else if (o instanceof String[]) {
                    final String[] strings = (String[]) o;
                    v = encoder.compactStrings(strings);
                    stringsSeen = strings.length;
                } else {
                    throw BCF2FieldEncoder.incompatibleType(o, type);
                }

                vs.add(v);
                nValues = Math.max(nValues, stringsSeen);
                charLength = Math.max(charLength, v.length);
            }
        }

        @Override
        void encodeType() throws IOException {
            encoder.encodeType(charLength, type);
        }

        @Override
        void encode() {
            for (final byte[] v : vs) {
                encoder.encodeRawString(v, charLength);
            }
            vs.clear();
            nValues = 0;
            charLength = 0;
        }
    }

    static class VecIntFieldEncoder extends BCF2FieldEncoder {

        private final List<Object> vs = new ArrayList<>();

        VecIntFieldEncoder(final BCF2Encoder encoder) {
            super(encoder);
            type = BCF2Type.INT8;
            nValues = 0;
        }

        @Override
        void load(final Object o) {
            if (o != null) {
                if (o instanceof List) {
                    final List<Integer> v = (List<Integer>) o;
                    type = BCF2Utils.maxIntegerType(type, BCF2Utils.determineIntegerType(v));
                    nValues = Math.max(nValues, v.size());
                } else if (o instanceof Integer) {
                    final Integer v = (Integer) o;
                    type = BCF2Utils.maxIntegerType(type, BCF2Utils.determineIntegerType(v));
                    nValues = Math.max(nValues, 1);
                } else if (o instanceof int[]) {
                    final int[] v = (int[]) o;
                    type = BCF2Utils.maxIntegerType(type, BCF2Utils.determineIntegerType(v));
                    nValues = Math.max(nValues, v.length);
                } else {
                    // TODO do we need to support Integer[] ?
                    throw BCF2FieldEncoder.incompatibleType(o, type);
                }
            }
            vs.add(o);
        }

        @Override
        void encode() throws IOException {
            if (nValues > 0) {
                for (final Object o : vs) {
                    final int valuesWritten;
                    if (o == null) {
                        valuesWritten = 0;
                    } else if (o instanceof List) {
                        final List<Integer> v = (List<Integer>) o;
                        encoder.encodeRawVecInt(v, type);
                        valuesWritten = v.size();
                    } else if (o instanceof Integer) {
                        final Integer v = (Integer) o;
                        encoder.encodeRawInt(v, type);
                        valuesWritten = 1;
                    } else if (o instanceof int[]) {
                        final int[] v = (int[]) o;
                        encoder.encodeRawVecInt(v, type);
                        valuesWritten = v.length;
                    } else {
                        throw BCF2FieldEncoder.incompatibleType(o, type);
                    }
                    // In order to produce output that bcftools can interpret, we always write one MISSING
                    // value even if the input is entirely absent, which we would otherwise write as a vector of
                    // all EOV values
                    if (valuesWritten == 0) {
                        encoder.encodeRawMissingValue(type);
                    }
                    encoder.encodePaddingValues(nValues - Math.max(valuesWritten, 1), type);
                }
            }
            vs.clear();
            type = BCF2Type.INT8;
            nValues = 0;
        }
    }

    static class VecFloatFieldEncoder extends BCF2FieldEncoder {

        private final List<Object> vs = new ArrayList<>();

        VecFloatFieldEncoder(final BCF2Encoder encoder) {
            super(encoder);
            type = BCF2Type.FLOAT;
            nValues = 0;
        }

        @Override
        void load(final Object o) {
            if (o != null) {
                if (o instanceof List) {
                    final List<Double> v = (List<Double>) o;
                    nValues = Math.max(nValues, v.size());
                } else if (o instanceof Double) {
                    nValues = Math.max(nValues, 1);
                } else if (o instanceof double[]) {
                    final double[] v = (double[]) o;
                    nValues = Math.max(nValues, v.length);
                } else {
                    // TODO do we need to support Double[] ?
                    throw BCF2FieldEncoder.incompatibleType(o, type);
                }
            }
            vs.add(o);
        }

        @Override
        void encode() throws IOException {
            if (nValues > 0) {
                for (final Object o : vs) {
                    final int valuesWritten;
                    if (o == null) {
                        valuesWritten = 0;
                    } else if (o instanceof List) {
                        final List<Double> v = (List<Double>) o;
                        encoder.encodeRawVecFloat(v);
                        valuesWritten = v.size();
                    } else if (o instanceof Double) {
                        final Double v = (Double) o;
                        encoder.encodeRawFloat(v);
                        valuesWritten = 1;
                    } else if (o instanceof double[]) {
                        final double[] v = (double[]) o;
                        encoder.encodeRawVecFloat(v);
                        valuesWritten = v.length;
                    } else {
                        throw BCF2FieldEncoder.incompatibleType(o, type);
                    }

                    // In order to produce output that bcftools can interpret, we always write one MISSING
                    // value even if the input is entirely absent, which we would otherwise write as a vector of
                    // all EOV values
                    if (valuesWritten == 0) {
                        encoder.encodeRawMissingValue(type);
                    }
                    encoder.encodePaddingValues(nValues - Math.max(valuesWritten, 1), BCF2Type.FLOAT);
                }
            }
            vs.clear();
            nValues = 0;
        }
    }

    static TribbleException incompatibleType(final Object o, final BCF2Type type) {
        final String error = "Could not write object: %s whose type %s is incompatible with declared header of type: %s";
        return new TribbleException(String.format(error, o, o.getClass().getSimpleName(), type));
    }
}
