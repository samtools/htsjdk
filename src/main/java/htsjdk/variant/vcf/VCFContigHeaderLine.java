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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A special class representing a contig VCF header line.  Knows the true contig order and sorts on that
 *
 * Note: this class has a natural ordering that is inconsistent with equals()
 *
 * @author mdepristo
 */
public class VCFContigHeaderLine extends VCFSimpleHeaderLine {
    private static final long serialVersionUID = 1L;
    protected final static Log logger = Log.getInstance(VCFContigHeaderLine.class);

    final static Pattern VALID_CONTIG_ID_PATTERN = Pattern.compile("[0-9A-Za-z!#$%&+./:;?@^_|~-][0-9A-Za-z!#$%&*+./:;=?@^_|~-]*");
    final Integer contigIndex;

    public static final String LENGTH_ATTRIBUTE = "length";
    public static final String ASSEMBLY_ATTRIBUTE = "assembly";
    public static final String MD5_ATTRIBUTE = "md5";
    public static final String URL_ATTRIBUTE = "URL";
    public static final String SPECIES_ATTRIBUTE = "species";

    /**
     * create a VCF contig header line
     *
     * NOTE: This is retained for backward compatibility, but is deprecated and should not be used.
     *
     * @param line      the header line
     * @param version   the vcf header version
     * @param key            the key for this header line
     * @param contigIndex the contig index for this contig
     */
    @Deprecated // starting after version 2.24.1
    public VCFContigHeaderLine(final String line, final VCFHeaderVersion version, final String key, final int contigIndex) {
        // deprecated because this constructor has a parameter to specify the key (??), but for
        // contig lines the key has to be "contig"
        this(line, version, contigIndex);
        if (!VCFHeader.CONTIG_KEY.equals(key)) {
            logger.warn(String.format(
                    "Found key \"%s\". The key for contig header lines must be %s.",
                    key,
                    VCFHeader.CONTIG_KEY));
        }
    }

    /**
     * create a VCF contig header line
     *
     * @param line      the header line
     * @param version   the vcf header version
     * @param contigIndex the contig index for this contig
     */
    public VCFContigHeaderLine(final String line, final VCFHeaderVersion version, final int contigIndex) {
        this(VCFHeaderLineTranslator.parseLine(
                version, line, Collections.singletonList(VCFSimpleHeaderLine.ID_ATTRIBUTE)), contigIndex);
        if (!VCFHeader.CONTIG_KEY.equals(getKey())) {
            logger.warn(String.format(
                    "Found key \"%s\". The key for contig header lines must be %s.",
                    getKey(),
                    VCFHeader.CONTIG_KEY));
        }
        if (contigIndex < 0) {
            throw new TribbleException(String.format("The contig index (%d) is less than zero.", contigIndex));
        }
        validateForVersionOrThrow(version);
    }

    public VCFContigHeaderLine(final Map<String, String> mapping, final int contigIndex) {
        super(VCFHeader.CONTIG_KEY, mapping);
	    if (contigIndex < 0) {
            throw new TribbleException(String.format("The contig index (%d) is less than zero.", contigIndex));
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
                if (sequenceRecord.getSequenceLength() != 0) {
                    this.put(LENGTH_ATTRIBUTE, Integer.toString(sequenceRecord.getSequenceLength()));
                }
                if (assembly != null) {
                    if (!assembly.equals(sequenceRecord.getAssembly())) {
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
     * Get the SAMSequenceRecord that corresponds to this VCF header line.
     * If the VCF header line does not have a length tag, the SAMSequenceRecord returned will be set to have a length of
     * SAMSequenceRecord.UNKNOWN_SEQUENCE_LENGTH. Records with unknown length will match any record with the same name
     * when evaluated by SAMSequenceRecord.isSameSequence.
     * @return The SAMSequenceRecord containing the ID, length, assembly, and index of this contig. Returns null if the
     * contig header line does not have a length.
     */
	public SAMSequenceRecord getSAMSequenceRecord() {
        final String lengthString = this.getGenericFieldValue(LENGTH_ATTRIBUTE);
        final int length;
        if (lengthString == null) {
            length = SAMSequenceRecord.UNKNOWN_SEQUENCE_LENGTH;
        } else {
            length = Integer.parseInt(lengthString);
        }
        final SAMSequenceRecord record = new SAMSequenceRecord(this.getID(), length);
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

    @Override
    public Optional<VCFValidationFailure<VCFHeaderLine>> validateForVersion(final VCFHeaderVersion vcfTargetVersion) {
        if (vcfTargetVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3)) {
             if (!VALID_CONTIG_ID_PATTERN.matcher(getID()).matches()) {
                return Optional.of(new VCFValidationFailure<>(
                        vcfTargetVersion,
                        this,
                        String.format("Contig headerLine ID \"%s\" doesn't conform to contig ID restrictions", getID())));
            }
        }

        return super.validateForVersion(vcfTargetVersion);
    }

    public Integer getContigIndex() {
        return contigIndex;
    }

    /**
     * Note: this class has a natural ordering that is inconsistent with equals()
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
     * NOTE: this class has a natural ordering that is inconsistent with equals(). This results
     * in inconsistent behavior when these lines are used in the sets that are created/accepted
     * by VCFHeader (ie., getMetaDataInSortedOrder will filter out VCFContigHeaderLines that are
     * returned by getMetaDataInInputOrder or getContigheaderLines).
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
