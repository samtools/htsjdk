package htsjdk.samtools.util.htsget;


/**
 * Class allowing deserialization from json htsget error response
 */
public class HtsgetErrorResponse {
    private String error;
    private String message;

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public static HtsgetErrorResponse parse(final String s) {
        final HtsgetErrorResponse resp = new HtsgetErrorResponse();
        final mjson.Json j = mjson.Json.read(s);
        final mjson.Json htsget = j.at("htsget");

        final mjson.Json errorJson = htsget.at("error");
        if (errorJson != null) {
            resp.error = errorJson.asString();
        }

        final mjson.Json messageJson = htsget.at("message");
        if (errorJson != null) {
            resp.message = messageJson.asString();
        }
        return resp;
    }
}
