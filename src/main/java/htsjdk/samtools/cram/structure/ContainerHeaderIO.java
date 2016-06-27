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

import htsjdk.samtools.cram.io.CRC32OutputStream;
import htsjdk.samtools.cram.io.CramArray;
import htsjdk.samtools.cram.io.CramInt;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.io.LTF8;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class ContainerHeaderIO {

    public boolean readContainerHeader(final Container container, final InputStream inputStream)
            throws IOException {
        return readContainerHeader(2, container, inputStream);
    }

    public boolean readContainerHeader(final int major, final Container container, final InputStream inputStream)
            throws IOException {
        final byte[] peek = new byte[4];
        int character = inputStream.read();
        if (character == -1)
            return false;

        peek[0] = (byte) character;
        for (int i = 1; i < peek.length; i++) {
            character = inputStream.read();
            if (character == -1)
                throw new RuntimeException("Incomplete or broken stream.");
            peek[i] = (byte) character;
        }

        container.containerByteSize = CramInt.int32(peek);
        container.sequenceId = ITF8.readUnsignedITF8(inputStream);
        container.alignmentStart = ITF8.readUnsignedITF8(inputStream);
        container.alignmentSpan = ITF8.readUnsignedITF8(inputStream);
        container.nofRecords = ITF8.readUnsignedITF8(inputStream);
        container.globalRecordCounter = LTF8.readUnsignedLTF8(inputStream);
        container.bases = LTF8.readUnsignedLTF8(inputStream);
        container.blockCount = ITF8.readUnsignedITF8(inputStream);
        container.landmarks = CramArray.array(inputStream);
        if (major >= 3)
            container.checksum = CramInt.int32(inputStream);

        return true;
    }

    /**
     * Write CRAM {@link Container} out into the given {@link OutputStream}.
     * @param major CRAM major version
     * @param container container to be written
     * @param outputStream the output stream to write the container to
     * @return number of bytes written out to the output stream
     * @throws IOException as per java IO contract
     */
    public int writeContainerHeader(final int major, final Container container, final OutputStream outputStream)
            throws IOException {
        final CRC32OutputStream crc32OutputStream = new CRC32OutputStream(outputStream);

        int length = (CramInt.writeInt32(container.containerByteSize, crc32OutputStream) + 7) / 8;
        length += (ITF8.writeUnsignedITF8(container.sequenceId, crc32OutputStream) + 7) / 8;
        length += (ITF8.writeUnsignedITF8(container.alignmentStart, crc32OutputStream) + 7) / 8;
        length += (ITF8.writeUnsignedITF8(container.alignmentSpan, crc32OutputStream) + 7) / 8;
        length += (ITF8.writeUnsignedITF8(container.nofRecords, crc32OutputStream) + 7) / 8;
        length += (LTF8.writeUnsignedLTF8(container.globalRecordCounter, crc32OutputStream) + 7) / 8;
        length += (LTF8.writeUnsignedLTF8(container.bases, crc32OutputStream) + 7) / 8;
        length += (ITF8.writeUnsignedITF8(container.blockCount, crc32OutputStream) + 7) / 8;
        length += (CramArray.write(container.landmarks, crc32OutputStream) + 7) / 8;

        if (major >= 3) {
            outputStream.write(crc32OutputStream.getCrc32_LittleEndian());
            length += 4 ;
        }

        return length;
    }
}
