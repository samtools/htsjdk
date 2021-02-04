package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;

public class VCF43Validator {

    public static void validate(final VCFHeader header) {
        // Only validate if the header is not null and has a version which is before 4.3
        if (header == null || (header.getVCFHeaderVersion() != null
            && !header.getVCFHeaderVersion().isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3))) {
            return;
        }

        header.getMetaDataInInputOrder().forEach(VCF43Validator::validate);
    }

    public static void validate(final VCFHeaderLine headerLine) {
        if (headerLine instanceof VCFContigHeaderLine) {
            VCF43Validator.validate((VCFContigHeaderLine) headerLine);
        } else if (headerLine instanceof VCFSimpleHeaderLine) {
            VCF43Validator.validate((VCFSimpleHeaderLine) headerLine);
        } else if (headerLine instanceof VCFCompoundHeaderLine) {
            VCF43Validator.validate((VCFCompoundHeaderLine) headerLine);
        } else if (headerLine.getKey().equals(VCFConstants.PEDIGREE_HEADER_KEY)) {
            throw new TribbleException("VCFv4.3 pedigree lines must contain an ID");
        }
    }

    private static void validate(final VCFSimpleHeaderLine headerLine) {
        final String id = headerLine.getID();
        if (id.isEmpty()) {
            throw new TribbleException("VCFHeaderLine: ID cannot be empty");
        }
    }

    private static void validate(final VCFCompoundHeaderLine headerLine) {
        final String id = headerLine.getID();
        if (id.isEmpty()) {
            throw new TribbleException("VCFHeaderLine: ID cannot be empty");
        }

        final char firstChar = id.charAt(0);
        // Key cannot start with '.' or number, except for the thousand genomes key
        // This check will also include '/' but this is not allowed at all so it is not a false positive
        if ('.' <= firstChar && firstChar <= '9' && !id.equals(VCFConstants.THOUSAND_GENOMES_KEY)) {
            throw new TribbleException("VCFHeaderLine: ID cannot begin with character: " + firstChar);
        }
        for (int i = 0; i < id.length(); i++) {
            final char c = id.charAt(i);
            if (!(('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || ('0' <= c && c <= '9') || c == '_' || c == '.')) {
                throw new TribbleException("VCFHeaderLine: ID: " + id + " contains illegal character: " + c);
            }
        }
    }

    private static void validate(final VCFContigHeaderLine headerLine) {
        final String id = headerLine.getID();
         if (id.isEmpty()) {
            throw new TribbleException("VCFContigHeaderLine: ID cannot be empty");
        }
        final char firstChar = id.charAt(0);
        if (firstChar == '*' || firstChar == '=') {
            throw new TribbleException("VCFContigHeaderLine: Contig ID cannot begin with character: " + firstChar);
        }
        for (int i = 0; i < id.length(); i++) {
            final char c = id.charAt(i);
            boolean legal = false;
            if ('!' <= c && c <= '~') {
                switch(c) {
                    case '\\':
                    case ',':
                    case '"':
                    case '`': case '\'':
                    case '(': case ')':
                    case '[': case ']':
                    case '{': case '}':
                    case '<': case '>':
                        break;
                    default:
                        legal = true;
                }
            }
            if (!legal) {
                throw new TribbleException("VCFContigHeaderLine: Contig ID contains illegal character: " + c);
            }
        }
    }
}
