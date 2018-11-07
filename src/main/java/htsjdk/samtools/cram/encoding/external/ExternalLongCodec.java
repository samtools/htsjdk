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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class ExternalLongCodec extends ExternalCodec<Long> {
    public ExternalLongCodec(final InputStream inputStream, final OutputStream outputStream) {
        super(inputStream, outputStream);
    }

    @Override
    public Long read() {
        long result = 0;
        try {
            for (int i = 0; i < 8; i++) {
                result <<= 8;
                result |= inputStream.read();
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void write(Long value) {
        try {
            for (int i = 0; i < 8; i++) {
                outputStream.write((int) (value & 0xFF));
                value >>>= 8;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Long read(final int length) {
        throw new RuntimeException("Not implemented.");
    }
}
