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
package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.BlockCompressedStreamConstants;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.samtools.util.TerminatorlessBlockCompressedOutputStream;
import htsjdk.tribble.index.IndexCreator;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.tribble.index.tabix.StreamBasedTabixIndexCreator;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.index.tabix.TabixIndex;
import htsjdk.tribble.index.tabix.TabixIndexMerger;
import htsjdk.tribble.index.tabix.TbiEqualityChecker;
import htsjdk.tribble.util.TabixUtils;
import htsjdk.utils.ValidationUtils;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class VCFMergerTest extends HtsjdkTest {

    private final static Path VCF_FILE = new File("src/test/resources/htsjdk/variant/HiSeq.10000.vcf.bgz").toPath();

    /**
     * Writes a <i>partitioned VCF</i>.
     *
     * A partitioned VCF is a directory containing the following files:
     * <ol>
     *     <li>A file named <i>header</i> containing all header bytes in VCF format.</li>
     *     <li>Zero or more files named <i>part-00000</i>, <i>part-00001</i>, ... etc, containing a list of VCF records.</li>
     *     <li>A file named <i>terminator</i> containing a BGZF end-of-file marker block (only if the VCF is bgzip-compressed).</li>
     * </ol>
     *
     * If the VCF is bgzip-compressed then the header and part files must be all bgzip-compressed.
     *
     * For a compressed VCF, if an index is required, then a tabix index can be generated for each (headerless) part file. These files
     * should be named <i>.part-00000.tbi</i>, <i>.part-00001.tbi</i>, ... etc. Note the leading <i>.</i> to make the files hidden.
     *
     * This format has the following properties:
     *
     * <ul>
     *     <li>Parts and their indexes may be written in parallel, since one part file can be written independently of the others.</li>
     *     <li>A VCF file can be created from a partitioned VCF file by concatenating all the non-hidden files (<i>header</i>, <i>part-00000</i>, <i>part-00001</i>, ..., <i>terminator</i>).</li>
     *     <li>A VCF index can be created from a partitioned VCF file by merging all of the hidden files with a <i>.tbi</i> suffix. Note that this is <i>not</i> a simple file concatenation operation. See {@link TabixIndexMerger}.</li>
     * </ul>
     *
     * Note that this writer is only for single-threaded use. Consider using the implementation in Disq for a partitioned VCF writer
     * that works with multiple threads or in a distributed setting.
     */
    static class PartitionedVCFFileWriter implements VariantContextWriter {
        private final Path outputDir;
        private VCFHeader header;
        private final int recordsPerPart;
        private final boolean compressed;
        private long recordCount = 0;
        private int partNumber = -1;
        private VariantContextWriter variantContextWriter;

        public PartitionedVCFFileWriter(Path outputDir, int recordsPerPart, boolean compressed) {
            this.outputDir = outputDir;
            this.recordsPerPart = recordsPerPart;
            this.compressed = compressed;
        }

        @Override
        public void writeHeader(VCFHeader header) {
            ValidationUtils.nonNull(header.getSequenceDictionary(), "VCF header must have a sequence dictionary");
            this.header = header;
            try (OutputStream headerOut = Files.newOutputStream(outputDir.resolve("header"))) {
                OutputStream out =
                        compressed ? new BlockCompressedOutputStream(headerOut, (Path) null) : headerOut;
                VariantContextWriter writer =
                        new VariantContextWriterBuilder().clearOptions().setOutputVCFStream(out).build();
                writer.writeHeader(header);
                out.flush(); // don't close BlockCompressedOutputStream since we don't want to write the terminator after the header
            } catch (IOException e) {
                throw new RuntimeIOException(e);
            }
        }

        @Override
        public void add(VariantContext vc) {
            if (recordCount % recordsPerPart == 0) {
                // start a new part
                try {
                    if (variantContextWriter != null) {
                        variantContextWriter.close();
                    }
                    partNumber++;
                    String partName = String.format("part-%05d", partNumber);
                    OutputStream out = Files.newOutputStream(outputDir.resolve(partName));
                    OutputStream indexOut = Files.newOutputStream(outputDir.resolve("." + partName + TabixUtils.STANDARD_INDEX_EXTENSION));
                    IndexCreator tabixIndexCreator = new StreamBasedTabixIndexCreator(
                                        header.getSequenceDictionary(), TabixFormat.VCF, indexOut);
                    this.variantContextWriter = new VariantContextWriterBuilder()
                            .setOutputStream(
                                    compressed ? new TerminatorlessBlockCompressedOutputStream(out) : out)
                            .setReferenceDictionary(header.getSequenceDictionary())
                            .setIndexCreator(tabixIndexCreator)
                            .modifyOption(Options.INDEX_ON_THE_FLY, tabixIndexCreator != null)
                            .unsetOption(Options.DO_NOT_WRITE_GENOTYPES)
                            .unsetOption(Options.ALLOW_MISSING_FIELDS_IN_HEADER)
                            .unsetOption(Options.WRITE_FULL_FORMAT_FIELD)
                            .build();
                    variantContextWriter.setHeader(header);
                } catch (IOException e) {
                    throw new RuntimeIOException(e);
                }
            }
            recordCount++;
            variantContextWriter.add(vc);
        }

        @Override
        public void setHeader(VCFHeader header) {
            // ignore
        }

        @Override
        public void close() {
            if (variantContextWriter != null) {
                variantContextWriter.close();
            }
            if (compressed) {
                // write terminator
                try (OutputStream out = Files.newOutputStream(outputDir.resolve("terminator"))) {
                    out.write(BlockCompressedStreamConstants.EMPTY_GZIP_BLOCK);
                } catch (IOException e) {
                    throw new RuntimeIOException(e);
                }
            }
        }

        @Override
        public boolean checkError() {
            return variantContextWriter != null && variantContextWriter.checkError();
        }
    }

    /**
     * Merge the files created by {@link PartitionedVCFFileWriter} into a single VCF file and index.
     */
    static class PartitionedVCFFileMerger {
        public void merge(Path dir, Path outputVcf, Path outputTbi) throws IOException {
            Path headerPath = dir.resolve("header");
            List<Path> vcfParts = Files.list(dir)
                    .filter(path -> !path.toString().endsWith(TabixUtils.STANDARD_INDEX_EXTENSION)) // include header and terminator
                    .sorted()
                    .collect(Collectors.toList());
            List<Path> tbiParts = Files.list(dir)
                    .filter(path -> path.toString().endsWith(TabixUtils.STANDARD_INDEX_EXTENSION))
                    .sorted()
                    .collect(Collectors.toList());

            ValidationUtils.validateArg(vcfParts.size() - 2 == tbiParts.size(), "Number of VCF part files does not match number of TBI files (" + tbiParts.size() + ")");

            // merge VCF parts
            try (OutputStream out = Files.newOutputStream(outputVcf)) {
                for (Path vcfPart : vcfParts) {
                    Files.copy(vcfPart, out);
                }
            }

            // merge index parts
            try (OutputStream out = Files.newOutputStream(outputTbi)) {
                TabixIndexMerger tabixIndexMerger = new TabixIndexMerger(out, Files.size(headerPath));
                int i = 1; // start from 1 since we ignore the header
                for (Path tbiPart : tbiParts) {
                    try (InputStream in = Files.newInputStream(tbiPart)) {
                        TabixIndex index = new TabixIndex(new BlockCompressedInputStream(in));
                        tabixIndexMerger.processIndex(index, Files.size(vcfParts.get(i++)));
                    }
                }
                tabixIndexMerger.finish(Files.size(outputVcf));
            }
        }
    }

    private static Path indexVcf(Path vcf, Path tbi) throws IOException {
        TabixIndex tabixIndex = IndexFactory.createTabixIndex(vcf.toFile(), new VCFCodec(), null);
        tabixIndex.write(tbi);
        return tbi;
    }

    @Test
    public void test() throws IOException {
        final Path outputDir = IOUtil.createTempDir(this.getClass().getSimpleName() + ".", ".tmp").toPath();
        IOUtil.deleteOnExit(outputDir);

        final Path outputVcf = File.createTempFile(this.getClass().getSimpleName() + ".", IOUtil.COMPRESSED_VCF_FILE_EXTENSION).toPath();
        IOUtil.deleteOnExit(outputVcf);

        final Path outputTbi = IOUtil.addExtension(outputVcf, TabixUtils.STANDARD_INDEX_EXTENSION);
        IOUtil.deleteOnExit(outputTbi);

        final Path outputTbiMerged = File.createTempFile(this.getClass().getSimpleName() + ".", TabixUtils.STANDARD_INDEX_EXTENSION).toPath();
        IOUtil.deleteOnExit(outputTbiMerged);

        // 1. Read an input VCF and write it out in partitioned form (header, parts, terminator)
        try (VCFFileReader vcfReader = new VCFFileReader(VCF_FILE, false);
             PartitionedVCFFileWriter partitionedVCFFileWriter = new PartitionedVCFFileWriter(outputDir, 2500, true)) {
            partitionedVCFFileWriter.writeHeader(vcfReader.getFileHeader());
            for (VariantContext vc : vcfReader) {
                partitionedVCFFileWriter.add(vc);
            }
        }

        // 2. Merge the partitioned VCF and index
        new PartitionedVCFFileMerger().merge(outputDir, outputVcf, outputTbiMerged);

        // 3. Index the merged VCF (using regular indexing)
        indexVcf(outputVcf, outputTbi);

        // 4. Assert that the merged index is the same as the index produced from the merged file

        // Don't check for strict equality (byte identical), since the TBI files
        // generated by the two methods have one difference: the final virtual
        // file position in the last bin is at the end of the empty BGZF block
        // in TBI files generated by in the usual way, and is at the start of the empty block
        // for those generated by merging.
        TbiEqualityChecker.assertEquals(outputVcf, outputTbi, outputTbiMerged, false);
    }
}
