package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.ByteArrayLenEncoding;
import htsjdk.samtools.cram.encoding.CRAMEncoding;
import htsjdk.samtools.cram.encoding.core.*;
import htsjdk.samtools.cram.encoding.core.experimental.GolombIntegerEncoding;
import htsjdk.samtools.cram.encoding.core.experimental.GolombLongEncoding;
import htsjdk.samtools.cram.encoding.core.experimental.GolombRiceIntegerEncoding;
import htsjdk.samtools.cram.encoding.external.*;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.CRAMEncodingStrategy;
import htsjdk.samtools.cram.structure.CompressionHeaderEncodingMap;
import htsjdk.samtools.cram.structure.DataSeries;
import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.util.Tuple;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class CRAMAllEncodingStrategiesTest extends HtsjdkTest {

    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/cram");

    // TODO: need a better test file; this has mate validation errors
    // TODO: SAM validation error: ERROR: Read name20FUKAAXX100202:2:1:20271:61529,
    // TODO: Mate Alignment start (9999748) must be <= reference sequence length (200) on reference 20
    //final File cramSourceFile = new File(TEST_DATA_DIR, "NA12878.20.21.1-100.100-SeqsPerSlice.0-unMapped.cram");
    //final File referenceFile = new File(TEST_DATA_DIR, "human_g1k_v37.20.21.1-100.fasta");
    final File cramSourceFile = new File("/Users/cnorman/projects/gatk/src/test/resources/large/CEUTrio.HiSeq.WGS.b37.NA12878.20.21.cram");
    final File referenceFile = new File("/Users/cnorman/projects/gatk/src/test/resources/large/human_g1k_v37.20.21.fasta");

    @Test
    public final void testAllEncodingStrategyCombinations() throws IOException {
        final Map<Integer, CRAMEncodingStrategy> encodingStrategyByTest = new HashMap<>();
        final Map<Integer, Long> fileSizeByTest = new HashMap<>();

        System.out.println(String.format("Test file size: %,d", Files.size(cramSourceFile.toPath())));
        int testCount = 1;
        // gzip 0-9
        for (int compLevel = 0; compLevel < 10; compLevel++) {
            // TODO: the larger reads/slice and slices/container values are only interesting if there
            // TODO: are enough records in the input file to cause these thresholds to be crossed
            //for (int readsPerSlice : Arrays.asList(1000, 10000, 20000)) {
            for (int readsPerSlice : Arrays.asList(10000)) {
                //TODO: properly divide up lists of records into containers/slices with the correct reference context(s)
                //for (int slicesPerContainer : Arrays.asList(1, 2, 10, 100)) {
                for (int slicesPerContainer : Arrays.asList(1)) {
                    for (final CRAMEncodingStrategy encodingStrategy : getAllEncodingMapsForParams(compLevel, readsPerSlice, slicesPerContainer)) {
                        final File tempOutFile = File.createTempFile("test", ".cram");
                        tempOutFile.deleteOnExit();
                        final long fileSize = writeWithEncodingParams(cramSourceFile, tempOutFile, referenceFile, encodingStrategy);
                        encodingStrategyByTest.put(testCount, encodingStrategy);
                        fileSizeByTest.put(testCount, fileSize);
                        System.out.println(String.format("Test %,d File size: %,d Encoding: %s", testCount, fileSize, encodingStrategy));
                        testCount++;
                    }
                    //TODO: add validation/equality assertion
                }
            }
            System.out.println();
        }

        // sort and display encoding strategies ordered by ascending result file size
        final List<Tuple<Long, Integer>> bestEncodingStrategies =
                fileSizeByTest.entrySet()
                .stream()
                .map(e -> new Tuple<>(e.getValue(), e.getKey()))
                .collect(Collectors.toList());
        bestEncodingStrategies.sort(Comparator.comparing(t -> t.a));
        bestEncodingStrategies
                .forEach((Tuple<Long, Integer> t) ->
                        System.out.println(String.format("Size: %,d Test: %d Encoding: %s",
                                t.a,
                                t.b,
                                encodingStrategyByTest.get(t.b)))
                );
    }

    // write with encoding params and return the size of the generated file
    private long writeWithEncodingParams(
            final File inputCRAM,
            final File outputCRAM,
            final File referenceFile,
            final CRAMEncodingStrategy cramEncodingStrategy) throws IOException {
        try (final SamReader reader = SamReaderFactory.makeDefault()
                .referenceSequence(referenceFile)
                .validationStringency((ValidationStringency.LENIENT))
                .open(inputCRAM);
             final FileOutputStream fos = new FileOutputStream(outputCRAM)) {
            final CRAMFileWriter cramWriter = new CRAMFileWriter(
                    cramEncodingStrategy,
                    fos,
                    null,
                    true,
                    new ReferenceSource(referenceFile),
                    reader.getFileHeader(),
                    outputCRAM.getName());
            final SAMRecordIterator inputIterator = reader.iterator();
            while (inputIterator.hasNext()) {
                cramWriter.addAlignment(inputIterator.next());
            }
            cramWriter.close();
        }
        return Files.size(outputCRAM.toPath());
    }

    private List<CRAMEncodingStrategy> getAllEncodingMapsForParams(
        final int gzipCompressionLevel,
        final int readsPerSlice,
        final int slicesPerContainer) throws IOException {
        final List<CRAMEncodingStrategy> allDataSeriesStrategies = new ArrayList<>();
        final CRAMEncodingStrategy encodingStrategy = createCRAMEncodingStrategy(gzipCompressionLevel, readsPerSlice, slicesPerContainer);
        for (final Path encodingMapPath: getAllEncodingMaps(new CompressionHeaderEncodingMap(encodingStrategy))) {
            encodingStrategy.setEncodingMap(encodingMapPath);
            allDataSeriesStrategies.add(encodingStrategy);
        }
        return allDataSeriesStrategies;
    }

    private List<Path> getAllEncodingMaps(final CompressionHeaderEncodingMap encodingMap) throws IOException {
        final List<Path> allEncodingMapPaths = new ArrayList<>();
        final File tempOutFile = File.createTempFile("test", ".cram");
        tempOutFile.deleteOnExit();

        //final List<List<CompressionHeaderEncodingMap.EncodingMapEntry>> allMaps = new ArrayList<>();
        //TODO: generate encodingMaps here
        //for (final DataSeries ds : DataSeries.values()) {
        //    if (ds == DataSeries.TM_TestMark || ds == DataSeries.TV_TestMark) {
        //        continue;
        //    }
        //}

        // TODO: Until we can generate non-external encodings (many of which require a pass over the data)
        // TODO: to determine parameters before being encoded), just return the one we're handed

        encodingMap.writeToPath(tempOutFile.toPath());
        allEncodingMapPaths.add(tempOutFile.toPath());

        return allEncodingMapPaths;
    }

    private final CRAMEncodingStrategy createCRAMEncodingStrategy(
            final int gzipCompressionLevel,
            final int readsPerSlice,
            final int slicesPerContainer) {
        final CRAMEncodingStrategy encodingStrategy = new CRAMEncodingStrategy();
        encodingStrategy.setGZIPCompressionLevel(gzipCompressionLevel);
        encodingStrategy.setReadsPerSlice(readsPerSlice);
        encodingStrategy.setSlicesPerContainer(slicesPerContainer);
        return encodingStrategy;
    }
}
