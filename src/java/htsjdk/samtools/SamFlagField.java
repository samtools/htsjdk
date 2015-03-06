package htsjdk.samtools;

/**
 * This determines how flag fields are represented in the SAM file.
 *
 * In a string FLAG, each character represents one bit with
 * p=0x1 (paired), P=0x2 (properly paired), u=0x4 (unmapped),
 * U=0x8 (mate unmapped), r=0x10 (reverse), R=0x20 (mate reverse)
 * 1=0x40 (first), 2=0x80 (second), s=0x100 (not primary), 
 * x=0x200 (failure), d=0x400 (duplicate), and S=0x800 (secondary).
 * This was inspired by 'samtools view -X'.
 *
 * We also output a character when the following bits *are not* set:
 * m=0x4 (mapped), M=0x8 (mate mapped), f=0x10 (forward), F=0x20 
 * (mate forward).
 * 
 * @author nhomer
 */
public enum SamFlagField {
    NONE {
        @Override
        public String format(final int flag) {
            throw new SAMException("NONE not allowed for the SamFlagField when writing the SAM flag field.");
        }
        @Override
        public int parse(final String flag) {
            throw new SAMException("NONE not allowed for the SamFlagField when reading the SAM flag field.");
        } 
    },
    DEFAULT {
        @Override
        public String format(final int flag) {
            return Integer.toString(flag);
        }
        /** Throws NumberFormatException if it can't parse the flag **/
        @Override
        public int parse(final String flag) { return Integer.parseInt(flag); }
    },
    HEXADECIMAL {
        @Override
        public String format(final int flag) {
            return String.format("%#x", flag);
        }
        @Override
        public int parse(final String flag) {
            if (flag.startsWith("0x")) return Integer.valueOf(flag.substring(2), 16);
            else return Integer.valueOf(flag, 16); // this really should not be supported, it should always have 0x..
        }
    },
    OCTAL {
        @Override
        public String format(final int flag) {
            return String.format("%#o", flag);
        }
        @Override
        public int parse(final String flag) {
            return Integer.valueOf(flag, 8);
        }
    },
    STRING {
        /*  It is important that the first character of a string does not start with a digit, so we can
        determine which format given an input flag value.  See getSamFlagField.
         */
        private static final String flag2CharTable = "pPuUrR12sxdS\0\0\0\0"; // when the bit is set
        private static final String notFlag2CharTable = "\0\0mMfF\0\0\0\0\0\0\0\0\0\0"; // when the bit is not set

        private String getStringFlags(final int flag) {
            String s = "";
            for (int i = 0; i < 16; ++i) {
                if ((flag & 1 << i) != 0) { // the bit is set
                    if ('\0' != flag2CharTable.charAt(i)) s += flag2CharTable.charAt(i);
                }
                else { // the bit is not set
                    if ('\0' != notFlag2CharTable.charAt(i)) s += notFlag2CharTable.charAt(i);
                }
            }
            return s;
        }

        private int parseStringFlags(final String flag) {
            int ret = 0;
            for (int i = 0; i < flag.length(); i++) {
                final int idx = flag2CharTable.indexOf(flag.charAt(i));
                if (-1 != idx) {
                    ret = ret | (1 << idx);
                }
                else if (-1 == notFlag2CharTable.indexOf(flag.charAt(i))) {
                    throw new SAMFormatException("Unrecognized character: " + flag.charAt(i));
                }
            }
            return ret;
        }

        // For testing
        @Override
        public String getFlag2CharTable() { return flag2CharTable; }
        @Override
        public String getNotFlag2CharTable() { return notFlag2CharTable; }

        @Override
        public String format(final int flag) {
            return getStringFlags(flag);
        }

        @Override
        public int parse(final String flag) {
            return parseStringFlags(flag);
        }
    };
    abstract public String format(final int flag);
    abstract public int parse(final String flag);

    // For testing
    public String getFlag2CharTable() { return null; }
    public String getNotFlag2CharTable() { return null; }
    
    // Returns the type of flag field for this string.  This does not guarantee it is of the flag field,
    // as it only checks the first two characters.
    public static SamFlagField getSamFlagField(final String s) {
        if (s.isEmpty()) throw new SAMFormatException("Could not determine flag field type; saw an empty flag field");
        else if (s.startsWith("0x")) return HEXADECIMAL;
        else if (s.startsWith("0X")) return HEXADECIMAL;
        else if (s.startsWith("0") && s.length() > 1) return OCTAL; // not supported
        else if (Character.isDigit(s.charAt(0))) return DEFAULT;
        else return STRING;
    }
}