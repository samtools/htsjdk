package htsjdk.tribble.gff;

/**
 * Common constants shared by GtfCodec and Gff3Codec
 */
public class AbstractGxxConstants {
    public static final char ATTRIBUTE_DELIMITER = ';';
    public static final char FIELD_DELIMITER = '\t';
    public static final String COMMENT_START = "#";
    public static final String DIRECTIVE_START = "##";
    public static final String UNDEFINED_FIELD_VALUE = ".";
    public final static char END_OF_LINE_CHARACTER = '\n';
}