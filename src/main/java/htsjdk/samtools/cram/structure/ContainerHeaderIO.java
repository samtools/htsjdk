/**
 * ****************************************************************************
 * Copyright 2013 EMBL-EBI
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.io.*;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ContainerHeaderIO {
    /**
     * Reads container header only from an {@link InputStream}.
     *
     * @param major the CRAM version to assume
     * @param inputStream the input stream to read from
     * @param containerByteOffset the byte offset from the start of the stream
     * @return a new {@link Container} object with container header values filled out but empty body (no slices and blocks).
     */
    public static Container readContainerHeader(final int major,
                                                final InputStream inputStream,
                                                final long containerByteOffset) {
        final byte[] peek = new byte[4];
        try {
            int character = inputStream.read();
            if (character == -1) {
                final int majorVersionForEOF = 2;
                final byte[] eofMarker = major >= 3 ? CramIO.ZERO_F_EOF_MARKER : CramIO.ZERO_B_EOF_MARKER;

                try (final ByteArrayInputStream eofBAIS = new ByteArrayInputStream(eofMarker)) {
                    return readContainerHeader(majorVersionForEOF, eofBAIS, containerByteOffset);
                }
            }
            peek[0] = (byte) character;
            for (int i = 1; i < peek.length; i++) {
                character = inputStream.read();
                if (character == -1)
                    throw new RuntimeException("Incomplete or broken stream.");
                peek[i] = (byte) character;
            }
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }

        final int containerByteSize = CramInt.readInt32(peek);
        final ReferenceContext refContext = new ReferenceContext(ITF8.readUnsignedITF8(inputStream));
        final Container container = new Container(refContext);
        container.containerByteSize = containerByteSize;

        container.alignmentStart = ITF8.readUnsignedITF8(inputStream);
        container.alignmentSpan = ITF8.readUnsignedITF8(inputStream);
        container.nofRecords = ITF8.readUnsignedITF8(inputStream);
        container.globalRecordCounter = LTF8.readUnsignedLTF8(inputStream);
        container.bases = LTF8.readUnsignedLTF8(inputStream);
        container.blockCount = ITF8.readUnsignedITF8(inputStream);
        container.landmarks = CramIntArray.array(inputStream);
        if (major >= 3)
            container.checksum = CramInt.readInt32(inputStream);

        container.byteOffset = containerByteOffset;
        return container;
    }

    // convenience methods for SeekableStream and CountingInputStream
    // TODO: merge these two classes?

    /**
     * Reads container header only from a {@link SeekableStream}.
     *
     * @param major the CRAM version to assume
     * @param seekableStream the seekable input stream to read from
     * @return a new {@link Container} object with container header values filled out but empty body (no slices and blocks).
     */
    public static Container readContainerHeader(final int major, final SeekableStream seekableStream) {
        try {
            final long containerByteOffset = seekableStream.position();
            return readContainerHeader(major, seekableStream, containerByteOffset);
        }
        catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    /**
     * Reads container header only from a {@link CountingInputStream}.
     *
     * @param major the CRAM version to assume
     * @param countingStream the counting input stream to read from
     * @return a new {@link Container} object with container header values filled out but empty body (no slices and blocks).
     */
    public static Container readContainerHeader(final int major, final CountingInputStream countingStream) {
        final long containerByteOffset = countingStream.getCount();
        return readContainerHeader(major, countingStream, containerByteOffset);
    }

    /**
     * Write CRAM {@link Container} out into the given {@link OutputStream}.
     * @param major CRAM major version
     * @param container container to be written
     * @param outputStream the output stream to write the container to
     * @return number of bytes written out to the output stream
     */
    public static int writeContainerHeader(final int major, final Container container, final OutputStream outputStream) {
        final CRC32OutputStream crc32OutputStream = new CRC32OutputStream(outputStream);

        int length = (CramInt.writeInt32(container.containerByteSize, crc32OutputStream) + 7) / 8;
        length += (ITF8.writeUnsignedITF8(container.getReferenceContext().getSerializableId(), crc32OutputStream) + 7) / 8;
        length += (ITF8.writeUnsignedITF8(container.alignmentStart, crc32OutputStream) + 7) / 8;
        length += (ITF8.writeUnsignedITF8(container.alignmentSpan, crc32OutputStream) + 7) / 8;
        length += (ITF8.writeUnsignedITF8(container.nofRecords, crc32OutputStream) + 7) / 8;
        length += (LTF8.writeUnsignedLTF8(container.globalRecordCounter, crc32OutputStream) + 7) / 8;
        length += (LTF8.writeUnsignedLTF8(container.bases, crc32OutputStream) + 7) / 8;
        length += (ITF8.writeUnsignedITF8(container.blockCount, crc32OutputStream) + 7) / 8;
        length += (CramIntArray.write(container.landmarks, crc32OutputStream) + 7) / 8;

        if (major >= 3) {
            try {
                outputStream.write(crc32OutputStream.getCrc32_LittleEndian());
            } catch (final IOException e) {
                throw new RuntimeIOException(e);
            }
            length += 4 ;
        }

        return length;
    }
}
