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
import htsjdk.samtools.cram.io.ITF8;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


class ExternalIntegerCodec extends AbstractBitCodec<Integer> {
    private final OutputStream outputStream;
    private final InputStream inputStream;
    private final OutputStream nullOutputStream = new OutputStream() {

        @Override
        public void write(@SuppressWarnings("NullableProblems") final byte[] b) throws IOException {
        }

        @Override
        public void write(final int b) throws IOException {
        }

        @Override
        public void write(@SuppressWarnings("NullableProblems") final byte[] b, final int off, final int length) throws IOException {
        }
    };

    public ExternalIntegerCodec(final OutputStream outputStream, final InputStream inputStream) {
        this.outputStream = outputStream;
        this.inputStream = inputStream;
    }

    @Override
    public Integer read(final BitInputStream bitInputStream) throws IOException {
        return ITF8.readUnsignedITF8(inputStream);
    }

    @Override
    public long write(final BitOutputStream bitOutputStream, final Integer value) throws IOException {
        return ITF8.writeUnsignedITF8(value, outputStream);
    }

    @Override
    public long numberOfBits(final Integer value) {
        try {
            return ITF8.writeUnsignedITF8(value, nullOutputStream);
        } catch (final IOException e) {
            // this should never happened but still:
            throw new RuntimeException(e);
        }
    }

    @Override
    public Integer read(final BitInputStream bitInputStream, final int length) throws IOException {
        throw new RuntimeException("Not implemented.");
    }
}
