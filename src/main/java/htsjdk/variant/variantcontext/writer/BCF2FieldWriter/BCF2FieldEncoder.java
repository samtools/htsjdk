package htsjdk.variant.variantcontext.writer.BCF2FieldWriter;

import htsjdk.tribble.TribbleException;
import htsjdk.variant.bcf2.BCF2Type;
import htsjdk.variant.bcf2.BCF2Utils;
import htsjdk.variant.variantcontext.writer.BCF2Encoder;

import java.io.IOException;
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

    public abstract void start();

    public abstract void load(final Object o);

    public void encodeType() throws IOException {
        encoder.encodeType(nValues, type);
    }

    public abstract void encode() throws IOException;


    static class AtomicIntFieldEncoder extends BCF2FieldEncoder {

        private final List<Integer> vs = new ArrayList<>();

        AtomicIntFieldEncoder(final BCF2Encoder encoder) {
            super(encoder);
            nValues = 1;
        }

        @Override
        public void start() {
            type = BCF2Type.INT8;
        }

        @Override
        public void load(final Object o) {
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
        public void encode() throws IOException {
            encoder.encodeRawVecInt(vs, type);
            vs.clear();
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
        public void start() {
        }

        @Override
        public void load(final Object o) {
            if (o == null) {
                vs.add(null);
            } else if (o instanceof Double) {
                vs.add((Double) o);
            } else {
                throw BCF2FieldEncoder.incompatibleType(o, type);
            }
        }

        @Override
        public void encode() throws IOException {
            encoder.encodeRawVecFLoat(vs);
            vs.clear();
        }
    }

    static class CharFieldEncoder extends BCF2FieldEncoder {

        private static final byte[] EMPTY = new byte[0];

        private final List<byte[]> vs = new ArrayList<>();

        CharFieldEncoder(final BCF2Encoder encoder) {
            super(encoder);
            type = BCF2Type.CHAR;
        }

        @Override
        public void start() {
            nValues = 0;
        }

        @Override
        public void load(final Object o) {
            if (o == null) {
                vs.add(EMPTY);
            } else if (o instanceof String) {
                final byte[] b = ((String) o).getBytes(StandardCharsets.UTF_8);
                nValues = Math.max(nValues, b.length);
                vs.add(b);
            } else {
                throw BCF2FieldEncoder.incompatibleType(o, type);
            }
        }

        @Override
        public void encode() throws IOException {
            for (final byte[] v : vs) {
                encoder.encodeRawString(v, nValues);
            }
            vs.clear();
        }
    }

    static class StringFieldEncoder extends BCF2FieldEncoder {

        private static final byte[] EMPTY = new byte[0];

        private final List<byte[]> vs = new ArrayList<>();
        private int charLength;

        StringFieldEncoder(final BCF2Encoder encoder) {
            super(encoder);
            type = BCF2Type.CHAR;
        }

        @Override
        public void start() {
            nValues = 0;
            charLength = 0;
        }

        @Override
        public void load(final Object o) {
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
        public void encodeType() throws IOException {
            encoder.encodeType(charLength, type);
        }

        @Override
        public void encode() throws IOException {
            for (final byte[] v : vs) {
                encoder.encodeRawString(v, charLength);
            }
            vs.clear();
        }
    }

    static class VecIntFieldEncoder extends BCF2FieldEncoder {

        private final List<Object> vs = new ArrayList<>();

        VecIntFieldEncoder(final BCF2Encoder encoder) {
            super(encoder);
        }

        @Override
        public void start() {
            type = BCF2Type.INT8;
            nValues = 0;
        }

        @Override
        public void load(final Object o) {
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
        public void encode() throws IOException {
            for (final Object o : vs) {
                if (o == null) {
                    encoder.encodePaddingValues(nValues, type);
                } else if (o instanceof List) {
                    final List<Integer> v = (List<Integer>) o;
                    encoder.encodeRawVecInt(v, nValues, type);
                } else if (o instanceof Integer) {
                    final Integer v = (Integer) o;
                    encoder.encodeRawInt(v, type);
                    encoder.encodePaddingValues(nValues - 1, type);
                } else if (o instanceof int[]) {
                    final int[] v = (int[]) o;
                    encoder.encodeRawVecInt(v, nValues, type);
                }
            }
            vs.clear();
        }
    }

    static class VecFloatFieldEncoder extends BCF2FieldEncoder {

        private final List<Object> vs = new ArrayList<>();

        VecFloatFieldEncoder(final BCF2Encoder encoder) {
            super(encoder);
            type = BCF2Type.FLOAT;
        }

        @Override
        public void start() {
            nValues = 0;
        }

        @Override
        public void load(final Object o) {
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
        public void encode() throws IOException {
            for (final Object o : vs) {
                if (o == null) {
                    encoder.encodePaddingValues(nValues, type);
                } else if (o instanceof List) {
                    final List<Double> v = (List<Double>) o;
                    encoder.encodeRawVecFLoat(v, nValues);
                } else if (o instanceof Double) {
                    final Double v = (Double) o;
                    encoder.encodeRawFloat(v);
                    encoder.encodePaddingValues(nValues - 1, BCF2Type.FLOAT);
                } else if (o instanceof double[]) {
                    final double[] v = (double[]) o;
                    encoder.encodeRawVecFLoat(v, nValues);
                }
            }
            vs.clear();
        }
    }

    private static TribbleException incompatibleType(final Object o, final BCF2Type type) {
        final String error = "Could not write object: %s whose type is incompatible with declared header of type: %s";
        return new TribbleException(String.format(error, o, type));
    }
}
