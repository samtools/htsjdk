package htsjdk.samtools.cram.ref;

import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class EnaRefService {
    private static final Log log = Log.getInstance(EnaRefService.class);
    private static final int HTTP_OK = HttpURLConnection.HTTP_OK;
    private static final int HTTP_FOUND = 302;
    private static final int HTTP_NOT_FOUND = HttpURLConnection.HTTP_NOT_FOUND;
    private static final int HTTP_INTERNAL_SEVER_PROBLEM = HttpURLConnection.HTTP_INTERNAL_ERROR;
    // this is a non-standard code and I'm not sure why it's here
    private static final int HTTP_CONNECTION_TIMEOUT = 522;
    private static final int HTTP_MOVED_PERMANENTLY = HttpURLConnection.HTTP_MOVED_PERM;

    byte[] getSequence(final String md5) {
        final int restBetweenTries_ms = 0;
        final int maxTries = 1;
        final int timeout_ms = 0;
        return getSequence(md5, timeout_ms, maxTries, restBetweenTries_ms);
    }

    /**
     * Tries to download sequence bases using its md5 checksum. This method can
     * try downloading the sequence many times before giving up.
     *
     * @param md5                 MD5 checksum string of the sequence to download
     * @param timeoutMs          timeout in milliseconds before failing with the {@link EnaRefService.GaveUpException}
     * @param maxTries            maximum number of tries before failing with the {@link EnaRefService.GaveUpException}
     * @param restBetweenTriesMs wait this number of milliseconds before repeating attempt
     * @return sequence bases or null if there is no sequence with such md5
     * @throws GaveUpException if the sequence could not be downloaded within the time/try
     *                         limit.
     */
    byte[] getSequence(final String md5, final long timeoutMs, int maxTries, final long restBetweenTriesMs) {
        if (md5 == null)
            throw new NullPointerException("Expecting sequence md5 but got null.");
        if (!md5.matches("[a-z0-9]{32}"))
            throw new RuntimeException("Does not look like an md5 checksum: " + md5);

        // from https://www.ebi.ac.uk/ena/software/cram-reference-registry
        final String httpEbiString = "https://www.ebi.ac.uk/ena/cram/md5/%s";
        final String urlString = String.format(httpEbiString, md5);
        final URL url;
        try {
            url = new URL(urlString);
        } catch (final MalformedURLException e) {
            throw new RuntimeException("Invalid sequence url: " + urlString, e);
        }

        InputStream inputStream = null;
        final long startTime = System.currentTimeMillis();
        do {
            try {
                final HttpURLConnection http = (HttpURLConnection) url.openConnection();
                final int readTimeoutMs = 0;
                http.setReadTimeout(readTimeoutMs);
                final int code = http.getResponseCode();
                switch (code) {
                    case HTTP_OK:
                        inputStream = http.getInputStream();
                        if (inputStream == null)
                            throw new RuntimeException("Failed to download sequence for md5: " + md5);

                        log.info("Downloading reference sequence: " + urlString);
                        final byte[] bases = InputStreamUtils.readFully(inputStream);
                        log.info("Downloaded " + bases.length + " bases.");
                        return bases;
                    case HTTP_NOT_FOUND:
                        return null;
                    case HTTP_CONNECTION_TIMEOUT:
                    case HTTP_INTERNAL_SEVER_PROBLEM:
                        break;
                    case HTTP_MOVED_PERMANENTLY:
                        log.error("It seems that the base URL for the ENA service has changed permanently. Got error:" + code +
                        "\n Please contact the HtsJdk developers at www.github.com/samtools/htsjdk. \n" +
                                "Tried to access "+ url + "\n" +
                                "Response header: " + http.getHeaderFields().toString());
                        throw new RuntimeException("Bad http status code: " + code);
                    default:
                        throw new RuntimeException("Unknown http status code: " + code);
                }

                if (startTime - System.currentTimeMillis() < timeoutMs && maxTries > 1 && restBetweenTriesMs > 0)
                    try {
                        Thread.sleep(restBetweenTriesMs);
                    } catch (final InterruptedException e) {
                        throw new RuntimeException(e);
                    }

            } catch (final IOException e) {
                log.error("Connection attempt failed: " + e.getMessage());
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (final Exception e) {
                        log.error(e.getMessage());
                    }
                }
            }

        } while (startTime - System.currentTimeMillis() < timeoutMs && --maxTries > 0);

        throw new GaveUpException(md5);
    }

    public static class GaveUpException extends RuntimeException {
        private static final long serialVersionUID = -8997576068346912410L;
        private String md5;

        public GaveUpException(final String md5) {
            this.setMd5(md5);
        }

        public String getMd5() {
            return md5;
        }

        public void setMd5(final String md5) {
            this.md5 = md5;
        }
    }
}
