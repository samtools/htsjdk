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
package htsjdk.variant.example;

import htsjdk.samtools.Defaults;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.ProgressLogger;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFCodec;
import htsjdk.variant.vcf.VCFHeader;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * This is a example program showing how to use Feature readers and (optionally) writers.
 * It's also useful for measuring time.
 * An example invocation is:
 * java -cp dist/htsjdk-2.1.1.jar htsjdk.variant.example.PrintVariantsExample in.vcf out.vcf
 * <p>
 * Arguments:
 * - the first argument is the input file (VCF)
 * - the second argument is optional and is the name of the output file (nothing gets written if this argument is missing)
 */
public final class PrintVariantsExample {
    private PrintVariantsExample() {
    }

    private static final Log log = Log.getInstance(PrintVariantsExample.class);

    public static void main(final String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: " + PrintVariantsExample.class.getCanonicalName() + " inFile [outFile]");
            System.exit(1);
        }
        final File inputFile = new File(args[0]);
        final File outputFile = args.length >= 2 ? new File(args[1]) : null;

        final long start = System.currentTimeMillis();

        log.info("Start with args:" + Arrays.toString(args));
        printConfigurationInfo();

        try(final VariantContextWriter writer = outputFile == null ? null : new VariantContextWriterBuilder().setOutputFile(outputFile).setOutputFileType(VariantContextWriterBuilder.OutputType.VCF).unsetOption(Options.INDEX_ON_THE_FLY).build();
            final AbstractFeatureReader<VariantContext, LineIterator> reader = AbstractFeatureReader.getFeatureReader(inputFile.getAbsolutePath(), new VCFCodec(), false)){

            log.info(reader.getClass().getSimpleName() + " hasIndex " + reader.hasIndex());
            if (writer != null){
                log.info(writer.getClass().getSimpleName());
                writer.writeHeader((VCFHeader) reader.getHeader());
            }

            final ProgressLogger pl = new ProgressLogger(log, 1000000);
            for (final VariantContext vc : reader.iterator()) {
                if (writer != null){
                    writer.add(vc);
                }
                pl.record(vc.getContig(), vc.getStart());
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

        log.info(Defaults.allDefaults().entrySet().stream().map(e -> e.getKey() + ':' + e.getValue()).collect(Collectors.<String>joining(" ")));
    }
}
