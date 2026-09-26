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
import htsjdk.tribble.TribbleException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A special class representing a contig VCF header line.  Knows the true contig order and sorts on that
 *
 * Note: this class has a natural ordering that is inconsistent with equals()
 *
 * @author mdepristo
 */
public class VCFContigHeaderLine extends VCFSimpleHeaderLine {
    final Integer contigIndex;

    /**
     * create a VCF contig header line
     *
     * @param line      the header line
     * @param version   the vcf header version
     * @param key            the key for this header line
     */
    public VCFContigHeaderLine(
            final String line, final VCFHeaderVersion version, final String key, final int contigIndex) {
        super(line, version, key, null, Collections.emptyList());
        if (contigIndex < 0) throw new TribbleException("The contig index is less than zero.");
        this.contigIndex = contigIndex;
    }

    public VCFContigHeaderLine(final Map<String, String> mapping, final int contigIndex) {
        super(VCFHeader.CONTIG_KEY, mapping);
        if (contigIndex < 0) throw new TribbleException("The contig index is less than zero.");
        this.contigIndex = contigIndex;
    }

    VCFContigHeaderLine(final SAMSequenceRecord sequenceRecord, final String assembly) {
        super(VCFHeader.CONTIG_KEY, fieldsFor(sequenceRecord, assembly));
        this.contigIndex = sequenceRecord.getSequenceIndex();
    }

    /**
     * The fields of the contig line for a sequence record, in the order they are written: ID and length, then each of
     * assembly, md5, species and URL that is present. These are the contig keys with {@code @SQ} equivalents (AS, M5,
     * SP and UR); the record's other tags have no key in VCF.
     */
    private static Map<String, String> fieldsFor(final SAMSequenceRecord sequenceRecord, final String assembly) {
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put("ID", sequenceRecord.getSequenceName());
        fields.put("length", Integer.toString(sequenceRecord.getSequenceLength()));
        if (assembly != null) fields.put("assembly", assembly);
        if (sequenceRecord.getMd5() != null) fields.put("md5", sequenceRecord.getMd5());
        if (sequenceRecord.getSpecies() != null) fields.put("species", sequenceRecord.getSpecies());
        final String url = sequenceRecord.getAttribute(SAMSequenceRecord.URI_TAG);
        if (url != null) fields.put("URL", url);
        return fields;
    }

    public Integer getContigIndex() {
        return contigIndex;
    }

    /**
     * Get the SAMSequenceRecord that corresponds to this VCF header line.
     * If the VCF header line does not have a length tag, the SAMSequenceRecord returned will be set to have a length of
     * SAMSequenceRecord.UNKNOWN_SEQUENCE_LENGTH. Records with unknown length will match any record with the same name
     * when evaluated by SAMSequenceRecord.isSameSequence.
     * @return The SAMSequenceRecord containing the ID, length, assembly, md5, URL, species and index of this contig.
     * Returns null if the contig header line does not have a length.
     */
    public SAMSequenceRecord getSAMSequenceRecord() {
        final String lengthString = this.getGenericFieldValue("length");
        final int length;
        if (lengthString == null) {
            length = SAMSequenceRecord.UNKNOWN_SEQUENCE_LENGTH;
        } else {
            length = Integer.parseInt(lengthString);
        }
        final SAMSequenceRecord record = new SAMSequenceRecord(this.getID(), length);
        // a null value leaves the tag out
        record.setAssembly(this.getGenericFieldValue("assembly"));
        record.setMd5(this.getGenericFieldValue("md5"));
        record.setAttribute(SAMSequenceRecord.URI_TAG, this.getGenericFieldValue("URL"));
        record.setSpecies(this.getGenericFieldValue("species"));
        record.setSequenceIndex(this.contigIndex);
        return record;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass() || !super.equals(o)) {
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
     */
    @Override
    public int compareTo(final Object other) {
        if (other instanceof VCFContigHeaderLine)
            return contigIndex.compareTo(((VCFContigHeaderLine) other).contigIndex);
        else {
            return super.compareTo(other);
        }
    }
}
