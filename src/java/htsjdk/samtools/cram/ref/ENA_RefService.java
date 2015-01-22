package htsjdk.samtools.cram.ref;

import htsjdk.samtools.cram.io.ByteBufferUtils;
import net.sf.picard.util.Log;
import net.sf.picard.util.Log.LogLevel;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class ENA_RefService {
    private static final Log log = Log.getInstance(ENA_RefService.class);
    private String HTTP_WWW_EBI_AC_UK_ENA_CRAM_MD5_S = "http://www.ebi.ac.uk/ena/cram/md5/%s";
    private static final int HTTP_OK = 200;
    private static final int HTTP_FOUND = 302;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_INTERNAL_SEVER_PROBLEM = 500;
    private static final int HTTP_CONNECTION_TIMEOUT = 522;

    private int timeout_ms = 0;
    private int maxTries = 1;
    private int read_timeout_ms = 0;
    private int restBetweenTries_ms = 0;

    public static void main(String[] args) throws IOException {
        Log.setGlobalLogLevel(LogLevel.INFO);
        test("57151e6196306db5d9f33133572a5482");
        test("0000088cbcebe818eb431d58c908c698");
    }

    private static void test(String md5) throws IOException {
        byte[] bases;
        try {
            bases = new ENA_RefService().getSequence(md5);
            if (bases == null)
                log.error("Not found");
        } catch (GaveUpException e) {
            log.error("Gave up to download sequence for md5: " + md5);
        }
    }

    public byte[] getSequence(String md5) throws IOException, GaveUpException {
        return getSequence(md5, timeout_ms, maxTries, restBetweenTries_ms);
    }

    /**
     * Tries to download sequence bases using its md5 checksum. This method can
     * try downloading the sequence many times before giving up.
     *
     * @param md5
     * @param timeout_ms
     * @param maxTries
     * @param restBetweenTries_ms
     * @return sequence bases or null if there is no sequence with such md5
     * @throws IOException     if reading from the url fails
     * @throws GaveUpException if the sequence could not be downloaded within the time/try
     *                         limit.
     */
    public byte[] getSequence(String md5, long timeout_ms, int maxTries, long restBetweenTries_ms) throws IOException,
            GaveUpException {
        if (md5 == null)
            throw new NullPointerException("Expecting sequence md5 but got null.");
        if (!md5.matches("[a-z0-9]{32}"))
            throw new RuntimeException("Does not look like an md5 checksum: " + md5);

        String urlString = String.format(HTTP_WWW_EBI_AC_UK_ENA_CRAM_MD5_S, md5);
        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid sequnce url: " + urlString, e);
        }

        InputStream is = null;
        long startTime = System.currentTimeMillis();
        do {
            try {
                HttpURLConnection http = (HttpURLConnection) url.openConnection();
                http.setReadTimeout(read_timeout_ms);
                int code = http.getResponseCode();
                switch (code) {
                    case HTTP_OK:
                        is = http.getInputStream();
                        if (is == null)
                            throw new RuntimeException("Failed to download sequence for md5: " + md5);

                        log.info("Downloading reference sequence: " + urlString);
                        byte[] bases = ByteBufferUtils.readFully(is);
                        log.info("Downloaded " + bases.length + " bases.");
                        return bases;
                    case HTTP_NOT_FOUND:
                        return null;
                    case HTTP_CONNECTION_TIMEOUT:
                    case HTTP_INTERNAL_SEVER_PROBLEM:
                        break;
                    default:
                        throw new RuntimeException("Unknown http status code: " + code);
                }

                if (startTime - System.currentTimeMillis() < timeout_ms && maxTries > 1 && restBetweenTries_ms > 0)
                    try {
                        Thread.sleep(restBetweenTries_ms);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

            } catch (IOException e) {
                log.error("Connection attempt failed: " + e.getMessage());
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (Exception e) {
                        log.error(e.getMessage());
                    }
                }
            }

        } while (startTime - System.currentTimeMillis() < timeout_ms && --maxTries > 0);

        throw new GaveUpException(md5);
    }

    public static class GaveUpException extends Exception {
        private static final long serialVersionUID = -8997576068346912410L;
        private String md5;

        public GaveUpException(String md5) {
            this.setMd5(md5);
        }

        public String getMd5() {
            return md5;
        }

        public void setMd5(String md5) {
            this.md5 = md5;
        }
    }
}
