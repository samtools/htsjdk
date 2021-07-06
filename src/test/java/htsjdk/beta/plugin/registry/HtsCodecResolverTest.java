package htsjdk.beta.plugin.registry;

import htsjdk.HtsjdkTest;
import htsjdk.beta.plugin.HtsCodec;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.IOUtils;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleBuilder;
import htsjdk.beta.plugin.bundle.IOPathResource;
import htsjdk.beta.plugin.bundle.InputStreamResource;
import htsjdk.beta.plugin.bundle.OutputStreamResource;
import htsjdk.beta.plugin.bundle.SeekableStreamResource;
import htsjdk.beta.plugin.registry.testcodec.HtsTestCodec;
import htsjdk.beta.plugin.registry.testcodec.HtsTestCodecFormat;
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

//TODO: test/rules for how to resolve input index
//TODO: test SeekableStreamInputs
//TODO: test large number of inputs where the caller asserts the content type, content subtype, version
//TODO: add a test where the expected file extension isn't present (what are the rules for canDecode* ?)
//TODO: handle *writable* custom protocol schemes (though we don't have any ? genomicsDB ?)

// Tests for resolving codec (encoder/decoder) requests given a set of input/output resources and a list
// of registered codecs.
public class HtsCodecResolverTest extends HtsjdkTest {
    // all of the test codecs created here have the same codec (content) type, with each codec varying by
    // content sub type, version, or protocol scheme
    final static String TEST_CODEC_CONTENT_TYPE = "TEST_CODEC_CONTENT_TYPE";

    // parameters for file format 1
    final static String FORMAT_1_CONTENT_SUBTYPE = HtsTestCodecFormat.FILE_FORMAT_1.toString();
    final static String FORMAT_1_STREAM_SIGNATURE = HtsTestCodecFormat.FILE_FORMAT_1.name();
    final static String FORMAT_1_FILE_EXTENSION = ".f1";

    // parameters for file format 2
    final static String FORMAT_2_CONTENT_SUBTYPE = HtsTestCodecFormat.FILE_FORMAT_2.toString();
    final static String FORMAT_2_STREAM_SIGNATURE = HtsTestCodecFormat.FILE_FORMAT_2.name();
    final static String FORMAT_2_FILE_EXTENSION = ".f2";

    // parameters for file format 3, which uses a custom protocol scheme
    final static String FORMAT_3_FILE_EXTENSION = ".f3";
    final static String FORMAT_3_PROTOCOL_SCHEME = "ps3";

    final static HtsVersion V1_0 = new HtsVersion(1, 0, 0);
    final static HtsVersion V1_1 = new HtsVersion(1, 1, 0);
    final static HtsVersion V2_0 = new HtsVersion(2, 0, 0);

    // file format FORMAT_1, V1_0
    final static HtsTestCodec FORMAT_1_V1_0 = new HtsTestCodec(
            HtsTestCodecFormat.FILE_FORMAT_1,
            V1_0,
            FORMAT_1_FILE_EXTENSION,
            null,
            false);
    // file format FORMAT_1, V1_1
    final static HtsTestCodec FORMAT_1_V1_1 = new HtsTestCodec(
            HtsTestCodecFormat.FILE_FORMAT_1,
            V1_1,
            FORMAT_1_FILE_EXTENSION,
            null,
            false);
    // file format FORMAT_1, V2_0
    final static HtsTestCodec FORMAT_1_V2_0 = new HtsTestCodec(
            HtsTestCodecFormat.FILE_FORMAT_1,
            V2_0,
            FORMAT_1_FILE_EXTENSION,
            null,
            false);

    // file format FORMAT_2, V1_0
    final static HtsTestCodec FORMAT_2_V1_0 = new HtsTestCodec(
            HtsTestCodecFormat.FILE_FORMAT_2,
            V1_0,
            FORMAT_2_FILE_EXTENSION,
            null,
            false);
    // file format FORMAT_2, V1_1
    final static HtsTestCodec FORMAT_2_V1_1 = new HtsTestCodec(
            HtsTestCodecFormat.FILE_FORMAT_2,
            V1_1,
            FORMAT_2_FILE_EXTENSION,
            null,
            false);
    // file format FORMAT_2, V2_0, uses gzipped inputs
    final static HtsTestCodec FORMAT_2_V2_0 = new HtsTestCodec(
            HtsTestCodecFormat.FILE_FORMAT_2,
            V2_0,
            FORMAT_2_FILE_EXTENSION,
            null,
            true);  // expect gzipped inputs

    // file format FORMAT_3, V1_0, uses a custom protocol scheme
    final static HtsTestCodec FORMAT_3_V1_0 = new HtsTestCodec(
            HtsTestCodecFormat.FILE_FORMAT_3,
            V1_0,
            FORMAT_3_FILE_EXTENSION,
            FORMAT_3_PROTOCOL_SCHEME,   // custom protocol scheme
            false);

    // file format FORMAT_3, V2_0, uses a custom protocol scheme
    final static HtsTestCodec FORMAT_3_V2_0 = new HtsTestCodec(
            HtsTestCodecFormat.FILE_FORMAT_3,
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
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_1_FILE_EXTENSION,
                                FORMAT_1_STREAM_SIGNATURE + V1_0,
                                false),
                        FORMAT_1_V1_0.getDisplayName() },

                // input IOPath is FORMAT_1, V1_1, resolve to FORMAT_1_V1_1
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_1_FILE_EXTENSION,
                                FORMAT_1_STREAM_SIGNATURE + V1_1,
                                false),
                        FORMAT_1_V1_1.getDisplayName() },

                // input IOPath is FORMAT_1, V2_0, resolve to FORMAT_1_V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_1_FILE_EXTENSION,
                                FORMAT_1_STREAM_SIGNATURE + V2_0,
                                false),
                        FORMAT_1_V2_0.getDisplayName() },

                // input IOPath is FORMAT_1, V_2_0, with FORMAT_1 content subtype specified in the bundle,
                // resolve to FORMAT_1_V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                FORMAT_1_CONTENT_SUBTYPE,
                                FORMAT_1_FILE_EXTENSION,
                                FORMAT_1_STREAM_SIGNATURE + V2_0,
                                false),
                        FORMAT_1_V2_0.getDisplayName() },

                // input IOPath is FORMAT_2, V1_0, resolve to FORMAT_2_V1_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_2_FILE_EXTENSION,
                                FORMAT_2_STREAM_SIGNATURE + V1_0,
                                false),
                        FORMAT_2_V1_0.getDisplayName() },

                // input IOPath is FORMAT_2, V1_1, resolve to FORMAT_2_V1_1
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_2_FILE_EXTENSION,
                                FORMAT_2_STREAM_SIGNATURE + V1_1,
                                false),
                        FORMAT_2_V1_1.getDisplayName() },

                // input IOPath is gzipped FORMAT_2 (which requires GZIPPED inputs), V2_0, resolve to FORMAT_2_V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
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
                                        TEST_CODEC_CONTENT_TYPE))
                                .build(),
                        FORMAT_3_V1_0.getDisplayName() },

                // input STREAM is FORMAT_1, V1_0, resolve to FORMAT_1_V1_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputStreamBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_1_STREAM_SIGNATURE + V1_0,
                                false),
                        FORMAT_1_V1_0.getDisplayName() },

                // input STREAM is gzipped FORMAT_2, V2_0 (which requires GZIPPED inputs), resolve to  FORMAT_2_V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputStreamBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_2_STREAM_SIGNATURE + V2_0,
                                true),    // use gzipped inputs
                        FORMAT_2_V2_0.getDisplayName() },

                // input *SEEKABLE STREAM* is gzipped FORMAT_2, V2_0 (which requires GZIPPED inputs), resolve to  FORMAT_2_V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        new BundleBuilder().addPrimary(new SeekableStreamResource(
                                makeInputIOPathBundleWithContent(
                                    TEST_CODEC_CONTENT_TYPE,
                                    null,
                                    FORMAT_2_FILE_EXTENSION,
                                    FORMAT_2_STREAM_SIGNATURE + V2_0,
                                    true) // use gzipped inputs
                                .getPrimaryResource().getSeekableStream().get(), TEST_CODEC_CONTENT_TYPE, TEST_CODEC_CONTENT_TYPE)).build(),
                        FORMAT_2_V2_0.getDisplayName() },
        };
    }

    @Test(dataProvider = "resolveCodecForDecodingSucceeds")
    public void testResolveCodecForDecodingSucceeds(
            final List<HtsCodec<HtsTestCodecFormat, ?, ?>> codecs,
            final Bundle bundle,
            final String expectedCodecDisplayName) {
        final HtsCodecResolver<HtsTestCodecFormat, HtsCodec<HtsTestCodecFormat, ?, ?>>
                testCodecs = new HtsCodecResolver<>(TEST_CODEC_CONTENT_TYPE, HtsTestCodecFormat.FILE_FORMAT_1);
        codecs.forEach(c -> testCodecs.registerCodec(c));
        final HtsCodec<HtsTestCodecFormat, ?, ?> resolvedCodec = testCodecs.resolveForDecoding(bundle);
        Assert.assertEquals(resolvedCodec.getDisplayName(), expectedCodecDisplayName);
    }

    @DataProvider(name="resolveCodecForDecodingFails")
    public Object[][] resolveCodecForDecodingFails() {
        return new Object[][]{
                // array of codecs to register, a resource bundle, expected exception message fragment

                // input IOPath is FORMAT_1, V2_0, no codecs registered
                { Collections.emptyList(),
                        makeInputIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_1_FILE_EXTENSION,
                                FORMAT_1_STREAM_SIGNATURE + V2_0,
                                false),
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},

                // input IOPath is FORMAT_2, V1_0, no codec is registered for any FORMAT_2 version
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        makeInputIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_2_FILE_EXTENSION,
                                FORMAT_2_STREAM_SIGNATURE + V1_0,
                                false),
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},

                // input IOPath is FORMAT_1, V2_0, no codec is registered for V2_0 of FORMAT_1
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        makeInputIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
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

                // input IOPath resource has a content SUB-type that doesn't correspond to any format for the
                // content type
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        makeInputIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                "BOGUS_SUB_CONTENT_TYPE",
                                FORMAT_1_FILE_EXTENSION,
                                FORMAT_1_STREAM_SIGNATURE + V1_0,
                                false),
                        "does not correspond to any known subtype for content type" },

                // the resource in the bundle has a valid content type, and the file extension is valid,
                // but the signature in the underlying file is "BOGUS_SIGNATURE", which doesn't match the
                // signature for any of the codecs
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        makeInputIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_1_FILE_EXTENSION,
                                "BOGUS_SIGNATURE" + V1_0,
                                false),
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},

                // the resource in the bundle has a valid content type, but the signature in the underlying
                // file has version "V2_0", which doesn't match the version any codec expects
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        makeInputIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_1_FILE_EXTENSION,
                                FORMAT_1_STREAM_SIGNATURE + V2_0,
                                false),
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},

                // input *IOPath* has content *subtype* FORMAT_1 (specifying a subtype causes the resolver to
                // prune all codecs except those that handle that subtype), but the underlying file contains the
                // signature FORMAT_2, which none of the FORMAT_1 codecs accept
                //TODO: this should have a better error message or warning saying the content subtype in the
                // bundle resource doesn't match the signature in the underlying resource, which is likely
                // either a user or coding error
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                FORMAT_1_CONTENT_SUBTYPE,
                                FORMAT_2_FILE_EXTENSION,
                                FORMAT_2_STREAM_SIGNATURE + V2_0,
                                false),
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},

                // input *STREAM* has content *subtype* FORMAT_1 (specifying a subtype causes the resolver to
                // prune all codecs except those that handle that subtype), but the underlying stream signature
                // contains the signature FORMAT_2, which none of the FORMAT_1 codecs accept
                //TODO: this should have a better error message or warning saying the content type in the bundle
                // resource doesn't match the signature in the underlying stream, since its likely user or
                // programming error
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputStreamBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                FORMAT_1_CONTENT_SUBTYPE,
                                FORMAT_2_STREAM_SIGNATURE + V1_0,
                                false),
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},

                // IOPath with a file extension that doesn't match the specified subtype
                //TODO: this should have a better error message or warning saying the file extension for the resource
                // doesn't match the file extension, since thats likely user or programming error
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                FORMAT_1_CONTENT_SUBTYPE,
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
                                        TEST_CODEC_CONTENT_TYPE,
                                        FORMAT_1_CONTENT_SUBTYPE)
                            ).build(),
                        "specifies a custom protocol (bogus) which no registered codec claims, and for which no NIO file system provider is available"},

                // the primary resource has the required content type, but is an output-only resource (OutputStream),
                // not a readable input resource
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        makeOutputStreamBundle(TEST_CODEC_CONTENT_TYPE, null),
                        "cannot be used as an input resource"},
        };
    }

    @Test(dataProvider = "resolveCodecForDecodingFails", expectedExceptions=RuntimeException.class)
    public void testResolveCodecForDecodingFails(
            final List<HtsCodec<HtsTestCodecFormat, ?, ?>> codecs,
            final Bundle bundle,
            final String expectedMessage) {
        final HtsCodecResolver<HtsTestCodecFormat, HtsCodec<HtsTestCodecFormat, ?, ?>>
                testCodecs = new HtsCodecResolver<>(TEST_CODEC_CONTENT_TYPE, HtsTestCodecFormat.FILE_FORMAT_1);
        codecs.forEach(c -> testCodecs.registerCodec(c));
        try {
            testCodecs.resolveForDecoding(bundle);
        } catch (final RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains(expectedMessage));
            throw e;
        }
    }

    @DataProvider(name="resolveCodecForEncodingSucceeds")
    public Object[][] resolveCodecForEncodingSucceeds() {
        // Note: unlike inputs, for outputs a version must either be explicitly requested
        // by the caller, or else default to the newest version registered for the format.
        // The format (content subtype) may also need to be explicitly requested, since it may not
        // be discoverable from the output (i.e., if the output is to a raw stream).
        return new Object[][]{
                // array of codecs to register, resource bundle, requested version (or null), expected display name of resolved codec

                // no specific version requested, resolve to the newest version registered for FORMAT_1, resolve
                // to FORMAT_1, V1_0
                { Arrays.asList(FORMAT_1_V1_0),
                        makeInputIOPathBundle(TEST_CODEC_CONTENT_TYPE, null, FORMAT_1_FILE_EXTENSION),
                        HtsVersion.NEWEST_VERSION,
                        FORMAT_1_V1_0.getDisplayName() },

                // no specific version requested, resolve to the newest version registered for FORMAT_1, which
                // is FORMAT_1, V1_1
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        makeInputIOPathBundle(TEST_CODEC_CONTENT_TYPE, null, FORMAT_1_FILE_EXTENSION),
                        HtsVersion.NEWEST_VERSION,
                        FORMAT_1_V1_1.getDisplayName() },

                // no specific version requested, resolve to the newest version registered for FORMAT_1, which
                // is FORMAT_1, V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0),
                        makeInputIOPathBundle(TEST_CODEC_CONTENT_TYPE, null, FORMAT_1_FILE_EXTENSION),
                        HtsVersion.NEWEST_VERSION,
                        FORMAT_1_V2_0.getDisplayName() },

                // no specific version requested, resolve to the newest version registered for FORMAT_1, which is
                // V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundle(TEST_CODEC_CONTENT_TYPE, null, FORMAT_1_FILE_EXTENSION),
                        HtsVersion.NEWEST_VERSION,
                        FORMAT_1_V2_0.getDisplayName() },

                // FORMAT_1 file extension, request version V1_0, resolve to FORMAT, 1_V1_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1),
                        makeInputIOPathBundle(TEST_CODEC_CONTENT_TYPE, null, FORMAT_1_FILE_EXTENSION),
                        V1_0,
                        FORMAT_1_V1_0.getDisplayName() },

                // FORMAT_1 file extension, request version V1_1, so resolve to FORMAT_1, V1_1
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0),
                        makeInputIOPathBundle(TEST_CODEC_CONTENT_TYPE, null, FORMAT_1_FILE_EXTENSION),
                        V1_1,
                        FORMAT_1_V1_1.getDisplayName() },

                // FORMAT_1 file extension, request V2_0, so resolve to FORMAT_1, V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0),
                        makeInputIOPathBundle(TEST_CODEC_CONTENT_TYPE, null, FORMAT_1_FILE_EXTENSION),
                        V2_0,
                        FORMAT_1_V2_0.getDisplayName() },

                // FORMAT_2 file extension, request V2_0, so resolve to FORMAT_2, V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundle(TEST_CODEC_CONTENT_TYPE, null, FORMAT_2_FILE_EXTENSION),
                        V2_0,
                        FORMAT_2_V2_0.getDisplayName() },

                // FORMAT_2 file extension, content subtype FORMAT_2 specified in the bundle, request V2_0,
                // resolve to FORMAT_2_V2_0;
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundle(TEST_CODEC_CONTENT_TYPE, FORMAT_2_CONTENT_SUBTYPE, FORMAT_2_FILE_EXTENSION),
                        V2_0,
                        FORMAT_2_V2_0.getDisplayName() },

                // FORMAT_3 custom protocol scheme, no version specified, resolve to FORMAT_3, V1_0
                { Arrays.asList(FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0),
                        new BundleBuilder()
                                .addPrimary(new IOPathResource(
                                        new HtsPath(FORMAT_3_PROTOCOL_SCHEME + ":///myFile" + FORMAT_3_FILE_EXTENSION),
                                        TEST_CODEC_CONTENT_TYPE))
                                .build(),
                        HtsVersion.NEWEST_VERSION,
                        FORMAT_3_V1_0.getDisplayName() },

                // FORMAT_3 custom protocol scheme, no version specified, resolve to FORMAT_3, V2_0
                { Arrays.asList(FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0, FORMAT_3_V2_0),
                        new BundleBuilder()
                                .addPrimary(new IOPathResource(
                                        new HtsPath(FORMAT_3_PROTOCOL_SCHEME + ":///myFile" + FORMAT_3_FILE_EXTENSION),
                                        TEST_CODEC_CONTENT_TYPE))
                                .build(),
                        HtsVersion.NEWEST_VERSION,
                        FORMAT_3_V2_0.getDisplayName() },

                // FORMAT_3 custom protocol scheme, version FORMAT_3_V1_0 specified, resolve to FORMAT, 3_V1_0
                { Arrays.asList(FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0, FORMAT_3_V2_0),
                        new BundleBuilder()
                                .addPrimary(new IOPathResource(
                                        new HtsPath(FORMAT_3_PROTOCOL_SCHEME + ":///myFile" + FORMAT_3_FILE_EXTENSION),
                                        TEST_CODEC_CONTENT_TYPE))
                                .build(),
                        V1_0,
                        FORMAT_3_V1_0.getDisplayName() },

                // output *stream*, with content subtype FORMAT_2 specified, request V1_1, resolve to FORMAT_2, V1_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeOutputStreamBundle(TEST_CODEC_CONTENT_TYPE, FORMAT_2_CONTENT_SUBTYPE),
                        V1_1,
                        FORMAT_2_V1_1.getDisplayName() },

                // output *stream*, with embedded content subtype (FORMAT_2)  specified, request NEWEST_VERSION,
                // resolve to FORMAT_2, V2_0
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeOutputStreamBundle(TEST_CODEC_CONTENT_TYPE, FORMAT_2_CONTENT_SUBTYPE),
                        HtsVersion.NEWEST_VERSION,
                        FORMAT_2_V2_0.getDisplayName() },
        };
    }

    @Test(dataProvider = "resolveCodecForEncodingSucceeds")
    public void testResolveCodecForEncodingSucceeds(
            final List<HtsCodec<HtsTestCodecFormat, ?, ?>> codecs,
            final Bundle bundle,
            final HtsVersion htsVersionRequested,
            final String expectedCodecDisplayName) {
        final HtsCodecResolver<HtsTestCodecFormat, HtsCodec<HtsTestCodecFormat, ?, ?>>
                testCodecs = new HtsCodecResolver<>(TEST_CODEC_CONTENT_TYPE, HtsTestCodecFormat.FILE_FORMAT_1);
        codecs.forEach(c -> testCodecs.registerCodec(c));
        final HtsCodec<HtsTestCodecFormat, ?, ?> resolvedCodec = testCodecs.resolveForEncoding(
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
                                TEST_CODEC_CONTENT_TYPE,
                                null,
                                FORMAT_1_FILE_EXTENSION),
                        HtsVersion.NEWEST_VERSION,
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},

                // content type and version specified, no codecs registered
                { Collections.emptyList(),
                        makeInputIOPathBundle(
                                TEST_CODEC_CONTENT_TYPE,
                                FORMAT_1_CONTENT_SUBTYPE,
                                FORMAT_1_FILE_EXTENSION),
                        V1_0,
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},

                // FORMAT_1 file extension, FORMAT_1 subtype specified, no FORMAT_1 codecs registered
                { Arrays.asList(FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0, FORMAT_3_V2_0),
                        makeInputIOPathBundle(
                                TEST_CODEC_CONTENT_TYPE,
                                FORMAT_1_CONTENT_SUBTYPE,
                                FORMAT_1_FILE_EXTENSION),
                        V1_0,
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},

                // no content subtype specified for OutputStream (for output streams, the format and subtype
                // must be specified)
                //TODO: this should have a better error message saying the content subtype must be specified
                { Arrays.asList(FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0, FORMAT_3_V2_0),
                        makeOutputStreamBundle(TEST_CODEC_CONTENT_TYPE, null),
                        V1_0,
                        HtsCodecResolver.MULTIPLE_SUPPORTING_CODECS_ERROR},

                // content subtype specified for IOPath resource, but requested version not registered
                { Arrays.asList(FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0, FORMAT_3_V2_0),
                        makeInputIOPathBundle(
                                TEST_CODEC_CONTENT_TYPE,
                                FORMAT_1_CONTENT_SUBTYPE,
                                FORMAT_1_FILE_EXTENSION),
                        new HtsVersion(3, 0, 0), // version 3.0.0 not registered
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},

                // content subtype specified for OutputStream resource, but requested version isn't registered
                { Arrays.asList(FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0, FORMAT_3_V2_0),
                        makeOutputStreamBundle(TEST_CODEC_CONTENT_TYPE, FORMAT_1_CONTENT_SUBTYPE),
                        new HtsVersion(3, 0, 0), // version 3.0.0 not registered
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},

                // bundle contains an INPUT stream resource instead of an OUTPUT stream
                { Arrays.asList(FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0, FORMAT_3_V1_0, FORMAT_3_V2_0),
                        makeInputStreamBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                FORMAT_1_CONTENT_SUBTYPE,
                                "irrelevant content",
                                false),
                        V1_0,
                        "cannot be used as an output resource" },

                // IOPath with file extension doesn't match the specified subtype
                { Arrays.asList(FORMAT_1_V1_0, FORMAT_1_V1_1, FORMAT_1_V2_0, FORMAT_2_V1_0, FORMAT_2_V1_1, FORMAT_2_V2_0),
                        makeInputIOPathBundleWithContent(
                                TEST_CODEC_CONTENT_TYPE,
                                FORMAT_1_CONTENT_SUBTYPE,
                                FORMAT_3_FILE_EXTENSION, // use the wrong file extension
                                FORMAT_1_STREAM_SIGNATURE + V2_0,
                                false),
                        V1_0,
                        HtsCodecResolver.NO_SUPPORTING_CODEC_ERROR},
        };
    }

    @Test(dataProvider = "resolveCodecForEncodingFails", expectedExceptions=RuntimeException.class)
    public void testResolveCodecForEncodingFails(
            final List<HtsCodec<HtsTestCodecFormat, ?, ?>> codecs,
            final Bundle bundle,
            final HtsVersion htsVersion,
            final String expectedMessage) {
        final HtsCodecResolver<HtsTestCodecFormat, HtsCodec<HtsTestCodecFormat, ?, ?>>
                testCodecs = new HtsCodecResolver<>(TEST_CODEC_CONTENT_TYPE, HtsTestCodecFormat.FILE_FORMAT_1);
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

    private IOPath createTempFileWithContents(final String contents, final String extension, final boolean gzipOutput) {
        Assert.assertTrue(extension.startsWith("."));
        final IOPath tempFile = IOUtils.createTempPath("codecResolution", extension);
        IOUtils.writeStringToPath(tempFile, contents, gzipOutput);
        return tempFile;
    }

    private Bundle makeInputIOPathBundleWithContent(
            final String contentType,
            final String contentSubType,
            final String fileExtension,
            final String contents,
            final boolean gzipOutput) {
        final IOPath tempPath = createTempFileWithContents(contents, fileExtension, gzipOutput);
        final IOPathResource ioPathResource = contentSubType == null ?
                new IOPathResource(tempPath, contentType) :
                new IOPathResource(tempPath, contentType, contentSubType);
        return new BundleBuilder().addPrimary(ioPathResource).build();
    }

    private Bundle makeInputIOPathBundle(
            final String contentType,
            final String contentSubType,
            final String fileExtension) {
        final IOPath tempPath = IOUtils.createTempPath("codecResolution", fileExtension);
        if (contentSubType == null) {
            return new BundleBuilder()
                    .addPrimary(new IOPathResource(tempPath, contentType))
                    .build();
        } else {
            return new BundleBuilder()
                    .addPrimary(new IOPathResource(tempPath, contentType, contentSubType))
                    .build();
        }

    }

    public Bundle makeOutputStreamBundle(final String contentType, final String contentSubType) {
        final OutputStream os = new ByteArrayOutputStream();
        return new BundleBuilder()
                .addPrimary(new OutputStreamResource(os, "test stream", contentType, contentSubType))
                .build();
    }

    private Bundle makeInputStreamBundleWithContent(
            final String contentType,
            final String contentSubType,
            final String contents,
            final boolean gzipInput) {
        final String displayName = "testDisplayName";
        try (final InputStream bis =
                     gzipInput == true ?
                             getInputStreamOnGzippedContent(contents) :
                             new ByteArrayInputStream(contents.getBytes())) {
            final InputStreamResource isr = contentSubType == null ?
                    new InputStreamResource(bis, displayName, contentType) :
                    new InputStreamResource(bis, displayName, contentType, contentSubType);
            return new BundleBuilder().addPrimary(isr).build();
        } catch (final IOException e) {
             throw new HtsjdkIOException(e);
        }
    }

    final InputStream getInputStreamOnGzippedContent(final String contents) {
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
