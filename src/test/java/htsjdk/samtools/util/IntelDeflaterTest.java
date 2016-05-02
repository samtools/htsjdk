/*
 * The MIT License
 *
 * Copyright (c) 2016 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 */
package htsjdk.samtools.util;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.util.zip.DeflaterFactory;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * This is a test for IntelDeflater.
 */

public class IntelDeflaterTest {
    static final File TEST_DIR = new File("testdata/htsjdk/samtools");

    @DataProvider(name="TestIntelDeflaterIsLoadedData")
    Iterator<Object[]> TestIntelDeflaterIsLoadedData(){

        List<File> files = CollectionUtil.makeList(
                new File(TEST_DIR, "coordinate_sorted.sam"),
                new File(TEST_DIR, "queryname_sorted.sam"),
                new File(TEST_DIR, "compressed.bam"),
                new File(TEST_DIR, "empty.bam"),
                new File(TEST_DIR, "cram_with_bai_index.cram"),
                new File(TEST_DIR, "uncompressed.sam"),
                new File(TEST_DIR, "cram_with_crai_index.cram"));

        List<Boolean> eagerlyDecodes = CollectionUtil.makeList(Boolean.TRUE, Boolean.FALSE);
        List<Integer> compressionLevels = CollectionUtil.makeList(1, 2, 3, 4, 5, 6, 7, 8, 9);

        List<Object[]> retVal = new ArrayList<>();
        files.stream()
                .forEach(file ->
                        eagerlyDecodes.stream()
                                .forEach(eagerlyDecode -> compressionLevels.stream()
                                        .forEach(compressionLevel ->
                                                retVal.add(new Object[]{file, eagerlyDecode, compressionLevel}))));
        return retVal.iterator();
    }

    @Test(dataProvider = "TestIntelDeflaterIsLoadedData", groups="intel",expectedExceptions = IllegalAccessError.class)
    public void TestIntelDeflatorIsLoaded(final File inputFile, final Boolean eagerlyDecode,final Integer compressionLevel) throws IOException,IllegalAccessError {

        Log log = Log.getInstance(IntelDeflaterTest.class);
        Log.setGlobalLogLevel(Log.LogLevel.INFO);

        log.info("In TestIntelDeflatorIsLoaded. testing: " + inputFile);
        IOUtil.assertFileIsReadable(inputFile);

        final File outputFile = File.createTempFile("IntelDeflater", "bam");
        outputFile.deleteOnExit();


        Assert.assertTrue(DeflaterFactory.usingIntelDeflater(), "IntelDeflater is not loaded.");
        log.info("IntelDeflater is loaded");


        SamReaderFactory readerFactory = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT);
        if (eagerlyDecode) {
            readerFactory = readerFactory.enable(SamReaderFactory.Option.EAGERLY_DECODE);
        }

        if(inputFile.getName().endsWith(".cram")) {
            readerFactory.referenceSequence(new File(TEST_DIR, "hg19mini.fasta"));
        }

        final SamReader reader = readerFactory.open(inputFile);
        final SAMFileHeader header = reader.getFileHeader();
        int nRecords = 0;
        try (final SAMFileWriter writer = new SAMFileWriterFactory().makeBAMWriter(header, true, outputFile, compressionLevel)) {
            for (final SAMRecord record : reader) {
                writer.addAlignment(record);
                nRecords++;
            }
        } catch (Exception e) {
            Assert.fail("Error reading record no. " + nRecords);
        }

        log.info("wrote " + nRecords + " Records");

        int nReadRecords = 0;
        try (final SamReader outputReader = readerFactory.open(outputFile)) {
            for (final SAMRecord ignored : outputReader) {
                nReadRecords++;
            }
        } catch (Exception e) {
            Assert.fail("Error reading record written with the IntelDeflater library");
        }
        log.info("read " + nReadRecords + " Records");

        Assert.assertEquals(nReadRecords, nRecords, "Number of read records mismatches number of written records.");

        throw new IllegalAccessError("Got to the end successfully! (i.e. no segmentation fault");
    }
}
