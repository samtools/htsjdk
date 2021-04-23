package htsjdk.variant.variantcontext.writer.BCF2FieldWriter;

import htsjdk.variant.variantcontext.writer.BCF2Encoder;
import htsjdk.variant.vcf.VCFCompoundHeaderLine;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLineCount;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import htsjdk.variant.vcf.VCFStandardHeaderLines;

import java.util.HashMap;
import java.util.Map;

public class BCF2FieldWriterManager {

    private final Map<String, BCF2FieldWriter.SiteWriter> infoWriters;
    private final Map<String, BCF2FieldWriter.GenotypeWriter> formatWriters;

    public BCF2FieldWriterManager(final VCFHeader header, final Map<String, Integer> dict, final BCF2Encoder encoder) {
        infoWriters = new HashMap<>(header.getInfoHeaderLines().size());
        for (final VCFInfoHeaderLine line : header.getInfoHeaderLines()) {
            final String field = line.getID();
            final VCFInfoHeaderLine maybeStandardHeader = VCFStandardHeaderLines.getInfoLine(field, false);
            if (maybeStandardHeader != null) {
                validateStandardHeader(line, maybeStandardHeader);
            }
            final int offset = dict.get(field);
            final BCF2FieldWriter.SiteWriter writer = BCF2FieldWriter.createSiteWriter(line, offset, encoder);
            add(infoWriters, field, writer);
        }

        formatWriters = new HashMap<>(header.getFormatHeaderLines().size());
        for (final VCFFormatHeaderLine line : header.getFormatHeaderLines()) {
            final String field = line.getID();
            final VCFFormatHeaderLine maybeStandardHeader = VCFStandardHeaderLines.getFormatLine(field, false);
            if (maybeStandardHeader != null) {
                validateStandardHeader(line, maybeStandardHeader);
            }
            final int offset = dict.get(field);
            final BCF2FieldWriter.GenotypeWriter writer = BCF2FieldWriter.createGenotypeWriter(line, offset, encoder);
            add(formatWriters, field, writer);
        }
    }

    public BCF2FieldWriter.SiteWriter getInfoWriter(final String field) {
        return infoWriters.get(field);
    }

    public BCF2FieldWriter.GenotypeWriter getFormatWriter(final String field) {
        return formatWriters.get(field);
    }

    private <T extends BCF2FieldWriter> void add(final Map<String, T> map, final String field, final T writer) {
        if (map.containsKey(field))
            throw new IllegalStateException("BUG: field " + field + " already seen in VCFHeader while building BCF2 field encoders");
        map.put(field, writer);
    }

    private static <T extends VCFCompoundHeaderLine> void validateStandardHeader(final T actualLine, final T expectedLine) {
        // TODO should this be a hard error or just a warning?
        final VCFHeaderLineType actualType = actualLine.getType();
        final VCFHeaderLineType expectedType = expectedLine.getType();
        if (actualType != expectedType) {
            final String error = String.format("Header with standard key: `%s` has type: %s which does not match standard type: %s",
                actualLine.getID(),
                actualType,
                expectedType
            );
            System.err.println(error);
        }

        final VCFHeaderLineCount actualCountType = actualLine.getCountType();
        final VCFHeaderLineCount expectedCountType = expectedLine.getCountType();
        if (actualCountType != expectedCountType || actualLine.isFixedCount() && actualLine.getCount() != expectedLine.getCount()) {
            final String error = String.format("Header with standard key: `%s` has count: %s which does not match standard count: %s",
                actualLine.getID(),
                actualLine.isFixedCount() ? actualLine.getCount() : actualCountType,
                expectedLine.isFixedCount() ? expectedLine.getCount() : expectedCountType
            );
            System.err.println(error);
        }
    }
}
