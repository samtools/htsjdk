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
package htsjdk.samtools.cram.build;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMTextHeaderCodec;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.CRAMVersion;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A collection of methods to read and write special values to/from CRAM files.
 */
public final class CramIO {

    /**
     * @deprecated since June 2019 Use {@link FileExtensions#CRAM} instead.
     */
    @Deprecated
    public static final String CRAM_FILE_EXTENSION = FileExtensions.CRAM;
    /**
     * The 'zero-B' EOF marker as per CRAM specs v2.1. This is basically a serialized empty CRAM container with sequence id set to some
     * number to spell out 'EOF' in hex.
     */
    public static final byte[] ZERO_B_EOF_MARKER = new byte[] {
            0x0b, 0x00, 0x00, 0x00, (byte) 0xff, (byte) 0xff,(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xe0,
            0x45, 0x4f, 0x46, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x01, 0x00, 0x06, 0x06, 0x01, 0x00, 0x01,
            0x00, 0x01, 0x00};
    /**
     * The zero-F EOF marker as per CRAM specs v3.0. This is basically a serialized empty CRAM container with sequence id set to some number
     * to spell out 'EOF' in hex.
     */
    public static final byte[] ZERO_F_EOF_MARKER = new byte[] {
            0x0f, 0x00, 0x00, 0x00, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x0f, (byte) 0xe0, 0x45,
            0x4f, 0x46, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x05, (byte) 0xbd, (byte) 0xd9, 0x4f, 0x00, 0x01, 0x00,
            0x06, 0x06, 0x01, 0x00, 0x01, 0x00, 0x01, 0x00, (byte) 0xee, 0x63, 0x01, 0x4b };

    public static final int EOF_ALIGNMENT_START = 4542278;
    public static final int EOF_BLOCK_SIZE_V3 = 15; // defined by CRAM spec
    public static final int EOF_BLOCK_SIZE_V2 = 11; // defined by CRAM spec
    public static final int EOF_ALIGNMENT_SPAN = 0;

    /**
     * Write an end-of-file marker to the {@link OutputStream}. The specific EOF marker is chosen based on
     * the CRAM version. On read, this is interpreted as a special container sentinel indicating no more containers.
     *
     * The treatment of these EOF markers is asymmetric in that on read, the EOF marker is read in as a special
     * container with sentinel values indicating it is an EOF container (as defined by the spec).
     *
     * @param cramVersion      the CRAM version to assume
     * @param outputStream the stream to write to
     * @return the number of bytes written out
     */
    public static long writeCramEOF(final CRAMVersion cramVersion, final OutputStream outputStream) {
        try {
            if (cramVersion.compatibleWith(CramVersions.CRAM_v3)) {
                outputStream.write(ZERO_F_EOF_MARKER);
                return ZERO_F_EOF_MARKER.length;
            }

            if (cramVersion.compatibleWith(CramVersions.CRAM_v2_1)) {
                outputStream.write(ZERO_B_EOF_MARKER);
                return ZERO_B_EOF_MARKER.length;
            }
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }

        throw new IllegalArgumentException(String.format("Unrecognized CRAM version %s", cramVersion));
    }

    /**
     * Writes CRAM header into the specified {@link OutputStream}.
     *
     * @param cramHeader the {@link CramHeader} object to write
     * @param outputStream the output stream to write to
     * @return the number of bytes written out
     */
    public static long writeCramHeader(final CramHeader cramHeader, final OutputStream outputStream) {
        try {
            outputStream.write(CramHeader.MAGIC);
            outputStream.write(cramHeader.getCRAMVersion().getMajor());
            outputStream.write(cramHeader.getCRAMVersion().getMinor());
            outputStream.write(cramHeader.getId());
            for (int i = cramHeader.getId().length; i < CramHeader.CRAM_ID_LENGTH; i++) {
                outputStream.write(0);
            }
            return CramHeader.CRAM_HEADER_LENGTH;
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    /**
     * Read CRAM header from the given {@link InputStream}.
     *
     * @param inputStream input stream to read from
     * @return complete {@link CramHeader} object
     */
    public static CramHeader readCramHeader(final InputStream inputStream) {
        try {
            for (final byte magicByte : CramHeader.MAGIC) {
                if (magicByte != inputStream.read()) {
                    throw new RuntimeException("Input does not have a valid CRAM header.");
                }
            }

            final CRAMVersion cramVersion = new CRAMVersion(inputStream.read(), inputStream.read());
            if (!CramVersions.isSupportedVersion(cramVersion)) {
                throw new RuntimeException(String.format("CRAM version %s is not supported", cramVersion));
            }

            final CramHeader header = new CramHeader(cramVersion, null);
            final DataInputStream dataInputStream = new DataInputStream(inputStream);
            dataInputStream.readFully(header.getId());

            return header;
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    public static byte[] samHeaderToByteArray(final SAMFileHeader samFileHeader) {
        try (final ByteArrayOutputStream headerBodyOS = new ByteArrayOutputStream()) {
            try (final OutputStreamWriter outStreamWriter = new OutputStreamWriter(headerBodyOS)) {
                new SAMTextHeaderCodec().encode(outStreamWriter, samFileHeader);
            }
            final ByteBuffer buf = ByteBuffer.allocate(4);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            buf.putInt(headerBodyOS.size());
            buf.flip();
            final byte[] bytes = new byte[buf.limit()];
            buf.get(bytes);

            try (final ByteArrayOutputStream headerOS = new ByteArrayOutputStream()) {
                headerOS.write(bytes);
                headerOS.write(headerBodyOS.toByteArray(), 0, headerBodyOS.size());
                return headerOS.toByteArray();
            }
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

}
