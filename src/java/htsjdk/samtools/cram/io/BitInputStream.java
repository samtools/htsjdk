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
package htsjdk.samtools.cram.io;

import java.io.IOException;

public interface BitInputStream {

    public boolean readBit() throws IOException;

    public int readBits(int len) throws IOException;

    public long readLongBits(int len) throws IOException;

    public boolean endOfStream() throws IOException;

    public boolean putBack(long b, int numBits);

    public void alignToByte() throws IOException;

    public int readAlignedBytes(byte[] array) throws IOException;

    public byte readByte() throws IOException;

    public boolean ensureMarker(long marker, int nofBits) throws IOException;
}
