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

import htsjdk.samtools.cram.common.NullOutputStream;
import htsjdk.samtools.cram.io.ByteBufferUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ContainerHeaderIO {

    public boolean readContainerHeader(Container c, InputStream is) throws IOException {
        byte[] peek = new byte[4];
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

        c.containerByteSize = ByteBufferUtils.int32(peek);
        c.sequenceId = ByteBufferUtils.readUnsignedITF8(is);
        c.alignmentStart = ByteBufferUtils.readUnsignedITF8(is);
        c.alignmentSpan = ByteBufferUtils.readUnsignedITF8(is);
        c.nofRecords = ByteBufferUtils.readUnsignedITF8(is);
        c.globalRecordCounter = ByteBufferUtils.readUnsignedLTF8(is);
        c.bases = ByteBufferUtils.readUnsignedLTF8(is);
        c.blockCount = ByteBufferUtils.readUnsignedITF8(is);
        c.landmarks = ByteBufferUtils.array(is);

        return true;
    }

    public int writeContainerHeader(Container c, OutputStream os) throws IOException {
        int len = ByteBufferUtils.writeInt32(c.containerByteSize, os);
        len += ByteBufferUtils.writeUnsignedITF8(c.sequenceId, os);
        len += ByteBufferUtils.writeUnsignedITF8(c.alignmentStart, os);
        len += ByteBufferUtils.writeUnsignedITF8(c.alignmentSpan, os);
        len += ByteBufferUtils.writeUnsignedITF8(c.nofRecords, os);
        len += ByteBufferUtils.writeUnsignedLTF8(c.globalRecordCounter, os);
        len += ByteBufferUtils.writeUnsignedLTF8(c.bases, os);
        len += ByteBufferUtils.writeUnsignedITF8(c.blockCount, os);
        len += ByteBufferUtils.write(c.landmarks, os);

        return len;
    }

    public int sizeOfContainerHeader(Container c) throws IOException {
        NullOutputStream nos = new NullOutputStream();
        return writeContainerHeader(c, nos);
    }
}
