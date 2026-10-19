package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.seekablestream.SeekableMemoryStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.GzipTestStreams;
import htsjdk.samtools.util.RuntimeIOException;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.testng.Assert;
import org.testng.annotations.Test;

public class VariantFileTypeTest extends HtsjdkTest {

    private static final String VCF_42_HEADER = "##fileformat=VCFv4.2\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n";

    // -- fromFileName --

    private static void assertNameIs(final String fileName, final VariantFileType expected) {
        Assert.assertEquals(VariantFileType.fromFileName(fileName), Optional.of(expected), fileName);
    }

    private static void assertNameIsUnrecognised(final String fileName) {
        Assert.assertEquals(VariantFileType.fromFileName(fileName), Optional.empty(), fileName);
    }

    @Test
    public void aBcfNameIsBcf() {
        assertNameIs("x.bcf", VariantFileType.BCF);
    }

    @Test
    public void anUpperCaseBcfNameIsBcf() {
        assertNameIs("x.BCF", VariantFileType.BCF);
    }

    @Test
    public void aVcfNameIsVcf() {
        assertNameIs("x.vcf", VariantFileType.VCF);
    }

    @Test
    public void anUpperCaseVcfNameIsVcf() {
        assertNameIs("x.VCF", VariantFileType.VCF);
    }

    @Test
    public void aGvcfNameIsVcf() {
        assertNameIs("x.gvcf", VariantFileType.VCF);
    }

    @Test
    public void aVcfGzNameIsBlockCompressedVcf() {
        assertNameIs("x.vcf.gz", VariantFileType.BLOCK_COMPRESSED_VCF);
    }

    @Test
    public void aVcfGzipNameIsBlockCompressedVcf() {
        assertNameIs("x.vcf.gzip", VariantFileType.BLOCK_COMPRESSED_VCF);
    }

    @Test
    public void aVcfBgzNameIsBlockCompressedVcf() {
        assertNameIs("x.vcf.bgz", VariantFileType.BLOCK_COMPRESSED_VCF);
    }

    @Test
    public void aVcfBgzfNameIsBlockCompressedVcf() {
        assertNameIs("x.vcf.bgzf", VariantFileType.BLOCK_COMPRESSED_VCF);
    }

    @Test
    public void aMixedCaseVcfGzNameIsBlockCompressedVcf() {
        assertNameIs("x.Vcf.Gz", VariantFileType.BLOCK_COMPRESSED_VCF);
    }

    @Test
    public void anUpperCaseGvcfBgzfNameIsBlockCompressedVcf() {
        assertNameIs("x.GVCF.BGZF", VariantFileType.BLOCK_COMPRESSED_VCF);
    }

    @Test
    public void aGvcfGzNameIsBlockCompressedVcf() {
        assertNameIs("x.gvcf.gz", VariantFileType.BLOCK_COMPRESSED_VCF);
    }

    @Test
    public void aGDotVcfGzNameIsBlockCompressedVcf() {
        assertNameIs("x.g.vcf.gz", VariantFileType.BLOCK_COMPRESSED_VCF);
    }

    @Test
    public void aTxtNameIsUnrecognised() {
        assertNameIsUnrecognised("x.txt");
    }

    @Test
    public void aNameWithoutAnExtensionIsUnrecognised() {
        assertNameIsUnrecognised("x");
    }

    @Test
    public void aNameEndingInVcfWithoutADotIsUnrecognised() {
        assertNameIsUnrecognised("xvcf");
    }

    @Test
    public void aBareGzNameIsUnrecognised() {
        assertNameIsUnrecognised("x.gz");
    }

    @Test
    public void aTxtGzNameIsUnrecognised() {
        assertNameIsUnrecognised("x.txt.gz");
    }

    @Test
    public void aBcfGzNameIsUnrecognised() {
        assertNameIsUnrecognised("x.bcf.gz");
    }

    @Test
    public void aTribbleIndexNameIsUnrecognised() {
        assertNameIsUnrecognised("x.vcf.idx");
    }

    @Test
    public void aTabixIndexNameIsUnrecognised() {
        assertNameIsUnrecognised("x.vcf.gz.tbi");
    }

    @Test
    public void anEmptyNameIsUnrecognised() {
        assertNameIsUnrecognised("");
    }

    @Test
    public void aPathIsRecognisedByItsFileName() {
        Assert.assertEquals(
                VariantFileType.fromFileName(Path.of("dir", "x.VCF.GZ")),
                Optional.of(VariantFileType.BLOCK_COMPRESSED_VCF));
    }

    @Test
    public void aPathIsNotRecognisedByItsDirectoryName() {
        Assert.assertEquals(VariantFileType.fromFileName(Path.of("x.vcf", "y.txt")), Optional.empty());
    }

    @Test
    public void aRootPathIsUnrecognised() {
        Assert.assertEquals(VariantFileType.fromFileName(Path.of("/")), Optional.empty());
    }

    // -- fromContents(byte[]) --

    private static byte[] ascii(final String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] bgzf(final byte[] data) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (BlockCompressedOutputStream bgzf = new BlockCompressedOutputStream(out, (Path) null)) {
            bgzf.write(data);
        }
        return out.toByteArray();
    }

    private static byte[] gzip(final byte[] data) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data);
        }
        return out.toByteArray();
    }

    /** The start of a raw BCF: magic, version and a header length, as {@link htsjdk.variant.bcf2.BCF2Codec} reads. */
    private static byte[] rawBcfStart(final int major, final int minor) {
        return new byte[] {'B', 'C', 'F', (byte) major, (byte) minor, 0x10, 0, 0, 0};
    }

    private static void assertContentsAre(final byte[] prefix, final VariantFileType expected) {
        Assert.assertEquals(VariantFileType.fromContents(prefix), Optional.of(expected));
    }

    private static void assertContentsAreUnrecognised(final byte[] prefix) {
        Assert.assertEquals(VariantFileType.fromContents(prefix), Optional.empty());
    }

    @Test
    public void aVcf42IsVcf() {
        assertContentsAre(ascii(VCF_42_HEADER), VariantFileType.VCF);
    }

    @Test
    public void aVcf45IsVcf() {
        assertContentsAre(ascii("##fileformat=VCFv4.5\n"), VariantFileType.VCF);
    }

    @Test
    public void aVcf33IsVcf() {
        assertContentsAre(ascii("##fileformat=VCFv3.3\n"), VariantFileType.VCF);
    }

    @Test
    public void aVcf32IsVcf() {
        assertContentsAre(ascii("##format=VCRv3.2\n"), VariantFileType.VCF);
    }

    @Test
    public void aBgzfVcfIsBlockCompressedVcf() throws IOException {
        assertContentsAre(bgzf(ascii(VCF_42_HEADER)), VariantFileType.BLOCK_COMPRESSED_VCF);
    }

    @Test
    public void aGzipVcfIsBlockCompressedVcf() throws IOException {
        assertContentsAre(gzip(ascii(VCF_42_HEADER)), VariantFileType.BLOCK_COMPRESSED_VCF);
    }

    @Test
    public void aGzipVcfWithALongFileNameInItsHeaderIsBlockCompressedVcf() throws IOException {
        final GzipParameters parameters = new GzipParameters();
        final char[] longName = new char[20_000];
        Arrays.fill(longName, 'n');
        parameters.setFileName(new String(longName) + ".vcf");
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(out, parameters)) {
            gzip.write(ascii(VCF_42_HEADER));
        }
        assertContentsAre(out.toByteArray(), VariantFileType.BLOCK_COMPRESSED_VCF);
    }

    @Test
    public void aMagicLineSplitAcrossGzipMembersIsBlockCompressedVcf() throws IOException {
        assertContentsAre(
                GzipTestStreams.multiMemberGzip("##file", "format=VCFv4.2\n"), VariantFileType.BLOCK_COMPRESSED_VCF);
    }

    @Test
    public void aRawBcf21IsBcf() {
        assertContentsAre(rawBcfStart(2, 1), VariantFileType.BCF);
    }

    @Test
    public void aRawBcf22IsBcf() {
        assertContentsAre(rawBcfStart(2, 2), VariantFileType.BCF);
    }

    @Test
    public void aBgzfBcf21IsBcf() throws IOException {
        assertContentsAre(bgzf(rawBcfStart(2, 1)), VariantFileType.BCF);
    }

    @Test
    public void aBgzfBcf22IsBcf() throws IOException {
        assertContentsAre(bgzf(rawBcfStart(2, 2)), VariantFileType.BCF);
    }

    @Test
    public void aBcfOfAnotherMinorVersionTwoIsBcf() {
        assertContentsAre(rawBcfStart(2, 7), VariantFileType.BCF);
    }

    @Test
    public void aBcf1IsUnrecognised() {
        assertContentsAreUnrecognised(rawBcfStart(1, 1));
    }

    @Test
    public void aBcfMagicWithoutItsVersionIsUnrecognised() {
        assertContentsAreUnrecognised(ascii("BCF"));
    }

    @Test
    public void aHeaderWithoutAFileformatLineIsUnrecognised() {
        assertContentsAreUnrecognised(ascii("#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n"));
    }

    @Test
    public void anEmptyPrefixIsUnrecognised() {
        assertContentsAreUnrecognised(new byte[0]);
    }

    @Test
    public void aThreeBytePrefixIsUnrecognised() {
        assertContentsAreUnrecognised(ascii("##f"));
    }

    @Test
    public void aShortPrefixOfAVcfIsUnrecognised() {
        assertContentsAreUnrecognised(ascii("##fileformat=VCF"));
    }

    @Test
    public void randomBytesAreUnrecognised() {
        final byte[] random = new byte[4096];
        new Random(42).nextBytes(random);
        assertContentsAreUnrecognised(random);
    }

    @Test
    public void randomBytesAfterTheGzipMagicAreUnrecognised() {
        final Random random = new Random(42);
        for (int i = 0; i < 100; i++) {
            final byte[] prefix = new byte[1 + random.nextInt(4096)];
            random.nextBytes(prefix);
            prefix[0] = 0x1f;
            if (prefix.length > 1) prefix[1] = (byte) 0x8b;
            if (prefix.length > 2) prefix[2] = 8;
            assertContentsAreUnrecognised(prefix);
        }
    }

    @Test
    public void aGzipHeaderAloneIsUnrecognised() throws IOException {
        final byte[] gzip = gzip(ascii(VCF_42_HEADER));
        assertContentsAreUnrecognised(Arrays.copyOf(gzip, 10));
    }

    @Test
    public void aGzipVcfCutBeforeTheMagicLineIsInflatedIsUnrecognised() throws IOException {
        final byte[] gzip = gzip(ascii(VCF_42_HEADER));
        assertContentsAreUnrecognised(Arrays.copyOf(gzip, 12));
    }

    @Test
    public void aBgzfVcfCutInsideItsFirstBlockIsBlockCompressedVcf() throws IOException {
        final StringBuilder header = new StringBuilder("##fileformat=VCFv4.2\n");
        final Random random = new Random(42);
        for (int i = 0; i < 2000; i++) {
            header.append("##contig=<ID=contig")
                    .append(random.nextInt())
                    .append(",length=")
                    .append(random.nextInt(Integer.MAX_VALUE))
                    .append(">\n");
        }
        final byte[] bgzf = bgzf(ascii(header.toString()));
        // the first block's length, less one, is in bytes 16 and 17
        final int firstBlockLength = ((bgzf[16] & 0xff) | (bgzf[17] & 0xff) << 8) + 1;
        Assert.assertTrue(firstBlockLength > 10_000, "the first block is too small to cut: " + firstBlockLength);
        assertContentsAre(Arrays.copyOf(bgzf, firstBlockLength / 2), VariantFileType.BLOCK_COMPRESSED_VCF);
    }

    // -- fromContents(InputStream) --

    @Test
    public void aMarkSupportingStreamIsRecognisedAndLeftWhereItWas() throws IOException {
        final InputStream stream = new BufferedInputStream(new ByteArrayInputStream(bgzf(rawBcfStart(2, 2))));
        Assert.assertEquals(VariantFileType.fromContents(stream), Optional.of(VariantFileType.BCF));
        Assert.assertEquals(stream.read(), 0x1f);
    }

    @Test
    public void aStreamLongerThanTheProbeIsLeftWhereItWas() throws IOException {
        final byte[] vcf = new byte[VariantFileType.PROBE_LENGTH * 3];
        Arrays.fill(vcf, (byte) 'A');
        final byte[] magic = ascii(VCF_42_HEADER);
        System.arraycopy(magic, 0, vcf, 0, magic.length);
        final InputStream stream = new BufferedInputStream(new ByteArrayInputStream(vcf));
        Assert.assertEquals(VariantFileType.fromContents(stream), Optional.of(VariantFileType.VCF));
        Assert.assertEquals(stream.readAllBytes(), vcf);
    }

    /**
     * A stream of {@code bytes} that fails if read past them, where a pipe whose writer has written only those
     * bytes, and is still open, would block.
     */
    private static InputStream streamThatMustNotBeReadPast(final byte[] bytes) {
        return new InputStream() {
            private int position = 0;

            @Override
            public int read() {
                if (position == bytes.length) {
                    throw new AssertionError("read past the bytes written so far, where a pipe would block");
                }
                return bytes[position++] & 0xff;
            }

            @Override
            public int read(final byte[] buffer, final int offset, final int length) {
                if (length == 0) {
                    return 0;
                }
                if (position == bytes.length) {
                    throw new AssertionError("read past the bytes written so far, where a pipe would block");
                }
                final int n = Math.min(length, bytes.length - position);
                System.arraycopy(bytes, position, buffer, offset, n);
                position += n;
                return n;
            }

            @Override
            public int available() {
                return bytes.length - position;
            }
        };
    }

    @Test
    public void aStreamIsRecognisedWithoutReadingPastTheBytesThatTellItsType() {
        final InputStream stream = new BufferedInputStream(streamThatMustNotBeReadPast(ascii(VCF_42_HEADER)));
        Assert.assertEquals(VariantFileType.fromContents(stream), Optional.of(VariantFileType.VCF));
    }

    @Test
    public void aCompressedStreamIsRecognisedWithoutReadingPastTheBytesThatTellItsType() throws IOException {
        final InputStream stream = new BufferedInputStream(streamThatMustNotBeReadPast(gzip(ascii(VCF_42_HEADER))));
        Assert.assertEquals(VariantFileType.fromContents(stream), Optional.of(VariantFileType.BLOCK_COMPRESSED_VCF));
    }

    @Test
    public void aStreamThatDoesNotSupportMarkIsRejected() {
        final InputStream stream = new PushbackInputStream(new ByteArrayInputStream(ascii(VCF_42_HEADER)));
        Assert.assertThrows(IllegalArgumentException.class, () -> VariantFileType.fromContents(stream));
    }

    // -- fromContents(SeekableStream) --

    @Test
    public void aSeekableStreamIsRecognisedFromItsPositionAndSeekedBackToIt() throws IOException {
        final byte[] junk = ascii("not a variant file\n");
        final byte[] vcf = gzip(ascii(VCF_42_HEADER));
        final byte[] bytes = Arrays.copyOf(junk, junk.length + vcf.length);
        System.arraycopy(vcf, 0, bytes, junk.length, vcf.length);
        final SeekableStream stream = new SeekableMemoryStream(bytes, "test");
        stream.seek(junk.length);
        Assert.assertEquals(VariantFileType.fromContents(stream), Optional.of(VariantFileType.BLOCK_COMPRESSED_VCF));
        Assert.assertEquals(stream.position(), junk.length);
    }

    // -- fromContents(Path) --

    @Test
    public void aFileIsRecognisedByItsContentsWhateverItsName() throws IOException {
        final Path bcf = Files.createTempFile("VariantFileTypeTest.", ".txt");
        try {
            Files.write(bcf, bgzf(rawBcfStart(2, 2)));
            Assert.assertEquals(VariantFileType.fromContents(bcf), Optional.of(VariantFileType.BCF));
        } finally {
            Files.delete(bcf);
        }
    }

    @Test
    public void aMissingFileThrowsRuntimeIOException() throws IOException {
        final Path missing = Files.createTempFile("VariantFileTypeTest.", ".vcf");
        Files.delete(missing);
        Assert.assertThrows(RuntimeIOException.class, () -> VariantFileType.fromContents(missing));
    }
}
