package htsjdk.samtools;

/**
 * Checks that text can be written without corrupting the file it goes into.
 *
 * <p>SAM text has no escapes, so a tab or line break inside a value would end its field or line. Header text is
 * UTF-8, so a header value may hold any other char except half of a surrogate pair. Read names and Z and A tags are stored one byte per char, as
 * htsjdk reads them (ISO-8859-1), so they cannot hold a char above 0xFF; BAM and CRAM also end them with a NUL.
 * Bytes 0x80 to 0xFF are allowed although the spec asks for ASCII, so that records other tools wrote pass through
 * unchanged.
 */
final class WritableText {

    /** Where the text is written, which decides the chars that would corrupt it. */
    enum Destination {
        /** A header tag name or value. */
        HEADER_FIELD("a SAM header", true, true, false),
        /** A whole {@code @CO} line, which may contain tabs. */
        HEADER_COMMENT("a SAM header", false, true, false),
        SAM_RECORD("SAM", true, true, true),
        BAM_RECORD("BAM", false, false, true),
        CRAM_RECORD("CRAM", false, false, true);

        private final String description;
        private final boolean rejectsTab;
        private final boolean rejectsLineBreaks;
        private final boolean oneBytePerChar;

        Destination(
                final String description,
                final boolean rejectsTab,
                final boolean rejectsLineBreaks,
                final boolean oneBytePerChar) {
            this.description = description;
            this.rejectsTab = rejectsTab;
            this.rejectsLineBreaks = rejectsLineBreaks;
            this.oneBytePerChar = oneBytePerChar;
        }
    }

    private WritableText() {}

    /**
     * Throws if {@code value} holds a char that would corrupt {@code destination}.
     *
     * @param what names the value in the message, e.g. "Header tag DS"
     * @throws IllegalArgumentException naming the value and the char
     */
    static void require(final String what, final String value, final Destination destination) {
        final int index = indexOfUnwritableChar(value, destination);
        if (index >= 0) {
            throw unwritable(what, value, index, destination);
        }
    }

    /**
     * Throws if the read name or a Z or A tag of {@code record} holds a char that would corrupt {@code destination}.
     * The message is built only on failure, since record writers call this for every record.
     *
     * @throws IllegalArgumentException naming the field and the char
     */
    static void requireInRecord(final SAMRecord record, final Destination destination) {
        final String readName = record.getReadName();
        if (readName != null) {
            final int index = indexOfUnwritableChar(readName, destination);
            if (index >= 0) {
                throw unwritable("Read name", readName, index, destination);
            }
        }
        for (SAMBinaryTagAndValue attribute = record.getBinaryAttributes();
                attribute != null;
                attribute = attribute.getNext()) {
            if (attribute.value instanceof String || attribute.value instanceof Character) {
                final String text = attribute.value.toString();
                final int index = indexOfUnwritableChar(text, destination);
                if (index >= 0) {
                    throw unwritable(
                            "Tag " + SAMTag.makeStringTag(attribute.tag) + " of read " + readName,
                            text,
                            index,
                            destination);
                }
            }
        }
    }

    private static int indexOfUnwritableChar(final String value, final Destination destination) {
        for (int i = 0; i < value.length(); ++i) {
            final char c = value.charAt(i);
            // Control chars are rare, so testing for them first keeps the common case to one or two comparisons.
            if (c < ' ') {
                if (c == 0
                        || (c == '\t' && destination.rejectsTab)
                        || ((c == '\n' || c == '\r') && destination.rejectsLineBreaks)) {
                    return i;
                }
            } else if (c > 0xFF) {
                if (destination.oneBytePerChar) {
                    return i;
                }
                // UTF-8 cannot encode half a surrogate pair; String.getBytes would write '?' for it.
                if (Character.isHighSurrogate(c)
                        && i + 1 < value.length()
                        && Character.isLowSurrogate(value.charAt(i + 1))) {
                    ++i;
                } else if (Character.isSurrogate(c)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static IllegalArgumentException unwritable(
            final String what, final String value, final int index, final Destination destination) {
        final char c = value.charAt(index);
        final String found =
                switch (c) {
                    case '\t' -> "a tab";
                    case '\n' -> "a line feed";
                    case '\r' -> "a carriage return";
                    case 0 -> "a NUL";
                    default ->
                        destination.oneBytePerChar
                                ? String.format("U+%04X, which does not fit in one byte", (int) c)
                                : String.format("U+%04X, half of a surrogate pair, which UTF-8 cannot encode", (int) c);
                };
        return new IllegalArgumentException(what + " cannot be written to " + destination.description
                + " because it contains " + found + ": "
                + value.replace("\t", "\\t")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\0", "\\0"));
    }
}
