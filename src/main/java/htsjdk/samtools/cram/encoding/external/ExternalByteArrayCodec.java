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

import htsjdk.samtools.cram.io.InputStreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ExternalByteArrayCodec extends ExternalCodec<byte[]> {
    public ExternalByteArrayCodec(final InputStream inputStream, final OutputStream outputStream) {
        super(inputStream, outputStream);
    }

    @Override
    public byte[] read(final int length) {
        try {
            return InputStreamUtils.readFully(inputStream, length);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void write(final byte[] object) {
        try {
            outputStream.write(object);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] read() {
        throw new RuntimeException("Cannot read byte array of unknown length.");
    }

}
