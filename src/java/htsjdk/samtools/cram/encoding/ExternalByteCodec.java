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
package htsjdk.samtools.cram.encoding;

import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.cram.io.BitwiseUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class ExternalByteCodec extends AbstractBitCodec<Byte> {
    private OutputStream os;
    private InputStream is;

    public ExternalByteCodec(OutputStream os, InputStream is) {
        this.os = os;
        this.is = is;
    }

    @Override
    public Byte read(BitInputStream bis) throws IOException {
        return (byte) is.read();
    }

    @Override
    public long write(BitOutputStream bos, Byte object) throws IOException {
        os.write(object);
        return 8;
    }

    @Override
    public long numberOfBits(Byte object) {
        return 8;
    }

    @Override
    public Byte read(BitInputStream bis, int len) throws IOException {
        throw new RuntimeException("Not implemented.");
    }

    @Override
    public void readInto(BitInputStream bis, byte[] array, int offset,
                         int valueLen) throws IOException {
        BitwiseUtils.readFully(is, array, offset, valueLen);
    }
}
