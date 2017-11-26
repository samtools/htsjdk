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
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.Tribble;
import htsjdk.variant.utils.GeneralUtils;
import htsjdk.variant.variantcontext.GenotypesContext;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class VCFUtils {


    public static Set<VCFHeaderLine> smartMergeHeaders(final Collection<VCFHeader> headers, final boolean emitWarnings) throws IllegalStateException {
        // We need to maintain the order of the VCFHeaderLines, otherwise they will be scrambled in the returned Set.
        // This will cause problems for VCFHeader.getSequenceDictionary and anything else that implicitly relies on the line ordering.
        final LinkedHashMap<String, VCFHeaderLine> map = new LinkedHashMap<>(); // from KEY.NAME -> line
        final HeaderConflictWarner conflictWarner = new HeaderConflictWarner(emitWarnings);

        // todo -- needs to remove all version headers from sources and add its own VCF version line
        for ( final VCFHeader source : headers ) {
            for ( final VCFHeaderLine line : source.getMetaDataInSortedOrder()) {

                String key = line.getKey();
                if ( line instanceof VCFIDHeaderLine )
                    key = key + "-" + ((VCFIDHeaderLine)line).getID();

                if ( map.containsKey(key) ) {
                    final VCFHeaderLine other = map.get(key);
                    if ( line.equals(other) ) {
                        // continue;
                    } else if ( ! line.getClass().equals(other.getClass()) ) {
                        throw new IllegalStateException("Incompatible header types: " + line + " " + other );
                    } else if ( line instanceof VCFFilterHeaderLine ) {
                        final String lineName = ((VCFFilterHeaderLine) line).getID();
                        final String otherName = ((VCFFilterHeaderLine) other).getID();
                        if ( ! lineName.equals(otherName) )
                            throw new IllegalStateException("Incompatible header types: " + line + " " + other );
                    } else if ( line instanceof VCFCompoundHeaderLine ) {
                        final VCFCompoundHeaderLine compLine = (VCFCompoundHeaderLine)line;
                        final VCFCompoundHeaderLine compOther = (VCFCompoundHeaderLine)other;

                        // if the names are the same, but the values are different, we need to quit
                        if (! (compLine).equalsExcludingDescription(compOther) ) {
                            if ( compLine.getType().equals(compOther.getType()) ) {
                                // The Number entry is an Integer that describes the number of values that can be
                                // included with the INFO field. For example, if the INFO field contains a single
                                // number, then this value should be 1. However, if the INFO field describes a pair
                                // of numbers, then this value should be 2 and so on. If the number of possible
                                // values varies, is unknown, or is unbounded, then this value should be '.'.
                                conflictWarner.warn(line, "Promoting header field Number to . due to number differences in header lines: " + line + " " + other);
                                compOther.setNumberToUnbounded();
                            } else if ( compLine.getType() == VCFHeaderLineType.Integer && compOther.getType() == VCFHeaderLineType.Float ) {
                                // promote key to Float
                                conflictWarner.warn(line, "Promoting Integer to Float in header: " + compOther);
                                map.put(key, compOther);
                            } else if ( compLine.getType() == VCFHeaderLineType.Float && compOther.getType() == VCFHeaderLineType.Integer ) {
                                // promote key to Float
                                conflictWarner.warn(line, "Promoting Integer to Float in header: " + compOther);
                            } else {
                                throw new IllegalStateException("Incompatible header types, collision between these two types: " + line + " " + other );
                            }
                        }
                        if ( ! compLine.getDescription().equals(compOther.getDescription()) )
                            conflictWarner.warn(line, "Allowing unequal description fields through: keeping " + compOther + " excluding " + compLine);
                    } else {
                        // we are not equal, but we're not anything special either
                        conflictWarner.warn(line, "Ignoring header line already in map: this header line = " + line + " already present header = " + other);
                    }
                } else {
                    map.put(key, line);
                }
            }
        }
        // returning a LinkedHashSet so that ordering will be preserved. Ensures the contig lines do not get scrambled.
        return new LinkedHashSet<>(map.values());
    }

    /**
     * Add / replace the contig header lines in the VCFHeader with the in the reference file and master reference dictionary
     *
     * @param oldHeader the header to update
     * @param referenceFile the file path to the reference sequence used to generate this vcf
     * @param refDict the SAM formatted reference sequence dictionary
     */
    public static VCFHeader withUpdatedContigs(final VCFHeader oldHeader, final File referenceFile, final SAMSequenceDictionary refDict) {
        return new VCFHeader(withUpdatedContigsAsLines(oldHeader.getMetaDataInInputOrder(), referenceFile, refDict), oldHeader.getGenotypeSamples());
    }

    public static Set<VCFHeaderLine> withUpdatedContigsAsLines(final Set<VCFHeaderLine> oldLines, final File referenceFile, final SAMSequenceDictionary refDict) {
        return withUpdatedContigsAsLines(oldLines, referenceFile, refDict, false);
    }

    public static Set<VCFHeaderLine> withUpdatedContigsAsLines(final Set<VCFHeaderLine> oldLines, final File referenceFile, final SAMSequenceDictionary refDict, final boolean referenceNameOnly) {
        final Set<VCFHeaderLine> lines = new LinkedHashSet<>(oldLines.size());

        for ( final VCFHeaderLine line : oldLines ) {
            if ( line instanceof VCFContigHeaderLine )
                continue; // skip old contig lines
            if ( line.getKey().equals(VCFHeader.REFERENCE_KEY) )
                continue; // skip the old reference key
            lines.add(line);
        }

        for ( final VCFHeaderLine contigLine : makeContigHeaderLines(refDict, referenceFile) )
            lines.add(contigLine);

        final String referenceValue;
        if (referenceFile != null) {
            if (referenceNameOnly) {
                final int extensionStart = referenceFile.getName().lastIndexOf('.');
                referenceValue = extensionStart == -1 ? referenceFile.getName() : referenceFile.getName().substring(0, extensionStart);
            }
            else {
                referenceValue = "file://" + referenceFile.getAbsolutePath();
            }
            lines.add(new VCFHeaderLine(VCFHeader.REFERENCE_KEY, referenceValue));
        }
        return lines;
    }

    /**
     * Create VCFHeaderLines for each refDict entry, and optionally the assembly if referenceFile != null
     * @param refDict reference dictionary
     * @param referenceFile for assembly name.  May be null
     * @return list of vcf contig header lines
     */
    public static List<VCFContigHeaderLine> makeContigHeaderLines(final SAMSequenceDictionary refDict,
                                                                  final File referenceFile) {
        final List<VCFContigHeaderLine> lines = new ArrayList<>();
        final String assembly = referenceFile != null ? getReferenceAssembly(referenceFile.getName()) : null;
        for ( final SAMSequenceRecord contig : refDict.getSequences() )
            lines.add(makeContigHeaderLine(contig, assembly));
        return lines;
    }

    private static VCFContigHeaderLine makeContigHeaderLine(final SAMSequenceRecord contig, final String assembly) {
        final Map<String, String> map = new LinkedHashMap<>(3);
        map.put("ID", contig.getSequenceName());
        map.put("length", String.valueOf(contig.getSequenceLength()));
        if ( assembly != null ) map.put("assembly", assembly);
        return new VCFContigHeaderLine(map, contig.getSequenceIndex());
    }

    /**
     * This method creates a temporary VCF file and it's appropriately named index file, and will delete them on exit.
     * @param prefix - The prefix string to be used in generating the file's name; must be at least three characters long
     * @param suffix - The suffix string to be used in generating the file's name; may be null, in which case the suffix ".tmp" will be used
     * @return A File object referencing the newly created temporary VCF file
     * @throws IOException - if a file could not be created.
     */
    public static File createTemporaryIndexedVcfFile(final String prefix, final String suffix) throws IOException {
        final File out = File.createTempFile(prefix, suffix);
        out.deleteOnExit();
        String indexFileExtension = null;
        if (suffix.endsWith(IOUtil.COMPRESSED_VCF_FILE_EXTENSION)) {
            indexFileExtension = IOUtil.COMPRESSED_VCF_INDEX_EXTENSION;
        }
        else if (suffix.endsWith(IOUtil.VCF_FILE_EXTENSION)) {
            indexFileExtension = IOUtil.VCF_INDEX_EXTENSION;
        }
        if (indexFileExtension != null) {
            final File indexOut = new File(out.getAbsolutePath() + indexFileExtension);
            indexOut.deleteOnExit();
        }
        return out;
    }

    /**
     * This method makes a copy of the input VCF and creates an index file for it in the same location.
     * This is done so that we don't need to store the index file in the same repo
     * The copy of the input is done so that it and its index are in the same directory which is typically required.
     * @param vcfFile the vcf file to index
     * @return File a vcf file (index file is created in same path).
     */
    public static File createTemporaryIndexedVcfFromInput(final File vcfFile, final String tempFilePrefix) throws IOException {
        final String extension;

        if (vcfFile.getAbsolutePath().endsWith(IOUtil.VCF_FILE_EXTENSION) ) extension = IOUtil.VCF_FILE_EXTENSION;
        else if (vcfFile.getAbsolutePath().endsWith(IOUtil.COMPRESSED_VCF_FILE_EXTENSION) ) extension = IOUtil.COMPRESSED_VCF_FILE_EXTENSION;
        else throw new IllegalArgumentException("couldn't find a " + IOUtil.VCF_FILE_EXTENSION + " or " + IOUtil.COMPRESSED_VCF_FILE_EXTENSION + " ending for input file " + vcfFile.getAbsolutePath());

        File output = createTemporaryIndexedVcfFile(tempFilePrefix, extension);

        final VCFFileReader in = new VCFFileReader(vcfFile, false);
        final VCFHeader header = in.getFileHeader();

        final VariantContextWriter out = new VariantContextWriterBuilder().
                setReferenceDictionary(header.getSequenceDictionary()).
                setOptions(EnumSet.of(Options.INDEX_ON_THE_FLY)).
                setOutputFile(output).build();
        out.writeHeader(header);
        for (final VariantContext ctx : in) {
            out.add(ctx);
        }
        out.close();
        in.close();
        return output;
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
        else if (refPath.contains("hg38"))
            assembly = "hg38";
        return assembly;
    }

    /** Only displays a warning if warnings are enabled and an identical warning hasn't been already issued */
    private static final class HeaderConflictWarner {
        boolean emitWarnings;
        Set<String> alreadyIssued = new HashSet<>();

        private HeaderConflictWarner( final boolean emitWarnings ) {
            this.emitWarnings = emitWarnings;
        }

        public void warn(final VCFHeaderLine line, final String msg) {
            if ( GeneralUtils.DEBUG_MODE_ENABLED && emitWarnings && ! alreadyIssued.contains(line.getKey()) ) {
                alreadyIssued.add(line.getKey());
                System.err.println(msg);
            }
        }
    }
}
