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

import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.Log;
import htsjdk.tribble.TribbleException;

import java.util.*;
import java.util.regex.Pattern;

/**
 * A special class representing a contig VCF header line.  Knows the true contig order and sorts on that
 *
 * Note: this class has a natural ordering that is inconsistent with equals()
 *
 * @author mdepristo
 */
public class VCFContigHeaderLine extends VCFStructuredHeaderLine {
    private static final long serialVersionUID = 1L;
    protected final static Log logger = Log.getInstance(VCFContigHeaderLine.class);

    final static Pattern VALID_CONTIG_ID_PATTERN = Pattern.compile("[!-)+-<>-~][!-~]*");

    final Integer contigIndex;

    public static String LENGTH_ATTRIBUTE = "length";
    public static String ASSEMBLY_ATTRIBUTE = "assembly";
    public static String MD5_ATTRIBUTE = "md5";
    public static String URL_ATTRIBUTE = "URL";
    public static String SPECIES_ATTRIBUTE = "species";

    /**
     * create a VCF contig header line
     *
     * @param line      the header line
     * @param version   the vcf header version
     * @param contigIndex
     */
    public VCFContigHeaderLine(final String line, final VCFHeaderVersion version, final int contigIndex) {
        this(VCFHeaderLineTranslator.parseLine(
                version, line, Collections.singletonList(VCFStructuredHeaderLine.ID_ATTRIBUTE)), contigIndex);
        validateForVersion(version);
    }

    public VCFContigHeaderLine(final Map<String, String> mapping, final int contigIndex) {
        super(VCFHeader.CONTIG_KEY, mapping);
	    if (contigIndex < 0) {
	        throw new TribbleException("The contig index is less than zero.");
        }
        this.contigIndex = contigIndex;
    }

    /**
     * Return a VCFContigHeaderLine representing a SAMSequenceRecord.
     *
     * NOTE: round-tripping between VCFContigHeaderLines and SAMSequenceRecords can be lossy since they
     * don't necessarily have equivalent attributes, i.e., SAMSequenceRecord can have a species attribute
     * that isn't defined by the VCF spec.
     *
     * @return VCFContigHeaderLine for the SAMSequenceRecord
     */
    public VCFContigHeaderLine(final SAMSequenceRecord sequenceRecord, final String assembly) {
        // preserve order of keys in contig line (ID, length, assembly)
        this(new LinkedHashMap<String, String>() {{
                this.put(ID_ATTRIBUTE, sequenceRecord.getSequenceName());
                this.put(LENGTH_ATTRIBUTE, Integer.toString(sequenceRecord.getSequenceLength()));
                if (assembly != null) {
                    if (!assembly.equals(sequenceRecord.getAssembly()) && VCFUtils.getVerboseVCFLogging()) {
                        logger.warn(String.format(
                                        "Inconsistent \"assembly\" attribute values found while creating VCFContigLine " +
                                        "(with assembly \"%s\") from SAMSequenceRecord (with assembly \"%s\")",
                                        assembly,
                                        sequenceRecord.getAssembly()));
                    }
                    this.put(ASSEMBLY_ATTRIBUTE, assembly);
                }
                if (sequenceRecord.getMd5() != null) {
                     this.put(MD5_ATTRIBUTE, sequenceRecord.getMd5());
                }
                if (sequenceRecord.getAttribute(SAMSequenceRecord.URI_TAG) != null) {
                    this.put(URL_ATTRIBUTE, sequenceRecord.getAttribute(SAMSequenceRecord.URI_TAG));
                }
                if (sequenceRecord.getAttribute(SAMSequenceRecord.SPECIES_TAG) != null) {
                    this.put(SPECIES_ATTRIBUTE, sequenceRecord.getAttribute(SAMSequenceRecord.SPECIES_TAG));
                }
            }},
            sequenceRecord.getSequenceIndex()
        );
	}

    /**
     * Return a SAMSequenceRecord representing this contig line.
     *
     * NOTE: roundtripping between VCFContigHeaderLines and SAMSequenceRecords can be lossy since they
     * don't necessarily have equivalent attributes, i.e., SAMSequenceRecord can have a species attribute
     * that isn't defined by the VCF spec.
     *
     * @return SAMSequenceRecord for this contig line
     */
    public SAMSequenceRecord getSAMSequenceRecord() {
        final String lengthString = this.getGenericFieldValue(LENGTH_ATTRIBUTE);
        if (lengthString == null) {
            throw new TribbleException("Contig " + this.getID() + " does not have a length field.");
        }
        final SAMSequenceRecord record = new SAMSequenceRecord(this.getID(), Integer.valueOf(lengthString));
        final String assemblyString = this.getGenericFieldValue(ASSEMBLY_ATTRIBUTE);
        if (assemblyString != null) {
            record.setAssembly(assemblyString);
        }
        record.setSequenceIndex(this.contigIndex);
        final String md5 = getGenericFieldValue(MD5_ATTRIBUTE);
        if (md5 != null) {
            record.setMd5(md5);
        }
        final String url = getGenericFieldValue(URL_ATTRIBUTE);
        if (url != null) {
            record.setAttribute(SAMSequenceRecord.URI_TAG, url);
        }
        final String species = getGenericFieldValue(SPECIES_ATTRIBUTE);
        if (species != null) {
            record.setSpecies(species);
        }
        return record;
    }

    /**
     * Validate that this header line conforms to the target version.
     */
    @Override
    public void validateForVersion(final VCFHeaderVersion vcfTargetVersion) {
        super.validateForVersion(vcfTargetVersion);
        if (vcfTargetVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3)) {
            //TODO: V4.3 The contig names must not use a reserved symbolic allele/exclude the characters
            // l/r chevron, l/r bracket, colon, asterisk
            if (VALID_CONTIG_ID_PATTERN.matcher(getID()).matches()) {
                String message = String.format("Contig headerLineID \"%s\" in \"%s\" header line doesn't conform to VCF contig ID restrictions" ,
                        getID(),
                        getKey());
                throw new TribbleException.InvalidHeader(message);
            }
        }
    }

    public Integer getContigIndex() {
        return contigIndex;
    }

    /**
     * Note: this class has a natural ordering that is inconsistent with equals()
     *
     * @param o
     * @return
     */
    @Override
    public boolean equals(final Object o) {
        if ( this == o ) {
            return true;
        }
        if ( o == null || getClass() != o.getClass() || ! super.equals(o) ) {
            return false;
        }

        final VCFContigHeaderLine that = (VCFContigHeaderLine) o;
        return contigIndex.equals(that.contigIndex);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + contigIndex.hashCode();
        return result;
    }

    /**
     * IT IS CRITICAL THAT THIS BE OVERRIDDEN SO WE SORT THE CONTIGS IN THE CORRECT ORDER
     *
     * Note: this class has a natural ordering that is inconsistent with equals()
     */
    @Override
    public int compareTo(final Object other) {
        if ( other instanceof VCFContigHeaderLine )
            return contigIndex.compareTo(((VCFContigHeaderLine) other).contigIndex);
        else {
            return super.compareTo(other);
        }
    }
}