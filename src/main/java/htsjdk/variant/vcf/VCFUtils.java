/*
* Copyright (c) 2012 The Broad Institute
* 
* Permission is hereby granted, free of charge, to any person
* obtaining a copy of this software and associated documentation
* files (the "Software"), to deal in the Software without
* restriction, including without limitation the rights to use,
* copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the
* Software is furnished to do so, subject to the following
* conditions:
* 
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package htsjdk.variant.vcf;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.tribble.TribbleException;
import htsjdk.variant.utils.GeneralUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class VCFUtils {

    public static final char[] DANGEROUS_VCF_CHARACTERS = {'%', ';', ':', '=', ',', '\n', '\r', '\t'};

    public static Set<VCFHeaderLine> smartMergeHeaders(final Collection<VCFHeader> headers, final boolean emitWarnings) throws IllegalStateException {
        // We need to maintain the order of the VCFHeaderLines, otherwise they will be scrambled in the returned Set.
        // This will cause problems for VCFHeader.getSequenceDictionary and anything else that implicitly relies on the line ordering.
        final TreeMap<String, VCFHeaderLine> map = new TreeMap<String, VCFHeaderLine>(); // from KEY.NAME -> line
        final HeaderConflictWarner conflictWarner = new HeaderConflictWarner(emitWarnings);

        // todo -- needs to remove all version headers from sources and add its own VCF version line
        for (final VCFHeader source : headers) {
            //System.out.printf("Merging in header %s%n", source);
            for (final VCFHeaderLine line : source.getMetaDataInSortedOrder()) {

                String key = line.getKey();
                if (line instanceof VCFIDHeaderLine)
                    key = key + "-" + ((VCFIDHeaderLine) line).getID();

                if (map.containsKey(key)) {
                    final VCFHeaderLine other = map.get(key);
                    if (line.equals(other)) {
                        // continue;
                    } else if (!line.getClass().equals(other.getClass())) {
                        throw new IllegalStateException("Incompatible header types: " + line + " " + other);
                    } else if (line instanceof VCFFilterHeaderLine) {
                        final String lineName = ((VCFFilterHeaderLine) line).getID();
                        final String otherName = ((VCFFilterHeaderLine) other).getID();
                        if (!lineName.equals(otherName))
                            throw new IllegalStateException("Incompatible header types: " + line + " " + other);
                    } else if (line instanceof VCFCompoundHeaderLine) {
                        final VCFCompoundHeaderLine compLine = (VCFCompoundHeaderLine) line;
                        final VCFCompoundHeaderLine compOther = (VCFCompoundHeaderLine) other;

                        // if the names are the same, but the values are different, we need to quit
                        if (!(compLine).equalsExcludingDescription(compOther)) {
                            if (compLine.getType().equals(compOther.getType())) {
                                // The Number entry is an Integer that describes the number of values that can be
                                // included with the INFO field. For example, if the INFO field contains a single
                                // number, then this value should be 1. However, if the INFO field describes a pair
                                // of numbers, then this value should be 2 and so on. If the number of possible
                                // values varies, is unknown, or is unbounded, then this value should be '.'.
                                conflictWarner.warn(line, "Promoting header field Number to . due to number differences in header lines: " + line + " " + other);
                                compOther.setNumberToUnbounded();
                            } else if (compLine.getType() == VCFHeaderLineType.Integer && compOther.getType() == VCFHeaderLineType.Float) {
                                // promote key to Float
                                conflictWarner.warn(line, "Promoting Integer to Float in header: " + compOther);
                                map.put(key, compOther);
                            } else if (compLine.getType() == VCFHeaderLineType.Float && compOther.getType() == VCFHeaderLineType.Integer) {
                                // promote key to Float
                                conflictWarner.warn(line, "Promoting Integer to Float in header: " + compOther);
                            } else {
                                throw new IllegalStateException("Incompatible header types, collision between these two types: " + line + " " + other);
                            }
                        }
                        if (!compLine.getDescription().equals(compOther.getDescription()))
                            conflictWarner.warn(line, "Allowing unequal description fields through: keeping " + compOther + " excluding " + compLine);
                    } else {
                        // we are not equal, but we're not anything special either
                        conflictWarner.warn(line, "Ignoring header line already in map: this header line = " + line + " already present header = " + other);
                    }
                } else {
                    map.put(key, line);
                    //System.out.printf("Adding header line %s%n", line);
                }
            }
        }
        // returning a LinkedHashSet so that ordering will be preserved. Ensures the contig lines do not get scrambled.
        return new LinkedHashSet<VCFHeaderLine>(map.values());
    }

    /**
     * Add / replace the contig header lines in the VCFHeader with the in the reference file and master reference dictionary
     *
     * @param oldHeader     the header to update
     * @param referenceFile the file path to the reference sequence used to generate this vcf
     * @param refDict       the SAM formatted reference sequence dictionary
     */
    public static VCFHeader withUpdatedContigs(final VCFHeader oldHeader, final File referenceFile, final SAMSequenceDictionary refDict) {
        return new VCFHeader(withUpdatedContigsAsLines(oldHeader.getMetaDataInInputOrder(), referenceFile, refDict), oldHeader.getGenotypeSamples());
    }

    public static Set<VCFHeaderLine> withUpdatedContigsAsLines(final Set<VCFHeaderLine> oldLines, final File referenceFile, final SAMSequenceDictionary refDict) {
        return withUpdatedContigsAsLines(oldLines, referenceFile, refDict, false);
    }

    public static Set<VCFHeaderLine> withUpdatedContigsAsLines(final Set<VCFHeaderLine> oldLines, final File referenceFile, final SAMSequenceDictionary refDict, final boolean referenceNameOnly) {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<VCFHeaderLine>(oldLines.size());

        for (final VCFHeaderLine line : oldLines) {
            if (line instanceof VCFContigHeaderLine)
                continue; // skip old contig lines
            if (line.getKey().equals(VCFHeader.REFERENCE_KEY))
                continue; // skip the old reference key
            lines.add(line);
        }

        for (final VCFHeaderLine contigLine : makeContigHeaderLines(refDict, referenceFile))
            lines.add(contigLine);

        final String referenceValue;
        if (referenceFile != null) {
            if (referenceNameOnly) {
                final int extensionStart = referenceFile.getName().lastIndexOf('.');
                referenceValue = extensionStart == -1 ? referenceFile.getName() : referenceFile.getName().substring(0, extensionStart);
            } else {
                referenceValue = "file://" + referenceFile.getAbsolutePath();
            }
            lines.add(new VCFHeaderLine(VCFHeader.REFERENCE_KEY, referenceValue));
        }
        return lines;
    }

    /**
     * Create VCFHeaderLines for each refDict entry, and optionally the assembly if referenceFile != null
     *
     * @param refDict       reference dictionary
     * @param referenceFile for assembly name.  May be null
     * @return list of vcf contig header lines
     */
    public static List<VCFContigHeaderLine> makeContigHeaderLines(final SAMSequenceDictionary refDict,
                                                                  final File referenceFile) {
        final List<VCFContigHeaderLine> lines = new ArrayList<VCFContigHeaderLine>();
        final String assembly = referenceFile != null ? getReferenceAssembly(referenceFile.getName()) : null;
        for (final SAMSequenceRecord contig : refDict.getSequences())
            lines.add(makeContigHeaderLine(contig, assembly));
        return lines;
    }

    private static VCFContigHeaderLine makeContigHeaderLine(final SAMSequenceRecord contig, final String assembly) {
        final Map<String, String> map = new LinkedHashMap<String, String>(3);
        map.put("ID", contig.getSequenceName());
        map.put("length", String.valueOf(contig.getSequenceLength()));
        if (assembly != null) map.put("assembly", assembly);
        return new VCFContigHeaderLine(map, contig.getSequenceIndex());
    }

    private static String getReferenceAssembly(final String refPath) {
        // This doesn't need to be perfect as it's not a required VCF header line, but we might as well give it a shot
        String assembly = null;
        if (refPath.contains("b37") || refPath.contains("v37"))
            assembly = "b37";
        else if (refPath.contains("b36"))
            assembly = "b36";
        else if (refPath.contains("hg18"))
            assembly = "hg18";
        else if (refPath.contains("hg19"))
            assembly = "hg19";
        return assembly;
    }

    /**
     * Only displays a warning if warnings are enabled and an identical warning hasn't been already issued
     */
    private static final class HeaderConflictWarner {
        boolean emitWarnings;
        Set<String> alreadyIssued = new HashSet<String>();

        private HeaderConflictWarner(final boolean emitWarnings) {
            this.emitWarnings = emitWarnings;
        }

        public void warn(final VCFHeaderLine line, final String msg) {
            if (GeneralUtils.DEBUG_MODE_ENABLED && emitWarnings && !alreadyIssued.contains(line.getKey())) {
                alreadyIssued.add(line.getKey());
                System.err.println(msg);
            }
        }
    }

    /**
     * A utility that decodes percent encoded strings found in VCF files. For example, when given the string '%3D%41'
     * will return the string '=A'. Throws an exception if the the encoding is uninterpretable.
     * NOTE: Decoding only functions for the first 127 Unicode Codepoints, and interprets
     *
     * @param input string to be decoded
     * @return a string (possibly the input string) that has interpreted every example of percent encoding
     *         into the corresponding character
     */
    public static String decodePercentEncodedChars(String input) {
        if (input.contains("%")) {
            StringBuilder builder = new StringBuilder(input.length() - 2);
            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);
                if (c == '%') {
                    // Checking for characters at the back of the sequence
                    if (i + 2 >= input.length()) {
                        throw new TribbleException.VCFException("Improperly formatted '%' escape sequence");
                    }
                    char[] trans;
                    try {
                        trans = Character.toChars(Integer.parseInt(input.substring(i + 1, i + 3), 16));
                    } catch (NumberFormatException e) {
                        throw new TribbleException.VCFException(String.format("'%%s' is not a valid percent encoded character"));
                    }
                    if (trans.length != 1) {
                        throw new TribbleException.VCFException("'%' escape sequence corresponded to an invalid codepoint");
                    }
                    builder.append(trans[0]);
                    i = i + 2;
                } else {
                    builder.append(c);
                }
            }
            return builder.toString();
        }
        return input;
    }

    /**
     * Method that returns a string corresponding to the input string where all instances of the '%' character have
     * been replaced with its percent encoded equivalent, "%25." This method is intended to be used for efficiently
     * converting pre-VCFv4.3 files to the new encoding standard.
     *
     * @param input the string to be encoded
     * @return a string (may be the input string) corresponding to every existing '%' as "%25"
     */
    public static String toPercentEncodingFast(String input) {
        if (input.indexOf('%') > -1) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < input.length(); i++) {
                if (input.charAt(i) == '%') {
                    builder.append("%25");
                } else {
                    builder.append(input.charAt(i));
                }
            }
            return builder.toString();
        }
        return input;
    }

    /**
     * Method that returns a string corresponding to the input string where all instances of the of dangerous characters
     * that will break the parsing of VCF files if found in the info field have been replaced with their capitalized
     * percent-encoded equivalent according to the VCFv4.3 spec. For example: the string "abc,def" -> "abc%2Cdef"
     *
     * @param input the string to be encoded
     * @return a string (may be the input string) corresponding to every existing '%' as "%25"
     */
    public static String toPercentEncodingSlow(String input) {
        if (containsDangerousCharacter(input)) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);
                boolean encoded = false;
                for (char danger : DANGEROUS_VCF_CHARACTERS) {
                    if (danger == c) {
                        builder.append("%"+(c > 16? "" : "0")+Integer.toHexString((int)c).toUpperCase());
                        encoded = true;
                        break;
                    }
                }
                if (!encoded ) builder.append(c);
            }
            return builder.toString();
        }
        return input;
    }

    private static boolean containsDangerousCharacter(String s) {
        for (int i = 0; i < 1000; i++) {
            int l = VCFUtils.DANGEROUS_VCF_CHARACTERS.length;
            for (int c = 0; c < l; c++) {
                if (s.indexOf(VCFUtils.DANGEROUS_VCF_CHARACTERS[c]) > -1) {
                    return true;
                }
            }
        }
        return false;
    }

}
