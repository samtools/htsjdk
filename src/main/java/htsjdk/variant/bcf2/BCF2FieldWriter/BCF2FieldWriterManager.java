package htsjdk.variant.bcf2.BCF2FieldWriter;

import htsjdk.samtools.util.Log;
import htsjdk.tribble.TribbleException;
import htsjdk.variant.bcf2.BCF2Encoder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFCompoundHeaderLine;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLineCount;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import htsjdk.variant.vcf.VCFStandardHeaderLines;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BCF2FieldWriterManager {
    private static final Log log = Log.getInstance(BCF2FieldWriterManager.class);

    private final Map<String, BCF2FieldWriter.SiteWriter> infoWriters;
    private final Map<String, BCF2FieldWriter.GenotypeWriter> formatWriters;
    private final List<String> sampleNames;

    public BCF2FieldWriterManager(final VCFHeader header, final Map<String, Integer> dict, final BCF2Encoder encoder) {
        infoWriters = new HashMap<>(header.getInfoHeaderLines().size());
        for (final VCFInfoHeaderLine line : header.getInfoHeaderLines()) {
            final String field = line.getID();
            validateStandardHeader(line, VCFStandardHeaderLines.getInfoLine(field, false));
            final int offset = dict.get(field);
            final BCF2FieldWriter.SiteWriter writer = BCF2FieldWriter.createSiteWriter(line, offset, encoder);
            infoWriters.put(field, writer);
        }

        formatWriters = new HashMap<>(header.getFormatHeaderLines().size());
        for (final VCFFormatHeaderLine line : header.getFormatHeaderLines()) {
            final String field = line.getID();
            validateStandardHeader(line, VCFStandardHeaderLines.getFormatLine(field, false));
            final int offset = dict.get(field);
            final BCF2FieldWriter.GenotypeWriter writer = BCF2FieldWriter.createGenotypeWriter(line, offset, encoder);
            formatWriters.put(field, writer);
        }

        sampleNames = header.getGenotypeSamples();
    }

    public void writeInfo(final VariantContext vc) throws IOException {
        for (final String field : vc.getAttributes().keySet()) {
            final BCF2FieldWriter.SiteWriter writer = infoWriters.get(field);
            if (writer == null) errorUnexpectedFieldToWrite(vc, field, "INFO");
            writer.encodeKey();
            writer.encode(vc);
        }
    }

    public void writeFormat(final VariantContext vc, final List<String> genotypeFields) throws IOException {
        for (final String field : genotypeFields) {
            final BCF2FieldWriter.GenotypeWriter writer = formatWriters.get(field);
            if (writer == null) errorUnexpectedFieldToWrite(vc, field, "FORMAT");
            writer.encodeKey();
            writer.encode(vc, sampleNames);
        }
    }

    private static <T extends VCFCompoundHeaderLine> void validateStandardHeader(
        final T actualLine,
        final T expectedLine
    ) {
        if (expectedLine == null) return;
        final VCFHeaderLineType actualType = actualLine.getType();
        final VCFHeaderLineType expectedType = expectedLine.getType();
        if (actualType != expectedType) {
            log.error(String.format(
                "Header with standard key: `%s` has type: %s which does not match standard type: %s",
                actualLine.getID(),
                actualType,
                expectedType
            ));
        }

        final VCFHeaderLineCount actualCountType = actualLine.getCountType();
        final VCFHeaderLineCount expectedCountType = expectedLine.getCountType();
        if (actualCountType != expectedCountType || actualLine.isFixedCount() && actualLine.getCount() != expectedLine.getCount()) {
            log.error(String.format(
                "Header with standard key: `%s` has count: %s which does not match standard count: %s",
                actualLine.getID(),
                actualLine.isFixedCount() ? actualLine.getCount() : actualCountType,
                expectedLine.isFixedCount() ? expectedLine.getCount() : expectedCountType
            ));
        }
    }

    private static void errorUnexpectedFieldToWrite(
        final VariantContext vc,
        final String field,
        final String fieldType
    ) {
        throw new TribbleException(String.format(
            "Found %s field %s of VariantContext at %s:%d from %s that has not been defined in the VCFHeader",
            fieldType, field,
            vc.getContig(), vc.getStart(), vc.getSource()
        ));
    }
}
