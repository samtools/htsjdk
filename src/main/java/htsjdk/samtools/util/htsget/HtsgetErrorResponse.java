package htsjdk.samtools.util.htsget;


/**
 * Class allowing deserialization from json htsget error response, as defined in https://samtools.github.io/hts-specs/htsget.html
 *
 * An example response could be as follows
 * {
 *     "htsget": {
 *         "error": "NotFound",
 *         "message": "No such accession 'ENS16232164'"
 *     }
 * }
 */
public class HtsgetErrorResponse {
    private final String error;
    private final String message;

    public HtsgetErrorResponse(final String error, final String message) {
        this.error = error;
        this.message = message;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public static HtsgetErrorResponse parse(final String s) {
        final mjson.Json j = mjson.Json.read(s);
        final mjson.Json htsget = j.at("htsget");
        if (htsget == null) {
            throw new IllegalStateException(new HtsgetMalformedResponseException("No htsget key found in response"));
        }

        final mjson.Json errorJson = htsget.at("error");
        final mjson.Json messageJson = htsget.at("message");

        return new HtsgetErrorResponse(
            errorJson == null ? null : errorJson.asString(),
            messageJson == null ? null : messageJson.asString());
    }
}
