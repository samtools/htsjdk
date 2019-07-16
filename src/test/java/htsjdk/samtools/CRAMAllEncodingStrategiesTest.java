package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.*;
import htsjdk.samtools.cram.compression.rans.RANS;
import htsjdk.samtools.cram.encoding.CRAMEncoding;
import htsjdk.samtools.cram.encoding.external.ExternalByteEncoding;
import htsjdk.samtools.cram.encoding.external.ExternalIntegerEncoding;
import htsjdk.samtools.cram.encoding.external.ExternalLongEncoding;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.*;
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
    final File cramSourceFile = new File(TEST_DATA_DIR, "NA12878.20.21.1-100.100-SeqsPerSlice.0-unMapped.cram");
    final File referenceFile = new File(TEST_DATA_DIR, "human_g1k_v37.20.21.1-100.fasta");
    //final File cramSourceFile = new File("/Users/cnorman/projects/gatk/src/test/resources/large/CEUTrio.HiSeq.WGS.b37.NA12878.20.21.cram");
    //final File referenceFile = new File("/Users/cnorman/projects/gatk/src/test/resources/large/human_g1k_v37.20.21.fasta");
    //final File cramSourceFile = new File("/Users/cnorman/projects/testdata/samn/DDP_ATCP_265_2.cram");
    //final File referenceFile = new File("/Users/cnorman/projects/references/hg38/Homo_sapiens_assembly38.fasta");

    @Test
    public final void testAllEncodingStrategyCombinations() throws IOException {
        final Map<Integer, CRAMEncodingStrategy> encodingStrategyByTest = new HashMap<>();
        final Map<Integer, String> encodingParamsByTest = new HashMap<>();
        final Map<Integer, Long> fileSizeByTest = new HashMap<>();

        System.out.println(String.format("Test file size: %,d", Files.size(cramSourceFile.toPath())));
        int testCount = 1;

        // the larger reads/slice and slices/container values are only interesting if there
        // are enough records in the input file to cause these thresholds to be crossed
        for (final int gzipCompressionLevel : Arrays.asList(5, 9)) {
            for (final int readsPerSlice : Arrays.asList(10000, 20000)) {
                for (final int slicesPerContainer : Arrays.asList(1, 3)) {
                    for (final DataSeries dataSeries : enumerateDataSeries()) {
                        for (final EncodingID encodingID : enumerateEncodingIDs()) {
                            for (final ExternalCompressor compressor : enumerateExternalCompressors(gzipCompressionLevel)) {
                                final CRAMEncodingStrategy testStrategy = createEncodingStrategyForParams(
                                        gzipCompressionLevel,
                                        readsPerSlice,
                                        slicesPerContainer,
                                        dataSeries,
                                        encodingID,
                                        compressor);
                                final File tempOutCRAM = File.createTempFile("encodingStrategiesTest", ".cram");
                                tempOutCRAM.delete();
                                final long fileSize = testWithEncodingStrategy(testStrategy, cramSourceFile, tempOutCRAM, referenceFile);
                                final String testSummary = String.format(
                                        "Test %,d FileSize: %,d DS: %s Encoding: %s Comp: %s %s",
                                        testCount,
                                        fileSize,
                                        dataSeries.getCanonicalName(),
                                        encodingID,
                                        compressor.getMethod(),
                                        testStrategy);
                                System.out.println(testSummary);
                                encodingParamsByTest.put(testCount, testSummary);
                                encodingStrategyByTest.put(testCount, testStrategy);
                                fileSizeByTest.put(testCount, fileSize);
                                testCount++;
                            }
                        }
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
        System.out.println(String.format("%d tests, sorted by result size:", testCount));
        bestEncodingStrategies
                // take the 500 best results
                .stream().limit(500)
                .forEach((Tuple<Long, Integer> t) ->
                        System.out.println(String.format("Size: %,d Test: %d Params: %s Encoding: %s",
                                t.a,
                                t.b,
                                encodingParamsByTest.get(t.b),
                                encodingStrategyByTest.get(t.b)))
                );
    }

    public final CRAMEncodingStrategy createEncodingStrategyForParams(
           final int  gzipCompressionLevel,
           final int readsPerSlice,
           final int slicesPerContainer,
           final DataSeries ds,
           final EncodingID encodingID,
           final ExternalCompressor compressor) throws IOException {
        final CRAMEncodingStrategy encodingStrategy = new CRAMEncodingStrategy();
        encodingStrategy.setGZIPCompressionLevel(gzipCompressionLevel);
        encodingStrategy.setRecordsPerSlice(readsPerSlice);
        encodingStrategy.setSlicesPerContainer(slicesPerContainer);
        final CompressionHeaderEncodingMap encodingMap = createEncodingMapVariationFor(ds, encodingID, compressor);
        final File tempEncodingMapPath = File.createTempFile("testEncodingMap", ".json");
        tempEncodingMapPath.deleteOnExit();
        final Path encodingMapPath = tempEncodingMapPath.toPath();
        encodingMap.writeToPath(encodingMapPath);
        encodingStrategy.setEncodingMap(encodingMapPath);
        return encodingStrategy;
    }

    // write with encoding params and return the size of the generated file
    private long testWithEncodingStrategy(
            final CRAMEncodingStrategy cramEncodingStrategy,
            final File inputCRAM,
            final File tempOutputCRAM,
            final File referenceFile) throws IOException {
        final File tempOutFile = File.createTempFile("encodingStrategiesTest", ".cram");
        tempOutFile.deleteOnExit();
        try (final SamReader reader = SamReaderFactory.makeDefault()
                .referenceSequence(referenceFile)
                .validationStringency((ValidationStringency.SILENT))
                .open(inputCRAM);
             final FileOutputStream fos = new FileOutputStream(tempOutputCRAM)) {
            final CRAMFileWriter cramWriter = new CRAMFileWriter(
                    cramEncodingStrategy,
                    fos,
                    null,
                    true,
                    new ReferenceSource(referenceFile),
                    reader.getFileHeader(),
                    tempOutputCRAM.getName());
            final SAMRecordIterator inputIterator = reader.iterator();
            while (inputIterator.hasNext()) {
                cramWriter.addAlignment(inputIterator.next());
            }
            cramWriter.close();
        }
        return Files.size(tempOutputCRAM.toPath());
    }

    public List<ExternalCompressor> enumerateExternalCompressors(final int gzipCompressionLevel) {
        return Arrays.asList(
                new GZIPExternalCompressor(gzipCompressionLevel),
                new RANSExternalCompressor(RANS.ORDER.ZERO),
                new RANSExternalCompressor(RANS.ORDER.ONE),
                new LZMAExternalCompressor(),
                new BZIP2ExternalCompressor()
        );
    }

    private List<DataSeries> enumerateDataSeries() {
        final List<DataSeries> seriesToUse = new ArrayList<>();
        // skip the ones this implementation doesn't use
        for (final DataSeries ds : DataSeries.values()) {
            if (ds != DataSeries.TM_TestMark
                    && ds != DataSeries.TV_TestMark
                    && ds != DataSeries.QQ_scores
                    && ds != DataSeries.BB_bases) {
                seriesToUse.add(ds);
            }
        }
        return seriesToUse;
    }

    private List<EncodingID> enumerateEncodingIDs() {
        //TODO: add HUFFMAN
        return Arrays.asList(EncodingID.EXTERNAL);
    }

    public CompressionHeaderEncodingMap createEncodingMapVariationFor(
            final DataSeries ds,
            final EncodingID id,
            final ExternalCompressor compressor) {

        final CompressionHeaderEncodingMap encodingMap = new CompressionHeaderEncodingMap(new CRAMEncodingStrategy());
        if (id == EncodingID.EXTERNAL) {
            encodingMap.putExternalEncoding(ds, compressor);
        } else {
            encodingMap.putEncoding(ds, createEncodingDescriptorFor(ds, id));
        }
        return encodingMap;
    }

    private EncodingDescriptor createEncodingDescriptorFor(
            final DataSeries ds,
            final EncodingID id) {
        switch (id) {
            case EXTERNAL:
                final CRAMEncoding<?> cramEncoding;

                switch (ds.getType()) {
                    case BYTE:
                        cramEncoding = new ExternalByteEncoding(ds.getExternalBlockContentId());
                        break;
                    case INT:
                        cramEncoding = new ExternalIntegerEncoding(ds.getExternalBlockContentId());
                        break;
                    case LONG:
                        cramEncoding = new ExternalLongEncoding(ds.getExternalBlockContentId());
                        break;
                    case BYTE_ARRAY:
                        cramEncoding = new ExternalByteEncoding(ds.getExternalBlockContentId());
                        break;
                    default:
                        throw new CRAMException("Unknown data series value type");
                }
                return cramEncoding.toEncodingDescriptor();

            case HUFFMAN:
                //TODO: need a way to create a huffman encoder before knowing the params
                switch(ds.getType()) {
                    case BYTE:
                    case INT:
                    case LONG:
                    case BYTE_ARRAY:
                        throw new IllegalArgumentException("Huffman test encoding not implemented");
                }

            case NULL:
            case GOLOMB:
            case BYTE_ARRAY_LEN:
            case BYTE_ARRAY_STOP:
            case BETA:
            case SUBEXPONENTIAL:
            case GOLOMB_RICE:
            case GAMMA:
            default:
                throw new IllegalArgumentException(
                        String.format("Can't create test encoding for encoding %s", id));
        }

    }

}
