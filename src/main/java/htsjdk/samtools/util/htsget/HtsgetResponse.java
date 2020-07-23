package htsjdk.samtools.util.htsget;

import htsjdk.samtools.util.RuntimeIOException;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class allowing deserialization from json htsget response
 */
public class HtsgetResponse {
    public static class Block {
        private URI uri;

        private Map<String, String> headers;

        private HtsgetClass dataClass;

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
                    throw new IllegalArgumentException("Unrecognized URI scheme in data block: " + this.uri.getScheme());
            }
        }

        /**
         * Parse a single data block from a json value
         *
         * @param blockJson json value representing a block
         * @return parsed block object
         */
        public static Block parse(final mjson.Json blockJson) {
            final Block block = new Block();

            final mjson.Json uri = blockJson.at("url");
            if (uri == null) {
                throw new IllegalArgumentException("No URI found in Htsget data block");
            }
            try {
                block.uri = new URI(uri.asString());
            } catch (final URISyntaxException e) {
                throw new IllegalArgumentException("Failed to parse URI in Htsget data block", e);
            }

            final mjson.Json dataClass = blockJson.at("class");
            if (dataClass != null) {
                block.dataClass = HtsgetClass.valueOf(dataClass.asString().toLowerCase());
            }

            final mjson.Json headers = blockJson.at("headers");
            if (headers != null) {
                block.headers = headers.asJsonMap().entrySet().stream()
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().asString()
                    ));
            }

            return block;
        }
    }

    private HtsgetFormat format;

    private List<Block> blocks;

    private String md5;

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
        final HtsgetResponse resp = new HtsgetResponse();
        final mjson.Json j = mjson.Json.read(s);
        final mjson.Json htsget = j.at("htsget");

        final mjson.Json md5 = htsget.at("md5");
        if (md5 != null) {
            resp.md5 = md5.asString();
        }

        final mjson.Json htsgetClass = htsget.at("format");
        if (htsgetClass != null) {
            resp.format = HtsgetFormat.valueOf(htsgetClass.asString().toUpperCase());
        }

        final mjson.Json blocksJson = htsget.at("urls");
        if (blocksJson == null) {
            throw new IllegalArgumentException("No urls field found in Htsget Response");
        }

        resp.blocks = blocksJson.asJsonList().stream()
            .map(Block::parse)
            .collect(Collectors.toList());

        return resp;
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
