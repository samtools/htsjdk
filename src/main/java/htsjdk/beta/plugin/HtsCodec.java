package htsjdk.beta.plugin;

import htsjdk.beta.io.bundle.BundleResource;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.io.bundle.SignatureStream;
import htsjdk.beta.plugin.reads.ReadsFormats;
import htsjdk.beta.plugin.registry.HtsCodecRegistry;
import htsjdk.io.IOPath;
import htsjdk.beta.io.bundle.Bundle;

/**
 * Base interface implemented by all {@link htsjdk.beta.plugin} codecs.
 * </p>
 * <H3>Codec Components</H3>
 * <p>
 *     Each version of a file format supported by the {@link htsjdk.beta.plugin} framework is
 *     represented by a trio of components:
 * <ul>
 *     <li>a codec that implements {@link HtsCodec}</li>
 *     <li>an encoder that implements {@link HtsEncoder}</li>
 *     <li>a decoder that implements {@link HtsDecoder}</li>
 * </ul>
 * <p>
 *     The {@link HtsCodec} is a lightweight and long-lived object that resides in an
 *     {@link htsjdk.beta.plugin.registry.HtsCodecRegistry}. A registry is used to resolve requests for
 *     an {@link HtsEncoder} or {@link HtsDecoder} that matches a given resource. The {@link HtsEncoder}
 *     and {@link HtsDecoder} objects do the work of actually writing and reading records to and from
 *     underlying resources.
 *     <p>
 *     A default, static, immutable {@link htsjdk.beta.plugin.registry.HtsCodecRegistry} is populated with
 *     {@link HtsCodec}s that are discovered and instantiated statically via a {@link java.util.ServiceLoader},
 *     and can be accessed using {@link htsjdk.beta.plugin.registry.HtsDefaultRegistry}. A private, mutable
 *     registry can be created at runtime via {@link HtsCodecRegistry#createPrivateRegistry()}, and populated
 *     dynamically by calls to {@link htsjdk.beta.plugin.registry.HtsCodecRegistry#registerCodec(HtsCodec)}.
 *     <p>
 *     The primary responsibility of an {@link HtsCodec} is to satisfy requests made by the framework during
 *     codec resolution, inspecting and recognizing input URIs and stream resources that match the
 *     supported format and version, and providing an {@link HtsEncoder} or {@link HtsDecoder} on demand, once
 *     a match is made.
 * <p>
 * <H3>Content Types</H3>
 *     The plugin framework supports four different types of HTS data, called content types:
 * <p>
 * <ul>
 *     <li> {@link HtsContentType#ALIGNED_READS} </li>
 *     <li> {@link HtsContentType#HAPLOID_REFERENCE} </li>
 *     <li> {@link HtsContentType#VARIANT_CONTEXTS} </li>
 *     <li> {@link HtsContentType#FEATURES} </li>
 * </ul>
 * <p>
 *   For each content type, there is a corresponding set of codec/decoder/encoder interfaces that
 *   are implemented by components that support that content type. These interfaces extend generic base
 *   interfaces, and provide generic parameter type instantiations appropriate for that content type.
 *   As an example, see {@link htsjdk.beta.plugin.reads.ReadsDecoder} which defines the interface for
 *   all {@link htsjdk.beta.plugin.HtsDecoder}s for the {@link HtsContentType#ALIGNED_READS} content
 *   type. The different implementations of component trios for a given content type all use the same
 *   content-type-specific interfaces, but each over a different combination of underlying file format
 *   and version.
 * <p>
 * The generic, base interfaces that are common to all codecs, encoders, and decoders are:
 * <ul>
 *     <li> {@link HtsCodec}: base codec interface </li>
 *     <li> {@link HtsEncoder}: base encoder interface </li>
 *     <li> {@link HtsEncoderOptions}: base options interface for encoders </li>
 *     <li> {@link HtsDecoder}: base decoder interface </li>
 *     <li> {@link HtsDecoderOptions}: base options interface for decoders </li>
 *     <li>  a class with string constants for each supported file format for that type </li>
 *     <li> {@link Bundle}: a optional type-specific
 *     {@link Bundle} implementation </li>
 * </ul>
 * <p>
 *     The packages containing the content type-specific interface definitions for each of
 *     the four different content types are:
 * <p>
 * <ul>
 *     <li> For {@link HtsContentType#ALIGNED_READS} codecs, see the {@link htsjdk.beta.plugin.reads} package </li>
 *     <li> For {@link HtsContentType#HAPLOID_REFERENCE} codecs, see the {@link htsjdk.beta.plugin.hapref} package </li>
 *     <li> For {@link HtsContentType#VARIANT_CONTEXTS} codecs, see the {@link htsjdk.beta.plugin.variants} package </li>
 *     <li> For {@link HtsContentType#FEATURES} codecs, see the {@link htsjdk.beta.plugin.features} package </li>
 * </ul>
 * <p>
 * <H3>Example Content Type: Reads</H3>
 * <p>
 *     As an example, the {@link htsjdk.beta.plugin.reads} package defines the following interfaces
 *     that extend the generic base interfaces for codecs with content type {@link HtsContentType#ALIGNED_READS}:
 * <ul>
 *     <li> {@link htsjdk.beta.plugin.reads.ReadsCodec}: reads codec interface, extends the generic
 *     {@link HtsCodec} interface </li>
 *     <li> {@link htsjdk.beta.plugin.reads.ReadsEncoder}: reads encoder, extends the generic
 *     {@link HtsEncoder} interface </li>
 *     <li> {@link htsjdk.beta.plugin.reads.ReadsEncoderOptions}: reads encoder options, extends the generic
 *     {@link HtsDecoderOptions} interface </li>
 *     <li> {@link htsjdk.beta.plugin.reads.ReadsDecoder}: reads decoder interface, extends the generic
 *     {@link HtsDecoder} interface </li>
 *     <li> {@link htsjdk.beta.plugin.reads.ReadsDecoderOptions}: reads decoder options, extends the generic
 *     {@link HtsDecoderOptions} interface </li>
 *     <li> {@link ReadsFormats}: an class with string constants for each possible
 *     supported reads file format </li>
 * </ul>
 * <H3>Codec Resolution</H3>
 * <p>
 *     The plugin framework uses registered codecs to conduct a series of probes into the structure
 *     and format of an input or output resource in order to find a matching codec that can produce
 *     an encoder or decoder for that resource. The values returned from the codec methods are
 *     used by the framework to prune a list of candidate codecs down, until a match is found. During codec
 *     resolution, the codec methods are called in the following order:
 * <ol>
 *     <li> {@link #ownsURI(IOPath)} </li>
 *     <li> {@link #canDecodeURI(IOPath)} </li>
 *     <li> {@link #canDecodeSignature(SignatureStream, String)} </li>
 * </ol>
 * <p>
 *     See the {@link htsjdk.beta.plugin.registry.HtsCodecResolver} methods for more detail on the resolution
 *     protocol:
 *     <ul>
 *     <li> {@link htsjdk.beta.plugin.registry.HtsCodecResolver#resolveForDecoding(Bundle)} </li>
 *     <li> {@link htsjdk.beta.plugin.registry.HtsCodecResolver#resolveForEncoding(Bundle)} </li>
 *     <li> {@link htsjdk.beta.plugin.registry.HtsCodecResolver#resolveForEncoding(Bundle, HtsVersion)} </li>
 * </ul>
 * <H3>Formats That Use a Custom URI or Protocol Scheme</H3>
 * <p>
 *     Many file formats consist of a single file that resides on a file system that is supported by a
 *     {@link java.nio} file system provider. Codecs that support such formats are generally agnostic
 *     about the IOPath or URI protocol scheme used to identify their resources, and assume that file contents
 *     can be accessed directly via a single stream created via a {@link java.nio} file system provider.
 * <p>
 *     However, some file formats use a specific, well known URI format or protocol scheme, often
 *     to identify a remote or otherwise specially-formatted resource, such as a local database
 *     that is distributed across multiple physical files. These codecs may bypass direct file {@link java.nio}
 *     system access, and instead use specialized code to access their underlying resources.
 * <p>
 *     For example, the {@link htsjdk.beta.codecs.reads.bam.bamV1_0.BAMCodecV1_0} assumes that IOPath
 *     resources can be accessed as a stream on a single file via either the "file://" protocol, or
 *     other protocols such gs:// or hdfs:// that have {@link java.nio} file system providers. It does
 *     not require or assume a particular URI format, and is agnostic about URI scheme.
 * <p>
 *     In contrast, the {@link htsjdk.beta.codecs.reads.htsget.htsgetBAMV1_2.HtsgetBAMCodecV1_2} codec
 *     is a specialized codec that handles remote resources via the "http://" protocol.
 *     It uses {@code http} to access the underlying resource, and bypasses direct {@link java.nio}
 *     file system access.
 * <p>
 *     Codecs for formats that use a custom URI format or protocol scheme such as {@code htsget} must be
 *     able to determine if they can decode or encode a resource purely by inspecting the IOPath/URI, and
 *     should follow these guidelines:
 *     <ul>
 *         <li>return true when {@link #ownsURI(IOPath)} is presented with an IOPath with
 *         a conforming URI </li>
 *         <li>return true when {@link #canDecodeURI(IOPath)} is presented with an IOPath
 *         with a conforming URI </li>
 *         <li> ensure that for a given IOPath, {@link #ownsURI(IOPath)} == {@link #canDecodeURI(IOPath)}
 *         <li>always return 0 from the {@link #getSignatureProbeLength()} method</li>
 *         <li>always return 0 from the {@link #getSignatureLength()} method</li>
 *     </ul>
 * <p>
 * <H3>Codec Implementation Guidelines</H3>
 * <p>
 *     <ul>
 *         <li>
 *             An HtsCodec class should implement only a single version of a single file format.
 *         </li>
 *         <li>
 *             HtsCodec instances may be shared across multiple registries, and should generally be immutable
 *             (HtsEncoder and HtsDecoder implementations may be mutable).
 *         </li>
 *         <li>
 *             The {@link #getDecoder(Bundle, HtsDecoderOptions)} implementation should not attempt to automatically
 *             resolve a companion index. {@link HtsDecoder}s should only satisfy index queries when the input bundle
 *             explicitly specifies an index resource.
 *         </li>
 *     </ul>
 * </p>
 * @param <D> the decoder options type for this codec
 * @param <E> the encoder options type for this codec
 */
public interface HtsCodec<D extends HtsDecoderOptions, E extends HtsEncoderOptions> extends Upgradeable {

    /**
     * Get the {@link HtsContentType} for this codec.
     * </p>
     * @return the {@link HtsContentType} for this codec. The {@link HtsContentType} determines the interfaces,
     * including the HEADER and RECORD types, used by the codec's {@link HtsEncoder} and {@link HtsDecoder}.
     * Each implementation of a given content type exposes the same interfaces, but over a different file
     * format or version. For example, both the BAM and HTSGET_BAM codecs have codec type
     * {@link HtsContentType#ALIGNED_READS}, and are derived from {@link htsjdk.beta.plugin.reads.ReadsCodec},
     * but the serialized file formats and access mechanisms for the two codecs are different).
     */
    HtsContentType getContentType();

    /**
     * Get the name of the file format supported by this codec. The format name defines the underlying
     * format handled by this codec, and also corresponds to the format of the primary bundle
     * resource that is required when decoding or encoding (see
     * {@link BundleResourceType} and {@link BundleResource#getFileFormat()}).
     *
     * @return the name of the underlying file format handled by this codec
     */
    String getFileFormat();

    /**
     * Get the version of the file format returned by {@link #getFileFormat()} that is supported by this codec.
     *
     * @return the file format version ({@link HtsVersion}) supported by this codec
     */
    HtsVersion getVersion();

    /**
     * Get a user-friendly display name for this codec.
     * </p>
     * It is recommended that the display name minimally include both the name of the supported file format and
     * the supported version.
     *
     * @return a user-friendly display name for this codec
     */
    default String getDisplayName() {
        return String.format("%s/%s/%s", getFileFormat(), getVersion(), getClass().getName());
    }

    /**
     * Determine if this codec "owns" the URI contained in {@code ioPath} see ({@link IOPath#getURI()}).
     * <p>
     * A codec "owns" the URI only if it has specific requirements on the URI protocol scheme, URI format,
     * or query parameters that go beyond a simple file extension, AND it explicitly recognizes the URI
     * as conforming to those requirements. File formats that only require a specific file extension should
     * always return false from {@link #ownsURI}, and should instead use the extension as a filter in
     * {@link #canDecodeURI(IOPath)}.
     * <p>
     * Returning true from this method will cause the framework to bypass the stream-oriented signature
     * probing that is used to resolve inputs to a codec handler. During codec resolution, if any registered
     * codec returns true for this method on {@code ioPath}, the signature probing protocol will instead:
     * <ol>
     * <li> immediately prune the list of candidate codecs to only those that return true for this method
     * on {@code ioPath} </li>
     * <li> not attempt to obtain an InputStream on the IOPath containing the URI, on the assumption that
     * special handling is required in order to access the underlying resource (i.e., htsget
     * codec would claim an "http://" URI if the rest of the URI conforms to the expected format for that
     * codec's protocol). </li>
     * </ol>
     * <p>
     * Any codec that returns true from {@link #ownsURI(IOPath)} for a given IOPath must also return true
     * from {@link #canDecodeURI(IOPath)} for the same IOPath.
     *
     * For custom URI handlers, codecs should avoid making remote calls to determine the suitability
     * of the input resource; the return value for this method should be based only on the format
     * of the URI that is presented.
     *
     * @param ioPath the ioPath to inspect
     * @return true if the ioPath's URI represents a custom URI that this codec handles
     */
    default boolean ownsURI(final IOPath ioPath) { return false; }

    /**
     * Determine if the URI for <code>ioPath</code> (obtained via {@link IOPath#getURI()})
     * conforms to the expected URI format this codec's file format.
     * </p>
     * Most implementations only look at the file extension (see {@link htsjdk.io.IOPath#hasExtension}).
     * For codecs that implement formats that use specific, well known file extensions, the codec should
     * reject inputs that do not conform to any of the accepted extensions. If the format does not use a
     * specific extension, or if the codec cannot determine if it can decode the underlying resource
     * without inspecting the underlying stream, it is safe to return true, after which the framework will
     * subsequently call this codec's {@link #canDecodeSignature(SignatureStream, String)} method, at
     * which time the codec can inspect the actual underlying stream via the {@link SignatureStream}.
     * <p>
     * Implementations should generally not inspect the URI's protocol scheme unless the file format
     * supported by the codec requires the use a specific protocol scheme. For codecs that do own
     * a specific scheme or URI format, any codec that returns true from {@link #ownsURI(IOPath)} for a
     * given IOPath must also return true from {@link #canDecodeURI(IOPath)} for the same IOPath.
     * <p>
     * It is never safe to attempt to directly inspect the underlying stream for <code>ioPath</code>
     * in this method. If the stream needs to be inspected, it should be done using the signature stream
     * when the {@link #canDecodeSignature(SignatureStream, String)} method is called.
     * </p>
     * For custom URI handlers (see {@link #ownsURI(IOPath)}, codecs should avoid making remote calls
     * to determine the suitability of the input resource; the return value for this method should be based
     * only on the format of the URI that is presented.
     *
     * @param ioPath to be decoded
     * @return true if the codec can provide a decoder to provide this URI
     */
    boolean canDecodeURI(final IOPath ioPath);

    /**
     * Determine if the codec can decode an input stream by inspecting a signature embedded
     * within the stream.
     * </p>
     * The probingInputStream stream will contain only a fragment of the actual input stream, taken
     * from the start of the stream, the size of which will be the lesser of:
     * <p>
     * <ol>
     *     <li> the number of bytes returned by {@link #getSignatureProbeLength} </li>
     *     <li> the entire input stream, for streams that are smaller than {@link #getSignatureProbeLength} </li>
     * </ol>
     * <p>
     * Codecs that handle custom URIs that reference remote resources (those that return true for {@link #ownsURI})
     * should generally not inspect the stream, and should return false from this method, since the method
     * will never be called with any resource for which {@link #ownsURI} returned true.
     *
     * @param signatureStream the stream to be inspect for the resource's embedded
     *                              signature and version
     * @param sourceName a display name describing the source of the input stream, for use in error messages
     * @return true if this codec recognizes the stream by it's signature, and can provide a decoder to
     * decode the stream, otherwise false
     */
    boolean canDecodeSignature(final SignatureStream signatureStream, final String sourceName);

    /**
     * Get the number of bytes in the format and version signature used by the file format supported
     * by this codec.
     *
     * @return if the file format supported by this codecs is not remote, and is accessible via a local file
     * or stream, the size of the unique signature/version for this file format. otherwise 0.
     * </p>
     * Note: Codecs that are custom URI handlers (those that return true for {@link #ownsURI}), should
     * always return 0 from this method.
     */
    int getSignatureLength();

    /**
     * Get the number of bytes of needed by this codec to probe an input stream for a format/version
     * signature, and determine if it can supply a decoder for the stream.
     *
     * @return the number of bytes this codec must consume from a stream in order to determine whether
     * it can decode that stream. This number may differ from the actual signature size
     * as returned by {@link #getSignatureLength()} for codecs that support compressed or encrypted
     * streams, since they may require a larger and more semantically meaningful input fragment
     * (such as an entire encrypted or compressed block) in order to inspect the plaintext signature.
     * <p>
     * Therefore {@code signatureProbeLength} should be expressed in "compressed/encrypted" space rather
     * than "plaintext" space. The length returned from this method is used to determine the size of the
     * {@link SignatureStream} that is subsequently passed to
     * {@link #canDecodeSignature(SignatureStream, String)}.
     * <p>
     * Note: Codecs that are custom URI handlers (those that return true for {@link #ownsURI(IOPath)}),
     * should always return 0 from this method when it is called.
     * </p>
     */
    default int getSignatureProbeLength() { return getSignatureLength(); }

    /**
     * Get an {@link HtsDecoder} to decode the provided inputs. The input bundle must contain
     * resources of the type required by this codec. To find a codec appropriate for decoding a
     * given resource, use an {@link htsjdk.beta.plugin.registry.HtsCodecResolver} obtained
     * from an {@link htsjdk.beta.plugin.registry.HtsCodecRegistry}.
     * <p>
     * The framework will never call thi* method unless either {@link #ownsURI(IOPath)}, or
     * {@link #canDecodeURI(IOPath)} and {@link #canDecodeSignature(SignatureStream, String)} (IOPath)}
     * return true for {@code inputBundle}.
     *
     * @param inputBundle input to be decoded. To get a decoder for use with index queries that use
     *                    {@link htsjdk.beta.plugin.interval.HtsQuery} methods, the bundle must contain
     *                    an index resource.
     *
     * @param decoderOptions options for the decoder to use
     * @return an {@link HtsDecoder} that can decode the provided inputs
     */
    HtsDecoder<?, ? extends HtsRecord> getDecoder(final Bundle inputBundle, final D decoderOptions);

    /**
     * Get an {@link HtsEncoder} to encode to the provided outputs. The output bundle must contain
     * resources of the type required by this codec. To find a codec appropriate for encoding a given
     * resource, use an {@link htsjdk.beta.plugin.registry.HtsCodecResolver} obtained from an
     * {@link htsjdk.beta.plugin.registry.HtsCodecRegistry}.
     * </p>
     * The framework will never call this method unless either {@link #ownsURI(IOPath)}, or
     * {@link #canDecodeURI(IOPath)} returned true for {@code outputBundle}.
     *
     * @param outputBundle target output for the encoder
     * @param encoderOptions encoder options to use
     * @return an {@link HtsEncoder} suitable for writing to the provided outputs
     */
    HtsEncoder<?, ? extends HtsRecord> getEncoder(final Bundle outputBundle, final E encoderOptions);

}
