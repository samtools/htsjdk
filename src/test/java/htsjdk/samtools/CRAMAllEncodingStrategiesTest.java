package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.compression.*;
import htsjdk.samtools.cram.compression.rans.RANS;
import htsjdk.samtools.cram.encoding.ByteArrayLenEncoding;
import htsjdk.samtools.cram.encoding.external.*;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.util.Tuple;
import htsjdk.utils.SamtoolsTestUtils;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class CRAMAllEncodingStrategiesTest extends HtsjdkTest {

    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/cram");

    @DataProvider(name="roundTripTestFiles")
    public Object[][] roundTripTestFiles() {
        return new Object[][] {
                { new File(TEST_DATA_DIR, "NA12878.20.21.1-100.100-SeqsPerSlice.500-unMapped.cram"),
                        new File(TEST_DATA_DIR, "human_g1k_v37.20.21.1-100.fasta") },
//                { new File("/Users/cnorman/projects/gatk/src/test/resources/large/CEUTrio.HiSeq.WGS.b37.NA12878.20.21.cram"),
//                        new File("/Users/cnorman/projects/gatk/src/test/resources/large/human_g1k_v37.20.21.fasta") },
//                { new File("/Users/cnorman/projects/references/NA12878.cram"),
//                        new File("/Users/cnorman/projects/references/hg38/Homo_sapiens_assembly38.fasta") },
//                { new File("/Users/cnorman/projects/testdata/samn/DDP_ATCP_265_2.cram"),
//                        new File("/Users/cnorman/projects/references/hg38/Homo_sapiens_assembly38.fasta") },
//                  // this file has NM/MD tags, but the samtools roundtrip args discard them so sam roundtrip fails
//                { new File("/Users/cnorman/projects/references/m64020_190208_213731-88146610-all.bam"),
//                        new File("/Users/cnorman/projects/references/hg38/Homo_sapiens_assembly38.fasta") }
        };
    }

    @Test(dataProvider = "roundTripTestFiles")
    public final void testRoundTripDefaultEncodingStrategy(final File sourceFile, final File referenceFile) throws IOException {
        final CRAMEncodingStrategy testStrategy = new CRAMEncodingStrategy();
        final File tempOutCRAM = File.createTempFile("testRoundTrip", ".cram");

        System.out.println(String.format("Test file size: %,d (%s) Output file: %s",
                Files.size(sourceFile.toPath()),
                sourceFile.toPath(),
                tempOutCRAM.toPath()));

        long startTime = System.currentTimeMillis();
        final long fileSize = CRAMTestUtils.writeToCRAMWithEncodingStrategy(testStrategy, sourceFile, tempOutCRAM, referenceFile);
        long endTime = System.currentTimeMillis();

        System.out.println(String.format(
                "Output size: %,d Elapsed time minutes: %,d Strategy: %s",
                fileSize,
                (endTime-startTime)/1000/60, testStrategy));

        assertRoundTripFidelity(sourceFile, tempOutCRAM, referenceFile, true);
        assertRoundtripFidelityWithSamtools(tempOutCRAM, referenceFile);
        tempOutCRAM.delete();
    }

    @Test(dataProvider = "roundTripTestFiles")
    public final void testBestEncodingStrategy(final File cramSourceFile, final File referenceFile) throws IOException {
        // src/test/resources/htsjdk/samtools/cram/json/CRAMEncodingMapProfileBEST.json contains the encoding map used by this strategy
        final File encodingStrategyFile = new File("src/test/resources/htsjdk/samtools/cram/json/CRAMEncodingStrategyTemplate.json");
        final CRAMEncodingStrategy testStrategy = CRAMEncodingStrategy.readFromPath(encodingStrategyFile.toPath());

        final File tempOutCRAM = File.createTempFile("bestEncodingStrategyTest", ".cram");
        System.out.println(String.format("Test file/size: %,d (%s) Output file: %s",
                Files.size(cramSourceFile.toPath()),
                cramSourceFile.toPath(),
                tempOutCRAM.toPath()));

        final long start = System.currentTimeMillis();
        final long fileSize = CRAMTestUtils.writeToCRAMWithEncodingStrategy(testStrategy, cramSourceFile, tempOutCRAM, referenceFile);
        long end = System.currentTimeMillis();

        System.out.println(String.format("Size: %,d Elapsed time minutes: %,d Strategy: %s", fileSize, (end-start)/1000/60, testStrategy));

        assertRoundTripFidelity(cramSourceFile, tempOutCRAM, referenceFile, false);

        tempOutCRAM.delete();
    }

    @Test(dataProvider = "roundTripTestFiles")
    public final void testAllEncodingStrategyCombinations(final File cramSourceFile, final File referenceFile) throws IOException {
        final Map<Integer, CRAMEncodingStrategy> encodingStrategyByTestNumber = new HashMap<>();
        final Map<Integer, String> testSummaryByTestNumber = new HashMap<>();
        final Map<Integer, Long> fileSizeByTestNumber = new HashMap<>();

        System.out.println(String.format("Original test file size: %,d", Files.size(cramSourceFile.toPath())));
        int testCount = 1;

        for (final Tuple<String, CRAMEncodingStrategy> testStrategy : getAllEncodingStrategies()) {
            final File tempOutCRAM = File.createTempFile("allEncodingStrategyCombinations", ".cram");

            final long startTime = System.currentTimeMillis();
            final long fileSize = CRAMTestUtils.writeToCRAMWithEncodingStrategy(testStrategy.b, cramSourceFile, tempOutCRAM, referenceFile);
            final long endTime = System.currentTimeMillis();

            assertRoundTripFidelity(cramSourceFile, tempOutCRAM, referenceFile, false);
            //assertRoundtripFidelityWithSamtools(tempOutCRAM, referenceFile);

            tempOutCRAM.delete();
            final File mapPath = new File(testStrategy.b.getCustomCompressionMapPath());
            mapPath.delete();

            final String testSummary = String.format(
                    "Size: %,d Test: %,d Seconds: %d, %s %s",
                    fileSize,
                    testCount,
                    (endTime - startTime) / 1000,
                    testStrategy.a,
                    testStrategy.b);
            System.out.println(testSummary);

            testSummaryByTestNumber.put(testCount, testSummary);
            encodingStrategyByTestNumber.put(testCount, testStrategy.b);
            fileSizeByTestNumber.put(testCount, fileSize);
            testCount++;
            System.out.println();
        }

        // sort and display encoding strategies ordered by ascending result file size
        final List<Tuple<Long, Integer>> bestEncodingStrategies =
                fileSizeByTestNumber.entrySet()
                .stream()
                .map(e -> new Tuple<>(e.getValue(), e.getKey()))
                .collect(Collectors.toList());
        bestEncodingStrategies.sort(Comparator.comparing(t -> t.a));
        System.out.println(String.format("%d tests, top 50 sorted by result size:", testCount));
        // take the 50 best results
        bestEncodingStrategies
                .stream().limit(50)
                .forEach((Tuple<Long, Integer> t) ->
                        System.out.println(String.format("Test: %d Summary: %s Encoding: %s",
                                t.b,
                                testSummaryByTestNumber.get(t.b),
                                encodingStrategyByTestNumber.get(t.b)))
                );
    }

    private List<Tuple<String, CRAMEncodingStrategy>> getAllEncodingStrategies() throws IOException {
        // description, strategy
        final List<Tuple<String, CRAMEncodingStrategy>> allStrategies = new ArrayList<>();

        // Note that the larger reads/slice and slices/container values are only interesting when there
        // are enough records in the test file to cause these thresholds to be crossed
        for (final int gzipCompressionLevel : Arrays.asList(5, 9)) {
            for (final int readsPerSlice : Arrays.asList(10000, 20000)) {
                for (final int slicesPerContainer : Arrays.asList(1, 2)) {
                    for (final DataSeries dataSeries : enumerateDataSeries()) {
                        for (final EncodingDescriptor encodingDescriptor : enumerateEncodingDescriptorsFor(dataSeries)) {
                            for (final ExternalCompressor compressor : enumerateExternalCompressors(gzipCompressionLevel)) {
                                final String strategyDescription = String.format(
                                        "Series: %s Encoding: %s Compressor: %s",
                                        dataSeries,
                                        encodingDescriptor.getEncodingID(),
                                        compressor);
                                final CRAMEncodingStrategy strategy = createEncodingStrategyForParams(
                                        gzipCompressionLevel,
                                        readsPerSlice,
                                        slicesPerContainer,
                                        dataSeries,
                                        encodingDescriptor,
                                        compressor);
                                allStrategies.add(new Tuple<>(strategyDescription, strategy));
                            }
                        }
                    }
                }
            }
        }
        return allStrategies;
    }

    public void assertRoundTripFidelity(
            final File sourceFile,
            final File targetCRAMFile,
            final File referenceFile,
            final boolean emitDetail) throws IOException {
        try (final SamReader sourceReader = SamReaderFactory.makeDefault()
                .referenceSequence(referenceFile)
                .validationStringency((ValidationStringency.SILENT))
                .open(sourceFile);
             final CRAMFileReader copyReader = new CRAMFileReader(targetCRAMFile, new ReferenceSource(referenceFile))) {
            final SAMRecordIterator sourceIterator = sourceReader.iterator();
            final SAMRecordIterator targetIterator = copyReader.getIterator();
            while (sourceIterator.hasNext() && targetIterator.hasNext()) {
                if (emitDetail) {
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

    private void assertRoundtripFidelityWithSamtools(final File sourceCRAM, final File referenceFile) throws IOException {
        if (SamtoolsTestUtils.isSamtoolsAvailable()) {
            final long start = System.currentTimeMillis();
            final File samtoolsOutFile = SamtoolsTestUtils.getWriteToTemporaryCRAM(
                    sourceCRAM,
                    referenceFile,
                    "--input-fmt-option decode_md=0 --output-fmt-option store_md=0 --output-fmt-option store_nm=0");
            final long end = System.currentTimeMillis();
            System.out.println(String.format("Elapsed time minutes %,d", (end-start)/1000/60));
            System.out.println(String.format("Samtools file size: %,d (%s)", Files.size(samtoolsOutFile.toPath()), samtoolsOutFile.toPath()));
            assertRoundTripFidelity(sourceCRAM, samtoolsOutFile, referenceFile, true);
        }
    }

    public final CRAMEncodingStrategy createEncodingStrategyForParams(
           final int  gzipCompressionLevel,
           final int readsPerSlice,
           final int slicesPerContainer,
           final DataSeries ds,
           final EncodingDescriptor encodingDescriptor,
           final ExternalCompressor compressor) throws IOException {
        final CRAMEncodingStrategy encodingStrategy = new CRAMEncodingStrategy();
        encodingStrategy.setGZIPCompressionLevel(gzipCompressionLevel);
        encodingStrategy.setReadsPerSlice(readsPerSlice);
        encodingStrategy.setSlicesPerContainer(slicesPerContainer);
        final CompressionHeaderEncodingMap encodingMap = createEncodingMapExternalEncodingVariationFor(ds, encodingDescriptor, compressor);
        final File tempEncodingMapFile = File.createTempFile("testEncodingMap", ".json");
        tempEncodingMapFile.deleteOnExit();
        final Path encodingMapPath = tempEncodingMapFile.toPath();
        encodingMap.writeToPath(encodingMapPath);
        encodingStrategy.setEncodingMap(encodingMapPath);

        // save the map to retrieve it in case its the best one
        //final File tempStrategyFile = File.createTempFile("testEncodingMap", ".json");
        //tempStrategyFile.deleteOnExit();
        //final Path strategyPath = tempStrategyFile.toPath();
        //encodingStrategy.writeToPath(strategyPath);

        return encodingStrategy;
    }

    public List<ExternalCompressor> enumerateExternalCompressors(final int gzipCompressionLevel) {
        final RANS rans = new RANS();
        return Arrays.asList(
                new GZIPExternalCompressor(gzipCompressionLevel),
                new RANSExternalCompressor(RANS.ORDER.ZERO, rans),
                new RANSExternalCompressor(RANS.ORDER.ONE, rans)
                //NOTE: don't use LZMA or BZIP compression if we wantto validate using samtools since not
                // all samtools builds have these enabled
                //new LZMAExternalCompressor(),
                //new BZIP2ExternalCompressor()
        );
    }

    private List<DataSeries> enumerateDataSeries() {
        final List<DataSeries> seriesToUse = new ArrayList<>();
        // skip the ones this implementation doesn't use
        for (final DataSeries ds : DataSeries.values()) {
            if (ds != DataSeries.TM_TestMark
                    && ds != DataSeries.TV_TestMark
                    && ds != DataSeries.QQ_scores
                    && ds != DataSeries.BB_Bases) {
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
