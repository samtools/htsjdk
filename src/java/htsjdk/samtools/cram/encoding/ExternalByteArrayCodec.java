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

public class ExternalByteArrayCodec extends AbstractBitCodec<byte[]> {
    private OutputStream os;
    private InputStream is;

    public ExternalByteArrayCodec(OutputStream os, InputStream is) {
        this.os = os;
        this.is = is;
    }

    @Override
    public byte[] read(BitInputStream bis, int len) throws IOException {
        return BitwiseUtils.readFully(is, len);
    }

    @Override
    public void readInto(BitInputStream bis, byte[] array, int offset,
                         int valueLen) throws IOException {
        BitwiseUtils.readFully(is, array, offset, valueLen);
    }

    @Override
    public void skip(BitInputStream bis) throws IOException {
        is.skip(1);
    }

    @Override
    public void skip(BitInputStream bis, int len) throws IOException {
        is.skip(len);
    }

    @Override
    public long write(BitOutputStream bos, byte[] object) throws IOException {
        os.write(object);
        return numberOfBits(object);
    }

    @Override
    public long numberOfBits(byte[] object) {
        return object.length * 8;
    }

    @Override
    public byte[] read(BitInputStream bis) throws IOException {
        throw new RuntimeException("Cannot read byte array of unknown length.");
    }

}
