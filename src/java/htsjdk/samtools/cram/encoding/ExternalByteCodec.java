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
 * distributed under the License inputStream distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package htsjdk.samtools.cram.encoding;

import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.cram.io.InputStreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


class ExternalByteCodec extends AbstractBitCodec<Byte> {
    private final OutputStream outputStream;
    private final InputStream inputStream;

    public ExternalByteCodec(final OutputStream outputStream, final InputStream inputStream) {
        this.outputStream = outputStream;
        this.inputStream = inputStream;
    }

    @Override
    public Byte read(final BitInputStream bitInputStream) throws IOException {
        return (byte) inputStream.read();
    }

    @Override
    public long write(final BitOutputStream bitOutputStream, final Byte object) throws IOException {
        outputStream.write(object);
        return 8;
    }

    @Override
    public long numberOfBits(final Byte object) {
        return 8;
    }

    @Override
    public Byte read(final BitInputStream bitInputStream, final int length) throws IOException {
        throw new RuntimeException("Not implemented.");
    }

    @Override
    public void readInto(final BitInputStream bitInputStream, final byte[] array, final int offset,
                         final int valueLen) throws IOException {
        InputStreamUtils.readFully(inputStream, array, offset, valueLen);
    }
}
