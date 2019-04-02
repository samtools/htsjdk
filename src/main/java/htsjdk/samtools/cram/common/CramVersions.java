package htsjdk.samtools.cram.common;

public class CramVersions {
    public static final Version CRAM_v2_1 = new Version(2, 1, 0);
    public static final Version CRAM_v3 = new Version(3, 0, 0);

    /**
     * The 'zero-B' EOF marker as per CRAM specs v2.1. This is basically a serialized empty CRAM container with sequence id set to some
     * number to spell out 'EOF' in hex.
     */
    private static final byte[] CRAMv2_1_EOF_MARKER = bytesFromHex("0b 00 00 00 ff ff ff ff ff e0 45 4f 46 00 00 00 00 01 00 00 01 00 06 06 01 00 " +
            "" + "01 00 01 00");
    /**
     * The zero-F EOF marker as per CRAM specs v3.0. This is basically a serialized empty CRAM container with sequence id set to some number
     * to spell out 'EOF' in hex.
     */
    private static final byte[] CRAMv3_EOF_MARKER = bytesFromHex("0f 00 00 00 ff ff ff ff 0f e0 45 4f 46 00 00 00 00 01 00 05 bd d9 4f 00 01 00 " +
            "" + "06 06 01 00 01 00 01 00 ee 63 01 4b");

    private static byte[] bytesFromHex(final String string) {
        final String clean = string.replaceAll("[^0-9a-fA-F]", "");
        if (clean.length() % 2 != 0) throw new RuntimeException("Not a hex string: " + string);
        final byte[] data = new byte[clean.length() / 2];
        for (int i = 0; i < clean.length(); i += 2) {
            data[i / 2] = (Integer.decode("0x" + clean.charAt(i) + clean.charAt(i + 1))).byteValue();
        }
        return data;
    }


    /**
     * The default CRAM version when creating a new CRAM output file or stream.
     */
    public static final Version DEFAULT_CRAM_VERSION = CRAM_v3;

    public static byte[] eofForVersion(final Version cramVersion) {
        if (cramVersion.compatibleWith(CramVersions.CRAM_v3)) {
            return CRAMv3_EOF_MARKER;
        }

        if (cramVersion.compatibleWith(CramVersions.CRAM_v2_1)) {
            return CRAMv2_1_EOF_MARKER;
        }

        return new byte[0];
    }
}
