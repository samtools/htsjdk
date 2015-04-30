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
import htsjdk.samtools.cram.io.ITF8;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


class ExternalIntegerCodec extends AbstractBitCodec<Integer> {
    private final OutputStream os;
    private final InputStream is;
    private final OutputStream nullOS = new OutputStream() {

        @Override
        public void write(@SuppressWarnings("NullableProblems") final byte[] b) throws IOException {
        }

        @Override
        public void write(final int b) throws IOException {
        }

        @Override
        public void write(@SuppressWarnings("NullableProblems") final byte[] b, final int off, final int len) throws IOException {
        }
    };

    public ExternalIntegerCodec(final OutputStream os, final InputStream is) {
        this.os = os;
        this.is = is;
    }

    @Override
    public Integer read(final BitInputStream bis) throws IOException {
        return ITF8.readUnsignedITF8(is);
    }

    @Override
    public long write(final BitOutputStream bos, final Integer value) throws IOException {
        return ITF8.writeUnsignedITF8(value, os);
    }

    @Override
    public long numberOfBits(final Integer value) {
        try {
            return ITF8.writeUnsignedITF8(value, nullOS);
        } catch (final IOException e) {
            // this should never happened but still:
            throw new RuntimeException(e);
        }
    }

    @Override
    public Integer read(final BitInputStream bis, final int len) throws IOException {
        throw new RuntimeException("Not implemented.");
    }
}
