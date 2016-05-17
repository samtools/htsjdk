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

public abstract class AbstractBitCodec<T> implements BitCodec<T> {

    @Override
    public abstract T read(BitInputStream bitInputStream) throws IOException;

    @Override
    public abstract T read(BitInputStream bitInputStream, int valueLen) throws IOException;

    @Override
    public void readInto(final BitInputStream bitInputStream, final byte[] array, final int offset,
                         final int valueLen) throws IOException {
        throw new RuntimeException("Not implemented.");
    }

    @Override
    public void skip(final BitInputStream bitInputStream) throws IOException {
        read(bitInputStream);
    }

    @Override
    public void skip(final BitInputStream bitInputStream, final int length) throws IOException {
        read(bitInputStream, length);
    }

    @Override
    public abstract long write(BitOutputStream bitOutputStream, T object)
            throws IOException;

    @Override
    public abstract long numberOfBits(T object);

}
