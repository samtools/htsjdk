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
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.common.CRAMVersion;

import java.util.Arrays;
import java.util.Objects;

/**
 * A CRAM file header, including the file format definition (including CRAM version and content id),
 * and the SAMFileHeader.
 */
public final class CramHeader {
    public static final byte[] MAGIC = "CRAM".getBytes();

    public static final int CRAM_MAGIC_LENGTH = MAGIC.length;
    public static final int CRAM_ID_LENGTH = 20;
    public static final int CRAM_VERSION_LENGTH = 2;
    public static final int CRAM_HEADER_LENGTH = CRAM_MAGIC_LENGTH + CRAM_VERSION_LENGTH + CRAM_ID_LENGTH;

    private CRAMVersion cramVersion;
    private final byte[] id;

    /**
     * Create a new {@link CramHeader} object with the specified version and id.
     * The id field by default is guaranteed to be byte[20].
     *
     * @param cramVersion       the CRAM version to assume
     * @param id            an identifier of the content associated with this header
     */
    public CramHeader(final CRAMVersion cramVersion, final String id) {
        this.cramVersion = cramVersion;
        this.id = new byte[CRAM_ID_LENGTH];
        Arrays.fill(this.id, (byte) 0);
        if (id != null) {
            System.arraycopy(id.getBytes(),0, this.id, 0, Math.min(id.length(), this.id.length));
        }
    }

    public byte[] getId() {
        return id;
    }

    public CRAMVersion getCRAMVersion() {
        return cramVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final CramHeader that = (CramHeader) o;
        return Objects.equals(cramVersion, that.cramVersion) &&
                Arrays.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        int result = cramVersion.hashCode();
        result = 31 * result + Arrays.hashCode(id);
        return result;
    }
}
