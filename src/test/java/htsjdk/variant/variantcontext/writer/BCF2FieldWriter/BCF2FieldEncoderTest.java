package htsjdk.variant.variantcontext.writer.BCF2FieldWriter;

import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.bcf2.BCF2Type;
import htsjdk.variant.bcf2.BCF2Utils;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.BCF2Encoder;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeaderLineCount;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BCF2FieldEncoderTest extends VariantBaseTest {

    private static final BCF2Encoder.BCF2_2Encoder ENCODER = new BCF2Encoder.BCF2_2Encoder();
    private static final BCF2FieldEncoder.AtomicIntFieldEncoder ATOMIC_INT = new BCF2FieldEncoder.AtomicIntFieldEncoder(ENCODER);
    private static final BCF2FieldEncoder.AtomicFloatFieldEncoder ATOMIC_FLOAT = new BCF2FieldEncoder.AtomicFloatFieldEncoder(ENCODER);
    private static final BCF2FieldEncoder.CharFieldEncoder CHAR = new BCF2FieldEncoder.CharFieldEncoder(ENCODER);
    private static final BCF2FieldEncoder.StringFieldEncoder STRING = new BCF2FieldEncoder.StringFieldEncoder(ENCODER);
    private static final BCF2FieldEncoder.VecIntFieldEncoder VEC_INT = new BCF2FieldEncoder.VecIntFieldEncoder(ENCODER);
    private static final BCF2FieldEncoder.VecFloatFieldEncoder VEC_FLOAT = new BCF2FieldEncoder.VecFloatFieldEncoder(ENCODER);


    @DataProvider(name = "fieldEncoderCases")
    public static Object[][] fieldEncoderCases() {
        final List<Object[]> cases = new ArrayList<>();

        // Integer encoding
        {
            for (final BCF2Type intType : BCF2Utils.INTEGER_TYPES_BY_SIZE) {
                final int byteWidth = intType.getSizeInBytes();
                final List<Object> intsToEncode = Arrays.asList(1, -1, null, 1 << (byteWidth * 8 - 2));
                final ByteBuffer bytes = ByteBuffer.allocate(intsToEncode.size() * byteWidth);
                for (final Object o : intsToEncode) {
                    final int i = o == null ? intType.getMissingBytes() : (Integer) o;
                    for (int shift = 0; shift < byteWidth; shift++) {
                        bytes.put((byte) (i >> (shift * 8)));
                    }
                }
                cases.add(new Object[]{
                    ATOMIC_INT,
                    intsToEncode,
                    bytes.array(),
                });
            }
        }

        // Float encoding
        {
            final int byteWidth = BCF2Type.FLOAT.getSizeInBytes();
            final List<Object> floatsToEncode = Arrays.asList(1.0, -1.0, null, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            final ByteBuffer bytes = ByteBuffer.allocate(floatsToEncode.size() * byteWidth);
            for (final Object o : floatsToEncode) {
                final int i = o == null ? BCF2Type.FLOAT.getMissingBytes() : Float.floatToRawIntBits((float) (double) (Double) o);
                for (int shift = 0; shift < byteWidth; shift++) {
                    bytes.put((byte) (i >> (shift * 8)));
                }
            }
            cases.add(new Object[]{
                ATOMIC_FLOAT,
                floatsToEncode,
                bytes.array(),
            });
        }

        // Char encoding
        {
            final List<Object> stringsToEncode = Arrays.asList("str", null, "\0a\0");
            final int maxByteWidth = stringsToEncode
                .stream()
                .mapToInt(o -> o == null ? 0 : ((String) o).getBytes(StandardCharsets.UTF_8).length)
                .max().getAsInt();
            final ByteBuffer bytes = ByteBuffer.allocate(stringsToEncode.size() * maxByteWidth);
            for (final Object o : stringsToEncode) {
                final byte[] b = o == null ? new byte[0] : ((String) o).getBytes(StandardCharsets.UTF_8);
                bytes.put(b);
                for (int i = maxByteWidth - b.length; i > 0; i--) bytes.put((byte) 0);
            }
            cases.add(new Object[]{
                CHAR,
                stringsToEncode,
                bytes.array(),
            });
        }

        // String encoding
        {
            final List<Object> stringsToEncode = Arrays.asList("st", null, Arrays.asList("a", "b"), new String[]{"a", "b"});
            final byte[] bytes = new byte[]{
                's', 't', '\0',   // padding
                '\0', '\0', '\0', // null values should be encoded as all NULL bytes
                'a', ',', 'b',    // lists of strings joined with ,
                'a', ',', 'b',    // arrays of strings joined with ,
            };
            cases.add(new Object[]{
                STRING,
                stringsToEncode,
                bytes,
            });
        }

        // Vector of integers encoding
        {
            for (final BCF2Type intType : BCF2Utils.INTEGER_TYPES_BY_SIZE) {
                final int byteWidth = intType.getSizeInBytes();
                final List<Object> vecsToEncode = Arrays.asList(
                    Arrays.asList(null, 1),  // Internal null should be missing bytes, not EOV
                    new int[]{1},            // Short vector should be EOV padded
                    null,                    // Entirely missing vector should be all EOV
                    1 << (byteWidth * 8 - 2) // Atomic value should be treated as vector of size 1
                );
                final int nValues = 2;
                final ByteBuffer bytes = ByteBuffer.allocate(nValues * vecsToEncode.size() * byteWidth);
                final int[] ints = new int[]{
                    intType.getMissingBytes(), 1,
                    1, intType.getEOVBytes(),
                    intType.getEOVBytes(), intType.getEOVBytes(),
                    1 << (byteWidth * 8 - 2), intType.getEOVBytes(),
                };
                for (final int i : ints) {
                    for (int shift = 0; shift < byteWidth; shift++) {
                        bytes.put((byte) (i >> (shift * 8)));
                    }
                }
                cases.add(new Object[]{
                    VEC_INT,
                    vecsToEncode,
                    bytes.array(),
                });
            }
        }

        // Vector of floats encoding
        {
            final int byteWidth = BCF2Type.FLOAT.getSizeInBytes();
            final List<Object> vecsToEncode = Arrays.asList(
                Arrays.asList(null, 1.0), // Internal null should be missing bytes, not EOV
                new double[]{1.0},        // Short vector should be EOV padded
                null,                     // Entirely missing vector should be all EOV
                Double.NaN                // Atomic value should be treated as vector of size 1
            );
            final int nValues = 2;
            final ByteBuffer bytes = ByteBuffer.allocate(nValues * vecsToEncode.size() * byteWidth);
            final int[] ints = new int[]{
                BCF2Type.FLOAT.getMissingBytes(), Float.floatToRawIntBits(1.0f),
                Float.floatToRawIntBits(1.0f), BCF2Type.FLOAT.getEOVBytes(),
                BCF2Type.FLOAT.getEOVBytes(), BCF2Type.FLOAT.getEOVBytes(),
                Float.floatToRawIntBits((float) Double.NaN), BCF2Type.FLOAT.getEOVBytes(),
            };
            for (final int i : ints) {
                for (int shift = 0; shift < byteWidth; shift++) {
                    bytes.put((byte) (i >> (shift * 8)));
                }
            }
            cases.add(new Object[]{
                VEC_FLOAT,
                vecsToEncode,
                bytes.array(),
            });
        }

        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "fieldEncoderCases")
    public static void testFieldEncoders(
        final BCF2FieldEncoder encoder,
        final List<Object> objects,
        final byte[] expectedBytes
    ) throws IOException {
        encoder.start();
        for (final Object o : objects) {
            encoder.load(o);
        }
        encoder.encode();
        Assert.assertEquals(expectedBytes, ENCODER.getRecordBytes());
    }


    @DataProvider(name = "siteWriterCases")
    public static Object[][] siteWriterCases() {
        final List<Object[]> cases = new ArrayList<>();

        // Generic encoder
        {
            final VCFInfoHeaderLine info = new VCFInfoHeaderLine("genericKey", 2, VCFHeaderLineType.Integer, "test");
            final BCF2FieldWriter.SiteAttributeWriter writer = new BCF2FieldWriter.SiteAttributeWriter(info, 1, ENCODER);
            final VariantContext vc1 = new VariantContextBuilder()
                .attribute("genericKey", 1)
                .chr("dummy")
                .alleles("A")
                .make();
            final byte[] bytes1 = new byte[]{
                0x21, // 2 8-bit ints
                1, (byte) BCF2Type.INT8.getEOVBytes() // Field writer should pad out array to 2 elements to match header count
            };
            cases.add(new Object[]{
                writer, vc1, bytes1,
            });

            final VariantContext vc2 = new VariantContextBuilder()
                .chr("dummy")
                .alleles("A")
                .make();
            final byte[] bytes2 = new byte[]{
                0x01, // Field writer should directly write typed missing, ignoring header count
            };
            cases.add(new Object[]{
                writer, vc2, bytes2,
            });
        }

        // Flag writer
        {
            final VCFInfoHeaderLine info = new VCFInfoHeaderLine("genericKey", 0, VCFHeaderLineType.Flag, "test");
            final BCF2FieldWriter.SiteFlagWriter writer = new BCF2FieldWriter.SiteFlagWriter(info, 1, ENCODER);
            final VariantContext vc = new VariantContextBuilder()
                .attribute("genericKey", true)
                .chr("dummy")
                .alleles("A")
                .make();
            final byte[] bytes = new byte[]{
                0x11, // 1 8-bit int
                1,
            };
            cases.add(new Object[]{
                writer, vc, bytes,
            });
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "siteWriterCases")
    public void testSiteWriters(
        final BCF2FieldWriter.SiteWriter writer,
        final VariantContext vc,
        final byte[] expectedBytes
    ) throws IOException {
        // Skip starting so we don't get key in output
        writer.encode(vc);
        Assert.assertEquals(expectedBytes, ENCODER.getRecordBytes());
    }


    @DataProvider(name = "genotypeWriterCases")
    public static Object[][] genotypeWriterCases() {
        final List<Object[]> cases = new ArrayList<>();

        // Generic encoder
        {
            final VCFFormatHeaderLine info = new VCFFormatHeaderLine("genericKey", 2, VCFHeaderLineType.Integer, "test");
            final BCF2FieldWriter.GenotypeAttributeWriter writer = new BCF2FieldWriter.GenotypeAttributeWriter(info, 1, ENCODER);
            final VariantContext vc = new VariantContextBuilder()
                .attribute("genericKey", 1)
                .chr("dummy")
                .genotypes(new GenotypeBuilder()
                    .name("sample")
                    .attribute("genericKey", 1)
                    .make()
                )
                .alleles("A")
                .make();
            final byte[] bytes = new byte[]{
                0x21, // 2 8-bit ints
                1, (byte) BCF2Type.INT8.getEOVBytes() // Field writer should pad out array to 2 elements to match header count
            };
            cases.add(new Object[]{
                writer, vc, new String[]{"sample"}, bytes,
            });
        }

        // FT encoder
        {
            final VCFFormatHeaderLine info = new VCFFormatHeaderLine("FT", 1, VCFHeaderLineType.String, "test");
            final BCF2FieldWriter writer = BCF2FieldWriter.createGenotypeWriter(info, 1, ENCODER);
            final VariantContext vc = new VariantContextBuilder()
                .chr("dummy")
                .genotypes(
                    new GenotypeBuilder()
                        .name("hasFilter")
                        .filter("f")
                        .make(),
                    new GenotypeBuilder()
                        .name("noFilter")
                        .unfiltered() // should be encoded as PASS
                        .make()
                )
                .alleles("A")
                .make();
            final byte[] bytes = new byte[]{
                0x47, // Strings of length 4
                'f', 0, 0, 0,
                'P', 'A', 'S', 'S',
            };
            cases.add(new Object[]{
                writer, vc, new String[]{"hasFilter", "noFilter"}, bytes,
            });
        }

        // GT encoder
        {
            final VCFFormatHeaderLine format = new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "test");
            final Allele ref = Allele.REF_A;
            final Allele alt = Allele.ALT_T;

            final BCF2FieldWriter writer = BCF2FieldWriter.createGenotypeWriter(format, 1, ENCODER);
            final VariantContext vc = new VariantContextBuilder()
                .chr("dummy")
                .alleles(Arrays.asList(ref, alt))
                .genotypes(
                    new GenotypeBuilder()
                        .name("refAlt")
                        .alleles(Arrays.asList(ref, alt))
                        .make(),
                    new GenotypeBuilder()
                        .name("refAltPhased")
                        .alleles(Arrays.asList(ref, alt))
                        .phased(true)
                        .make(),
                    new GenotypeBuilder()
                        .name("missingMissing")
                        .alleles(Arrays.asList(Allele.NO_CALL, Allele.NO_CALL))
                        .make(),
                    new GenotypeBuilder()
                        .name("haploid")
                        .alleles(Collections.singletonList(ref))
                        .make()
                )
                .make();
            final byte[] bytes = new byte[]{
                0x21, // 2 8-bit ints
                0x02, 0x04,
                0x02, 0x05,
                0x00, 0x00,
                0x02, (byte) 0x81,
            };
            cases.add(new Object[]{
                writer, vc,
                vc.getGenotypes().stream().map(Genotype::getSampleName).toArray(String[]::new),
                bytes,
            });
        }

        // Inline integer encoder
        {
            final VCFFormatHeaderLine format = new VCFFormatHeaderLine("DP", 1, VCFHeaderLineType.Integer, "test");
            final BCF2FieldWriter writer = BCF2FieldWriter.createGenotypeWriter(format, 1, ENCODER);
            final VariantContext vc = new VariantContextBuilder()
                .chr("dummy")
                .genotypes(
                    new GenotypeBuilder()
                        .name("small")
                        .DP(2)
                        .make(),
                    new GenotypeBuilder()
                        .name("big")
                        .DP(256)
                        .make()
                )
                .alleles("A")
                .make();

            final byte[] bytes = new byte[]{
                0x12, // 1 16-bit int
                0x02, 0x00,
                (byte) 256, 256 >> 8,
            };

            cases.add(new Object[]{
                writer, vc,
                vc.getGenotypes().stream().map(Genotype::getSampleName).toArray(String[]::new),
                bytes,
            });
        }

        // Inline vector of integer encoder
        {
            final VCFFormatHeaderLine format = new VCFFormatHeaderLine("PL", VCFHeaderLineCount.G, VCFHeaderLineType.Integer, "test");
            final Allele ref = Allele.REF_A;
            final Allele alt = Allele.ALT_T;
            final BCF2FieldWriter writer = BCF2FieldWriter.createGenotypeWriter(format, 1, ENCODER);
            final VariantContext vc = new VariantContextBuilder()
                .chr("dummy")
                .alleles(Arrays.asList(ref, alt))
                .genotypes(
                    new GenotypeBuilder()
                        .name("small")
                        .alleles(Arrays.asList(ref, alt))
                        .PL(new int[]{1, 2})
                        .make(),
                    new GenotypeBuilder()
                        .name("big")
                        .alleles(Arrays.asList(ref, alt))
                        .PL(new int[]{256}) // should pad out
                        .make()
                )
                .make();

            final byte[] bytes = new byte[]{
                0x32, // 3 16-bit ints
                0x01, 0x00, 0x02, 0x00, (byte) BCF2Type.INT16.getEOVBytes(), (byte) (BCF2Type.INT16.getEOVBytes() >> 8),
                (byte) 256, 256 >> 8, (byte) BCF2Type.INT16.getEOVBytes(), (byte) (BCF2Type.INT16.getEOVBytes() >> 8), (byte) BCF2Type.INT16.getEOVBytes(), (byte) (BCF2Type.INT16.getEOVBytes() >> 8)
            };

            cases.add(new Object[]{
                writer, vc,
                vc.getGenotypes().stream().map(Genotype::getSampleName).toArray(String[]::new),
                bytes,
            });
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "genotypeWriterCases")
    public void testGenotypeWriters(
        final BCF2FieldWriter.GenotypeWriter writer,
        final VariantContext vc,
        final String[] sampleNames,
        final byte[] expectedBytes
    ) throws IOException {
        // Skip starting so we don't get key in output
        writer.encode(vc, sampleNames);
        Assert.assertEquals(expectedBytes, ENCODER.getRecordBytes());
    }
}
