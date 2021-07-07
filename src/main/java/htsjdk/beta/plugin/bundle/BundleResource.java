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
     * Get the display name for this resource.
     *
     * @return the display name for this resource
     */
    String getDisplayName();

    /**
     * Get the content type for this resource.
     *
     * @return a string representing name of the content type for this resource
     */
    String getContentType();

    /**
     * Get the file format for this resource.
     *
     * @return the file format for this resource, or Optional.empty if not present
     */
    Optional<String> getFileFormat();

    /**
     * Get the {@link IOPath} backing this resource, or Optional.empty.
     *
     * @return the {@link IOPath} backing this resource, or Optional.empty if the resource has no backing
     * {@link IOPath}
     */
    Optional<IOPath> getIOPath();

    /**
     * Get an {@link InputStream} for this resource, or Optional.empty.
     *
     * @return an {@link InputStream} for this resource, or Optional.empty if {@link
     * BundleResource#hasInputType()} is false for this resource.
     *
     * The stream returned by this method may be buffered or use read-ahead, which may not be
     * suitable for applications such as index creation that need control over the position of the
     * underlying stream.
     */
    Optional<InputStream> getInputStream();

    /**
     * Get an {@link OutputStream} for this resource, or Optional.empty.
     *
     * @return an {@link OutputStream} for this resource, or Optional.empty if {@link
     * BundleResource#hasOutputType()} is false for this resource
     */
    Optional<OutputStream> getOutputStream();

    /**
     * Get a {@link SeekableStream} for this resource, or Optional.empty.
     *
     * @return an {@link SeekableStream} for this resource, or Optional.empty if this is not an input
     * type (see {@link BundleResource#hasInputType()}), or is an input type for which no {@link SeekableStream}
     * can be obtained (see {@link #hasSeekableStream}).
     */
    Optional<SeekableStream> getSeekableStream();

    /**
     * Get a {@link SignatureStream} for this resource.
     *
     * This method requires access to the first {@code signatureProbeLength} bytes of the underlying
     * resource. {@link BundleResource} implementations that are backed by raw streams that can only be consumed
     * once, such as {@link InputStreamResource}, may consume and buffer a portion of the underlying resource's
     * stream in order to allow subsequent callers of the {@link #getInputStream()}) method to be presented with
     * the entire stream, including the signature. Calls to this method may have the side effect of changing
     * or resetting the current position of the underlying stream; serial calls to
     * {@link #getSignatureStream} on the same object are not necessarily idempotent; and implementations
     * are free to throw to prevent serial calls to this method.
     *
     * @param signatureProbeLength the number of bytes of the underlying resource to include in the
     *                             {@link SignatureStream} stream. {@code signatureProbeLength} should be
     *                             expressed in "compressed(/encrypted)" space rather than "plaintext" space.
     *                             For example, a file format signature may consist of {@code n} bytes of ASCII,
     *                             but for formats that use compressed streams, the codec may need access to an
     *                             entire compressed block in order to inspect those {@code n} bytes. {@code
     *                             signatureProbeLength} should use the compressed block size, in order to ensure
     *                             that the {@link SignatureStream} contains a semantically meaningful fragment
     *                             of the underlying input.
     * @return a {@link SignatureStream} over the first {@code signatureProbeLength} bytes of this
     * resource, for use with signature probing for codec resolution. Only applicable to resources for
     * which {@link #hasInputType()} is true.
     * @throws IllegalArgumentException if this method has previously been called on this resource, or if
     * {@link #hasInputType()} is false for this resource
     */
    SignatureStream getSignatureStream(final int signatureProbeLength);

    /**
     * Return true if is this resource is backed by a type that can be used for input. Some resource types,
     * such as {@link InputStreamResource}, can be used for input but not for output (see
     * {@link #hasOutputType}. Others, such as {@link OutputStreamResource}, can be used for output but not
     * for input. Some resource types may be suitable for both (for example see {@link IOPathResource}).
     * <p>
     * The determination is based only on the type of the resource, and does not imply a guarantee about
     * whether the resource type is actually readable.
     *
     * @return true if the type of this resource makes it suitable for use as a source of input.
     */
    boolean hasInputType();

    /**
     * Return true if this resource is backed by a type that can be used for output. Some resource types,
     * such as {@link InputStreamResource}, can be used for input but not for output (see
     * {@link #hasOutputType}. Others, such as {@link OutputStreamResource}, can be used for output but
     * not for input. Some resource types may be suitable for both (for example see {@link IOPathResource}).
     * <p>
     * The determination is based only on the type of the resource, and does not imply a guarantee about
     * whether the resource is actually writeable.
     *
     * @return true if the type of this resource makes it suitable for use as target for output.
     */
    boolean hasOutputType();

    /**
     * Returns true if this resource can be rendered as a {@link SeekableStream}.
     *
     * @return true if this resource can be rendered as a {@link SeekableStream} (see {@link #getSeekableStream})
     */
    boolean hasSeekableStream();

}
