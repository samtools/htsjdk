package htsjdk.samtools.util.htsget;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.util.Locatable;
import mjson.Json;

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

    public mjson.Json queryBody() {
        final mjson.Json postBody = Json.object();
        if (this.getFormat() != null) {
            postBody.set("format", this.getFormat().toString());
        }
        if (this.getClass() != null) {
            postBody.set("class", this.getDataClass().toString());
        }
        if (!this.getFields().isEmpty()) {
            postBody.set(
                "fields",
                Json.array(this.getFields().stream()
                    .map(HtsgetRequestField::toString)
                    .toArray())
            );
        }
        if (!this.getTags().isEmpty()) {
            postBody.set("tags", Json.array(this.getTags().toArray()));
        }
        if (!this.getNoTags().isEmpty()) {
            postBody.set("notags", Json.array(this.getNoTags().toArray()));
        }
        if (!this.intervals.isEmpty()) {
            postBody.set("regions", Json.array(
                this.intervals.stream()
                    .map(interval -> {
                        final mjson.Json intervalJson = Json.object();
                        if (interval != null && interval.getContig() != null) {
                            intervalJson.set("referenceName", interval.getContig());
                            // Do not insert start and end for unmapped reads or if we are requesting the entire contig
                            if (!interval.getContig().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME)) {
                                // getStart() - 1 is necessary as GA4GH standards use 0-based coordinates while Locatables are 1-based
                                intervalJson.set("start", interval.getStart() - 1);
                                if (interval.getEnd() != Integer.MAX_VALUE && interval.getEnd() != -1) {
                                    intervalJson.set("end", interval.getEnd());
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
            conn.connect();

            final byte[] query = this.queryBody().toString().getBytes();

            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Length", Integer.toString(query.length));
            conn.getOutputStream().write(query);

            return conn;
        } catch (final IOException e) {
            throw new RuntimeException("IOException while attempting htsget download, request: " + reqURI, e);
        }
    }
}
