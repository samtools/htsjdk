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
package htsjdk.samtools.cram.encoding.external;

import htsjdk.samtools.cram.io.LTF8;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Encode Longs using an External Data Block
 */
class ExternalLongCodec extends ExternalCodec<Long> {
    /**
     * Construct an External Codec for Longs
     *
     * @param inputStream the input bytestream to read from
     * @param outputStream the output bytestream to write to
     */
    public ExternalLongCodec(final ByteArrayInputStream inputStream,
                             final ByteArrayOutputStream outputStream) {
        super(inputStream, outputStream);
    }

    @Override
    public Long read() {
        return LTF8.readUnsignedLTF8(inputStream);
    }

    @Override
    public void write(final Long value) {
        LTF8.writeUnsignedLTF8(value, outputStream);
    }

    @Override
    public Long read(final int length) {
        throw new RuntimeException("Not implemented.");
    }
}
