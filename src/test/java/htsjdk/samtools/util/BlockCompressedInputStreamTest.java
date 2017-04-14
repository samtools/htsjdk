package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.util.zip.InflaterFactory;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Inflater;

public class BlockCompressedInputStreamTest extends HtsjdkTest {
	// random data pulled from /dev/random then compressed using bgzip from tabix
	private static final File BLOCK_UNCOMPRESSED = new File("src/test/resources/htsjdk/samtools/util/random.bin");
	private static final File BLOCK_COMPRESSED = new File("src/test/resources/htsjdk/samtools/util/random.bin.gz");
	private static final long[] BLOCK_COMPRESSED_OFFSETS = new long[] { 0, 0xfc2e, 0x1004d, 0x1fc7b, 0x2009a, };
	private static final long[] BLOCK_UNCOMPRESSED_END_POSITIONS = new long[] { 64512, 65536, 130048 };
	@Test
    public void stream_should_match_uncompressed_stream() throws Exception {
		byte[] uncompressed = Files.readAllBytes(BLOCK_UNCOMPRESSED.toPath());
		try (BlockCompressedInputStream stream = new BlockCompressedInputStream(new FileInputStream(BLOCK_COMPRESSED))) {
			for (int i = 0; i < uncompressed.length; i++) {
				Assert.assertEquals(stream.read(), Byte.toUnsignedInt(uncompressed[i]));
			}
			Assert.assertTrue(stream.endOfBlock());
		}
	}
	@Test
    public void endOfBlock_should_be_true_only_when_entire_block_is_read() throws Exception {
		long size = BLOCK_UNCOMPRESSED.length();
		// input file contains 5 blocks
		List<Long> offsets = new ArrayList<>();
		for (int i = 0; i < BLOCK_UNCOMPRESSED_END_POSITIONS.length; i++) {
			offsets.add(BLOCK_UNCOMPRESSED_END_POSITIONS[i]);
		}
		List<Long> endOfBlockTrue = new ArrayList<>();
		try (BlockCompressedInputStream stream = new BlockCompressedInputStream(new FileInputStream(BLOCK_COMPRESSED))) {
			for (long i = 0; i < size; i++) {
				if (stream.endOfBlock()) {
					endOfBlockTrue.add(i);
				}
				stream.read();
			}
		}
		Assert.assertEquals(endOfBlockTrue, offsets);
	}
	@Test
    public void decompression_should_cross_block_boundries() throws Exception {
		byte[] uncompressed = Files.readAllBytes(BLOCK_UNCOMPRESSED.toPath());
		try (BlockCompressedInputStream stream = new BlockCompressedInputStream(new FileInputStream(BLOCK_COMPRESSED))) {
			byte[] decompressed = new byte[uncompressed.length]; 
			stream.read(decompressed);
			Assert.assertEquals(decompressed, uncompressed);
			Assert.assertTrue(stream.endOfBlock());
			Assert.assertEquals(stream.read(), -1);
		}
	}
	@Test
    public void seek_should_read_block() throws Exception {
		byte[] uncompressed = Files.readAllBytes(BLOCK_UNCOMPRESSED.toPath());
		try (SeekableFileStream sfs = new SeekableFileStream(BLOCK_COMPRESSED)) {
			try (BlockCompressedInputStream stream = new BlockCompressedInputStream(sfs)) {
				// seek to the start of the first block
				for (int i = 0; i < BLOCK_COMPRESSED_OFFSETS.length-1; i++) {
					stream.seek(BLOCK_COMPRESSED_OFFSETS[i] << 16);
					Assert.assertEquals(sfs.position(), BLOCK_COMPRESSED_OFFSETS[i + 1]);
					// check 
					byte[] actual = new byte[uncompressed.length];
					int len = stream.read(actual);
					actual = Arrays.copyOf(actual, len);
					byte[] expected = Arrays.copyOfRange(uncompressed, uncompressed.length - actual.length, uncompressed.length);
					Assert.assertEquals(actual, expected);
				}
			}
		}
	}
	@Test
    public void available_should_return_number_of_bytes_left_in_current_block() throws Exception {
		try (BlockCompressedInputStream stream = new BlockCompressedInputStream(BLOCK_COMPRESSED)) {
			for (int i = 0; i < BLOCK_UNCOMPRESSED_END_POSITIONS[0]; i++) {
				Assert.assertEquals(stream.available(), BLOCK_UNCOMPRESSED_END_POSITIONS[0] - i);
				stream.read();
			}
		}
	}

    private static class CountingInflater extends Inflater {
        // Must be static unfortunately, since there's no way to reach down into an inflater instance given a stream
        static int inflateCalls = 0;

        CountingInflater(boolean gzipCompatible) {
            super(gzipCompatible);
        }
        @Override
        public int inflate(byte[] b, int off, int len) throws java.util.zip.DataFormatException {
            inflateCalls++;
            return super.inflate(b, off, len);
        }
    }

    private static class CountingInflaterFactory extends InflaterFactory {
        @Override
        public Inflater makeInflater( boolean gzipCompatible ) {
            return new CountingInflater(gzipCompatible);
        }
    }

    @FunctionalInterface
    private interface CheckedExceptionInputStreamSupplier {
        InputStream get() throws IOException;
    }

    private List<String> writeTempBlockCompressedFileForInflaterTest( final File tempFile ) throws IOException {
        final List<String> linesWritten = new ArrayList<>();
        try ( final BlockCompressedOutputStream bcos = new BlockCompressedOutputStream(tempFile, 5) ) {
            String s = "Hi, Mom!\n";
            bcos.write(s.getBytes()); //Call 1
            linesWritten.add(s);
            s = "Hi, Dad!\n";
            bcos.write(s.getBytes()); //Call 2
            linesWritten.add(s);
            bcos.flush();
            final StringBuilder sb = new StringBuilder(BlockCompressedStreamConstants.DEFAULT_UNCOMPRESSED_BLOCK_SIZE * 2);
            s = "1234567890123456789012345678901234567890123456789012345678901234567890\n";
            while ( sb.length() <= BlockCompressedStreamConstants.DEFAULT_UNCOMPRESSED_BLOCK_SIZE ) {
                sb.append(s);
                linesWritten.add(s);
            }
            bcos.write(sb.toString().getBytes()); //Call 3
        }
        return linesWritten;
    }

    @DataProvider(name = "customInflaterInput")
    public Object[][] customInflateInput() throws IOException {
        final File tempFile = File.createTempFile("testCustomInflater.", ".bam");
        tempFile.deleteOnExit();
        final List<String> linesWritten = writeTempBlockCompressedFileForInflaterTest(tempFile);

        final InflaterFactory countingInflaterFactory = new CountingInflaterFactory();

        return new Object[][]{
                // set the default InflaterFactory to a CountingInflaterFactory
                {(CheckedExceptionInputStreamSupplier) () -> new BlockCompressedInputStream(new FileInputStream(tempFile), false), linesWritten, 4, countingInflaterFactory},
                {(CheckedExceptionInputStreamSupplier) () -> new BlockCompressedInputStream(tempFile), linesWritten, 4, countingInflaterFactory},
                {(CheckedExceptionInputStreamSupplier) () -> new AsyncBlockCompressedInputStream(tempFile), linesWritten, 4, countingInflaterFactory},
                {(CheckedExceptionInputStreamSupplier) () -> new BlockCompressedInputStream(new URL("http://broadinstitute.github.io/picard/testdata/index_test.bam")), null, 21, countingInflaterFactory},
                // provide a CountingInflaterFactory explicitly
                {(CheckedExceptionInputStreamSupplier) () -> new BlockCompressedInputStream(new FileInputStream(tempFile), false, countingInflaterFactory), linesWritten, 4, null},
                {(CheckedExceptionInputStreamSupplier) () -> new BlockCompressedInputStream(tempFile, countingInflaterFactory), linesWritten, 4, null},
                {(CheckedExceptionInputStreamSupplier) () -> new AsyncBlockCompressedInputStream(tempFile, countingInflaterFactory), linesWritten, 4, null},
                {(CheckedExceptionInputStreamSupplier) () -> new BlockCompressedInputStream(new URL("http://broadinstitute.github.io/picard/testdata/index_test.bam"), countingInflaterFactory), null, 21, null}
        };
    }

    @Test(dataProvider = "customInflaterInput", singleThreaded = true)
    public void testCustomInflater(final CheckedExceptionInputStreamSupplier bcisSupplier,
                                   final List<String> expectedOutput,
                                   final int expectedInflateCalls,
                                   final InflaterFactory customDefaultInflaterFactory) throws Exception
    {
        // clear inflate call counter in CountingInflater
        CountingInflater.inflateCalls = 0;

        // If requested, set the global default InflaterFactory to a custom factory. Otherwise, set it to the default.
        if ( customDefaultInflaterFactory != null )  {
            BlockGunzipper.setDefaultInflaterFactory(customDefaultInflaterFactory);
        }
        else {
            BlockGunzipper.setDefaultInflaterFactory(new InflaterFactory());
        }

        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(bcisSupplier.get()))) {
            String line;
            for (int i = 0; (line = reader.readLine()) != null; ++i) {
                // check expected output, if provided
                if (expectedOutput != null) {
                    Assert.assertEquals(line + "\n", expectedOutput.get(i));
                }
            }
        }

        // verify custom inflater was used by checking number of inflate calls
        Assert.assertEquals(CountingInflater.inflateCalls, expectedInflateCalls, "inflate calls");

        // Reset the default InflaterFactory back to the default value
        BlockGunzipper.setDefaultInflaterFactory(new InflaterFactory());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testSetNullInflaterFactory() {
        // test catching null InflaterFactory
        BlockGunzipper.setDefaultInflaterFactory(null);
    }
}
