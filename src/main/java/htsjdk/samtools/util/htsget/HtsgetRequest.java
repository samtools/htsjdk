package htsjdk.samtools.util.htsget;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.util.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Builder for an htsget GET request that allows opening a connection
 * using the request after validating that it is properly formed.
 * <p>
 * This class currently supports version 1.2.0 of the spec as defined in https://samtools.github.io/hts-specs/htsget.html
 */
public class HtsgetRequest {
    private final static Log log = Log.getInstance(HtsgetRequest.class);
    public static final Interval UNMAPPED_UNPLACED_INTERVAL = new Interval("*", 1, Integer.MAX_VALUE);
    protected static final String PROTOCOL_VERSION = "vnd.ga4gh.htsget.v1.2.0";
    protected static final String ACCEPT_TYPE = "application/" + PROTOCOL_VERSION + "+json";

    private final URI endpoint;

    // Query parameters
    protected HtsgetFormat format;
    protected HtsgetClass dataClass;
    protected Locatable interval;
    protected final EnumSet<HtsgetRequestField> fields;
    protected final Set<String> tags;
    protected final Set<String> notags;

    /**
     * Construct an HtsgetRequest from a URI identifying a valid resource on a htsget server
     *
     * @param endpoint the full URI including both server path and the ID of the htsget resource,
     *                 without the filtering parameters defined in the htsget spec such as start or referenceName
     */
    public HtsgetRequest(final URI endpoint) {
        this.endpoint = endpoint;
        this.fields = EnumSet.noneOf(HtsgetRequestField.class);
        this.tags = new HashSet<>();
        this.notags = new HashSet<>();
    }

    public URI getEndpoint() {
        return this.endpoint;
    }

    public HtsgetFormat getFormat() {
        return this.format;
    }

    public HtsgetClass getDataClass() {
        return this.dataClass;
    }

    public Locatable getInterval() {
        return this.interval;
    }

    public Set<HtsgetRequestField> getFields() {
        return Collections.unmodifiableSet(this.fields);
    }

    public Set<String> getTags() {
        return Collections.unmodifiableSet(this.tags);
    }

    public Set<String> getNoTags() {
        return Collections.unmodifiableSet(this.notags);
    }

    public void setFormat(final HtsgetFormat format) {
        this.format = format;
    }

    public void setDataClass(final HtsgetClass dataClass) {
        this.dataClass = dataClass;
    }

    public void setInterval(final Locatable interval) {
        this.interval = interval;
    }

    public void addField(final HtsgetRequestField field) {
        this.fields.add(field);
    }

    public void addFields(final Collection<HtsgetRequestField> fields) {
        this.fields.addAll(fields);
    }

    public void addTag(final String tag) {
        this.tags.add(tag);
    }

    public void addTags(final Collection<String> tags) {
        this.tags.addAll(tags);
    }

    public void addNotag(final String notag) {
        this.notags.add(notag);
    }

    public void addNotags(final Collection<String> notags) {
        this.notags.addAll(notags);
    }

    public HtsgetRequest withFormat(final HtsgetFormat format) {
        this.format = format;
        return this;
    }

    public HtsgetRequest withDataClass(final HtsgetClass dataClass) {
        this.dataClass = dataClass;
        return this;
    }

    public HtsgetRequest withInterval(final Locatable interval) {
        this.interval = interval;
        return this;
    }

    public HtsgetRequest withField(final HtsgetRequestField field) {
        this.fields.add(field);
        return this;
    }

    public HtsgetRequest withFields(final Collection<HtsgetRequestField> fields) {
        this.fields.addAll(fields);
        return this;
    }

    public HtsgetRequest withTag(final String tag) {
        this.tags.add(tag);
        return this;
    }

    public HtsgetRequest withTags(final Collection<String> tags) {
        this.tags.addAll(tags);
        return this;
    }

    public HtsgetRequest withNotag(final String notag) {
        this.notags.add(notag);
        return this;
    }

    public HtsgetRequest withNotags(final Collection<String> notags) {
        this.notags.addAll(notags);
        return this;
    }

    /**
     * Validates that the user query obeys htsget spec
     */
    public void validateRequest() {
        if (this.dataClass != null && this.dataClass == HtsgetClass.header && (
            this.interval != null ||
                !this.fields.isEmpty() ||
                !this.tags.isEmpty() ||
                !this.notags.isEmpty())) {
            throw new IllegalArgumentException("Invalid request: no query parameters except `format` may be specified when class=header");
        }

        if (this.format != null) {
            final String path = this.endpoint.getPath();
            if ((path.endsWith(FileExtensions.BAM) || path.endsWith(FileExtensions.CRAM)) && (
                this.format != HtsgetFormat.BAM && this.format != HtsgetFormat.CRAM)) {
                throw new IllegalArgumentException("Specified reads format: " + this.format + " is incompatible with id's file extension " + path);
            }
            if (FileExtensions.VCF_LIST.stream().anyMatch(path::endsWith) && (
                this.format != HtsgetFormat.VCF && this.format != HtsgetFormat.BCF)) {
                throw new IllegalArgumentException("Specified variant format: " + this.format + " is incompatible with id's file extension " + path);
            }
        }

        final String intersections = this.tags.stream()
            .filter(getNoTags()::contains)
            .collect(Collectors.joining(", "));
        if (!intersections.isEmpty()) {
            throw new IllegalArgumentException("Invalid request: tags and notags overlap in the following fields: " + intersections);
        }
    }

    /**
     * Convert request to a URI which can be used to make http request for data blocks
     */
    public URI toURI() {
        this.validateRequest();
        final Map<String, String> queryParams = new HashMap<>();

        if (this.format != null) {
            queryParams.put("format", this.format.toString());
        }
        if (this.dataClass != null) {
            queryParams.put("class", String.valueOf(this.dataClass));
        }
        if (this.interval != null && this.interval.getContig() != null) {
            queryParams.put("referenceName", this.interval.getContig());
            // Do not insert start and end for unmapped reads or if we are requesting the entire contig
            if (!(this.interval.getContig().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME))) {
                // getStart() - 1 is necessary as GA4GH standards use 0-based coordinates while Locatables are 1-based
                queryParams.put("start", String.valueOf(this.interval.getStart() - 1));
                if (this.interval.getEnd() != Integer.MAX_VALUE && this.interval.getEnd() != -1) {
                    queryParams.put("end", String.valueOf(this.interval.getEnd()));
                }
            }
        }
        if (!this.fields.isEmpty()) {
            queryParams.put(
                "fields",
                this.fields.stream().map(HtsgetRequestField::toString).collect(Collectors.joining(",")));
        }
        if (!this.tags.isEmpty()) {
            queryParams.put("tags", String.join(",", this.tags));
        }
        if (!this.notags.isEmpty()) {
            queryParams.put("notags", String.join(",", this.notags));
        }
        try {
            final String queryString = queryParams.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));

            final String updatedQuery = this.endpoint.getQuery() == null
                ? (queryString.isEmpty() ? null : queryString)
                : this.endpoint.getQuery() + "&" + queryString;

            return new URI(this.endpoint.getScheme(),
                this.endpoint.getUserInfo(), this.endpoint.getHost(), this.endpoint.getPort(),
                this.endpoint.getPath(), updatedQuery,
                this.endpoint.getFragment());
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException("Could not create URI for request", e);
        }
    }

    protected HttpURLConnection getConnection() {
        final URI reqURI = this.toURI();
        try {
            final HttpURLConnection conn = (HttpURLConnection) reqURI.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", HtsgetRequest.ACCEPT_TYPE);
            conn.connect();

            return conn;
        } catch (final IOException e) {
            throw new RuntimeException("IOException while attempting htsget download, request: " + reqURI, e);
        }
    }

    /**
     * Attempt to make htsget request and return response if there are no errors
     *
     * @return the response from the htsget server if request is successful as an HtsgetResponse object
     */
    public HtsgetResponse getResponse() {
        try {
            final HttpURLConnection conn = this.getConnection();
            final InputStream is = conn.getInputStream();
            final int statusCode = conn.getResponseCode();
            final String respContentType = conn.getContentType();
            if (respContentType != null &&
                !respContentType.isEmpty() &&
                !respContentType.contains(HtsgetRequest.PROTOCOL_VERSION)) {
                log.warn("Supported htsget protocol version: " + HtsgetRequest.PROTOCOL_VERSION +
                    "may not be compatible with received content type: " + respContentType);
            }

            final BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            final StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line);
            }
            final String json = out.toString();

            if (400 <= statusCode && statusCode < 500) {
                final HtsgetErrorResponse err = HtsgetErrorResponse.parse(json);
                throw new IllegalArgumentException(
                    "Invalid request, received error code: " + statusCode +
                        ", error type: " + err.getError() +
                        ", message: " + err.getMessage());
            } else if (statusCode == 200) {
                return HtsgetResponse.parse(json);
            } else {
                throw new IllegalStateException("Unrecognized status code: " + statusCode);
            }
        } catch (final IOException e) {
            throw new RuntimeIOException("IOException while attempting htsget download", e);
        }
    }
}
