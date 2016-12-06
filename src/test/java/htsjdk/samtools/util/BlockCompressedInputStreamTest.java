package htsjdk.samtools.util;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

import htsjdk.samtools.seekablestream.SeekableFileStream;

public class BlockCompressedInputStreamTest {
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
}
