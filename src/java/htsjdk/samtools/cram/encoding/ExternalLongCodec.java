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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


class ExternalLongCodec extends AbstractBitCodec<Long> {
    private final OutputStream os;
    private final InputStream is;

    public ExternalLongCodec(final OutputStream os, final InputStream is) {
        this.os = os;
        this.is = is;
    }

    @Override
    public Long read(final BitInputStream bis) throws IOException {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result <<= 8;
            result |= is.read();
        }
        return result;
    }

    @Override
    public long write(final BitOutputStream bos, Long value) throws IOException {
        for (int i = 0; i < 8; i++) {
            os.write((int) (value & 0xFF));
            value >>>= 8;
        }
        return 64;
    }

    @Override
    public long numberOfBits(final Long object) {
        return 8;
    }

    @Override
    public Long read(final BitInputStream bis, final int len) throws IOException {
        throw new RuntimeException("Not implemented.");
    }
}
