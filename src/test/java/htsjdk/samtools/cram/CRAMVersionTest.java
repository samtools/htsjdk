package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.CRAMFileWriter;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.common.CRAMVersion;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.io.CramInt;
import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.ContainerHeader;
import htsjdk.samtools.cram.structure.CramHeader;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.seekablestream.SeekableMemoryStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.zip.CRC32;

/**
 * Created by vadim on 18/02/2016.
 */
public class CRAMVersionTest extends HtsjdkTest {
    /**
     * The test purpose is to ensure that a CRAM written by {@link CRAMFileWriter} adheres to CRAM3 specs expectations:
     * 1. version 3.+, via both actual byte comparison and CramIO API
     * 2. EOF container
     * 3. trailing 4 bytes of a container bytes are a valid crc32 of previous bytes in the container
     * 3. trailing 4 bytes of a block bytes are a valid crc32 of previous bytes in the block
     * @throws IOException
     */
    @Test
    public void test_V3() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ReferenceSource source = new ReferenceSource((File) null);
        SAMFileHeader samFileHeader = new SAMFileHeader();
        CRAMVersion cramVersion = CramVersions.CRAM_v3;
        CRAMFileWriter w = new CRAMFileWriter(baos, source, samFileHeader, null);
        SAMRecord record = new SAMRecord(samFileHeader);
        record.setReadName("name");
        record.setAlignmentStart(SAMRecord.NO_ALIGNMENT_START);
        record.setReferenceIndex(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
        record.setReadUnmappedFlag(true);
        record.setReadBases("AAA".getBytes());
        record.setBaseQualities("!!!".getBytes());
        w.addAlignment(record);
        w.close();

        byte[] cramBytes = baos.toByteArray();

        SeekableStream cramSeekableStream = new SeekableMemoryStream(cramBytes, null);
        CramHeader cramHeader = CramIO.readCramHeader(cramSeekableStream);
        Container.readSAMFileHeaderContainer(cramHeader.getCRAMVersion(), cramSeekableStream, null);
        Assert.assertEquals(cramVersion, cramHeader.getCRAMVersion());

        // read whole container:
        long containerStart = cramSeekableStream.position();
        Container container = new Container(cramVersion, cramSeekableStream, containerStart);
        Assert.assertNotNull(container);

        // ensure EOF follows:
        final long streamPos = cramSeekableStream.position();
        Container eof = new Container(cramVersion, cramSeekableStream, streamPos);
        Assert.assertNotNull(eof);
        Assert.assertTrue(eof.isEOF());

        // position stream at the start of the 1st container:
        cramSeekableStream.seek(containerStart);
        // read only container header:
        new ContainerHeader(cramVersion, cramSeekableStream);

        // read the following 4 bytes of CRC32:
        int crcByteSize = 4;
        cramSeekableStream.seek(cramSeekableStream.position() - crcByteSize);
        byte[] crcBytes = InputStreamUtils.readFully(cramSeekableStream, crcByteSize);
        long firstBlockStart = cramSeekableStream.position();

        // rewind to 1st container start:
        cramSeekableStream.seek(containerStart);
        // read container header bytes:
        byte[] containerHeaderBytes = InputStreamUtils.readFully(cramSeekableStream, (int) (firstBlockStart - containerStart) - crcByteSize);

        // test that checksum matches:
        CRC32 digester = new CRC32();
        digester.update(containerHeaderBytes);
        Assert.assertEquals(container.getContainerHeader().getChecksum(), (int) digester.getValue());
        Assert.assertEquals(CramInt.readInt32(crcBytes), container.getContainerHeader().getChecksum());

        // test block's crc:
        cramSeekableStream.seek(firstBlockStart);
        Block.read(cramVersion, cramSeekableStream);
        long blockByteSyze = cramSeekableStream.position() - firstBlockStart - crcByteSize;
        cramSeekableStream.seek(firstBlockStart);
        final byte[] blockBytes = InputStreamUtils.readFully(cramSeekableStream, (int) blockByteSyze);
        crcBytes = InputStreamUtils.readFully(cramSeekableStream, crcByteSize);
        digester = new CRC32();
        digester.update(blockBytes);
        Assert.assertEquals(CramInt.readInt32(crcBytes), (int) digester.getValue());
    }
}
