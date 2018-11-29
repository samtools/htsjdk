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

import htsjdk.samtools.cram.io.ITF8;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

class ExternalIntegerCodec extends ExternalCodec<Integer> {
    public ExternalIntegerCodec(final ByteArrayInputStream inputStream, final ByteArrayOutputStream outputStream) {
        super(inputStream, outputStream);
    }

    @Override
    public Integer read() {
        return ITF8.readUnsignedITF8(inputStream);
    }

    @Override
    public void write(final Integer value) {
        ITF8.writeUnsignedITF8(value, outputStream);
    }

    @Override
    public Integer read(final int length) {
        throw new RuntimeException("Not implemented.");
    }
}
