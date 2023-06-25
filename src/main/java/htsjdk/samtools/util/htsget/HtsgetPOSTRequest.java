package htsjdk.samtools.util.htsget;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.util.Locatable;
import htsjdk.samtools.util.RuntimeIOException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Builder for an htsget POST request that allows opening a connection
 * using the request after validating that it is properly formed.
 * <p>
 * This class is experimental and subject to change as the POST api evolves.
 * <p>
 * See https://github.com/samtools/hts-specs/pull/285 for the API specification.
 */
public class HtsgetPOSTRequest extends HtsgetRequest {
    private final List<Locatable> intervals;

    /**
     * Construct an HtsgetRequest from a URI identifying a valid resource on a htsget server
     *
     * @param endpoint the full URI including both server path and the ID of the htsget resource,
     *                 without the filtering parameters defined in the htsget spec such as start or referenceName
     */

    public HtsgetPOSTRequest(final URI endpoint) {
        super(endpoint);
        this.intervals = new ArrayList<>();
    }

    public HtsgetPOSTRequest(final HtsgetRequest req) {
        super(req.getEndpoint());
        this.setFormat(req.getFormat());
        this.setDataClass(req.getDataClass());
        this.addFields(req.getFields());
        this.addTags(req.getTags());
        this.addNotags(req.getNoTags());
        this.intervals = new ArrayList<>();
        if (req.getInterval() != null) {
            this.intervals.add(req.getInterval());
        }
    }

    @Override
    public void setInterval(final Locatable interval) {
        this.intervals.clear();
        this.intervals.add(interval);
    }

    public void addInterval(final Locatable interval) {
        this.intervals.add(interval);
    }

    public void addIntervals(final Collection<Locatable> intervals) {
        this.intervals.addAll(intervals);
    }

    @Override
    public HtsgetPOSTRequest withFormat(final HtsgetFormat format) {
        this.format = format;
        return this;
    }

    @Override
    public HtsgetPOSTRequest withDataClass(final HtsgetClass dataClass) {
        this.dataClass = dataClass;
        return this;
    }

    @Override
    public HtsgetPOSTRequest withInterval(final Locatable interval) {
        this.intervals.add(interval);
        return this;
    }

    public HtsgetPOSTRequest withIntervals(final Collection<Locatable> intervals) {
        this.intervals.addAll(intervals);
        return this;
    }

    @Override
    public HtsgetPOSTRequest withField(final HtsgetRequestField field) {
        this.fields.add(field);
        return this;
    }

    @Override
    public HtsgetPOSTRequest withFields(final Collection<HtsgetRequestField> fields) {
        this.fields.addAll(fields);
        return this;
    }

    @Override
    public HtsgetPOSTRequest withTag(final String tag) {
        this.tags.add(tag);
        return this;
    }

    @Override
    public HtsgetPOSTRequest withTags(final Collection<String> tags) {
        this.tags.addAll(tags);
        return this;
    }

    @Override
    public HtsgetPOSTRequest withNotag(final String notag) {
        this.notags.add(notag);
        return this;
    }

    @Override
    public HtsgetPOSTRequest withNotags(final Collection<String> notags) {
        this.notags.addAll(notags);
        return this;
    }

    @Override
    public void validateRequest() {
        super.validateRequest();
        if (this.getDataClass() != null && this.getDataClass() == HtsgetClass.header && !this.intervals.isEmpty()) {
            throw new IllegalArgumentException("Invalid request: intervals cannot be specified when class=header");
        }
    }

    @Override
    public URI toURI() {
        return this.getEndpoint();
    }

    public JSONObject queryBody() {
        final JSONObject postBody = new JSONObject();
        if (this.format != null) {
            postBody.put("format", this.getFormat().toString());
        }
        if (this.dataClass != null) {
            postBody.put("class", this.getDataClass().toString());
        }
        if (!this.fields.isEmpty()) {
            postBody.put(
                "fields",
                new JSONArray(this.getFields().stream()
                    .map(HtsgetRequestField::toString)
                    .toArray())
            );
        }
        if (!this.tags.isEmpty()) {
            postBody.put("tags", new JSONArray(this.getTags().toArray()));
        }
        if (!this.notags.isEmpty()) {
            postBody.put("notags", new JSONArray(this.getNoTags().toArray()));
        }
        if (!this.intervals.isEmpty()) {
            postBody.put("regions", new JSONArray(
                this.intervals.stream()
                    .map(interval -> {
                        final JSONObject intervalJson = new JSONObject();
                        if (interval != null && interval.getContig() != null) {
                            intervalJson.put("referenceName", interval.getContig());
                            // Do not insert start and end for unmapped reads or if we are requesting the entire contig
                            if (!interval.getContig().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME)) {
                                // getStart() - 1 is necessary as GA4GH standards use 0-based coordinates while Locatables are 1-based
                                intervalJson.put("start", interval.getStart() - 1);
                                if (interval.getEnd() != Integer.MAX_VALUE && interval.getEnd() != -1) {
                                    intervalJson.put("end", interval.getEnd());
                                }
                            }
                        }
                        return intervalJson;
                    })
                    .toArray()
            ));
        }
        return postBody;
    }

    @Override
    protected HttpURLConnection getConnection() {
        final URI reqURI = this.getEndpoint();
        try {
            final HttpURLConnection conn = (HttpURLConnection) reqURI.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Accept", HtsgetRequest.ACCEPT_TYPE);

            conn.setDoOutput(true);
            final byte[] query = this.queryBody().toString().getBytes();
            conn.setRequestProperty("Content-Length", Integer.toString(query.length));
            conn.connect();

            conn.getOutputStream().write(query);

            return conn;
        } catch (final IOException e) {
            throw new RuntimeIOException("IOException while attempting htsget download, request: " + reqURI, e);
        }
    }
}
