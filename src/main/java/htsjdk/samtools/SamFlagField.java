/*
 * The MIT License
 *
 * Copyright (c) 2016 Nils Homer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

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
            throw new SAMFormatException("NONE not allowed for the SamFlagField when writing the SAM flag field.");
        }
        @Override
        protected int parseWithoutValidation(final String flag) {
            throw new SAMFormatException("NONE not allowed for the SamFlagField when reading the SAM flag field.");
        } 
    },
    DECIMAL {
        @Override
        public String format(final int flag) {
            return Integer.toString(flag);
        }
        /** Throws NumberFormatException if it can't parse the flag **/
        @Override
        protected int parseWithoutValidation(final String flag) {
            return Integer.parseInt(flag);
        }
    },
    HEXADECIMAL {
        @Override
        public String format(final int flag) {
            return String.format("%#x", flag);
        }
        @Override
        protected int parseWithoutValidation(final String flag) {
            return Integer.valueOf(flag.substring(2), 16);
        }
    },
    OCTAL {
        @Override
        public String format(final int flag) {
            return String.format("%#o", flag);
        }
        @Override
        protected int parseWithoutValidation(final String flag) {
            return Integer.valueOf(flag, 8);
        }
    },
    STRING {
        /*
        It is important that the first character of a string does not start with a digit, so we can
        determine which format given an input flag value.  See of.
         */

        @Override
        public String format(final int flag) {
            // Adapted from the the implementation here:
            //   https://github.com/jmarshall/cansam/blob/master/lib/alignment.cpp
            final StringBuilder value = new StringBuilder();

            if ((flag & SAMFlag.READ_UNMAPPED.flag) != 0)                   value.append('u');
            else                                                            value.append('m');

            if ((flag & SAMFlag.READ_REVERSE_STRAND.flag) != 0)             value.append('r');
            else if ((flag & SAMFlag.READ_UNMAPPED.flag) == 0)              value.append('f');

            if ((flag & SAMFlag.MATE_UNMAPPED.flag) != 0)                   value.append('U');
            else if ((flag & SAMFlag.READ_PAIRED.flag) != 0)                value.append('M');

            if ((flag & SAMFlag.MATE_REVERSE_STRAND.flag) != 0)             value.append('R');
            else if ((flag & SAMFlag.READ_PAIRED.flag) != 0)                value.append('F');

            if ((flag & SAMFlag.READ_PAIRED.flag) != 0)                     value.append('p');
            if ((flag & SAMFlag.PROPER_PAIR.flag) != 0)                     value.append('P');
            if ((flag & SAMFlag.FIRST_OF_PAIR.flag) != 0)                   value.append('1');
            if ((flag & SAMFlag.SECOND_OF_PAIR.flag) != 0)                  value.append('2');

            if ((flag & SAMFlag.NOT_PRIMARY_ALIGNMENT.flag) != 0)           value.append('s');
            if ((flag & SAMFlag.SUPPLEMENTARY_ALIGNMENT.flag) != 0)         value.append('S');
            if ((flag & SAMFlag.READ_FAILS_VENDOR_QUALITY_CHECK.flag) != 0) value.append('x');
            if ((flag & SAMFlag.DUPLICATE_READ.flag) != 0)                  value.append('d');

            return value.toString();
        }

        @Override
        protected int parseWithoutValidation(final String flag) {
            SamFlagField.validate(flag, STRING);

            // Adapted from the the implementation here:
            //   https://github.com/jmarshall/cansam/blob/master/lib/alignment.cpp

            int value = 0;

            for (int i = 0; i < flag.length(); i++) {
                switch (flag.charAt(i)) {
                    case 'p':  value |= SAMFlag.READ_PAIRED.flag;  break;
                    case 'P':  value |= SAMFlag.PROPER_PAIR.flag;  break;
                    case 'u':  value |= SAMFlag.READ_UNMAPPED.flag;  break;
                    case 'U':  value |= SAMFlag.MATE_UNMAPPED.flag;  break;
                    case 'r':  value |= SAMFlag.READ_REVERSE_STRAND.flag;  break;
                    case 'R':  value |= SAMFlag.MATE_REVERSE_STRAND.flag;  break;
                    case '1':  value |= SAMFlag.FIRST_OF_PAIR.flag;  break;
                    case '2':  value |= SAMFlag.SECOND_OF_PAIR.flag;  break;
                    case 's':  value |= SAMFlag.NOT_PRIMARY_ALIGNMENT.flag;  break;
                    case 'x':  value |= SAMFlag.READ_FAILS_VENDOR_QUALITY_CHECK.flag;  break;
                    case 'd':  value |= SAMFlag.DUPLICATE_READ.flag;  break;
                    case 'S':  value |= SAMFlag.SUPPLEMENTARY_ALIGNMENT.flag;  break;
                    case 'f':
                    case 'F':
                    case 'm':
                    case 'M':
                    case '_':
                        break;
                    default:
                        throw new SAMFormatException("Unknown flag character '" + flag.charAt(i) + "' in flag '" + flag + "'");
                }
            }

            return value;
        }
    };

    /** Returns the string associated with this flag field. */
    abstract public String format(final int flag);

    /** Parses the flag.  Validates that the flag is of the correct type. */
    public final int parse(final String flag) {
        return parse(flag, true);
    }

    /** Infers the format from the flag string and parses the flag. */
    public static int parseDefault(final String flag) {
        return SamFlagField.of(flag).parse(flag, false);
    }

    /** Performs the actual parsing based on the radix.  No validation that the flag is of the correct radix
     * should be performed.
     */
    abstract protected int parseWithoutValidation(final String flag);

    /** Parses the flag.  Performs optional validation that the flag is of the correct type. */
    private int parse(final String flag, final boolean withValidation) {
        if (withValidation) SamFlagField.validate(flag, this);
        return parseWithoutValidation(flag);
    }

    /**
     * Returns the type of flag field for this string.  This does not guarantee it is of the flag field,
     * as it only checks the first two characters.
     */
    public static SamFlagField of(final String s) {
        if (s.isEmpty()) throw new SAMFormatException("Could not determine flag field type; saw an empty flag field");
        else if (s.startsWith("0x")) return HEXADECIMAL;
        else if (s.startsWith("0X")) return HEXADECIMAL;
        else if (s.startsWith("0") && s.length() > 1) return OCTAL;
        else if (Character.isDigit(s.charAt(0))) return DECIMAL;
        else return STRING;
    }

    private static void validate(final String flag, final SamFlagField expectedField) {
        final SamFlagField actualField = SamFlagField.of(flag);
        if (actualField != expectedField) {
            throw new SAMFormatException(expectedField.name() + " sam flag must start with [1-9] but found '" + flag + "' (" + actualField.name() + ")");
        }
    }
}