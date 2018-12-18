/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 Daniel Gomez-Sanchez
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package htsjdk.samtools.seekablestream;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.BufferedLineReader;
import htsjdk.samtools.util.LineReader;
import htsjdk.samtools.util.TestUtil;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.zip.GZIPInputStream;

/**
 * Tests for the integration between {@link SeekableStream} and {@link GZIPInputStream}.
 *
 * <p>{@link GZIPInputStream} has a confirmed bug, that it relies on the {@link InputStream#available()}
 * method to detect end-of-file, which is unsafe given its contract (see the Oracle's bug tracker
 * issue https://bugs.java.com/bugdatabase/view_bug.do?bug_id=7036144#).
 *
 * <p>Previous implementations of {@link SeekableStream} didn't implement {@link InputStream#available()},
 * returning always 0 (see https://github.com/samtools/htsjdk/issues/898). This test confirm that if
 * the method is not implemented, a premature end-of-file can be detected prematurely.
 *
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public class SeekableStreamGZIPinputStreamIntegrationTest extends HtsjdkTest {
    private static final String TEST_CHR = "1";
    private static Collection<Allele> alleles = Arrays.asList(Allele.create("A", true), Allele.create("C"));
    private static String RANDOM_ATTRIBUTE = "RD";


    private static File createBgzipVcfsWithVariableSize(final int firstRecordAttributeLength, final int nSmallRecords) throws Exception {
        final VariantContext longRecord = new VariantContextBuilder("long", TEST_CHR, 1, 1, alleles)
                .attribute(RANDOM_ATTRIBUTE, generateRandomString(firstRecordAttributeLength))
                .make();
        final File tempFile = Files.createTempFile("test" + firstRecordAttributeLength + "_" + nSmallRecords, ".vcf.gz").toFile();
        try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                .setOptions(VariantContextWriterBuilder.NO_OPTIONS)
                .setOutputFile(tempFile)
                .setOutputFileType(VariantContextWriterBuilder.OutputType.BLOCK_COMPRESSED_VCF)
                .build()) {
            writer.setHeader(createTestHeader()); // do not write the header
            writer.add(longRecord);
            for (int i = 2; i <= nSmallRecords + 1; i++) {
                final VariantContext smallRecord = new VariantContextBuilder("short", TEST_CHR, i, i, alleles).attribute(RANDOM_ATTRIBUTE, ".").make();
                writer.add(smallRecord);
            }
        }
        return tempFile;
    }


    private static VCFHeader createTestHeader() {
        final VCFHeader header = new VCFHeader();
        header.addMetaDataLine(new VCFInfoHeaderLine(RANDOM_ATTRIBUTE, 1, VCFHeaderLineType.Character, "random string"));
        return header;
    }

    private static String generateRandomString(final int length) {
        final Random random = new Random(TestUtil.RANDOM_SEED);
        final StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            // generate number from 0 to 9, and append to the builder
            builder.append(random.nextInt(10));
        }
        return builder.toString();
    }

    @DataProvider
    public Iterator<Object[]> compressedVcfsToTest() throws Exception {
        final List<Object[]> data = new ArrayList<>();
        final int nSmallRecords = 1000000;
        for (int firstRecordLength = 1000; firstRecordLength <= 10000; firstRecordLength+=1000) {
            data.add(new Object[]{createBgzipVcfsWithVariableSize(firstRecordLength, nSmallRecords), nSmallRecords+1});
        }
        return data.iterator();
    }

    @Test(dataProvider = "compressedVcfsToTest")
    public void testWrappedSeekableStreamInGZIPinputStream(final File input, final long nLines) throws Exception {
        try (final LineReader reader = new BufferedLineReader(new GZIPInputStream(new SeekableFileStream(input)))) {
            for (int i = 0; i < nLines; i++) {
                Assert.assertNotNull(reader.readLine(), "line #" + reader.getLineNumber());
            }
            Assert.assertNull(reader.readLine());
            Assert.assertEquals(reader.getLineNumber(), nLines);
        }
    }

    @Test(dataProvider = "compressedVcfsToTest")
    public void testConsistencyWithBgzip(final File input, final long nLines) throws Exception {
        try (final InputStream gzIs = new GZIPInputStream(new SeekableFileStream(input));
             final InputStream bgzIs = new BlockCompressedInputStream(input)) {
            int bgz = bgzIs.read();
            while (bgz != -1) {
                Assert.assertEquals(gzIs.read(), bgz);
                bgz = bgzIs.read();
            }
            Assert.assertEquals(gzIs.read(), bgz);
        }

    }
}
