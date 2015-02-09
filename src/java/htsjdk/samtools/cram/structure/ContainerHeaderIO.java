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
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.io.CRC32_OutputStream;
import htsjdk.samtools.cram.io.CramArray;
import htsjdk.samtools.cram.io.CramInt;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.io.LTF8;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class ContainerHeaderIO {

    public boolean readContainerHeader(final Container c, final InputStream is)
            throws IOException {
        return readContainerHeader(2, c, is);
    }

    public boolean readContainerHeader(final int major, final Container c, final InputStream is)
            throws IOException {
        final byte[] peek = new byte[4];
        int ch = is.read();
        if (ch == -1)
            return false;

        peek[0] = (byte) ch;
        for (int i = 1; i < peek.length; i++) {
            ch = is.read();
            if (ch == -1)
                throw new RuntimeException("Incomplete or broken stream.");
            peek[i] = (byte) ch;
        }

        c.containerByteSize = CramInt.int32(peek);
        c.sequenceId = ITF8.readUnsignedITF8(is);
        c.alignmentStart = ITF8.readUnsignedITF8(is);
        c.alignmentSpan = ITF8.readUnsignedITF8(is);
        c.nofRecords = ITF8.readUnsignedITF8(is);
        c.globalRecordCounter = LTF8.readUnsignedLTF8(is);
        c.bases = LTF8.readUnsignedLTF8(is);
        c.blockCount = ITF8.readUnsignedITF8(is);
        c.landmarks = CramArray.array(is);
        if (major >= 3)
            c.checksum = CramInt.int32(is);

        return true;
    }

    public int writeContainerHeader(final int major, final Container c, final OutputStream os)
            throws IOException {
        final CRC32_OutputStream cos = new CRC32_OutputStream(os);

        int len = CramInt.writeInt32(c.containerByteSize, cos);
        len += ITF8.writeUnsignedITF8(c.sequenceId, cos);
        len += ITF8.writeUnsignedITF8(c.alignmentStart, cos);
        len += ITF8.writeUnsignedITF8(c.alignmentSpan, cos);
        len += ITF8.writeUnsignedITF8(c.nofRecords, cos);
        len += LTF8.writeUnsignedLTF8(c.globalRecordCounter, cos);
        len += LTF8.writeUnsignedLTF8(c.bases, cos);
        len += ITF8.writeUnsignedITF8(c.blockCount, cos);
        len += CramArray.write(c.landmarks, cos);

        if (major >= 3) {
            os.write(cos.getCrc32_LittleEndian());
            len += 4;
        }

        return len;
    }
}
