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
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package htsjdk.samtools.cram.encoding;

import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;

import java.io.IOException;


class NullCodec<T> extends AbstractBitCodec<T> {
    private final T defaultValue = null;

    public NullCodec() {
    }

    @Override
    public T read(final BitInputStream bitInputStream) throws IOException {
        return defaultValue;
    }

    @Override
    public T read(final BitInputStream bitInputStream, final int length) throws IOException {
        return defaultValue;
    }

    @Override
    public long write(final BitOutputStream bitOutputStream, final T object) throws IOException {
        return 0;
    }

    @Override
    public long numberOfBits(final T object) {
        return 0;
    }

}
