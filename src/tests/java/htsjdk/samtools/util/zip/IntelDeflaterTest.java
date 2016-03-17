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
package htsjdk.samtools.util.zip;

import htsjdk.samtools.*;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.ProgressLogger;
import htsjdk.samtools.util.zip.DeflaterFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.zip.DataFormatException;


/**
 * This is a test for IntelDeflater. 
 * Invoke using:
 * java -Dsamjdk.intel_deflater_so_path=$PWD/lib/jni/libIntelDeflater.so -cp dist/htsjdk-2.1.1.jar:testclasses/ htsjdk.samtools.util.zip.IntelDeflaterTest in.bam false a.bam 1
 * <p>
 * Arguments:
 * - the first argument is the input file (SAM or BAM)
 * - the second argument is a boolean (true or false) that indicates whether reads are to be eagerly decoded (useful for benchmarking)
 * - the third argument is the name of the output BAM file 
 * - the forth argument is compression level
 */
public final class IntelDeflaterTest {
    private IntelDeflaterTest() {
    }

    private static final Log log = Log.getInstance(IntelDeflaterTest.class);

    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            System.out.println("Usage: " + IntelDeflaterTest.class.getCanonicalName() + " inFile eagerDecode [outFile]");
            System.exit(1);
        }
        final File inputFile = new File(args[0]);
        final boolean eagerDecode = Boolean.parseBoolean(args[1]); //useful to test (realistic) scenarios in which every record is always fully decoded.
        final File outputFile = new File(args[2]);

	final int compressionLevel = Integer.parseInt(args[3]);


        final long start = System.currentTimeMillis();

	if (!DeflaterFactory.usingIntelDeflater()) {
	    System.out.println("Running without IntelDeflater. Please specify the path to enable IntelDeflater, exiting");
	    System.exit(1);
	}

        log.info("Start with args:" + Arrays.toString(args));
        printConfigurationInfo();

        SamReaderFactory readerFactory = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT);
        if (eagerDecode) {
            readerFactory = readerFactory.enable(SamReaderFactory.Option.EAGERLY_DECODE);
        }


        try (final SamReader reader = readerFactory.open(inputFile)) {
            final SAMFileHeader header = reader.getFileHeader();
            final SAMFileWriter writer = new SAMFileWriterFactory().makeBAMWriter(header, true, outputFile, compressionLevel);
	    final ProgressLogger pl = new ProgressLogger(log, 1000000);
	    for (final SAMRecord record : reader) {
		if (writer != null) {
		    writer.addAlignment(record);
		}
		pl.record(record);
	    }
	    writer.close();
	    log.info("BAM File Written using IntelDeflater");
	    log.info("Read the outputted BAM now to ensure correctness");

	    try (final SamReader outputReader = readerFactory.open(outputFile)) {
		    for (final SAMRecord record : outputReader) {
			pl.record(record);
		    }
		} catch (Exception e) {
		log.error("Error reading record written with the IntelDeflater library");
		System.exit(1);
	    }
        }
	
        final long end = System.currentTimeMillis();
        log.info(String.format("Done. Elapsed time %.3f seconds", (end - start) / 1000.0));
    }

    private static void printConfigurationInfo() throws IOException {
        log.info("Executing as " +
                System.getProperty("user.name") + '@' + InetAddress.getLocalHost().getHostName() +
                " on " + System.getProperty("os.name") + ' ' + System.getProperty("os.version") +
                ' ' + System.getProperty("os.arch") + "; " + System.getProperty("java.vm.name") +
                ' ' + System.getProperty("java.runtime.version") +
                ' ' + (DeflaterFactory.usingIntelDeflater() ? "IntelDeflater" : "JdkDeflater"));
        log.info("CREATE_INDEX:" + Defaults.CREATE_INDEX +
                ' ' + "CREATE_MD5:" + Defaults.CREATE_MD5 +
                ' ' + "USE_ASYNC_IO:" + Defaults.USE_ASYNC_IO +
                ' ' + "BUFFER_SIZE:" + Defaults.BUFFER_SIZE +
                ' ' + "COMPRESSION_LEVEL:" + Defaults.COMPRESSION_LEVEL +
                ' ' + "NON_ZERO_BUFFER_SIZE:" + Defaults.NON_ZERO_BUFFER_SIZE +
                ' ' + "CUSTOM_READER_FACTORY:" + Defaults.CUSTOM_READER_FACTORY);
    }
}
