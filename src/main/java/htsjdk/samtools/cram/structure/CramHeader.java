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

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.cram.common.Version;

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

    private Version version;
    private final byte[] id;
    private SAMFileHeader samFileHeader;

    /**
     * Create a new {@link CramHeader} object with the specified version and id.
     * The id field by default is guaranteed to be byte[20].
     *
     * @param version       the CRAM version to assume
     * @param id            an identifier of the content associated with this header
     */
    public CramHeader(final Version version, final String id, final SAMFileHeader samFileHeader) {
        this.version = version;
        this.id = new byte[CRAM_ID_LENGTH];
        Arrays.fill(this.id, (byte) 0);
        if (id != null) {
            System.arraycopy(id.getBytes(),0, this.id, 0, Math.min(id.length(), this.id.length));
        }
        this.samFileHeader = samFileHeader;
    }

    /**
     * Get the {@link SAMFileHeader} object associated with this CRAM file header.
     * @return the SAM file header
     */
    public SAMFileHeader getSamFileHeader() {
        return samFileHeader;
    }

    public byte[] getId() {
        return id;
    }

    public Version getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final CramHeader that = (CramHeader) o;
        return Objects.equals(version, that.version) &&
                Arrays.equals(id, that.id) &&
                Objects.equals(samFileHeader, that.samFileHeader);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(version, samFileHeader);
        result = 31 * result + Arrays.hashCode(id);
        return result;
    }
}
