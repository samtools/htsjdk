package htsjdk.beta.plugin.bundle;

import htsjdk.io.IOPath;
import htsjdk.samtools.seekablestream.SeekableStream;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;

/**
 * Interface defined for bundle resource objects that may be included in a {@link Bundle}.
 */
public interface BundleResource {
    /**
     * @return the display name for this resource
     */
    String getDisplayName();

    /**
     * @return the content type for this resource
     */
    String getContentType();

    /**
     * @return the content subtype for this resource, or Optional.empty if not present
     */
    Optional<String> getContentSubType();

    /**
     * @return an IOPath for this resource, or Optional.empty if not present
     */
    Optional<IOPath> getIOPath();

    /**
     * @return an {@link InputStream} for this resource, or Optional.empty if {@link
     * BundleResource#isInput()} is false for this resource
     */
    Optional<InputStream> getInputStream();

    /**
     * @return an {@link OutputStream} for this resource, or Optional.empty if {@link
     * BundleResource#isOutput()} is false for this resource
     */
    Optional<OutputStream> getOutputStream();

    /**
     * @return an {@link SeekableStream} for this resource, or Optional.empty if this is
     * an not an {@link BundleResource#isInput()}, or is an InputResource for which
     * no {@link SeekableStream} can be obtained
     */
    Optional<SeekableStream> getSeekableStream();

    /**
     * @return true if an InputStream can be obtained via {@link #getInputStream()} on this resource
     */
    boolean isInput();

    /**
     * @return true if an OutputStream can be obtained via {@link #getOutputStream()} on this resource
     */
    boolean isOutput();
}
