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
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.SAMFileHeader;

import java.util.Arrays;

public final class CramHeader {

    public static final byte[] magick = "CRAM".getBytes();
    private byte majorVersion;
    private byte minorVersion;
    public final byte[] id = new byte[20];

    {
        Arrays.fill(id, (byte) 0);
    }

    private SAMFileHeader samFileHeader;

    public CramHeader() {
    }

    public CramHeader(int majorVersion, int minorVersion, String id,
                      SAMFileHeader samFileHeader) {
        this.majorVersion = (byte) majorVersion;
        this.minorVersion = (byte) minorVersion;
        if (id != null)
            System.arraycopy(id.getBytes(), 0, this.id, 0,
                    Math.min(id.length(), this.id.length));
        this.samFileHeader = samFileHeader;
    }

    public void setID(String stringID) {
        System.arraycopy(stringID.getBytes(), 0, this.id, 0,
                Math.min(this.id.length, stringID.length()));
    }

    @Override
    public CramHeader clone() {
        CramHeader clone = new CramHeader();
        clone.majorVersion = majorVersion;
        clone.minorVersion = minorVersion;
        System.arraycopy(id, 0, clone.id, 0, id.length);
        clone.samFileHeader = getSamFileHeader().clone();

        return clone;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (!(obj instanceof CramHeader))
            return false;

        CramHeader h = (CramHeader) obj;

        if (getMajorVersion() != h.getMajorVersion())
            return false;
        if (getMinorVersion() != h.getMinorVersion())
            return false;
        if (!Arrays.equals(id, h.id))
            return false;
        return getSamFileHeader().equals(h.getSamFileHeader());
    }

    public byte getMajorVersion() {
        return majorVersion;
    }

    public void setMajorVersion(byte majorVersion) {
        this.majorVersion = majorVersion;
    }

    public byte getMinorVersion() {
        return minorVersion;
    }

    public void setMinorVersion(byte minorVersion) {
        this.minorVersion = minorVersion;
    }

    public SAMFileHeader getSamFileHeader() {
        return samFileHeader;
    }

    public void setSamFileHeader(SAMFileHeader samFileHeader) {
        this.samFileHeader = samFileHeader;
    }

    public byte[] getId() {
        return id;
    }
}
