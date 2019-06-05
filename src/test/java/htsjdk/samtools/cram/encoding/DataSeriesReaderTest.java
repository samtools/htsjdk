package htsjdk.samtools.cram.encoding;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.core.BetaIntegerEncoding;
import htsjdk.samtools.cram.encoding.reader.DataSeriesReader;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

public class DataSeriesReaderTest extends HtsjdkTest {

    @DataProvider(name = "negativeConstructor")
    public static Object[][] negativeConstructor() {
        return new Object[][] {
                // mismatch type and encoding
                {DataSeriesType.BYTE, new BetaIntegerEncoding(0, 8).toEncodingDescriptor()}
        };
    }

    @Test(dataProvider = "negativeConstructor", expectedExceptions = IllegalArgumentException.class)
    public void negativeConstructorTest(final DataSeriesType valueType,
                                        final EncodingDescriptor params) {
        final Block coreBlock = Block.createRawCoreDataBlock(new byte[2]);
        // use a raw external block so we don't have to compress it before attemptin to read...
        final Block extBlock = Block.createExternalBlock(BlockCompressionMethod.RAW, 27, new byte[2], 2);
        final List<Block> extBlocks = Arrays.asList(extBlock);
        new DataSeriesReader(valueType, params,
                new SliceBlocksReadStreams(
                        new SliceBlocks(coreBlock, extBlocks),
                        new CompressorCache()));
    }
}
