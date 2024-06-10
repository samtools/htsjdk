package htsjdk.beta.plugin.registry;

import htsjdk.HtsjdkTest;
import htsjdk.beta.plugin.HtsCodec;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.IOUtils;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleBuilder;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.io.bundle.IOPathResource;
import htsjdk.beta.io.bundle.InputStreamResource;
import htsjdk.beta.io.bundle.OutputStreamResource;
import htsjdk.beta.io.bundle.SeekableStreamResource;
import htsjdk.beta.plugin.registry.testcodec.HtsTestCodec;
import htsjdk.beta.plugin.registry.testcodec.HtsTestCodecFormats;
import htsjdk.beta.exception.HtsjdkIOException;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// Tests for resolving codec (encoder/decoder) requests given a set of input/output resources and a list
// of registered codecs.
public class HtsCodecResolverTest extends HtsjdkTest {
    // config parameters for file format 1
    final static String FORMAT_1_FORMAT_NAME = HtsTestCodecFormats.FILE_FORMAT_1;
    final static String FORMAT_1_STREAM_SIGNATURE = HtsTestCodecFormats.FILE_FORMAT_1;
    final static String FORMAT_1_FILE_EXTENSION = ".f1";

    // config parameters for file format 2
    final static String FORMAT_2_FORMAT_NAME = HtsTestCodecFormats.FILE_FORMAT_2;
    final static String FORMAT_2_STREAM_SIGNATURE = HtsTestCodecFormats.FILE_FORMAT_2;
    final static String FORMAT_2_FILE_EXTENSION = ".f2";

    // config parameters for file format 3, which uses a custom protocol scheme
    final static String FORMAT_3_FORMAT_NAME = HtsTestCodecFormats.FILE_FORMAT_3;
    final static String FORMAT_3_FILE_EXTENSION = ".f3";
    final static String FORMAT_3_PROTOCOL_SCHEME = "ps3";

    final static HtsVersion V1_0 = new HtsVersion(1, 0, 0);
    final static HtsVersion V1_1 = new HtsVersion(1, 1, 0);
    final static HtsVersion V2_0 = new HtsVersion(2, 0, 0);

    // file format FORMAT_1, V1_0
    final static HtsTestCodec FORMAT_1_V1_0 = new HtsTestCodec(
            FORMAT_1_FORMAT_NAME,
            V1_0,
            FORMAT_1_FILE_EXTENSION,
            null,
            false);
    // file format FORMAT_1, V1_1
    final static HtsTestCodec FORMAT_1_V1_1 = new HtsTestCodec(
            FORMAT_1_FORMAT_NAME,
            V1_1,
            FORMAT_1_FILE_EXTENSION,
            null,
            false);
    // file format FORMAT_1, V2_0
    final static HtsTestCodec FORMAT_1_V2_0 = new HtsTestCodec(
            FORMAT_1_FORMAT_NAME,
            V2_0,
            FORMAT_1_FILE_EXTENSION,
            null,
            false);

    // file format FORMAT_2, V1_0
    final static HtsTestCodec FORMAT_2_V1_0 = new HtsTestCodec(
            FORMAT_2_FORMAT_NAME,
            V1_0,
            FORMAT_2_FILE_EXTENSION,
            null,
            false);
    // file format FORMAT_2, V1_1
    final static HtsTestCodec FORMAT_2_V1_1 = new HtsTestCodec(
            FORMAT_2_FORMAT_NAME,
            V1_1,
            FORMAT_2_FILE_EXTENSION,
            null,
            false);
    // file format FORMAT_2, V2_0, uses gzipped inputs
    final static HtsTestCodec FORMAT_2_V2_0 = new HtsTestCodec(
            FORMAT_2_FORMAT_NAME,
            V2_0,
            FORMAT_2_FILE_EXTENSION,
            null,
            true);  // expect gzipped inputs

    // file format FORMAT_3, V1_0, uses a custom protocol scheme
    final static HtsTestCodec FORMAT_3_V1_0 = new HtsTestCodec(
            FORMAT_3_FORMAT_NAME,
            V1_0,
            FORMAT_3_FILE_EXTENSION,
            FORMAT_3_PROTOCOL_SCHEME,   // custom protocol scheme
            false);

    // file format FORMAT_3, V2_0, uses a custom protocol scheme
    final static HtsTestCodec FORMAT_3_V2_0 = new HtsTestCodec(
            FORMAT_3_FORMAT_NAME,
            V2_0,
            FORMAT_3_FILE_EXTENSION,
            FORMAT_3_PROTOCOL_SCHEME,   // custom protocol scheme
            false);

    @DataProvider(name="resolveCodecForDecodingSucceeds")
    public Object[][] resolveCodecForDecodingSucceeds() {
        return new Object[][]{
                // array of codecs to register, resource bundle, expected display name of resolved codec

                // input IOPath is FORMAT_1, V1_0, resolve to FORMAT_1_V1_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundleWithContent(
                                BundleResourceType.CT_ALIGNED_READS,
                                null,
                                FORMAT_1_FILE_EXTENSION,
                                FORMAT_1_STREAM_SIGNATURE + V1_0,
                                false),
                        FORMAT_1_V1_0.getDisplayName() },

                // input IOPath is FORMAT_1, V1_1, resolve to FORMAT_1_V1_1
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundleWithContent(
                                BundleResourceType.CT_ALIGNED_READS,
                                null,
                                FORMAT_1_FILE_EXTENSION,
                                FORMAT_1_STREAM_SIGNATURE + V1_1,
                                false),
                        FORMAT_1_V1_1.getDisplayName() },

                // input IOPath is FORMAT_1, V2_0, resolve to FORMAT_1_V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundleWithContent(
                                BundleResourceType.CT_ALIGNED_READS,
                                null,
                                FORMAT_1_FILE_EXTENSION,
                                FORMAT_1_STREAM_SIGNATURE + V2_0,
                                false),
                        FORMAT_1_V2_0.getDisplayName() },

                // input IOPath is FORMAT_1, V_2_0, with format FORMAT_1 specified in the bundle,
                // resolve to FORMAT_1_V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundleWithContent(
                                BundleResourceType.CT_ALIGNED_READS,
                                FORMAT_1_FORMAT_NAME,
                                FORMAT_1_FILE_EXTENSION,
                                FORMAT_1_STREAM_SIGNATURE + V2_0,
                                false),
                        FORMAT_1_V2_0.getDisplayName() },

                // input IOPath is FORMAT_2, V1_0, resolve to FORMAT_2_V1_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundleWithContent(
                                BundleResourceType.CT_ALIGNED_READS,
                                null,
                                FORMAT_2_FILE_EXTENSION,
                                FORMAT_2_STREAM_SIGNATURE + V1_0,
                                false),
                        FORMAT_2_V1_0.getDisplayName() },

                // input IOPath is FORMAT_2, V1_1, resolve to FORMAT_2_V1_1
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundleWithContent(
                                BundleResourceType.CT_ALIGNED_READS,
                                null,
                                FORMAT_2_FILE_EXTENSION,
                                FORMAT_2_STREAM_SIGNATURE + V1_1,
                                false),
                        FORMAT_2_V1_1.getDisplayName() },

                // input IOPath is gzipped FORMAT_2 (which requires GZIPPED inputs), V2_0, resolve to FORMAT_2_V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundleWithContent(
                                BundleResourceType.CT_ALIGNED_READS,
                                null,
                                FORMAT_2_FILE_EXTENSION,
                                FORMAT_2_STREAM_SIGNATURE + V2_0,
                                true),  // FORMAT_2_V2_0 uses gzipped inputs
                        FORMAT_2_V2_0.getDisplayName() },

                // input IOPath is FORMAT_3, (uses custom protocol scheme), resolve to newest version FORMAT_3_V1_0,
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0),
                        new BundleBuilder()
                                .addPrimary(new IOPathResource(
                                        new HtsPath(FORMAT_3_PROTOCOL_SCHEME + ":///myFile" + FORMAT_3_FILE_EXTENSION),
                                        BundleResourceType.CT_ALIGNED_READS))
                                .build(),
                        FORMAT_3_V1_0.getDisplayName() },

                // input STREAM is FORMAT_1, V1_0, resolve to FORMAT_1_V1_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputStreamBundleWithContent(
                                BundleResourceType.CT_ALIGNED_READS,
                                null,
                                FORMAT_1_STREAM_SIGNATURE + V1_0,
                                false),
                        FORMAT_1_V1_0.getDisplayName() },

                // input STREAM is gzipped FORMAT_2, V2_0 (which requires GZIPPED inputs), resolve to  FORMAT_2_V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputStreamBundleWithContent(
                                BundleResourceType.CT_ALIGNED_READS,
                                null,
                                FORMAT_2_STREAM_SIGNATURE + V2_0,
                                true),    // use gzipped inputs
                        FORMAT_2_V2_0.getDisplayName() },

                // input *SEEKABLE STREAM* is gzipped FORMAT_2, V2_0 (which requires GZIPPED inputs), resolve to  FORMAT_2_V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        new BundleBuilder().addPrimary(new SeekableStreamResource(
                                makeInputIOPathBundleWithContent(
                                        BundleResourceType.CT_ALIGNED_READS,
                                    null,
                                    FORMAT_2_FILE_EXTENSION,
                                    FORMAT_2_STREAM_SIGNATURE + V2_0,
                                    true) // use gzipped inputs
                                .getPrimaryResource().getSeekableStream().get(), BundleResourceType.CT_ALIGNED_READS, BundleResourceType.CT_ALIGNED_READS)).build(),
                        FORMAT_2_V2_0.getDisplayName() },
        };
    }

    @Test(dataProvider = "resolveCodecForDecodingSucceeds")
    public void testResolveCodecForDecodingSucceeds(
            final List<HtsCodec<?, ?>> codecs,
            final Bundle bundle,
            final String expectedCodecDisplayName) {
        final HtsCodecResolver<HtsCodec<?, ?>> testCodecs = new HtsCodecResolver<>(BundleResourceType.CT_ALIGNED_READS);
        codecs.forEach(c -> testCodecs.registerCodec(c));
        final HtsCodec<?, ?> resolvedCodec = testCodecs.resolveForDecoding(bundle);
        Assert.assertEquals(resolvedCodec.getDisplayName(), expectedCodecDisplayName);
    }

    @DataProvider(name="resolveCodecForDecodingFails")
    public Object[][] resolveCodecForDecodingFails() {
        return new Object[][]{
                // array of codecs to register, a resource bundle, expected exception message fragment

                // input IOPath is FORMAT_1, V2_0, no codecs registered
                { Collections.emptyList(),
                        makeInputIOPathBundleWithContent(
                                BundleResourceType.CT_ALIGNED_READS,
                                null,
                                FORMAT_1_FILE_EXTENSION,
                                FORMAT_1_STREAM_SIGNATURE + V2_0,
                                false),
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},

                // input IOPath is FORMAT_2, V1_0, no codec is registered for any FORMAT_2 version
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        makeInputIOPathBundleWithContent(
                                BundleResourceType.CT_ALIGNED_READS,
                                null,
                                FORMAT_2_FILE_EXTENSION,
                                FORMAT_2_STREAM_SIGNATURE + V1_0,
                                false),
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},

                // input IOPath is FORMAT_1, V2_0, no codec is registered for V2_0 of FORMAT_1
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        makeInputIOPathBundleWithContent(
                                BundleResourceType.CT_ALIGNED_READS,
                                null,
                                FORMAT_1_FILE_EXTENSION,
                                FORMAT_1_STREAM_SIGNATURE + V2_0,
                                false),
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},

                // the primary bundle resource has the wrong content type ("BOGUS_CONTENT_TYPE"),
                // the resolver requires primary content type TEST_CODEC_CONTENT_TYPE
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        makeInputIOPathBundleWithContent(
                                "BOGUS_CONTENT_TYPE",
                                null,
                                FORMAT_1_FILE_EXTENSION,
                                FORMAT_1_STREAM_SIGNATURE + V1_0,
                                false),
                        "for the resource does not match the requested content type" },

                // input IOPath resource has a format that doesn't correspond to any format for the
                // content type
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        makeInputIOPathBundleWithContent(
                                BundleResourceType.CT_ALIGNED_READS,
                                "BOGUS_FORMAT",
                                FORMAT_1_FILE_EXTENSION,
                                FORMAT_1_STREAM_SIGNATURE + V1_0,
                                false),
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR },

                // the resource in the bundle has a valid content type, and the file extension is valid,
                // but the signature in the underlying file is "BOGUS_FORMAT", which doesn't match the
                // signature for any of the codecs
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        makeInputIOPathBundleWithContent(
                                BundleResourceType.CT_ALIGNED_READS,
                                null,
                                FORMAT_1_FILE_EXTENSION,
                                "BOGUS_FORMAT" + V1_0,
                                false),
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR },

                // the resource in the bundle has a valid content type, but the signature in the underlying
                // file has version "V2_0", which doesn't match the version any codec expects
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        makeInputIOPathBundleWithContent(
                                BundleResourceType.CT_ALIGNED_READS,
                                null,
                                FORMAT_1_FILE_EXTENSION,
                                FORMAT_1_STREAM_SIGNATURE + V2_0,
                                false),
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},

                // input *IOPath* has *format* FORMAT_1 (specifying a format causes the resolver to
                // prune all codecs except those that handle that format), but the underlying file contains the
                // signature FORMAT_2, which none of the FORMAT_1 codecs accept
                //TODO: this should have a better error message or warning saying the format in the
                // bundle resource doesn't match the signature in the underlying resource, which is likely
                // either a user or coding error
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundleWithContent(
                                BundleResourceType.CT_ALIGNED_READS,
                                FORMAT_1_FORMAT_NAME,
                                FORMAT_2_FILE_EXTENSION,
                                FORMAT_2_STREAM_SIGNATURE + V2_0,
                                false),
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},

                // input *STREAM* resource has *format* FORMAT_1 (specifying a format causes the resolver to
                // prune all codecs except those that handle that format), but the underlying stream signature
                // contains the signature FORMAT_2, which none of the FORMAT_1 codecs accept
                //TODO: this should have a better error message or warning saying the content type in the bundle
                // resource doesn't match the signature in the underlying stream, since its likely user or
                // programming error
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputStreamBundleWithContent(
                                BundleResourceType.CT_ALIGNED_READS,
                                FORMAT_1_FORMAT_NAME,
                                FORMAT_2_STREAM_SIGNATURE + V1_0,
                                false),
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},

                // IOPath with a file extension that doesn't match the format
                //TODO: this should have a better error message or warning saying the file extension for the resource
                // doesn't match the actual format, since thats likely user or programming error
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundleWithContent(
                                BundleResourceType.CT_ALIGNED_READS,
                                FORMAT_1_FORMAT_NAME,
                                FORMAT_3_FILE_EXTENSION, // use the wrong file extension
                                FORMAT_1_STREAM_SIGNATURE + V2_0,
                                false),
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},

                // the user entered a URI that has a protocol scheme that no codec claims to support, and for
                // which no NIO provider is installed
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                    new BundleBuilder()
                            .addPrimary(
                                    new IOPathResource(
                                        new HtsPath("bogus://some/uri/some" + FORMAT_1_FILE_EXTENSION),
                                            BundleResourceType.CT_ALIGNED_READS,
                                            FORMAT_1_FORMAT_NAME)
                            ).build(),
                        "specifies a custom protocol (bogus) which no registered codec claims, and for which no NIO file system provider is available"},

                // the primary resource has the required content type, but is an output-only resource (OutputStream),
                // not a readable input resource
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        makeOutputStreamBundle(BundleResourceType.CT_ALIGNED_READS, null),
                        "cannot be used as an input resource"},
        };
    }

    @Test(dataProvider = "resolveCodecForDecodingFails", expectedExceptions=RuntimeException.class)
    public void testResolveCodecForDecodingFails(
            final List<HtsCodec<?, ?>> codecs,
            final Bundle bundle,
            final String expectedMessage) {
        final HtsCodecResolver<HtsCodec<?, ?>> testCodecs = new HtsCodecResolver<>(BundleResourceType.CT_ALIGNED_READS);
        codecs.forEach(c -> testCodecs.registerCodec(c));
        try {
            testCodecs.resolveForDecoding(bundle);
        } catch (final RuntimeException e) {
            // the test cases here throw both IllegalArgumentException and HtsjdkException, so catch RuntimeException
            Assert.assertTrue(e.getMessage().contains(expectedMessage));
            throw e;
        }
    }

    @DataProvider(name="resolveCodecForEncodingSucceeds")
    public Object[][] resolveCodecForEncodingSucceeds() {
        // Note: unlike inputs, for outputs a version must either be explicitly requested
        // by the caller, or else default to the newest version registered for the format.
        // The format may also need to be explicitly requested, since it may not
        // be discoverable from the output (i.e., if the output is to a raw stream).
        return new Object[][]{
                // array of codecs to register, resource bundle, requested version (or null), expected display name of resolved codec

                // no specific version requested, resolve to the newest version registered for FORMAT_1, resolve
                // to FORMAT_1, V1_0
                { Arrays.asList(FORMAT_1_V1_0),
                        makeInputIOPathBundle(BundleResourceType.CT_ALIGNED_READS, null, FORMAT_1_FILE_EXTENSION),
                        HtsVersion.NEWEST_VERSION,
                        FORMAT_1_V1_0.getDisplayName() },

                // no specific version requested, resolve to the newest version registered for FORMAT_1, which
                // is FORMAT_1, V1_1
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        makeInputIOPathBundle(BundleResourceType.CT_ALIGNED_READS, null, FORMAT_1_FILE_EXTENSION),
                        HtsVersion.NEWEST_VERSION,
                        FORMAT_1_V1_1.getDisplayName() },

                // no specific version requested, resolve to the newest version registered for FORMAT_1, which
                // is FORMAT_1, V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0),
                        makeInputIOPathBundle(BundleResourceType.CT_ALIGNED_READS, null, FORMAT_1_FILE_EXTENSION),
                        HtsVersion.NEWEST_VERSION,
                        FORMAT_1_V2_0.getDisplayName() },

                // no specific version requested, resolve to the newest version registered for FORMAT_1, which is
                // V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundle(BundleResourceType.CT_ALIGNED_READS, null, FORMAT_1_FILE_EXTENSION),
                        HtsVersion.NEWEST_VERSION,
                        FORMAT_1_V2_0.getDisplayName() },

                // FORMAT_1 file extension, request version V1_0, resolve to FORMAT, 1_V1_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        makeInputIOPathBundle(BundleResourceType.CT_ALIGNED_READS, null, FORMAT_1_FILE_EXTENSION),
                        V1_0,
                        FORMAT_1_V1_0.getDisplayName() },

                // FORMAT_1 file extension, request version V1_1, so resolve to FORMAT_1, V1_1
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0),
                        makeInputIOPathBundle(BundleResourceType.CT_ALIGNED_READS, null, FORMAT_1_FILE_EXTENSION),
                        V1_1,
                        FORMAT_1_V1_1.getDisplayName() },

                // FORMAT_1 file extension, request V2_0, so resolve to FORMAT_1, V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0),
                        makeInputIOPathBundle(BundleResourceType.CT_ALIGNED_READS, null, FORMAT_1_FILE_EXTENSION),
                        V2_0,
                        FORMAT_1_V2_0.getDisplayName() },

                // FORMAT_2 file extension, request V2_0, so resolve to FORMAT_2, V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundle(BundleResourceType.CT_ALIGNED_READS, null, FORMAT_2_FILE_EXTENSION),
                        V2_0,
                        FORMAT_2_V2_0.getDisplayName() },

                // FORMAT_2 file extension, format FORMAT_2 specified in the bundle, request V2_0,
                // resolve to FORMAT_2_V2_0;
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundle(BundleResourceType.CT_ALIGNED_READS, FORMAT_2_FORMAT_NAME, FORMAT_2_FILE_EXTENSION),
                        V2_0,
                        FORMAT_2_V2_0.getDisplayName() },

                // FORMAT_3 custom protocol scheme, no version specified, resolve to FORMAT_3, V1_0
                { Arrays.asList(FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0),
                        new BundleBuilder()
                                .addPrimary(new IOPathResource(
                                        new HtsPath(FORMAT_3_PROTOCOL_SCHEME + ":///myFile" + FORMAT_3_FILE_EXTENSION),
                                        BundleResourceType.CT_ALIGNED_READS))
                                .build(),
                        HtsVersion.NEWEST_VERSION,
                        FORMAT_3_V1_0.getDisplayName() },

                // FORMAT_3 custom protocol scheme, no version specified, resolve to FORMAT_3, V2_0
                { Arrays.asList(FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0, FORMAT_3_V2_0),
                        new BundleBuilder()
                                .addPrimary(new IOPathResource(
                                        new HtsPath(FORMAT_3_PROTOCOL_SCHEME + ":///myFile" + FORMAT_3_FILE_EXTENSION),
                                        BundleResourceType.CT_ALIGNED_READS))
                                .build(),
                        HtsVersion.NEWEST_VERSION,
                        FORMAT_3_V2_0.getDisplayName() },

                // FORMAT_3 custom protocol scheme, version FORMAT_3_V1_0 specified, resolve to FORMAT, 3_V1_0
                { Arrays.asList(FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0, FORMAT_3_V2_0),
                        new BundleBuilder()
                                .addPrimary(new IOPathResource(
                                        new HtsPath(FORMAT_3_PROTOCOL_SCHEME + ":///myFile" + FORMAT_3_FILE_EXTENSION),
                                        BundleResourceType.CT_ALIGNED_READS))
                                .build(),
                        V1_0,
                        FORMAT_3_V1_0.getDisplayName() },

                // output *stream*, with format FORMAT_2 specified, request V1_1, resolve to FORMAT_2, V1_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeOutputStreamBundle(BundleResourceType.CT_ALIGNED_READS, FORMAT_2_FORMAT_NAME),
                        V1_1,
                        FORMAT_2_V1_1.getDisplayName() },

                // output *stream*, with embedded format (FORMAT_2)  specified, request NEWEST_VERSION,
                // resolve to FORMAT_2, V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeOutputStreamBundle(BundleResourceType.CT_ALIGNED_READS, FORMAT_2_FORMAT_NAME),
                        HtsVersion.NEWEST_VERSION,
                        FORMAT_2_V2_0.getDisplayName() },
        };
    }

    @Test(dataProvider = "resolveCodecForEncodingSucceeds")
    public void testResolveCodecForEncodingSucceeds(
            final List<HtsCodec<?, ?>> codecs,
            final Bundle bundle,
            final HtsVersion htsVersionRequested,
            final String expectedCodecDisplayName) {
        final HtsCodecResolver<HtsCodec<?, ?>> testCodecs = new HtsCodecResolver<>(BundleResourceType.CT_ALIGNED_READS);
        codecs.forEach(c -> testCodecs.registerCodec(c));
        final HtsCodec<?, ?> resolvedCodec = testCodecs.resolveForEncoding(
                bundle,
                htsVersionRequested);
        Assert.assertEquals(resolvedCodec.getDisplayName(), expectedCodecDisplayName);
    }

    @DataProvider(name="resolveCodecForEncodingFails")
    public Object[][] resolveCodecForEncodingFails() {
        return new Object[][]{
                // array of codecs to register, a resource bundle, optional version, expected exception message fragment

                // no content type or version specified, no codecs registered
                { Collections.emptyList(),
                        makeInputIOPathBundle(
                                BundleResourceType.CT_ALIGNED_READS,
                                null,
                                FORMAT_1_FILE_EXTENSION),
                        HtsVersion.NEWEST_VERSION,
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},

                // content type and version specified, no codecs registered
                { Collections.emptyList(),
                        makeInputIOPathBundle(
                                BundleResourceType.CT_ALIGNED_READS,
                                FORMAT_1_FORMAT_NAME,
                                FORMAT_1_FILE_EXTENSION),
                        V1_0,
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},

                // FORMAT_1 file extension, format FORMAT_1 specified, no FORMAT_1 codecs registered
                { Arrays.asList(FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0, FORMAT_3_V2_0),
                        makeInputIOPathBundle(
                                BundleResourceType.CT_ALIGNED_READS,
                                FORMAT_1_FORMAT_NAME,
                                FORMAT_1_FILE_EXTENSION),
                        V1_0,
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},

                // no format specified for OutputStream (for output streams, the content type and format
                // must be specified)
                //TODO: this should have a better error message saying the format must be specified in the
                //resource for stream encoders
                { Arrays.asList(FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0, FORMAT_3_V2_0),
                        makeOutputStreamBundle(BundleResourceType.CT_ALIGNED_READS, null),
                        V1_0,
                        HtsCodecResolver.MULTIPLE_SUPPORTING_CODECS_ERROR},

                // format specified for IOPath resource, but requested version not registered
                { Arrays.asList(FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0, FORMAT_3_V2_0),
                        makeInputIOPathBundle(
                                BundleResourceType.CT_ALIGNED_READS,
                                FORMAT_1_FORMAT_NAME,
                                FORMAT_1_FILE_EXTENSION),
                        new HtsVersion(3, 0, 0), // version 3.0.0 not registered
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},

                // format specified for OutputStream resource, but requested version isn't registered
                { Arrays.asList(FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0, FORMAT_3_V2_0),
                        makeOutputStreamBundle(BundleResourceType.CT_ALIGNED_READS, FORMAT_1_FORMAT_NAME),
                        new HtsVersion(3, 0, 0), // version 3.0.0 not registered
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},

                // bundle contains an INPUT stream resource instead of an OUTPUT stream
                { Arrays.asList(FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0, FORMAT_3_V2_0),
                        makeInputStreamBundleWithContent(
                                BundleResourceType.CT_ALIGNED_READS,
                                FORMAT_1_FORMAT_NAME,
                                "irrelevant content",
                                false),
                        V1_0,
                        "cannot be used as an output resource" },

                // IOPath with file extension doesn't match the specified format
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundleWithContent(
                                BundleResourceType.CT_ALIGNED_READS,
                                FORMAT_1_FORMAT_NAME,
                                FORMAT_3_FILE_EXTENSION, // use the wrong file extension
                                FORMAT_1_STREAM_SIGNATURE + V2_0,
                                false),
                        V1_0,
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},
        };
    }

    @Test(dataProvider = "resolveCodecForEncodingFails", expectedExceptions=RuntimeException.class)
    public void testResolveCodecForEncodingFails(
            final List<HtsCodec<?, ?>> codecs,
            final Bundle bundle,
            final HtsVersion htsVersion,
            final String expectedMessage) {
        final HtsCodecResolver<HtsCodec<?, ?>> testCodecs = new HtsCodecResolver<>(BundleResourceType.CT_ALIGNED_READS);
        codecs.forEach(c -> testCodecs.registerCodec(c));
        try {
                testCodecs.resolveForEncoding(bundle, htsVersion);
        } catch (final RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains(expectedMessage));
            throw e;
        }
    }

    @Test(expectedExceptions={RuntimeException.class})
    public void testResolveCodecForEncodingMultipleCodecs() {
        // this test is somewhat contrived since in that its using two codecs that should never
        // really resolve to the same input, but it doesnt matter since the important thing is
        // to exercise the code path to ensure that it works
        try {
            HtsCodecResolver.getOneOrThrow(
                    Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                    () -> "test input");
        } catch (final RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains(HtsCodecResolver.MULTIPLE_SUPPORTING_CODECS_ERROR));
            throw e;
        }
    }

    private static IOPath createTempFileWithContents(final String contents, final String extension, final boolean gzipOutput) {
        Assert.assertTrue(extension.startsWith("."));
        final IOPath tempFile = IOUtils.createTempPath("codecResolution", extension);
        IOUtils.writeStringToPath(tempFile, contents, gzipOutput);
        return tempFile;
    }

    static Bundle makeInputIOPathBundleWithContent(
            final String contentType,
            final String format,
            final String fileExtension,
            final String contents,
            final boolean gzipOutput) {
        final IOPath tempPath = createTempFileWithContents(contents, fileExtension, gzipOutput);
        final IOPathResource ioPathResource = format == null ?
                new IOPathResource(tempPath, contentType) :
                new IOPathResource(tempPath, contentType, format);
        return new BundleBuilder().addPrimary(ioPathResource).build();
    }

    static Bundle makeInputIOPathBundle(
            final String contentType,
            final String format,
            final String fileExtension) {
        final IOPath tempPath = IOUtils.createTempPath("codecResolution", fileExtension);
        if (format == null) {
            return new BundleBuilder()
                    .addPrimary(new IOPathResource(tempPath, contentType))
                    .build();
        } else {
            return new BundleBuilder()
                    .addPrimary(new IOPathResource(tempPath, contentType, format))
                    .build();
        }

    }

    public static Bundle makeOutputStreamBundle(final String contentType, final String format) {
        final OutputStream os = new ByteArrayOutputStream();
        return new BundleBuilder()
                .addPrimary(new OutputStreamResource(os, "test stream", contentType, format))
                .build();
    }

    static Bundle makeInputStreamBundleWithContent(
            final String contentType,
            final String format,
            final String contents,
            final boolean gzipInput) {
        final String displayName = "testDisplayName";
        try (final InputStream bis =
                     gzipInput == true ?
                             getInputStreamOnGzippedContent(contents) :
                             new ByteArrayInputStream(contents.getBytes())) {
            final InputStreamResource isr = format == null ?
                    new InputStreamResource(bis, displayName, contentType) :
                    new InputStreamResource(bis, displayName, contentType, format);
            return new BundleBuilder().addPrimary(isr).build();
        } catch (final IOException e) {
             throw new HtsjdkIOException(e);
        }
    }

    final static InputStream getInputStreamOnGzippedContent(final String contents) {
        try (final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            try (final BlockCompressedOutputStream bcs = new BlockCompressedOutputStream(bos, (File) null)) {
                bcs.write(contents.getBytes());
            }
            final byte array[] = bos.toByteArray();
            return new ByteArrayInputStream(array);
        } catch (final IOException e) {
            throw new HtsjdkIOException(e);
        }
    }

}
