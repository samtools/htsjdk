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

// the imports for unit testing.

import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.writer.BCF2Encoder;
import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class BCF2EncoderDecoderUnitTest extends VariantBaseTest {
    private final double FLOAT_TOLERANCE = 1e-6;
    final List<BCF2TypedValue> primitives = new ArrayList<>();
    final List<BCF2TypedValue> basicTypes = new ArrayList<>();
    final List<BCF2TypedValue> forCombinations = new ArrayList<>();

    @BeforeSuite
    public void before() {
        basicTypes.add(new BCF2TypedValue(1, BCF2Type.INT8));
        basicTypes.add(new BCF2TypedValue(1000, BCF2Type.INT16));
        basicTypes.add(new BCF2TypedValue(1000000, BCF2Type.INT32));
        basicTypes.add(new BCF2TypedValue(1.2345e6, BCF2Type.FLOAT));
        basicTypes.add(new BCF2TypedValue("A", BCF2Type.CHAR));

        // small ints
        primitives.add(new BCF2TypedValue(0, BCF2Type.INT8));
        primitives.add(new BCF2TypedValue(10, BCF2Type.INT8));
        primitives.add(new BCF2TypedValue(-1, BCF2Type.INT8));
        primitives.add(new BCF2TypedValue(100, BCF2Type.INT8));
        primitives.add(new BCF2TypedValue(-100, BCF2Type.INT8));
        primitives.add(new BCF2TypedValue(-120, BCF2Type.INT8));    // last value in range
        primitives.add(new BCF2TypedValue(127, BCF2Type.INT8));    // last value in range

        // medium ints
        primitives.add(new BCF2TypedValue(-1000, BCF2Type.INT16));
        primitives.add(new BCF2TypedValue(1000, BCF2Type.INT16));
        primitives.add(new BCF2TypedValue(-128, BCF2Type.INT16));    // first value in range
        primitives.add(new BCF2TypedValue(128, BCF2Type.INT16));    // first value in range
        primitives.add(new BCF2TypedValue(-32760, BCF2Type.INT16)); // last value in range
        primitives.add(new BCF2TypedValue(32767, BCF2Type.INT16)); // last value in range

        // larger ints
        primitives.add(new BCF2TypedValue(-32768, BCF2Type.INT32)); // first value in range
        primitives.add(new BCF2TypedValue(32768, BCF2Type.INT32)); // first value in range
        primitives.add(new BCF2TypedValue(-100000, BCF2Type.INT32));
        primitives.add(new BCF2TypedValue(100000, BCF2Type.INT32));
        primitives.add(new BCF2TypedValue(-2147483640, BCF2Type.INT32));
        primitives.add(new BCF2TypedValue(2147483647, BCF2Type.INT32));

        // floats
        primitives.add(new BCF2TypedValue(0.0, BCF2Type.FLOAT));
        primitives.add(new BCF2TypedValue(-0.0, BCF2Type.FLOAT));
        primitives.add(new BCF2TypedValue(1.0, BCF2Type.FLOAT));
        primitives.add(new BCF2TypedValue(-1.0, BCF2Type.FLOAT));
        primitives.add(new BCF2TypedValue(1.1, BCF2Type.FLOAT));
        primitives.add(new BCF2TypedValue(-1.1, BCF2Type.FLOAT));
        primitives.add(new BCF2TypedValue(5.0 / 3.0, BCF2Type.FLOAT));
        primitives.add(new BCF2TypedValue(-5.0 / 3.0, BCF2Type.FLOAT));
        primitives.add(new BCF2TypedValue(1.23e3, BCF2Type.FLOAT));
        primitives.add(new BCF2TypedValue(1.23e6, BCF2Type.FLOAT));
        primitives.add(new BCF2TypedValue(1.23e9, BCF2Type.FLOAT));
        primitives.add(new BCF2TypedValue(1.23e12, BCF2Type.FLOAT));
        primitives.add(new BCF2TypedValue(1.23e15, BCF2Type.FLOAT));
        primitives.add(new BCF2TypedValue(-1.23e3, BCF2Type.FLOAT));
        primitives.add(new BCF2TypedValue(-1.23e6, BCF2Type.FLOAT));
        primitives.add(new BCF2TypedValue(-1.23e9, BCF2Type.FLOAT));
        primitives.add(new BCF2TypedValue(-1.23e12, BCF2Type.FLOAT));
        primitives.add(new BCF2TypedValue(-1.23e15, BCF2Type.FLOAT));
        primitives.add(new BCF2TypedValue(Float.MIN_VALUE, BCF2Type.FLOAT));
        primitives.add(new BCF2TypedValue(Float.MAX_VALUE, BCF2Type.FLOAT));
        primitives.add(new BCF2TypedValue(Double.NEGATIVE_INFINITY, BCF2Type.FLOAT));
        primitives.add(new BCF2TypedValue(Double.POSITIVE_INFINITY, BCF2Type.FLOAT));
        primitives.add(new BCF2TypedValue(Double.NaN, BCF2Type.FLOAT));

        // strings
        //primitives.add(new BCF2TypedValue("", BCFType.CHAR)); <- will be null (which is right)
        primitives.add(new BCF2TypedValue("S", BCF2Type.CHAR));
        primitives.add(new BCF2TypedValue("S2", BCF2Type.CHAR));
        primitives.add(new BCF2TypedValue("12345678910", BCF2Type.CHAR));
        primitives.add(new BCF2TypedValue("ABCDEFGHIJKLMNOPQRSTUVWXYZ", BCF2Type.CHAR));
        primitives.add(new BCF2TypedValue("ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ", BCF2Type.CHAR));

        // missing values
        for (final BCF2Type type : BCF2Type.values()) {
            primitives.add(new BCF2TypedValue(null, type));
        }

        forCombinations.add(new BCF2TypedValue(10, BCF2Type.INT8));
        forCombinations.add(new BCF2TypedValue(100, BCF2Type.INT8));
        forCombinations.add(new BCF2TypedValue(-100, BCF2Type.INT8));
        forCombinations.add(new BCF2TypedValue(-128, BCF2Type.INT16));    // first value in range
        forCombinations.add(new BCF2TypedValue(128, BCF2Type.INT16));    // first value in range
        forCombinations.add(new BCF2TypedValue(-100000, BCF2Type.INT32));
        forCombinations.add(new BCF2TypedValue(100000, BCF2Type.INT32));
        forCombinations.add(new BCF2TypedValue(0.0, BCF2Type.FLOAT));
        forCombinations.add(new BCF2TypedValue(1.23e6, BCF2Type.FLOAT));
        forCombinations.add(new BCF2TypedValue(-1.23e6, BCF2Type.FLOAT));
        forCombinations.add(new BCF2TypedValue("S", BCF2Type.CHAR));
        forCombinations.add(new BCF2TypedValue("ABCDEFGHIJKLMNOPQRSTUVWXYZ", BCF2Type.CHAR));
        forCombinations.add(new BCF2TypedValue("ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ", BCF2Type.CHAR));

        // missing values
        for (final BCF2Type type : BCF2Type.values()) {
            forCombinations.add(new BCF2TypedValue(null, type));
        }
    }

    // --------------------------------------------------------------------------------
    //
    // merge case Provider
    //
    // --------------------------------------------------------------------------------

    private static class BCF2TypedValue {
        final BCF2Type type;
        final Object value;

        private BCF2TypedValue(final int value, final BCF2Type type) {
            this(Integer.valueOf(value), type);
        }

        private BCF2TypedValue(final double value, final BCF2Type type) {
            this(Double.valueOf(value), type);
        }

        private BCF2TypedValue(final Object value, final BCF2Type type) {
            this.type = type;
            this.value = value;
        }

        public boolean isMissing() {
            return value == null;
        }

        @Override
        public String toString() {
            return String.format("%s of %s", value, type);
        }
    }

    // -----------------------------------------------------------------
    //
    // Test encoding of basic types
    //
    // -----------------------------------------------------------------

    @DataProvider(name = "BCF2EncodingTestProviderBasicTypes")
    public Object[][] BCF2EncodingTestProviderBasicTypes() {
        final List<Object[]> tests = new ArrayList<>();
        for (final BCFVersion version : BCFVersion.SUPPORTED_VERSIONS)
            for (final BCF2TypedValue tv : basicTypes)
                tests.add(new Object[]{Collections.singletonList(tv), version});
        return tests.toArray(new Object[][]{});
    }

    private interface EncodeMe {
        void encode(final BCF2Encoder encoder, final BCF2TypedValue tv) throws IOException;
    }


    @Test(dataProvider = "BCF2EncodingTestProviderBasicTypes")
    public void testBCF2BasicTypesWithStaticCalls(final List<BCF2TypedValue> toEncode, final BCFVersion version) throws IOException {
        testBCF2BasicTypesWithEncodeMe(
            toEncode,
            (encoder, tv) -> {
                switch (tv.type) {
                    case INT8:
                    case INT16:
                    case INT32:
                        encoder.encodeTypedInt((Integer) tv.value, tv.type);
                        break;
                    case FLOAT:
                        encoder.encodeTypedFloat((Double) tv.value);
                        break;
                    case CHAR:
                        encoder.encodeTypedString((String) tv.value);
                        break;
                }
            },
            version
        );
    }

    @Test(dataProvider = "BCF2EncodingTestProviderBasicTypes")
    public void testBCF2BasicTypesWithObjectType(final List<BCF2TypedValue> toEncode, final BCFVersion version) throws IOException {
        testBCF2BasicTypesWithEncodeMe(
            toEncode,
            (encoder, tv) -> encoder.encodeTyped(tv.value, tv.type),
            version
        );
    }

    public void testBCF2BasicTypesWithEncodeMe(final List<BCF2TypedValue> toEncode, final EncodeMe func, final BCFVersion version) throws IOException {
        for (final BCF2TypedValue tv : toEncode) {
            final BCF2Encoder encoder = BCF2Encoder.getEncoder(version);
            func.encode(encoder, tv);

            final BCF2Decoder decoder = BCF2Decoder.getDecoder(version, encoder.getRecordBytes());
            final Object decoded = decoder.decodeTypedValue();

            Assert.assertNotNull(decoded);
            Assert.assertFalse(decoded instanceof List);
            myAssertEquals(tv, decoded);
        }
    }

    @Test(dataProvider = "BCF2EncodingTestProviderBasicTypes")
    public void testBCF2EncodingVectors(final List<BCF2TypedValue> toEncode, final BCFVersion version) throws IOException {
        for (final BCF2TypedValue tv : toEncode) {
            for (final int length : Arrays.asList(2, 5, 10, 15, 20, 25)) {
                final BCF2Encoder encoder = BCF2Encoder.getEncoder(version);
                final List<Object> expected = Collections.nCopies(length, tv.value);
                encoder.encodeTyped(expected, tv.type);

                final BCF2Decoder decoder = BCF2Decoder.getDecoder(version, encoder.getRecordBytes());
                final Object decoded = decoder.decodeTypedValue();

                Assert.assertTrue(decoded instanceof List);
                final List<Object> decodedList = (List<Object>) decoded;
                Assert.assertEquals(decodedList.size(), expected.size());
                for (final Object decodedValue : decodedList)
                    myAssertEquals(tv, decodedValue);
            }
        }
    }

    @DataProvider(name = "BCF2EncodingTestProviderSingletons")
    public Object[][] BCF2EncodingTestProviderSingletons() {
        final List<Object[]> tests = new ArrayList<>();
        for (final BCFVersion version : BCFVersion.SUPPORTED_VERSIONS)
            for (final BCF2TypedValue tv : primitives)
                tests.add(new Object[]{Collections.singletonList(tv), version});
        return tests.toArray(new Object[][]{});
    }

    @Test(dataProvider = "BCF2EncodingTestProviderSingletons")
    public void testBCF2EncodingSingletons(final List<BCF2TypedValue> toEncode, final BCFVersion version) throws IOException {
        final byte[] record = encodeRecord(toEncode, version);
        decodeRecord(toEncode, record, version);
    }

    // -----------------------------------------------------------------
    //
    // Test encoding of vectors
    //
    // -----------------------------------------------------------------

    @DataProvider(name = "BCF2EncodingTestProviderSequences")
    public Object[][] BCF2EncodingTestProviderSequences() {
        final List<Object[]> tests = new ArrayList<>();
        for (final BCFVersion version : BCFVersion.SUPPORTED_VERSIONS)
            for (final BCF2TypedValue tv1 : forCombinations)
                for (final BCF2TypedValue tv2 : forCombinations)
                    for (final BCF2TypedValue tv3 : forCombinations)
                        tests.add(new Object[]{Arrays.asList(tv1, tv2, tv3), version});
        return tests.toArray(new Object[][]{});
    }

    @Test(dataProvider = "BCF2EncodingTestProviderBasicTypes")
    public void testBCF2EncodingVectorsWithMissing(final List<BCF2TypedValue> toEncode, final BCFVersion version) throws IOException {
        for (final BCF2TypedValue tv : toEncode) {
            if (tv.type != BCF2Type.CHAR) {
                for (final int length : Arrays.asList(2, 5, 10, 15, 20, 25)) {
                    final byte td = BCF2Utils.encodeTypeDescriptor(1, tv.type);

                    final BCF2Encoder encoder = BCF2Encoder.getEncoder(version);
                    for (int i = 0; i < length; i++) {
                        encoder.encodeRawValue(i % 2 == 0 ? null : tv.value, tv.type);
                    }

                    final BCF2Decoder decoder = BCF2Decoder.getDecoder(version, encoder.getRecordBytes());

                    for (int i = 0; i < length; i++) {
                        final Object decoded = decoder.decodeTypedValue(td);
                        myAssertEquals(i % 2 == 0 ? new BCF2TypedValue(null, tv.type) : tv, decoded);
                    }
                }
            }
        }
    }

    @Test(dataProvider = "BCF2EncodingTestProviderSequences", dependsOnMethods = "testBCF2EncodingSingletons")
    public void testBCF2EncodingTestProviderSequences(final List<BCF2TypedValue> toEncode, final BCFVersion version) throws IOException {
        final byte[] record = encodeRecord(toEncode, version);
        decodeRecord(toEncode, record, version);
    }

    // -----------------------------------------------------------------
    //
    // Test strings and lists of strings
    //
    // -----------------------------------------------------------------

    @DataProvider(name = "Strings")
    public Object[][] stringsProvider() {
        final List<Object[]> tests = new ArrayList<>();
        for (final BCFVersion version : BCFVersion.SUPPORTED_VERSIONS) {
            tests.add(new Object[]{"", version});
            tests.add(new Object[]{" ", version});
            tests.add(new Object[]{"s", version});
            tests.add(new Object[]{"sss", version});
        }
        return tests.toArray(new Object[][]{});
    }

    @Test(dataProvider = "Strings")
    public void testEncodingOfListOfString(final String s, final BCFVersion version) throws IOException {
        final BCF2Encoder encoder = BCF2Encoder.getEncoder(version);
        encoder.encodeTypedString(s);

        final BCF2Decoder decoder = BCF2Decoder.getDecoder(version, encoder.getRecordBytes());
        final String decoded = decoder.decodeUnexplodedString();

        Assert.assertEquals(s, decoded);
    }

    @DataProvider(name = "ListOfStrings")
    public Object[][] listofStringsProvider() {
        final List<Object[]> tests = new ArrayList<>();
        for (final BCFVersion version : BCFVersion.SUPPORTED_VERSIONS) {
            for (final int padding : Arrays.asList(0, 1, 5)) {
                tests.add(new Object[]{Collections.emptyList(), padding, version});
                tests.add(new Object[]{Collections.singletonList("s"), padding, version});
                tests.add(new Object[]{Arrays.asList("s", ""), padding, version});
                tests.add(new Object[]{Arrays.asList("s", "ss", "sss"), padding, version});
            }
        }
        return tests.toArray(new Object[][]{});
    }

    @Test(dataProvider = "ListOfStrings")
    public void testEncodingOfListOfString(final List<String> strings, final int padding, final BCFVersion version) {
        final BCF2Encoder encoder = BCF2Encoder.getEncoder(version);
        final byte[] bytes = encoder.compactStrings(strings);
        final int paddedSize = bytes.length + padding;
        encoder.encodeRawString(bytes, paddedSize);

        final BCF2Decoder decoder = BCF2Decoder.getDecoder(version, encoder.getRecordBytes());
        final List<String> decodedStrings = decoder.decodeExplodedStrings(paddedSize);

        // Padding values not included
        Assert.assertEquals(strings, decodedStrings);

        // The decoder should have drained all the remaining padding values from the stream
        Assert.assertTrue(decoder.blockIsFullyDecoded());
    }

    // -----------------------------------------------------------------
    //
    // Tests to determine the best type of arrays of integers
    //
    // -----------------------------------------------------------------

    @DataProvider(name = "BestIntTypeTests")
    public Object[][] BestIntTypeTests() {
        final List<Object[]> tests = new ArrayList<>();
        tests.add(new Object[]{Collections.singletonList(1), BCF2Type.INT8});
        tests.add(new Object[]{Arrays.asList(1, 10), BCF2Type.INT8});
        tests.add(new Object[]{Arrays.asList(1, 10, 100), BCF2Type.INT8});
        tests.add(new Object[]{Arrays.asList(1, -1), BCF2Type.INT8});
        tests.add(new Object[]{Arrays.asList(1, 1000), BCF2Type.INT16});
        tests.add(new Object[]{Arrays.asList(1, 1000, 10), BCF2Type.INT16});
        tests.add(new Object[]{Arrays.asList(1, 1000, 100), BCF2Type.INT16});
        tests.add(new Object[]{Collections.singletonList(1000), BCF2Type.INT16});
        tests.add(new Object[]{Collections.singletonList(100000), BCF2Type.INT32});
        tests.add(new Object[]{Arrays.asList(100000, 10), BCF2Type.INT32});
        tests.add(new Object[]{Arrays.asList(100000, 100), BCF2Type.INT32});
        tests.add(new Object[]{Arrays.asList(100000, 1, -10), BCF2Type.INT32});
        tests.add(new Object[]{Arrays.asList(-100000, 1, -10), BCF2Type.INT32});
        return tests.toArray(new Object[][]{});
    }

    @Test(dataProvider = "BestIntTypeTests")
    public void determineBestEncoding(final List<Integer> ints, final BCF2Type expectedType) {
        Assert.assertEquals(BCF2Utils.determineIntegerType(ints), expectedType);
        Assert.assertEquals(BCF2Utils.determineIntegerType(toPrimitive(ints.toArray(new Integer[0]))), expectedType);
    }

    private static int[] toPrimitive(final Integer[] array) {
        if (array == null) {
            return null;
        } else if (array.length == 0) {
            return new int[0];
        }

        final int[] result = new int[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = array[i];
        }
        return result;
    }

    // -----------------------------------------------------------------
    //
    // Tests managing and skipping multiple blocks
    //
    // -----------------------------------------------------------------

    @Test(dataProvider = "BCF2EncodingTestProviderSequences", dependsOnMethods = "testBCF2EncodingTestProviderSequences")
    public void testReadAndSkipWithMultipleBlocks(final List<BCF2TypedValue> block, final BCFVersion version) throws IOException {
        testReadAndSkipWithMultipleBlocks(block, forCombinations, version);
        testReadAndSkipWithMultipleBlocks(forCombinations, block, version);
    }

    public void testReadAndSkipWithMultipleBlocks(final List<BCF2TypedValue> block1, final List<BCF2TypedValue> block2, final BCFVersion version) throws IOException {
        final byte[] record1 = encodeRecord(block1, version);
        final byte[] record2 = encodeRecord(block2, version);

        // each record is individually good
        decodeRecord(block1, record1, version);
        decodeRecord(block2, record2, version);

        final BCF2Decoder decoder = BCF2Decoder.getDecoder(version);

        // test setting
        decoder.setRecordBytes(record1);
        decodeRecord(block1, decoder);
        decoder.setRecordBytes(record2);
        decodeRecord(block2, decoder);

        // test combining the streams
        final byte[] combined = combineRecords(record1, record2);
        final List<BCF2TypedValue> combinedObjects = new ArrayList<>(block1);
        combinedObjects.addAll(block2);

        // the combined bytes is the same as the combined objects
        InputStream stream = new ByteArrayInputStream(combined);
        decoder.readNextBlock(record1.length, stream);
        decodeRecord(block1, decoder);
        decoder.readNextBlock(record2.length, stream);
        decodeRecord(block2, decoder);

        // skipping the first block allows us to read the second block directly
        stream = new ByteArrayInputStream(combined);
        decoder.skipNextBlock(record1.length, stream);
        decoder.readNextBlock(record2.length, stream);
        decodeRecord(block2, decoder);
    }

    // -----------------------------------------------------------------
    //
    // Test encoding / decoding arrays of ints
    //
    // This checks that we can correctly encode and decode int[] with
    // the low-level decodeIntArray function arrays. This has to be
    // pretty comprehensive as decodeIntArray is a highly optimized
    // piece of code with lots of edge cases.  The values we are encoding
    // don't really matter -- just that the values come back as expected.
    //
    // decodeIntArray is only meant to decode arrays that are guaranteed
    // to not have internal missing values, but may be missing (or EOV)
    // padded, so we are interested in whether the encoder correctly
    // truncates padded arrays while draining the stream.
    // -----------------------------------------------------------------

    @DataProvider(name = "BCF2_2IntArrays")
    public Object[][] IntArrays() {
        final List<Object[]> tests = new ArrayList<>();
        for (final BCFVersion version : BCFVersion.SUPPORTED_VERSIONS) {
            for (final int nValues : Arrays.asList(0, 1, 2, 5, 10, 100)) {
                for (final int nPad : Arrays.asList(0, 1, 2, 5, 10, 100)) {
                    final int nElements = nValues + nPad;

                    final int[] vs = new int[nValues];

                    // add nValues from 0 to nValues - 1
                    for (int i = 0; i < nValues; i++)
                        vs[i] = i;

                    tests.add(new Object[]{vs, nElements, version});
                }
            }
        }

        return tests.toArray(new Object[][]{});
    }

    @Test(dataProvider = "BCF2_2IntArrays")
    public void testBCF2_2IntArrays(final int[] ints, final int paddedSize, final BCFVersion version) throws IOException {
        final BCF2Encoder encoder = BCF2Encoder.getEncoder(version);
        encoder.encodeTypedVecInt(ints, paddedSize);

        final BCF2Decoder decoder = BCF2Decoder.getDecoder(version, encoder.getRecordBytes());

        // read the int[] with the low-level version
        final byte typeDescriptor = decoder.readTypeDescriptor();
        final int size = decoder.decodeNumberOfElements(typeDescriptor);
        final int[] decoded = decoder.decodeIntArray(typeDescriptor, size);

        if (ints.length == 0) {
            Assert.assertNull(decoded);
        } else {
            // Padding values not included
            Assert.assertEquals(ints.length, decoded.length);

            // The decoder should have drained all the remaining padding values from the stream
            Assert.assertTrue(decoder.blockIsFullyDecoded());
        }
    }

    // -----------------------------------------------------------------
    //
    // Helper routines
    //
    // -----------------------------------------------------------------

    private byte[] combineRecords(final byte[] record1, final byte[] record2) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(record1);
        baos.write(record2);
        return baos.toByteArray();
    }

    private byte[] encodeRecord(final List<BCF2TypedValue> toEncode, final BCFVersion version) throws IOException {
        final BCF2Encoder encoder = BCF2Encoder.getEncoder(version);
        for (final BCF2TypedValue tv : toEncode) {
            encoder.encodeTyped(tv.value, tv.type);
        }

        // check output
        final byte[] record = encoder.getRecordBytes();
        Assert.assertNotNull(record);
        Assert.assertTrue(record.length > 0);
        return record;
    }

    private void decodeRecord(final List<BCF2TypedValue> toEncode, final byte[] record, final BCFVersion version) throws IOException {
        decodeRecord(toEncode, BCF2Decoder.getDecoder(version, record));
    }

    private void decodeRecord(final List<BCF2TypedValue> toEncode, final BCF2Decoder decoder) throws IOException {
        for (final BCF2TypedValue tv : toEncode) {
            Assert.assertFalse(decoder.blockIsFullyDecoded());
            final Object decoded = decoder.decodeTypedValue();

            myAssertEquals(tv, decoded);
        }

        Assert.assertTrue(decoder.blockIsFullyDecoded());
    }

    private void myAssertEquals(final BCF2TypedValue tv, final Object decoded) {
        if (tv.value == null) { // special needs for instanceof double
            Assert.assertNull(decoded);
        } else if (tv.type == BCF2Type.FLOAT) { // need tolerance for floats, and they aren't null
            Assert.assertTrue(decoded instanceof Double);

            final double valueFloat = (Double) tv.value;
            final double decodedFloat = (Double) decoded;

            VariantBaseTest.assertEqualsDoubleSmart(decodedFloat, valueFloat, FLOAT_TOLERANCE);
        } else
            Assert.assertEquals(decoded, tv.value);
    }
}