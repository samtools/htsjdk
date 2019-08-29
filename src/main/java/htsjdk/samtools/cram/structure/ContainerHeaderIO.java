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
     * @return a new {@link ContainerHeader} object with container header values filled out but empty body (no slices and blocks).
     */
    public static ContainerHeader readContainerHeader(final int major, final InputStream inputStream) {
        final byte[] peek = new byte[4];
        try {
            int character = inputStream.read();
            if (character == -1) {
                // Apparently this is synthesizing an EOF container for v2.1 if one isn't already present
                // in the input stream. Not sure why thats necessary ?
                final int majorVersionForEOF = 2;
                final byte[] eofMarker = major >= 3 ? CramIO.ZERO_F_EOF_MARKER : CramIO.ZERO_B_EOF_MARKER;

                try (final ByteArrayInputStream eofBAIS = new ByteArrayInputStream(eofMarker)) {
                    return readContainerHeader(majorVersionForEOF, eofBAIS);
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
        final int alignmentStart = ITF8.readUnsignedITF8(inputStream);
        final int alignmentSpan = ITF8.readUnsignedITF8(inputStream);
        final int nofRecords = ITF8.readUnsignedITF8(inputStream);
        final long globalRecordCounter = LTF8.readUnsignedLTF8(inputStream);
        final long bases = LTF8.readUnsignedLTF8(inputStream);
        final int blockCount = ITF8.readUnsignedITF8(inputStream);
        final int landmarks[] = CramIntArray.array(inputStream);
        final int checksum = major >= 3 ? CramInt.readInt32(inputStream) : 0;

        return new ContainerHeader(
                containerByteSize,
                refContext,
                alignmentStart,
                alignmentSpan,
                nofRecords,
                globalRecordCounter,
                bases,
                blockCount,
                landmarks,
                checksum);
    }

    /**
     * Write CRAM {@link Container} (header only) out into the given {@link OutputStream}.
     * @param major CRAM major version
     * @param containerHeader containerHeader to be written
     * @param outputStream the output stream to write the container header to
     * @return number of bytes written out to the output stream
     */
    //TODO: move this into containerHeader
    public static int writeContainerHeader(final int major, final ContainerHeader containerHeader, final OutputStream outputStream) {
        final CRC32OutputStream crc32OutputStream = new CRC32OutputStream(outputStream);

        int length = (CramInt.writeInt32(containerHeader.getContainerBlocksByteSize(), crc32OutputStream) + 7) / 8;
        length += (ITF8.writeUnsignedITF8(containerHeader.getReferenceContext().getSerializableId(), crc32OutputStream) + 7) / 8;
        length += (ITF8.writeUnsignedITF8(containerHeader.getAlignmentStart(), crc32OutputStream) + 7) / 8;
        length += (ITF8.writeUnsignedITF8(containerHeader.getAlignmentSpan(), crc32OutputStream) + 7) / 8;
        length += (ITF8.writeUnsignedITF8(containerHeader.getNofRecords(), crc32OutputStream) + 7) / 8;
        length += (LTF8.writeUnsignedLTF8(containerHeader.getGlobalRecordCounter(), crc32OutputStream) + 7) / 8;
        length += (LTF8.writeUnsignedLTF8(containerHeader.getBases(), crc32OutputStream) + 7) / 8;
        length += (ITF8.writeUnsignedITF8(containerHeader.getBlockCount(), crc32OutputStream) + 7) / 8;
        length += (CramIntArray.write(containerHeader.getLandmarks(), crc32OutputStream) + 7) / 8;

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
