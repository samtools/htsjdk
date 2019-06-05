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
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.util.*;

public class CRAMAllEncodingStrategiesTest extends HtsjdkTest {

    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/cram");
    private final CompressorCache compressorCache = new CompressorCache();

    @DataProvider(name="roundTripTestFiles")
    public Object[][] roundTripTestFiles() {
        return new Object[][] {
                { new File(TEST_DATA_DIR, "NA12878.20.21.1-100.100-SeqsPerSlice.500-unMapped.cram"),
                        new File(TEST_DATA_DIR, "human_g1k_v37.20.21.1-100.fasta") },
        };
    }

    @Test(dataProvider = "roundTripTestFiles")
    public final void testRoundTripDefaultEncodingStrategy(final File sourceFile, final File referenceFile) throws IOException {
        final CRAMEncodingStrategy testStrategy = new CRAMEncodingStrategy();
        final File tempOutCRAM = File.createTempFile("testRoundTrip", ".cram");
        tempOutCRAM.deleteOnExit();
        CRAMTestUtils.writeToCRAMWithEncodingStrategy(testStrategy, sourceFile, tempOutCRAM, referenceFile);
        assertRoundTripFidelity(sourceFile, tempOutCRAM, referenceFile, false);
        assertRoundtripFidelityWithSamtools(tempOutCRAM, referenceFile);
    }

    @Test(dataProvider = "roundTripTestFiles")
    public final void testAllEncodingStrategyCombinations(final File cramSourceFile, final File referenceFile) throws IOException {
        for (final Tuple<String, CRAMEncodingStrategy> testStrategy : getAllEncodingStrategies()) {
            final File tempOutCRAM = File.createTempFile("allEncodingStrategyCombinations", ".cram");
            tempOutCRAM.deleteOnExit();
            CRAMTestUtils.writeToCRAMWithEncodingStrategy(testStrategy.b, cramSourceFile, tempOutCRAM, referenceFile);
            assertRoundTripFidelity(cramSourceFile, tempOutCRAM, referenceFile, false);
            assertRoundtripFidelityWithSamtools(tempOutCRAM, referenceFile);
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
            final File samtoolsOutFile = SamtoolsTestUtils.convertToCRAM(
                    sourceCRAM,
                    referenceFile,
                    "--input-fmt-option decode_md=0 --output-fmt-option store_md=0 --output-fmt-option store_nm=0");
            assertRoundTripFidelity(sourceCRAM, samtoolsOutFile, referenceFile, false);
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
