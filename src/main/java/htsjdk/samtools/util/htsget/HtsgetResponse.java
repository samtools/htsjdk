package htsjdk.samtools.util.htsget;

import htsjdk.samtools.util.RuntimeIOException;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class allowing deserialization from json htsget response, as defined in https://samtools.github.io/hts-specs/htsget.html
 * <p>
 * This class currently supports version 1.2.0 of the spec
 * <p>
 * An example response could be as follows
 * {
 * "htsget" : {
 * "format" : "BAM",
 * "urls" : [
 * {
 * "url" : "data:application/vnd.ga4gh.bam;base64,QkFNAQ==",
 * "class" : "header"
 * },
 * {
 * "url" : "https://htsget.blocksrv.example/sample1234/run1.bam",
 * "headers" : {
 * "Authorization" : "Bearer xxxx",
 * "Range" : "bytes=65536-1003750"
 * },
 * "class" : "body"
 * }
 * ],
 * "md5": "abcd"
 * }
 * }
 */
public class HtsgetResponse {
    public static class Block {
        private final URI uri;

        private final Map<String, String> headers;

        private final HtsgetClass dataClass;

        public Block(final URI uri, final Map<String, String> headers, final HtsgetClass dataClass) {
            this.uri = uri;
            this.headers = headers;
            this.dataClass = dataClass;
        }

        public URI getUri() {
            return this.uri;
        }

        public Map<String, String> getHeaders() {
            return Collections.unmodifiableMap(this.headers);
        }

        public HtsgetClass getDataClass() {
            return this.dataClass;
        }

        /**
         * Gets data from this block either from its base64 encoded data url or by making an http request
         *
         * @return InputStream of data from this block
         */
        public InputStream getData() {
            switch (this.getUri().getScheme()) {
                case "http":
                case "https":
                    try {
                        final HttpURLConnection conn = (HttpURLConnection) this.uri.toURL().openConnection();
                        conn.setRequestMethod("GET");
                        this.headers.forEach(conn::setRequestProperty);
                        conn.connect();
                        return conn.getInputStream();
                    } catch (final IOException e) {
                        throw new RuntimeIOException("Could not retrieve data from block", e);
                    }
                case "data":
                    final String dataUri = this.uri.toString();
                    if (!dataUri.matches("^data:.*;base64,.*")) {
                        throw new IllegalArgumentException("data URI must be base64 encoded: " + dataUri);
                    }
                    return new ByteArrayInputStream(
                        Base64.getDecoder().decode(dataUri.replaceFirst("^data:.*;base64,", "")));
                default:
                    throw new IllegalStateException(new HtsgetMalformedResponseException("Unrecognized URI scheme in data block: " + this.uri.getScheme()));
            }
        }

        /**
         * Parse a single data block from a json value
         *
         * @param blockJson json value representing a block
         * @return parsed block object
         */
        public static Block parse(final mjson.Json blockJson) {
            final mjson.Json uriJson = blockJson.at("url");
            if (uriJson == null) {
                throw new IllegalArgumentException(
                    blockJson.toString().substring(0, Math.min(100, blockJson.toString().length())),
                    new HtsgetMalformedResponseException("No URI found in Htsget data block"));
            }
            final URI uri;
            try {
                uri = new URI(uriJson.asString());
            } catch (final URISyntaxException e) {
                throw new IllegalArgumentException(
                    blockJson.toString().substring(0, Math.min(100, blockJson.toString().length())),
                    new HtsgetMalformedResponseException("Could not parse URI in Htsget data block", e));
            }

            blockJson.asJsonMap().entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase("nnsr"))
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);

            final mjson.Json dataClassJson = blockJson.at("class");
            final HtsgetClass dataClass = dataClassJson == null
                ? null
                : HtsgetClass.valueOf(dataClassJson.asString().toLowerCase());


            final mjson.Json headersJson = blockJson.at("headers");
            final Map<String, String> headers = headersJson == null
                ? null
                : headersJson.asJsonMap().entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().asString()
                ));

            return new Block(uri, headers, dataClass);
        }
    }

    public HtsgetResponse(final HtsgetFormat format, final List<Block> blocks, final String md5) {
        this.format = format;
        this.blocks = blocks;
        this.md5 = md5;
    }

    private final HtsgetFormat format;

    private final List<Block> blocks;

    private final String md5;

    public HtsgetFormat getFormat() {
        return this.format;
    }

    public List<Block> getBlocks() {
        return Collections.unmodifiableList(this.blocks);
    }

    public String getMd5() {
        return this.md5;
    }

    /**
     * Parses HtsgetResponse object from json string
     *
     * @param s json string
     * @return parsed HtsgetResponse object
     */
    public static HtsgetResponse parse(final String s) {
        final mjson.Json j = mjson.Json.read(s);
        final mjson.Json htsget = j.at("htsget");
        if (htsget == null) {
            throw new IllegalStateException(new HtsgetMalformedResponseException("No htsget key found in response"));
        }

        final mjson.Json md5Json = htsget.at("md5");
        final mjson.Json formatJson = htsget.at("format");

        final mjson.Json blocksJson = htsget.at("urls");
        if (blocksJson == null) {
            throw new IllegalArgumentException("No urls field found in Htsget Response");
        }

        final List<Block> blocks = blocksJson.asJsonList().stream()
            .map(Block::parse)
            .collect(Collectors.toList());

        return new HtsgetResponse(
            formatJson == null ? null : HtsgetFormat.valueOf(formatJson.asString().toUpperCase()),
            blocks,
            md5Json == null ? null : md5Json.asString()
        );
    }

    /**
     * Lazily generates an InputStream over this response's data from the concatenation of the InputStreams from
     * each of the response's data blocks
     *
     * @return InputStream over this response's data
     */
    public InputStream getDataStream() {
        return new SequenceInputStream(new Enumeration<InputStream>() {
            private final Iterator<Block> iterator = HtsgetResponse.this.blocks.iterator();

            @Override
            public boolean hasMoreElements() {
                return this.iterator.hasNext();
            }

            @Override
            public InputStream nextElement() {
                return this.iterator.next().getData();
            }
        });
    }
}
