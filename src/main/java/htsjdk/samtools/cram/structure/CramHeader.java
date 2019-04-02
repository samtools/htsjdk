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
import htsjdk.samtools.util.RuntimeIOException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 * A starting object when dealing with CRAM files. A {@link CramHeader} holds 2 things:
 * 1. File format definition, including content id and version information
 * 2. SAM file header
 */
public final class CramHeader {
    public static final byte[] MAGIC = "CRAM".getBytes();
    public static final int MAX_ID_LENGTH = 20;     // in bytes; from CRAM spec

    private final Version version;
    private final byte[] id;
    private final SAMFileHeader samFileHeader;

    /**
     * Create a new {@link CramHeader} object with the specified version, id and SAM file header.
     * The id field will be truncated to 20 bytes.
     *
     * @param version       the CRAM version to assume
     * @param stringID      an identifier of the content associated with this header
     * @param samFileHeader the SAM file header
     */
    public CramHeader(final Version version, final String stringID, final SAMFileHeader samFileHeader) {
        this.version = version;
        this.samFileHeader = samFileHeader;

        if (stringID == null) {
            this.id = new byte[MAX_ID_LENGTH];
        } else {
            if (stringID.length() > MAX_ID_LENGTH) {
                this.id = stringID.substring(0, MAX_ID_LENGTH).getBytes();
            } else {
                this.id = stringID.getBytes();
            }
        }
    }

    /**
     * Get the {@link SAMFileHeader} object associated with this CRAM file header.
     * @return the SAM file header
     */
    public SAMFileHeader getSamFileHeader() {
        return samFileHeader;
    }

    byte[] getId() {
        return id;
    }

    public String getIdString() {
        return new String(getId());
    }

    /**
     * Write the header's ID to the given {@link OutputStream},
     * zero-padding to MAX_ID_LENGTH bytes if necessary.
     * @param outputStream the stream to write to
     */
    public void writeId(final OutputStream outputStream) {
        try {
            outputStream.write(getId());
            for (int i = getId().length; i < MAX_ID_LENGTH; i++)
                outputStream.write(0);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
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
