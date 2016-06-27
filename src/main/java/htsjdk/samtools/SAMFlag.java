/*
 * The MIT License
 *
 * Author: Pierre Lindenbaum PhD @yokofakun
 *  Institut du Thorax - Nantes - France
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

import java.util.HashSet;
import java.util.Set;

/**
 * SAM flags as enum, to be used in GUI, menu, etc...
 */
public enum SAMFlag {
    READ_PAIRED(                    0x1,    "Template having multiple segments in sequencing"),
    PROPER_PAIR(                    0x2,    "Each segment properly aligned according to the aligner"),
    READ_UNMAPPED(                  0x4,    "Segment unmapped"),
    MATE_UNMAPPED(                  0x8,    "Next segment in the template unmapped"),
    READ_REVERSE_STRAND(            0x10,   "SEQ being reverse complemented"),
    MATE_REVERSE_STRAND(            0x20,   "SEQ of the next segment in the template being reverse complemented"),
    FIRST_OF_PAIR(                  0x40,   "The first segment in the template"),
    SECOND_OF_PAIR(                 0x80,   "The last segment in the template"),
    NOT_PRIMARY_ALIGNMENT(          0x100,  "Secondary alignment"),
    READ_FAILS_VENDOR_QUALITY_CHECK(0x200,  "Not passing quality controls"),
    DUPLICATE_READ(                 0x400,  "PCR or optical duplicate"), 
    SUPPLEMENTARY_ALIGNMENT(        0x800,  "Supplementary alignment")
    ;

    /* visible for the package, to be used by SAMRecord */
    final int flag;
    private final String description;

    SAMFlag(int flag, String description) {
        this.flag = flag;
        this.description = description;
    }

    /** @return this flag as an int */
    public int intValue() {
        return flag;
    }

    /** @return a human label for this SAMFlag */
    public String getLabel() {
        return name().toLowerCase().replace('_', ' ');
    }

    /** @return a human description for this SAMFlag */
    public String getDescription() {
        return this.description;
    }

    /** @return the SAMFlag for the value 'flag' or null if it was not found */
    public static SAMFlag valueOf(int flag) {
        for (SAMFlag f : values()) {
            if (flag == f.flag)
                return f;
        }
        return null;
    }

    /** @return find SAMFlag the flag by name, or null if it was not found */
    public static SAMFlag findByName(String flag)
        {   
        for (SAMFlag f : values()) {
            if (f.name().equals(flag))
                return f;
        }
        return null;
    }

    /** @returns true if the bit for is set for flag */
    public boolean isSet(int flag) {
        return (this.flag & flag) != 0;
    }

    /** @returns true if the bit for is not set for flag */
    public boolean isUnset(int flag) {
        return !isSet(flag);
    }

    /** @returns the java.util.Set of SAMFlag for 'flag' */
    public static Set<SAMFlag> getFlags(int flag) {
        Set<SAMFlag> set = new HashSet<SAMFlag>();
        for (SAMFlag f : values()) {
            if (f.isSet(flag))
                set.add(f);
        }
        return set;
    }
}
