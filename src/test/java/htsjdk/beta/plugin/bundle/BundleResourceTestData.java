package htsjdk.beta.plugin.bundle;

import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class BundleResourceTestData {
    public final static IOPath READS_FILE = new HtsPath("file://myreads.bam");
    public final static IOPath READS_INDEX = new HtsPath("file://myreads.bai");

    public static final IOPathResource readsWithContentSubType = new IOPathResource(
            READS_FILE,
            BundleResourceType.ALIGNED_READS,
            BundleResourceType.READS_BAM);
    public static final IOPathResource readsNoContentSubType =new IOPathResource(
            READS_FILE,
            BundleResourceType.ALIGNED_READS);
    public static final IOPathResource indexWithContentSubType = new IOPathResource(
            READS_INDEX,
            BundleResourceType.READS_INDEX,
            BundleResourceType.READS_INDEX_BAI);
    public static final IOPathResource indexNoContentSubType = new IOPathResource(
            READS_INDEX,
            BundleResourceType.READS_INDEX);

    public final static class CustomHtsPath extends HtsPath {
        public CustomHtsPath(final String pathString) {
            super(pathString);
        }
    }

    // streams for making bundle resources that never need closing
    public static final InputStream fakeInputStream = new InputStream() {
        @Override
        public int read() throws IOException {
            // should never get here
            throw new IllegalStateException();
        }
    };
    public static final OutputStream fakeOutputStream = new OutputStream() {
        @Override
        public void write(int b) throws IOException {
            // should never get here
            throw new IllegalStateException();
        }
    };

}
