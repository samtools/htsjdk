/*
 * The MIT License
 *
 * Copyright (c) 2019 The Broad Institute
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
package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.CRAIIndex;
import htsjdk.samtools.cram.CRAIIndexMerger;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.seekablestream.SeekablePathStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.ProgressLoggerInterface;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.utils.ValidationUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class CRAMMergerTest extends HtsjdkTest {

    private final static Path CRAM_FILE = new File("src/test/resources/htsjdk/samtools/cram/CEUTrio.HiSeq.WGS.b37.NA12878.20.21.10m-10m100.cram").toPath();
    private final static Path CRAM_REF = new File("src/test/resources/htsjdk/samtools/reference/human_g1k_v37.20.21.fasta.gz").toPath();

    /**
     * Writes a <i>partitioned CRAM</i>.
     *
     * Technically, it is not valid to concatenate non-hidden files to create a CRAM file from a partitioned CRAM file, since each slice header has a record
     * counter acting a a sequential index of records in the file, which would be incorrect if the file was created by concatenation. The implementation
     * here ignores this complication.
     *
     * Note that this writer is only for single-threaded use. Consider using the implementation in Disq for a partitioned CRAM writer
     * that works with multiple threads or in a distributed setting.
     *
     * @see CRAIIndexMerger
     */
    static class PartitionedCRAMFileWriter implements SAMFileWriter {
        private final Path outputDir;
        private final CRAMReferenceSource referenceSource;
        private final SAMFileHeader header;
        private int recordsPerPart;
        private long recordCount = 0;
        private int partNumber = -1;
        private CRAMContainerStreamWriter samStreamWriter;
        private ProgressLoggerInterface progressLogger;

        public PartitionedCRAMFileWriter(Path outputDir, CRAMReferenceSource referenceSource, SAMFileHeader header, int recordsPerPart) {
            this.outputDir = outputDir;
            this.referenceSource = referenceSource;
            this.header = header;
            this.recordsPerPart = recordsPerPart;
        }

        @Override
        public void addAlignment(SAMRecord alignment) {
            if (recordCount == 0) {
                // write header
                try (OutputStream out = Files.newOutputStream(outputDir.resolve("header"))) {
                    CRAMContainerStreamWriter cramWriter = new CRAMContainerStreamWriter(out, null, referenceSource, this.header, "header");
                    cramWriter.writeHeader(this.header);
                    cramWriter.finish(false);
                } catch (IOException e) {
                    throw new RuntimeIOException(e);
                }
            }
            if (recordCount % recordsPerPart == 0) {
                // start a new part
                try {
                    if (samStreamWriter != null) {
                        samStreamWriter.finish(false);
                    }
                    partNumber++;
                    String partName = String.format("part-%05d", partNumber);
                    OutputStream out = Files.newOutputStream(outputDir.resolve(partName));
                    OutputStream indexOut = Files.newOutputStream(outputDir.resolve("." + partName + FileExtensions.CRAM_INDEX));
                    CRAMCRAIIndexer indexer = indexOut == null ? null : new CRAMCRAIIndexer(indexOut, header);
                    samStreamWriter = new CRAMContainerStreamWriter(out, referenceSource, header, partName, indexer);
                } catch (IOException e) {
                    throw new RuntimeIOException(e);
                }
            }
            recordCount++;
            samStreamWriter.writeAlignment(alignment);
            if (progressLogger != null) {
                progressLogger.record(alignment);
            }
        }

        @Override
        public SAMFileHeader getFileHeader() {
            return header;
        }

        @Override
        public void setProgressLogger(ProgressLoggerInterface progressLogger) {
            this.progressLogger = progressLogger;
        }

        @Override
        public void close() {
            if (samStreamWriter != null) {
                samStreamWriter.finish(false);
            }
            // write terminator
            try (OutputStream out = Files.newOutputStream(outputDir.resolve("terminator"))) {
                CramIO.writeCramEOF(CramVersions.DEFAULT_CRAM_VERSION, out);
            } catch (IOException e) {
                throw new RuntimeIOException(e);
            }
        }
    }

    /**
     * Merge the files created by {@link PartitionedCRAMFileWriter} into a single CRAM file and index.
     */
    static class PartitionedCRAMFileMerger {
        public void merge(Path dir, Path outputCram, Path outputCrai) throws IOException {
            Path headerPath = dir.resolve("header");
            List<Path> cramParts = Files.list(dir)
                    .filter(path -> !path.toString().endsWith(FileExtensions.CRAM_INDEX)) // include header and terminator
                    .sorted()
                    .collect(Collectors.toList());
            List<Path> craiParts = Files.list(dir)
                    .filter(path -> path.toString().endsWith(FileExtensions.CRAM_INDEX))
                    .sorted()
                    .collect(Collectors.toList());

            Assert.assertTrue(craiParts.size() > 1);

            ValidationUtils.validateArg(cramParts.size() - 2 == craiParts.size(), "Number of CRAM part files does not match number of CRAI files (" + craiParts.size() + ")");

            // merge CRAM parts
            try (OutputStream out = Files.newOutputStream(outputCram)) {
                for (Path cramPart : cramParts) {
                    Files.copy(cramPart, out);
                }
            }

            // merge index parts
            try (OutputStream out = Files.newOutputStream(outputCrai)) {
                CRAIIndexMerger craiIndexMerger = new CRAIIndexMerger(out, Files.size(headerPath));
                int i = 1; // start from 1 since we ignore the header
                for (Path craiPart : craiParts) {
                    try (InputStream in = Files.newInputStream(craiPart)) {
                        CRAIIndex index = CRAMCRAIIndexer.readIndex(in);
                        craiIndexMerger.processIndex(index, Files.size(cramParts.get(i++)));
                    }
                }
                craiIndexMerger.finish(Files.size(outputCram));
            }
        }
    }

    private static Path indexCram(Path cram, Path crai) throws IOException {
        try (SeekableStream in = new SeekablePathStream(cram);
             OutputStream out = Files.newOutputStream(crai)) {
            CRAMCRAIIndexer.writeIndex(in, out);
        }
        return crai;
    }

    @Test
    public void test() throws IOException {
        final Path outputDir = IOUtil.createTempDir(this.getClass().getSimpleName() + ".", ".tmp").toPath();
        IOUtil.deleteOnExit(outputDir);

        final Path outputCram = File.createTempFile(this.getClass().getSimpleName() + ".", FileExtensions.CRAM).toPath();
        IOUtil.deleteOnExit(outputCram);

        final Path outputCrai = IOUtil.addExtension(outputCram, FileExtensions.CRAM_INDEX);
        IOUtil.deleteOnExit(outputCrai);

        final Path outputCraiMerged = File.createTempFile(this.getClass().getSimpleName() + ".", FileExtensions.CRAM_INDEX).toPath();
        IOUtil.deleteOnExit(outputCraiMerged);

        // 1. Read an input CRAM and write it out in partitioned form (header, parts, terminator)
        ReferenceSource referenceSource = new ReferenceSource(CRAM_REF);
        try (SamReader samReader = SamReaderFactory.makeDefault().referenceSource(referenceSource).open(CRAM_FILE);
             PartitionedCRAMFileWriter partitionedCRAMFileWriter = new PartitionedCRAMFileWriter(outputDir, referenceSource, samReader.getFileHeader(), 250)) {
            for (SAMRecord samRecord : samReader) {
                partitionedCRAMFileWriter.addAlignment(samRecord);
            }
        }

        // 2. Merge the partitioned CRAM and index
        new PartitionedCRAMFileMerger().merge(outputDir, outputCram, outputCraiMerged);

        // 3. Index the merged CRAM (using regular indexing)
        indexCram(outputCram, outputCrai);

        // 4. Assert that the merged index is the same as the index produced from the merged file
        Assert.assertEquals(
                com.google.common.io.Files.toByteArray(outputCrai.toFile()),
                com.google.common.io.Files.toByteArray(outputCraiMerged.toFile()));
    }
}
