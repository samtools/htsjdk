package htsjdk.samtools.util.htsget;

import htsjdk.samtools.util.FileExtensions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.RuntimeIOException;


/**
 * Builder for an htsget request that allows converting the request
 * to a URI after validating that it is properly formed
 */
public class HtsgetRequest {
    public static final Interval UNMAPPED_UNPLACED_INTERVAL = new Interval("*", 1, Integer.MAX_VALUE);

    private final URI endpoint;

    // Query parameters
    private HtsgetFormat format;
    private HtsgetClass dataClass;
    private Interval interval;
    private final EnumSet<HtsgetRequestField> fields;
    private final Set<String> tags;
    private final Set<String> notags;

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

    public Interval getInterval() {
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

    public void setInterval(final Interval interval) {
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

    public HtsgetRequest withInterval(final Interval interval) {
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
    private void validateRequest() {
        if (this.dataClass != null && this.dataClass == HtsgetClass.header && (
            this.interval != null ||
                !this.fields.isEmpty() ||
                !this.tags.isEmpty() ||
                !this.notags.isEmpty())) {
            throw new IllegalArgumentException("Invalid request: no query parameters except `format` may be specified when class=header");
        }

        if (this.format != null) {
            final String path = this.endpoint.getPath();
            if ((path.endsWith(FileExtensions.BAM) || path.endsWith(FileExtensions.SAM) && (
                this.format != HtsgetFormat.BAM && this.format != HtsgetFormat.CRAM))
                ||
                FileExtensions.VCF_LIST.stream().anyMatch(path::endsWith) && (
                    this.format != HtsgetFormat.VCF && this.format != HtsgetFormat.BCF)) {
                throw new IllegalArgumentException("Specified format: " + this.format + " is incompatible with id's file extension");
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
        if (this.interval != null) {
            queryParams.put("referenceName", this.interval.getContig());
            // Do not insert start and end for unmapped reads or if we are requesting the entire contig
            if (!(this.interval.getContig().equals("*"))) {
                queryParams.put("start", String.valueOf(this.interval.getStart() - 1));
                if (this.interval.getEnd() != Integer.MAX_VALUE & this.interval.getEnd() != -1) {
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
            throw new IllegalArgumentException("Could not create URI for request");
        }
    }

    /**
     * Attempt to make htsget request and return response if there are no errors
     *
     * @return the response from the htsget server if request is successful as an HtsgetResponse object
     */
    public HtsgetResponse getResponse() {
        try {
            final URI reqURI = this.toURI();
            final HttpURLConnection conn = (HttpURLConnection) reqURI.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.connect();

            final InputStream is = conn.getInputStream();
            final int statusCode = conn.getResponseCode();

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
                throw new IllegalArgumentException("Unrecognized status code: " + statusCode);
            }
        } catch (final IOException e) {
            throw new RuntimeIOException("IOException while attempting htsget download", e);
        }
    }
}
