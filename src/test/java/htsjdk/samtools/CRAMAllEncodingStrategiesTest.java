package htsjdk.samtools;

import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.CRAMEncodingStrategy;
import htsjdk.samtools.util.ComparableTuple;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class CRAMAllEncodingStrategiesTest {

    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/cram");

    // TODO: need a better test file; this has mate validation errors
    // TODO: SAM validation error: ERROR: Read name20FUKAAXX100202:2:1:20271:61529,
    // TODO: Mate Alignment start (9999748) must be <= reference sequence length (200) on reference 20
    //final File cramSourceFile = new File(TEST_DATA_DIR, "NA12878.20.21.1-100.100-SeqsPerSlice.0-unMapped.cram");
    //final File referenceFile = new File(TEST_DATA_DIR, "human_g1k_v37.20.21.1-100.fasta");
    final File cramSourceFile = new File("/Users/cnorman/projects/gatk/src/test/resources/large/CEUTrio.HiSeq.WGS.b37.NA12878.20.21.cram");
    final File referenceFile = new File("/Users/cnorman/projects/gatk/src/test/resources/large/human_g1k_v37.20.21.fasta");

    @Test
    public final void testAllEncodingCombinations() throws IOException {
        final Map<Integer, Long> fileSizeByTest = new TreeMap<>();
        final Map<Integer, CRAMEncodingStrategy> strategyByFileSize= new HashMap<>();

        System.out.println(String.format("Test file size: %d", Files.size(cramSourceFile.toPath())));
        int testCount = 1;
        // gzip 0-9
        for (int compLevel = 0; compLevel < 10; compLevel++) {
            // TODO: the larger reads/slice and slices/container values are only interesting if there
            // TODO: are enough records in the input file to cause these thresholds to be crossed
            for (int readsPerSlice : Arrays.asList(1000, 10000, 20000)) {
                //TODO: properly divide up lists of records into containers/slices with the correct reference context(s)
                //for (int slicesPerContainer : Arrays.asList(1, 2, 10, 100)) {
                for (int slicesPerContainer : Arrays.asList(1)) {
                    final File tempOutFile = File.createTempFile("test", ".cram");
                    tempOutFile.deleteOnExit();
                    final CRAMEncodingStrategy encodingStrategy = createCRAMEncodingStrategy(compLevel, readsPerSlice, slicesPerContainer);
                    final long fileSize = writeWithEncodingParams(cramSourceFile, tempOutFile, referenceFile, encodingStrategy);
                    fileSizeByTest.put(testCount, fileSize);
                    strategyByFileSize.put(testCount, encodingStrategy);
                    System.out.println(String.format("Test %d File size: %d Encoding: %s", testCount, fileSize, encodingStrategy));
                    testCount++;
                }
            }
        }

        // print results sorted by result fileSize
        fileSizeByTest.forEach((k, v) -> System.out.println(String.format("Size: %d Test: %d Encoding: %s", v, k, strategyByFileSize.get(k))));
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

    private final CRAMEncodingStrategy createCRAMEncodingStrategy(
            int gzipCompressionLevel,
            int readsPerSlice,
            int slicesPerContainer) {
        final CRAMEncodingStrategy encodingStrategy = new CRAMEncodingStrategy();
        encodingStrategy.setGZIPCompressionLevel(gzipCompressionLevel);
        encodingStrategy.setReadsPerSlice(readsPerSlice);
        encodingStrategy.setSlicesPerContainer(slicesPerContainer);
        return encodingStrategy;
    }
}
