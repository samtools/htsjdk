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
package htsjdk.samtools.example;

import htsjdk.samtools.*;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.ProgressLogger;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


/**
 * This is a example program showing how to use SAM readers and (optionally) writers.
 * It's also useful for measuring time.
 * An example invocation is:
 * <code>java -cp dist/htsjdk-2.1.1.jar htsjdk.samtools.example.PrintReadsExample in.bam false a.bam</code>
 * Arguments:
 * - the first argument is the input file (SAM or BAM)
 * - the second argument is a boolean (true or false) that indicates whether reads are to be eagerly decoded (useful for benchmarking)
 * - the third argument is optional and is the name of the output file (nothing gets written if this argument is missing)
 */
public final class PrintReadsExample {
    private PrintReadsExample() {
    }

    private static final Log log = Log.getInstance(PrintReadsExample.class);

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: " + PrintReadsExample.class.getCanonicalName() + " inFile eagerDecode [outFile]");
            System.exit(1);
        }
        final File inputFile = new File(args[0]);
        final boolean eagerDecode = Boolean.parseBoolean(args[1]); //useful to test (realistic) scenarios in which every record is always fully decoded.
        final File outputFile = args.length >= 3 ? new File(args[2]) : null;

        final long start = System.currentTimeMillis();

        log.info("Start with args:" + Arrays.toString(args));
        printConfigurationInfo();

        SamReaderFactory readerFactory = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT);
        if (eagerDecode) {
            readerFactory = readerFactory.enable(SamReaderFactory.Option.EAGERLY_DECODE);
        }

        try (final SamReader reader = readerFactory.open(inputFile)) {
            final SAMFileHeader header = reader.getFileHeader();
            try (final SAMFileWriter writer = outputFile != null ? new SAMFileWriterFactory().makeBAMWriter(header, true, outputFile) : null) {
                final ProgressLogger pl = new ProgressLogger(log, 1000000);
                for (final SAMRecord record : reader) {
                    if (writer != null) {
                        writer.addAlignment(record);
                    }
                    pl.record(record);
                }
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
                ' ' + System.getProperty("java.runtime.version"));

        final List<String> list = Defaults.allDefaults().entrySet().stream().map(e -> e.getKey() + ':' + e.getValue()).collect(Collectors.toList());
        log.info(String.join(" ", list));
    }
}
