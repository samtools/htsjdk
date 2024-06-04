package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.compression.*;
import htsjdk.samtools.cram.encoding.ByteArrayLenEncoding;
import htsjdk.samtools.cram.encoding.external.*;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import htsjdk.samtools.util.Tuple;
import htsjdk.utils.SamtoolsTestUtils;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.util.*;

/**
 * Test roundtripping files through the GATK writer using both the default HTSJDK encoding strategy, plus a variety
 * of alternative encoding strategies, in order to stress test the writer implementation. Compares the results with
 * the original file, and then roundtrips the newly written CRAM through the samtools writer, validating that samtools
 * can consume the HTSJDK-written files with the expected level of roundtrip fidelity (CRAMs don't always roundtrip
 * with complete bit-level fidelity, i.e, samtools will resurrect NM/MD tags whether they were present in the original
 * file or not unless they are specifically excluded, etc.). So in some case, you can't use full SAMRecord comparisons,
 * in which case we fall back to lenient equality and restrict the comparison to read names, bases, alignment start/stop,
 * and quality scores.
 */
public class CRAMAllEncodingStrategiesTest extends HtsjdkTest {

    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/cram");
    private final CompressorCache compressorCache = new CompressorCache();

    @DataProvider(name="defaultStrategyRoundTripTestFiles")
    public Object[][] defaultStrategyRoundTripTestFiles() {
        return new Object[][] {
                // a test file with artificially small slices and containers to force multiple slices and containers
                { new File(TEST_DATA_DIR, "NA12878.20.21.1-100.100-SeqsPerSlice.500-unMapped.cram"),
                        new File(TEST_DATA_DIR, "human_g1k_v37.20.21.1-100.fasta"),
                        false, false },
                // the same file without the artificially small container constraints
                { new File(TEST_DATA_DIR, "CEUTrio.HiSeq.WGS.b37.NA12878.20.21.10m-10m100.cram"),
                        new File("src/test/resources/htsjdk/samtools/reference/human_g1k_v37.20.21.fasta.gz"),
                        false, false },
                // a test file with only unmapped reads
                { new File(TEST_DATA_DIR, "NA12878.unmapped.cram"),
                        new File(TEST_DATA_DIR, "human_g1k_v37.20.21.1-100.fasta"),
                        false, false },
                // generated with samtools 1.19 from the gatk bam file CEUTrio.HiSeq.WGS.b37.NA12878.20.21.bam
                { new File(TEST_DATA_DIR, "CEUTrio.HiSeq.WGS.b37.NA12878.20.21.v3.0.samtools.cram"),
                        new File("src/test/resources/htsjdk/samtools/reference/human_g1k_v37.20.21.fasta.gz"),
                        true, false },

                // these tests use lenient equality to only validate read names, bases, alignment start/stop, and qual scores

                // a user-contributed file with reads aligned only to the mito contig that has been rewritten (long ago) with GATK
                { new File(TEST_DATA_DIR, "mitoAlignmentStartTestGATKGen.cram"),
                        new File(TEST_DATA_DIR, "mitoAlignmentStartTest.fa"), true, false },
                // the original user-contributed file with reads aligned only to the mito contig
                { new File(TEST_DATA_DIR, "mitoAlignmentStartTest.cram"),
                        new File(TEST_DATA_DIR, "mitoAlignmentStartTest.fa"), true, false },
                // files created by rewriting the htsjdk test file src/test/resources/htsjdk/samtools/cram/mitoAlignmentStartTest.cram
                // using code that replicates the first read (which is aligned to position 1 of the mito contig) either
                // 10,000 or 20,000 times, to create a file with 2 or 3 containers, respectively, that have reads aligned to
                // position 1 of the contig
                { new File(TEST_DATA_DIR, "mitoAlignmentStartTest_2_containers_aligned_to_pos_1.cram"),
                        new File(TEST_DATA_DIR, "mitoAlignmentStartTest.fa"), true, false },
                { new File(TEST_DATA_DIR, "mitoAlignmentStartTest_3_containers_aligned_to_pos_1.cram"),
                        new File(TEST_DATA_DIR, "mitoAlignmentStartTest.fa"), true, false }
        };
    }

    @Test(dataProvider = "defaultStrategyRoundTripTestFiles")
    public final void testRoundTripDefaultEncodingStrategy(
            final File sourceFile,
            final File referenceFile,
            final boolean lenientEquality,
            final boolean emitDetail) throws IOException {
        // test the default encoding strategy
        final CRAMEncodingStrategy testStrategy = new CRAMEncodingStrategy();
        final File tempOutCRAM = File.createTempFile("testRoundTrip", ".cram");
        tempOutCRAM.deleteOnExit();
        CRAMTestUtils.writeToCRAMWithEncodingStrategy(testStrategy, sourceFile, tempOutCRAM, referenceFile);
        assertRoundTripFidelity(sourceFile, tempOutCRAM, referenceFile, lenientEquality, emitDetail);
        // test interop with samtools using this encoding
        assertRoundtripFidelityWithSamtools(tempOutCRAM, referenceFile, lenientEquality, emitDetail);
    }

    @DataProvider(name="encodingStrategiesTestFiles")
    public Object[][] encodingStrategiesTestFiles() {
        return new Object[][] {
                { new File(TEST_DATA_DIR, "NA12878.20.21.1-100.100-SeqsPerSlice.500-unMapped.cram"),
                        new File(TEST_DATA_DIR, "human_g1k_v37.20.21.1-100.fasta"), false, false },
        };
    }

    @Test(dataProvider = "encodingStrategiesTestFiles")
    public final void testAllEncodingStrategyCombinations(
            final File cramSourceFile,
            final File referenceFile,
            final boolean lenientEquality,
            final boolean emitDetail) throws IOException {
        for (final Tuple<String, CRAMEncodingStrategy> testStrategy : getAllEncodingStrategies()) {
            final File tempOutCRAM = File.createTempFile("allEncodingStrategyCombinations", ".cram");
            tempOutCRAM.deleteOnExit();
            CRAMTestUtils.writeToCRAMWithEncodingStrategy(testStrategy.b, cramSourceFile, tempOutCRAM, referenceFile);
            assertRoundTripFidelity(cramSourceFile, tempOutCRAM, referenceFile, lenientEquality, emitDetail);
            // test interop with samtools using this encoding
            assertRoundtripFidelityWithSamtools(tempOutCRAM, referenceFile, lenientEquality, emitDetail);
        }
    }

    private List<Tuple<String, CRAMEncodingStrategy>> getAllEncodingStrategies() {
        // description, strategy
        final List<Tuple<String, CRAMEncodingStrategy>> allStrategies = new ArrayList<>();

        // We don't use non-default values for reads/slice or slices/container since those are only interesting
        // on a test file that has enough records to cause the thresholds for these values to be crossed
        for (final DataSeries dataSeries : enumerateDataSeries()) {
            for (final EncodingDescriptor encodingDescriptor : enumerateEncodingDescriptorsFor(dataSeries)) {
                for (final ExternalCompressor compressor : enumerateExternalCompressors(5)) {
                    final String strategyDescription = String.format(
                            "Series: %s Encoding: %s Compressor: %s",
                            dataSeries,
                            encodingDescriptor.getEncodingID(),
                            compressor);
                    final CRAMEncodingStrategy strategy = createEncodingStrategyForParams(
                            5,
                            CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE,
                            1,
                            dataSeries,
                            encodingDescriptor,
                            compressor);
                    allStrategies.add(new Tuple<>(strategyDescription, strategy));
                }
            }
        }
        return allStrategies;
    }

    public void assertRoundTripFidelity(
            final File sourceFile,
            final File targetCRAMFile,
            final File referenceFile,
            final boolean lenientEquality,
            final boolean emitDetail) throws IOException {
        try (final SamReader sourceReader = SamReaderFactory.makeDefault()
                .referenceSequence(referenceFile)
                .validationStringency((ValidationStringency.SILENT))
                .open(sourceFile);
             final CRAMFileReader copyReader = new CRAMFileReader(targetCRAMFile, new ReferenceSource(referenceFile))) {
            final SAMRecordIterator sourceIterator = sourceReader.iterator();
            final SAMRecordIterator targetIterator = copyReader.getIterator();
            while (sourceIterator.hasNext() && targetIterator.hasNext()) {
                if (lenientEquality) {
                    final SAMRecord sourceRec = sourceIterator.next();
                    final SAMRecord targetRec = targetIterator.next();
                    Assert.assertEquals(targetRec.getReadName(), sourceRec.getReadName());
                    Assert.assertEquals(targetRec.getAlignmentStart(), sourceRec.getAlignmentStart());
                    Assert.assertEquals(targetRec.getAlignmentEnd(), sourceRec.getAlignmentEnd());
                    Assert.assertEquals(targetRec.getReadBases(), sourceRec.getReadBases());
                    Assert.assertEquals(targetRec.getBaseQualities(), sourceRec.getBaseQualities());
                } else if (emitDetail) {
                    final SAMRecord sourceRec = sourceIterator.next();
                    final SAMRecord targetRec = targetIterator.next();
                    if (!sourceRec.equals(targetRec)) {
                        System.out.println("Difference found:");
                        System.out.println(sourceRec.getSAMString());
                        System.out.println(targetRec.getSAMString());
                    }
                    Assert.assertEquals(targetRec, sourceRec);
                } else {
                    Assert.assertEquals(targetIterator.next(), sourceIterator.next());
                }
            }
            Assert.assertEquals(sourceIterator.hasNext(), targetIterator.hasNext());
        }
    }

    private void assertRoundtripFidelityWithSamtools(
            final File sourceCRAM,
            final File referenceFile,
            final boolean lenientEquality,
            final boolean emitDetail) throws IOException {
        if (SamtoolsTestUtils.isSamtoolsAvailable()) {
            final File samtoolsOutFile = SamtoolsTestUtils.convertToCRAM(
                    sourceCRAM,
                    referenceFile,
                    "--input-fmt-option decode_md=0 --output-fmt-option store_md=0 --output-fmt-option store_nm=0");
            assertRoundTripFidelity(sourceCRAM, samtoolsOutFile, referenceFile, lenientEquality, emitDetail);
        } else {
            throw new SkipException("samtools is not installed, skipping test");
        }
    }

    public final CRAMEncodingStrategy createEncodingStrategyForParams(
           final int  gzipCompressionLevel,
           final int readsPerSlice,
           final int slicesPerContainer,
           final DataSeries ds,
           final EncodingDescriptor encodingDescriptor,
           final ExternalCompressor compressor) {
        final CRAMEncodingStrategy encodingStrategy = new CRAMEncodingStrategy();
        encodingStrategy.setGZIPCompressionLevel(gzipCompressionLevel);
        encodingStrategy.setReadsPerSlice(readsPerSlice);
        encodingStrategy.setSlicesPerContainer(slicesPerContainer);
        encodingStrategy.setCustomCompressionHeaderEncodingMap(
                createEncodingMapExternalEncodingVariationFor(ds, encodingDescriptor, compressor));
        return encodingStrategy;
    }

    public List<ExternalCompressor> enumerateExternalCompressors(final int gzipCompressionLevel) {
        //NOTE: don't use LZMA or BZIP compression since we want to validate using samtools and
        // not all samtools builds have these enabled
        return Arrays.asList(
                compressorCache.getCompressorForMethod(BlockCompressionMethod.GZIP, gzipCompressionLevel),
                compressorCache.getCompressorForMethod(BlockCompressionMethod.RANS, 0),
                compressorCache.getCompressorForMethod(BlockCompressionMethod.RANS, 1)
        );
    }

    private List<DataSeries> enumerateDataSeries() {
        final List<DataSeries> seriesToUse = new ArrayList<>();
        // skip the ones this implementation doesn't use
        for (final DataSeries ds : DataSeries.values()) {
            if (!StructureTestUtils.DATASERIES_NOT_WRITTEN_BY_HTSJDK.contains(ds)) {
                seriesToUse.add(ds);
            }
        }
        return seriesToUse;
    }

    // For now, just use the descriptors that are the default for this implementation, since not all
    // encodings are interchangeable
    private Set<EncodingDescriptor> enumerateEncodingDescriptorsFor(final DataSeries ds) {
        final Set<EncodingDescriptor> descriptors = new HashSet<>();
        descriptors.add(new CompressionHeaderEncodingMap(new CRAMEncodingStrategy()).getEncodingDescriptorForDataSeries(ds));
        if (ds == DataSeries.RN_ReadName) {
            descriptors.add(
                    (new ByteArrayLenEncoding(
                        new ExternalIntegerEncoding(ds.getExternalBlockContentId()),
                        new ExternalByteArrayEncoding(ds.getExternalBlockContentId()))).toEncodingDescriptor());
        }
        return descriptors;
    }

    public CompressionHeaderEncodingMap createEncodingMapExternalEncodingVariationFor(
            final DataSeries ds,
            final EncodingDescriptor encodingDescriptor,
            final ExternalCompressor compressor) {
        // create a default encoding map and update it with the requested EXTERNAL descriptor/compressor
        final CompressionHeaderEncodingMap encodingMap = new CompressionHeaderEncodingMap(new CRAMEncodingStrategy());
        encodingMap.putExternalEncoding(ds, encodingDescriptor, compressor);
        return encodingMap;
    }

}
