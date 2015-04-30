/*******************************************************************************
 * Copyright 2013 EMBL-EBI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package htsjdk.samtools.cram.build;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMTextHeaderCodec;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.io.CountingInputStream;
import htsjdk.samtools.cram.io.ExposedByteArrayOutputStream;
import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.cram.structure.Block;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.ContainerIO;
import htsjdk.samtools.cram.structure.CramHeader;
import htsjdk.samtools.cram.structure.Slice;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.BufferedLineReader;
import htsjdk.samtools.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * A collection of methods to open and close CRAM files.
 */
public class CramIO {
    /**
     * The 'zero-B' EOF marker as per CRAM specs v2.1. This is basically a serialized empty CRAM container with sequence id set to some
     * number to spell out 'EOF' in hex.
     */
    public static final byte[] ZERO_B_EOF_MARKER = bytesFromHex("0b 00 00 00 ff ff ff ff ff e0 45 4f 46 00 00 00 00 01 00 00 01 00 06 06 01 00 " +
            "" + "01 00 01 00");
    /**
     * The zero-F EOF marker as per CRAM specs v3.0. This is basically a serialized empty CRAM container with sequence id set to some number
     * to spell out 'EOF' in hex.
     */
    public static final byte[] ZERO_F_EOF_MARKER = bytesFromHex("0f 00 00 00 ff ff ff ff 0f e0 45 4f 46 00 00 00 00 01 00 05 bd d9 4f 00 01 00 " +
            "" + "06 06 01 00 01 00 01 00 ee 63 01 4b");


    private static final int DEFINITION_LENGTH = 4 + 1 + 1 + 20;
    private static final Log log = Log.getInstance(CramIO.class);

    private static byte[] bytesFromHex(final String s) {
        final String clean = s.replaceAll("[^0-9a-fA-F]", "");
        if (clean.length() % 2 != 0) throw new RuntimeException("Not a hex string: " + s);
        final byte[] data = new byte[clean.length() / 2];
        for (int i = 0; i < clean.length(); i += 2) {
            data[i / 2] = (Integer.decode("0x" + clean.charAt(i) + clean.charAt(i + 1))).byteValue();
        }
        return data;
    }

    /**
     * Write an end-of-file marker to the {@link OutputStream}. The specific EOF marker is chosen based on the CRAM version.
     *
     * @param version      the CRAM version to assume
     * @param outputStream the stream to write to
     * @return the number of bytes written out
     * @throws IOException as per java IO contract
     */
    public static long issueEOF(final Version version, final OutputStream outputStream) throws IOException {
        if (version.compatibleWith(CramVersions.CRAM_v3)) {
            outputStream.write(ZERO_F_EOF_MARKER);
            return ZERO_F_EOF_MARKER.length;
        }

        if (version.compatibleWith(CramVersions.CRAM_v2_1)) {
            outputStream.write(ZERO_B_EOF_MARKER);
            return ZERO_B_EOF_MARKER.length;
        }
        return 0;
    }

    private static boolean streamEndsWith(final SeekableStream seekableStream, final byte[] marker) throws IOException {
        final byte[] tail = new byte[ZERO_B_EOF_MARKER.length];

        seekableStream.seek(seekableStream.length() - marker.length);
        InputStreamUtils.readFully(seekableStream, tail, 0, tail.length);

        // relaxing the ITF8 hanging bits:
        tail[8] |= 0xf0;
        return Arrays.equals(tail, marker);
    }

    /**
     * Check if the {@link SeekableStream} is properly terminated with a end-of-file marker.
     *
     * @param version        CRAM version to assume
     * @param seekableStream the stream to read from
     * @return true if the stream ends with a correct EOF marker, false otherwise
     * @throws IOException as per java IO contract
     */
    @SuppressWarnings("SimplifiableIfStatement")
    private static boolean checkEOF(final Version version, final SeekableStream seekableStream) throws IOException {

        if (version.compatibleWith(CramVersions.CRAM_v3)) return streamEndsWith(seekableStream, ZERO_B_EOF_MARKER);
        if (version.compatibleWith(CramVersions.CRAM_v2_1)) return streamEndsWith(seekableStream, ZERO_F_EOF_MARKER);

        return false;
    }

    /**
     * Check if the file: 1) contains proper CRAM header. 2) given the version info from the header check the end of file marker.
     *
     * @param file the CRAM file to check
     * @return true if the file is a valid CRAM file and is properly terminated with respect to the version.
     * @throws IOException as per java IO contract
     */
    public static boolean checkHeaderAndEOF(final File file) throws IOException {
        final SeekableStream ss = new SeekableFileStream(file);
        final CramHeader cramHeader = readCramHeader(ss);
        return checkEOF(cramHeader.getVersion(), ss);
    }

    /**
     * Writes CRAM header into the specified {@link OutputStream}.
     *
     * @param cramHeader the {@link CramHeader} object to write
     * @param os         the output stream to write to
     * @return the number of bytes written out
     * @throws IOException as per java IO contract
     */
    public static long writeCramHeader(final CramHeader cramHeader, final OutputStream os) throws IOException {
//        if (cramHeader.getVersion().major < 3) throw new RuntimeException("Deprecated CRAM version: " + cramHeader.getVersion().major);
        os.write("CRAM".getBytes("US-ASCII"));
        os.write(cramHeader.getVersion().major);
        os.write(cramHeader.getVersion().minor);
        os.write(cramHeader.getId());
        for (int i = cramHeader.getId().length; i < 20; i++)
            os.write(0);

        final long len = CramIO.writeContainerForSamFileHeader(cramHeader.getVersion().major, cramHeader.getSamFileHeader(), os);

        return CramIO.DEFINITION_LENGTH + len;
    }

    private static CramHeader readFormatDefinition(final InputStream is) throws IOException {
        for (final byte b : CramHeader.magic) {
            if (b != is.read()) throw new RuntimeException("Unknown file format.");
        }

        final Version version = new Version(is.read(), is.read(), 0);

        final CramHeader header= new CramHeader(version, null, null) ;

        final DataInputStream dis = new DataInputStream(is);
        dis.readFully(header.getId());

        return header;
    }

    /**
     * Read CRAM header from the given {@link InputStream}.
     *
     * @param is input stream to read from
     * @return complete {@link CramHeader} object
     * @throws IOException as per java IO contract
     */
    public static CramHeader readCramHeader(final InputStream is) throws IOException {
        final CramHeader header = readFormatDefinition(is);

        final SAMFileHeader samFileHeader = readSAMFileHeader(header.getVersion(), is, new String (header.getId()));

        return new CramHeader(header.getVersion(), new String (header.getId()), samFileHeader);
    }

    private static byte[] toByteArray(final SAMFileHeader samFileHeader) {
        final ExposedByteArrayOutputStream headerBodyOS = new ExposedByteArrayOutputStream();
        final OutputStreamWriter w = new OutputStreamWriter(headerBodyOS);
        new SAMTextHeaderCodec().encode(w, samFileHeader);
        try {
            w.close();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        final ByteBuffer buf = ByteBuffer.allocate(4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(headerBodyOS.size());
        buf.flip();
        final byte[] bytes = new byte[buf.limit()];
        buf.get(bytes);

        final ByteArrayOutputStream headerOS = new ByteArrayOutputStream();
        try {
            headerOS.write(bytes);
            headerOS.write(headerBodyOS.getBuffer(), 0, headerBodyOS.size());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        return headerOS.toByteArray();
    }

    private static long writeContainerForSamFileHeader(final int major, final SAMFileHeader samFileHeader, final OutputStream os) throws IOException {
        final byte[] data = toByteArray(samFileHeader);
        final int len = Math.max(1024, data.length + data.length / 2);
        final byte[] blockContent = new byte[len];
        System.arraycopy(data, 0, blockContent, 0, Math.min(data.length, len));
        final Block block = Block.buildNewFileHeaderBlock(blockContent);

        final Container c = new Container();
        c.blockCount = 1;
        c.blocks = new Block[]{block};
        c.landmarks = new int[0];
        c.slices = new Slice[0];
        c.alignmentSpan = 0;
        c.alignmentStart = 0;
        c.bases = 0;
        c.globalRecordCounter = 0;
        c.nofRecords = 0;
        c.sequenceId = 0;

        final ExposedByteArrayOutputStream baos = new ExposedByteArrayOutputStream();
        block.write(major, baos);
        c.containerByteSize = baos.size();

        final int containerHeaderByteSize = ContainerIO.writeContainerHeader(major, c, os);
        os.write(baos.getBuffer(), 0, baos.size());

        return containerHeaderByteSize + baos.size();
    }

    private static SAMFileHeader readSAMFileHeader(final Version version, InputStream is, final String id) throws IOException {
        final Container container = ContainerIO.readContainerHeader(version.major, is);
        final Block b;
        {
            if (version.compatibleWith(CramVersions.CRAM_v3)) {
                final byte[] bytes = new byte[container.containerByteSize];
                InputStreamUtils.readFully(is, bytes, 0, bytes.length);
                b = Block.readFromInputStream(version.major, new ByteArrayInputStream(bytes));
                // ignore the rest of the container
            } else {
                /*
                 * pending issue: container.containerByteSize is 2 bytes shorter
				 * then needed in the v21 test cram files.
				 */
                b = Block.readFromInputStream(version.major, is);
            }
        }

        is = new ByteArrayInputStream(b.getRawContent());

        final ByteBuffer buf = ByteBuffer.allocate(4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 4; i++)
            buf.put((byte) is.read());
        buf.flip();
        final int size = buf.asIntBuffer().get();

        final DataInputStream dis = new DataInputStream(is);
        final byte[] bytes = new byte[size];
        dis.readFully(bytes);

        final BufferedLineReader r = new BufferedLineReader(new ByteArrayInputStream(bytes));
        final SAMTextHeaderCodec codec = new SAMTextHeaderCodec();
        return codec.decode(r, id);
    }

    /**
     * Attempt to replace the SAM file header in the CRAM file. This will succeed only if there is sufficient space reserved in the existing
     * CRAM header. The implementation re-writes the first FILE_HEADER block in the first container of the CRAM file using random file
     * access.
     *
     * @param file      the CRAM file
     * @param newHeader the new CramHeader container a new SAM file header
     * @return true if successfully replaced the header, false otherwise
     * @throws IOException as per java IO contract
     */
    public static boolean replaceCramHeader(final File file, final CramHeader newHeader) throws IOException {

        final CountingInputStream cis = new CountingInputStream(new FileInputStream(file));

        final CramHeader header = readFormatDefinition(cis);
        final Container c = ContainerIO.readContainerHeader(header.getVersion().major, cis);
        final long pos = cis.getCount();
        cis.close();

        final Block block = Block.buildNewFileHeaderBlock(toByteArray(newHeader.getSamFileHeader()));
        final ExposedByteArrayOutputStream baos = new ExposedByteArrayOutputStream();
        block.write(newHeader.getVersion().major, baos);
        if (baos.size() > c.containerByteSize) {
            log.error("Failed to replace CRAM header because the new header does not fit.");
            return false;
        }
        final RandomAccessFile raf = new RandomAccessFile(file, "rw");
        raf.seek(pos);
        raf.write(baos.getBuffer(), 0, baos.size());
        raf.close();
        return true;
    }
}
