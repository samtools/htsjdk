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

/**
 * A starting object when dealing with CRAM files. A {@link CramHeader} holds 2 things: 1. File format definition, including content id and
 * version information 2. SAM file header
 */
public final class CramHeader {
    public static final byte[] MAGIC = "CRAM".getBytes();

    private Version version;
    private final byte[] id = new byte[20];

    {
        Arrays.fill(id, (byte) 0);
    }

    private SAMFileHeader samFileHeader;

    /**
     * Create a new {@link CramHeader} empty object.
     */
    private CramHeader() {
    }

    /**
     * Create a new {@link CramHeader} object with the specified version, id and SAM file header.
     * The id field by default is guaranteed to be byte[20].
     *
     * @param version       the CRAM version to assume
     * @param id            an identifier of the content associated with this header
     * @param samFileHeader the SAM file header
     */
    public CramHeader(final Version version, final String id, final SAMFileHeader samFileHeader) {
        this.version = version;

        if (id != null) System.arraycopy(id.getBytes(), 0, this.id, 0, Math.min(id.length(), this.id.length));
        this.samFileHeader = samFileHeader;
    }

    /**
     * Set the id of the header. A typical use is for example file name to be used when streaming or a checksum of the data contained in the
     * file.
     *
     * @param stringID a new id; only first 20 bytes from byte representation of java {@link String} will be used.
     */
    public void setID(final String stringID) {
        System.arraycopy(stringID.getBytes(), 0, this.id, 0, Math.min(this.id.length, stringID.length()));
    }

    /**
     * Copy the CRAM header into a new {@link CramHeader} object.
     * @return a complete copy of the header
     */
    @SuppressWarnings("CloneDoesntCallSuperClone")
    @Override
    public CramHeader clone() {
        final CramHeader clone = new CramHeader();
        clone.version = version;
        System.arraycopy(id, 0, clone.id, 0, id.length);
        clone.samFileHeader = getSamFileHeader().clone();

        return clone;
    }


    /**
     * Checks if content of a header is the same as this one.
     * @param obj another header to compare to
     * @return true if versions, ids and SAM file header are exactly the same, false otherwise
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof CramHeader)) return false;

        final CramHeader header = (CramHeader) obj;

        if (getVersion().major != header.getVersion().major) return false;
        //noinspection SimplifiableIfStatement
        if (getVersion().minor != header.getVersion().minor) return false;
        return Arrays.equals(id, header.id) && getSamFileHeader().equals(header.getSamFileHeader());
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

    public void setVersion(final Version version) { this.version = version; }
}
