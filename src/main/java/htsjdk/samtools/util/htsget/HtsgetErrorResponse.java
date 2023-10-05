package htsjdk.samtools.util.htsget;


import org.json.JSONObject;

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
        final JSONObject j = new JSONObject(s);
        final JSONObject htsget = j.optJSONObject("htsget");
        if (htsget == null) {
            throw new IllegalStateException(new HtsgetMalformedResponseException("No htsget key found in response"));
        }

        final String errorJson = htsget.optString("error", null);
        final String messageJson = htsget.optString("message", null);

        return new HtsgetErrorResponse(errorJson, messageJson);
    }
}
