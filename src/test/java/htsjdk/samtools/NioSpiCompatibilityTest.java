/*
 * The MIT License
 *
 * Copyright (c) 2026 The Broad Institute
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

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.reference.ReferenceSequence;
import htsjdk.samtools.reference.ReferenceSequenceFile;
import htsjdk.samtools.reference.ReferenceSequenceFileFactory;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Exercises the major htsjdk read/write/index public APIs against a NON-default NIO filesystem (the
 * in-memory jimfs provider) to prove that the {@link java.nio.file.Path}-based APIs are SPI compatible.
 *
 * <p>Every test operates exclusively on jimfs {@link Path} objects through the Path-based public APIs;
 * none of the deprecated {@link java.io.File} overloads are used. Each test creates an isolated jimfs
 * {@link FileSystem} in a try-with-resources block, copies any required small checked-in resources into
 * that filesystem, and cleans up readers/writers as it goes. All resources used are small and record
 * counts are tiny so the whole class runs in a few seconds.
 */
public class NioSpiCompatibilityTest extends HtsjdkTest {

    private static final Path TEST_DATA_DIR = Paths.get("src/test/resources/htsjdk/samtools");
    private static final Path REFERENCE_FASTA = TEST_DATA_DIR.resolve("hg19mini.fasta");
    private static final Path REFERENCE_FAI = TEST_DATA_DIR.resolve("hg19mini.fasta.fai");
    private static final Path REFERENCE_DICT = TEST_DATA_DIR.resolve("hg19mini.dict");

    /** A BAM (issue76.bam) that ships with a co-located .bai index, used for read + query tests. */
    private static final Path INDEXED_BAM = TEST_DATA_DIR.resolve("issue76.bam");

    private static final Path INDEXED_BAI = TEST_DATA_DIR.resolve("issue76.bam.bai");

    /** A small VCF (with self-describing header) used for the VCF round-trip test. */
    private static final Path SMALL_VCF = Paths.get("src/test/resources/htsjdk/tribble/test.vcf");

    /** Copies a real (default-filesystem) resource into the supplied jimfs filesystem, returning the jimfs path. */
    private static Path copyIntoJimfs(final FileSystem jimfs, final Path realPath, final String name)
            throws IOException {
        final Path dest = jimfs.getPath(name);
        Files.copy(realPath, dest);
        return dest;
    }

    /** Copies the hg19mini reference (fasta + .fai + .dict) into jimfs and returns the jimfs fasta path. */
    private static Path copyReferenceIntoJimfs(final FileSystem jimfs) throws IOException {
        final Path fasta = copyIntoJimfs(jimfs, REFERENCE_FASTA, "hg19mini.fasta");
        copyIntoJimfs(jimfs, REFERENCE_FAI, "hg19mini.fasta.fai");
        copyIntoJimfs(jimfs, REFERENCE_DICT, "hg19mini.dict");
        return fasta;
    }

    /** Builds a tiny coordinate-sorted header over the hg19mini sequence dictionary loaded from jimfs. */
    private static SAMFileHeader headerForReference(final Path jimfsFasta) {
        final SAMSequenceDictionary dict;
        try (final ReferenceSequenceFile ref = ReferenceSequenceFileFactory.getReferenceSequenceFile(jimfsFasta)) {
            dict = ref.getSequenceDictionary();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        final SAMFileHeader header = new SAMFileHeader(dict);
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        return header;
    }

    /** Creates a couple of mapped reads on contig "1" using the supplied bases. */
    private static List<SAMRecord> makeRecords(final SAMFileHeader header) {
        final List<SAMRecord> records = new ArrayList<>();
        final String bases = "TAACCCTAACCCTAACCCTAACCCTAACCC"; // 30bp, from contig 1 of hg19mini
        for (int i = 0; i < 3; i++) {
            final SAMRecord rec = new SAMRecord(header);
            rec.setReadName("read" + i);
            rec.setReferenceName("1");
            rec.setAlignmentStart(1001 + (i * 50));
            rec.setReadString(bases);
            rec.setBaseQualityString("IIIIIIIIIIIIIIIIIIIIIIIIIIIIII");
            rec.setCigarString(bases.length() + "M");
            rec.setMappingQuality(60);
            rec.setReadNegativeStrandFlag(false);
            records.add(rec);
        }
        return records;
    }

    /** 1. Read a BAM together with its index from jimfs, count records, and run an indexed query. */
    @Test
    public void readsIndexedBamFromNonDefaultFilesystem() throws IOException {
        try (final FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path bam = copyIntoJimfs(jimfs, INDEXED_BAM, "reads.bam");
            copyIntoJimfs(jimfs, INDEXED_BAI, "reads.bam.bai");

            try (final SamReader reader = SamReaderFactory.makeDefault().open(bam)) {
                Assert.assertTrue(reader.hasIndex(), "Expected the jimfs BAM to expose its .bai index");

                int total = 0;
                try (final CloseableIterator<SAMRecord> it = reader.iterator()) {
                    while (it.hasNext()) {
                        it.next();
                        total++;
                    }
                }
                Assert.assertTrue(total > 0, "Expected to read at least one record from the jimfs BAM");

                // An indexed query against the first sequence must succeed and not exceed the full count.
                final SAMSequenceRecord firstSeq =
                        reader.getFileHeader().getSequenceDictionary().getSequence(0);
                int queried = 0;
                try (final CloseableIterator<SAMRecord> it = reader.query(firstSeq.getSequenceName(), 0, 0, false)) {
                    while (it.hasNext()) {
                        it.next();
                        queried++;
                    }
                }
                Assert.assertTrue(queried <= total);
            }
        }
    }

    /** 2. Write a BAM (with index) to jimfs through the Path API, then read it back and check the count. */
    @Test
    public void writesAndReadsBackBamOnNonDefaultFilesystem() throws IOException {
        try (final FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path fasta = copyReferenceIntoJimfs(jimfs);
            final SAMFileHeader header = headerForReference(fasta);
            final List<SAMRecord> records = makeRecords(header);

            final Path bam = jimfs.getPath("out.bam");
            try (final SAMFileWriter writer =
                    new SAMFileWriterFactory().setCreateIndex(true).makeBAMWriter(header, true, bam)) {
                for (final SAMRecord rec : records) {
                    writer.addAlignment(rec);
                }
            }
            Assert.assertTrue(Files.size(bam) > 0);

            try (final SamReader reader = SamReaderFactory.makeDefault().open(bam)) {
                int readBack = 0;
                for (final SAMRecord ignored : reader) {
                    readBack++;
                }
                Assert.assertEquals(readBack, records.size(), "BAM record count did not round-trip on jimfs");
            }
        }
    }

    /** 3. Write and read a CRAM on jimfs using a jimfs-resident reference. */
    @Test
    public void writesAndReadsBackCramWithJimfsReference() throws IOException {
        try (final FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path fasta = copyReferenceIntoJimfs(jimfs);
            final SAMFileHeader header = headerForReference(fasta);
            final List<SAMRecord> records = makeRecords(header);

            final Path cram = jimfs.getPath("out.cram");
            try (final SAMFileWriter writer = new SAMFileWriterFactory().makeCRAMWriter(header, true, cram, fasta)) {
                for (final SAMRecord rec : records) {
                    writer.addAlignment(rec);
                }
            }
            Assert.assertTrue(Files.size(cram) > 0);

            final ReferenceSource referenceSource = new ReferenceSource(fasta);
            try (final SamReader reader = SamReaderFactory.makeDefault()
                    .referenceSource(referenceSource)
                    .open(cram)) {
                int readBack = 0;
                for (final SAMRecord rec : reader) {
                    Assert.assertEquals(
                            rec.getReadString(), records.get(readBack).getReadString());
                    readBack++;
                }
                Assert.assertEquals(readBack, records.size(), "CRAM record count did not round-trip on jimfs");
            }
        }
    }

    /** 4. Read a FASTA reference (fasta + .fai + .dict) from jimfs and fetch sequence data. */
    @Test
    public void readsFastaReferenceFromNonDefaultFilesystem() throws IOException {
        try (final FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path fasta = copyReferenceIntoJimfs(jimfs);

            try (final ReferenceSequenceFile ref = ReferenceSequenceFileFactory.getReferenceSequenceFile(fasta)) {
                final SAMSequenceDictionary dict = ref.getSequenceDictionary();
                Assert.assertNotNull(dict, "Expected a sequence dictionary loaded from the jimfs .dict");
                Assert.assertTrue(dict.size() > 0, "Sequence dictionary should not be empty");

                final String firstContig = dict.getSequence(0).getSequenceName();
                final ReferenceSequence whole = ref.getSequence(firstContig);
                Assert.assertTrue(whole.length() > 0, "Expected a non-empty sequence from jimfs");

                final ReferenceSequence sub = ref.getSubsequenceAt(firstContig, 1001, 1030);
                Assert.assertEquals(sub.getBases().length, 30, "Subsequence length mismatch on jimfs");
            }
        }
    }

    /** 5. Demonstrate BAM index auto-discovery on jimfs via {@link SamFiles#findIndex(Path)}. */
    @Test
    public void findsBamIndexOnNonDefaultFilesystem() throws IOException {
        try (final FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path bam = copyIntoJimfs(jimfs, INDEXED_BAM, "reads.bam");
            final Path bai = copyIntoJimfs(jimfs, INDEXED_BAI, "reads.bam.bai");

            final Path discovered = SamFiles.findIndex(bam);
            Assert.assertNotNull(discovered, "SamFiles.findIndex should locate the jimfs .bai");
            Assert.assertEquals(
                    discovered.getFileSystem(),
                    jimfs,
                    "Discovered index should live on the same (non-default) filesystem");
            Assert.assertEquals(discovered, bai, "Discovered index path should be the jimfs .bai");
        }
    }

    /** 5b. Build a BAM index on jimfs from a BAM that has none, using {@link BAMIndexer#createIndex}. */
    @Test
    public void buildsBamIndexOnNonDefaultFilesystem() throws IOException {
        try (final FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path fasta = copyReferenceIntoJimfs(jimfs);
            final SAMFileHeader header = headerForReference(fasta);

            // Write a coordinate-sorted BAM with no index alongside it.
            final Path bam = jimfs.getPath("noindex.bam");
            try (final SAMFileWriter writer =
                    new SAMFileWriterFactory().setCreateIndex(false).makeBAMWriter(header, true, bam)) {
                for (final SAMRecord rec : makeRecords(header)) {
                    writer.addAlignment(rec);
                }
            }
            Assert.assertNull(SamFiles.findIndex(bam), "BAM should not yet have an index");

            // Indexing requires each record to carry a file source, so enable that when re-opening the BAM.
            final Path bai = jimfs.getPath("noindex.bam.bai");
            try (final SamReader reader = SamReaderFactory.makeDefault()
                    .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                    .open(bam)) {
                BAMIndexer.createIndex(reader, bai);
            }
            Assert.assertTrue(Files.size(bai) > 0, "Generated jimfs .bai should be non-empty");

            // The freshly written index should now be discoverable and usable.
            Assert.assertEquals(SamFiles.findIndex(bam), bai);
            try (final SamReader reader = SamReaderFactory.makeDefault().open(bam)) {
                Assert.assertTrue(reader.hasIndex(), "Reader should pick up the jimfs-built index");
            }
        }
    }

    /** 6. Read a VCF from jimfs, write the variants to a new jimfs path, and confirm the count round-trips. */
    @Test
    public void writesAndReadsBackVcfOnNonDefaultFilesystem() throws IOException {
        try (final FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path inVcf = copyIntoJimfs(jimfs, SMALL_VCF, "in.vcf");

            final List<VariantContext> variants = new ArrayList<>();
            final VCFHeader header;
            try (final VCFFileReader reader = new VCFFileReader(inVcf, false)) {
                header = reader.getFileHeader();
                for (final VariantContext vc : reader) {
                    variants.add(vc);
                }
            }
            Assert.assertTrue(variants.size() > 0, "Expected to read at least one variant from the jimfs VCF");

            final Path outVcf = jimfs.getPath("out.vcf");
            try (final VariantContextWriter writer = new VariantContextWriterBuilder()
                    .setOutputPath(outVcf)
                    .setReferenceDictionary(header.getSequenceDictionary())
                    .unsetOption(Options.INDEX_ON_THE_FLY)
                    .build()) {
                writer.writeHeader(header);
                for (final VariantContext vc : variants) {
                    writer.add(vc);
                }
            }
            Assert.assertTrue(Files.size(outVcf) > 0);

            int readBack = 0;
            try (final VCFFileReader reader = new VCFFileReader(outVcf, false)) {
                for (final VariantContext ignored : reader) {
                    readBack++;
                }
            }
            Assert.assertEquals(readBack, variants.size(), "VCF variant count did not round-trip on jimfs");
        }
    }

    /** 7. Write FASTQ records to jimfs and read them back through the Path-based reader/writer. */
    @Test
    public void writesAndReadsBackFastqOnNonDefaultFilesystem() throws IOException {
        try (final FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path fastq = jimfs.getPath("reads.fastq");

            final List<htsjdk.samtools.fastq.FastqRecord> written = new ArrayList<>();
            written.add(new htsjdk.samtools.fastq.FastqRecord("read1", "ACGTACGT", "", "IIIIIIII"));
            written.add(new htsjdk.samtools.fastq.FastqRecord("read2", "TTTTGGGG", "", "FFFFFFFF"));

            try (final htsjdk.samtools.fastq.BasicFastqWriter writer =
                    new htsjdk.samtools.fastq.BasicFastqWriter(fastq)) {
                for (final htsjdk.samtools.fastq.FastqRecord rec : written) {
                    writer.write(rec);
                }
            }
            Assert.assertTrue(Files.size(fastq) > 0);

            final List<htsjdk.samtools.fastq.FastqRecord> readBack = new ArrayList<>();
            try (final htsjdk.samtools.fastq.FastqReader reader = new htsjdk.samtools.fastq.FastqReader(fastq)) {
                while (reader.hasNext()) {
                    readBack.add(reader.next());
                }
            }
            Assert.assertEquals(readBack.size(), written.size(), "FASTQ record count did not round-trip on jimfs");
            Assert.assertEquals(readBack.get(0).getReadName(), "read1");
            Assert.assertEquals(readBack.get(0).getReadString(), "ACGTACGT");
            Assert.assertEquals(readBack.get(1).getReadString(), "TTTTGGGG");
        }
    }
}
