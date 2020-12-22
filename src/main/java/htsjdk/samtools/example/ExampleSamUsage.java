/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
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
 * THE SOFTWARE.
 */
package htsjdk.samtools.example;

import htsjdk.io.HtsPath;
import htsjdk.samtools.DefaultSAMRecordFactory;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.seekablestream.SeekableStream;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;

public class ExampleSamUsage {
    public static SeekableStream myIndexSeekableStream() {
        throw new UnsupportedOperationException();
    }

    /**
     * Example usages of {@link htsjdk.samtools.SamReaderFactory}
     */
    public void openSamExamples() throws IOException {
        /**
         * Simplest case
         */
        try(SamReader reader = SamReaderFactory.makeDefault().open(new File("/my.bam"))){
        };

        /**
         * With different reader options
         */
        try(SamReader readerFromConfiguredFactory =
                SamReaderFactory.make()
                        .enable(SamReaderFactory.Option.DONT_MEMORY_MAP_INDEX)
                        .validationStringency(ValidationStringency.SILENT)
                        .samRecordFactory(DefaultSAMRecordFactory.getInstance())
                        .open(new HtsPath("/my.bam").toPath())) {
        }


        /**
         * With a more complicated source 
         */
        try(SamReader complicatedReader =
                SamReaderFactory.makeDefault()
                        .open(
                                SamInputResource.of(new URL("http://broadinstitute.org/my.bam")).index(myIndexSeekableStream())
                        )) {
        }

        /**
         * Broken down
         */
        final SamReaderFactory factory =
                SamReaderFactory.makeDefault().enable(SamReaderFactory.Option.VALIDATE_CRC_CHECKSUMS).validationStringency(ValidationStringency.LENIENT);

        final SamInputResource resource = SamInputResource.of(new HtsPath("/my.bam").toPath()).index(new URL("http://broadinstitute.org/my.bam.bai"));
        try(SamReader myReader = factory.open(resource)) {

            for (final SAMRecord samRecord : myReader) {
                System.err.print(samRecord);
            }
        }

    }

    /**
     * Read a SAM or BAM file, convert each read name to upper case, and write a new
     * SAM or BAM file.
     */
    public void convertReadNamesToUpperCase(final File inputSamOrBamFile, final File outputSamOrBamFile) throws IOException {

        try (SamReader reader = SamReaderFactory.makeDefault().open(inputSamOrBamFile);

             // makeSAMorBAMWriter() writes a file in SAM text or BAM binary format depending
             // on the file extension, which must be either .sam or .bam.

             // Since the SAMRecords will be written in the same order as they appear in the input file,
             // and the output file is specified as having the same sort order (as specified in
             // SAMFileHeader.getSortOrder(), presorted == true.  This is much more efficient than
             // presorted == false, if coordinate or queryname sorting is specified, because the SAMRecords
             // can be written to the output file directly rather than being written to a temporary file
             // and sorted after all records have been sent to outputSam.

             SAMFileWriter outputSam = new SAMFileWriterFactory().makeSAMOrBAMWriter(reader.getFileHeader(),
                     true, outputSamOrBamFile)) {

            for (final SAMRecord samRecord : reader) {
                // Convert read name to upper case.
                samRecord.setReadName(samRecord.getReadName().toUpperCase());
                outputSam.addAlignment(samRecord);
            }
        }
    }
}
