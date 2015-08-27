package htsjdk.samtools;

import htsjdk.samtools.cram.CRAIIndex;
import htsjdk.samtools.seekablestream.SeekableBufferedStream;
import htsjdk.samtools.seekablestream.SeekableStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * A helper class to read BAI and CRAI indexes. Main goal is to provide BAI stream as a sort of common API for all index types.
 * <p/>
 * Created by vadim on 14/08/2015.
 */
public enum SamIndexes {
    BAI(BAMIndex.BAMIndexSuffix, "BAI\1".getBytes()),
    // CRAI is gzipped text, so it's magic is same as {@link java.util.zip.GZIPInputStream.GZIP_MAGIC}
    CRAI(CRAIIndex.CRAI_INDEX_SUFFIX, new byte[]{(byte) 0x1f, (byte) 0x8b});

    public final String fileNameSuffix;
    public final byte[] magic;

    SamIndexes(final String fileNameSuffix, final byte[] magic) {
        this.fileNameSuffix = fileNameSuffix;
        this.magic = magic;
    }

    public static InputStream openIndexFileAsBaiOrNull(final File file, final SAMSequenceDictionary dictionary) throws IOException {
        return openIndexUrlAsBaiOrNull(file.toURI().toURL(), dictionary);
    }

    public static InputStream openIndexUrlAsBaiOrNull(final URL url, final SAMSequenceDictionary dictionary) throws IOException {
        if (url.getFile().toLowerCase().endsWith(BAI.fileNameSuffix.toLowerCase())) {
            return url.openStream();
        }
        if (url.getFile().toLowerCase().endsWith(CRAI.fileNameSuffix.toLowerCase())) {
            return CRAIIndex.openCraiFileAsBaiStream(url.openStream(), dictionary);
        }

        return null;
    }

    public static InputStream asBaiStreamOrNull(final InputStream inputStream, final SAMSequenceDictionary dictionary) throws IOException {
        final BufferedInputStream bis = new BufferedInputStream(inputStream);
        bis.mark(BAI.magic.length);
        if (doesStreamStartWith(bis, BAI.magic)) {
            bis.reset();
            return bis;
        } else {
            bis.reset();
        }

        bis.mark(CRAI.magic.length);
        if (doesStreamStartWith(bis, CRAI.magic)) {
            bis.reset();
            return CRAIIndex.openCraiFileAsBaiStream(bis, dictionary);
        } else {
            bis.reset();
        }

        return null;
    }

    public static SeekableStream asBaiSeekableStreamOrNull(final SeekableStream inputStream, final SAMSequenceDictionary dictionary) throws IOException {
        final SeekableBufferedStream bis = new SeekableBufferedStream(inputStream);
        bis.seek(0);
        if (doesStreamStartWith(bis, BAI.magic)) {
            bis.seek(0);
            return bis;
        }

        bis.seek(0);
        if (doesStreamStartWith(bis, CRAI.magic)) {
            bis.seek(0);
            return CRAIIndex.openCraiFileAsBaiStream(bis, dictionary);
        } else {
            bis.reset();
        }

        return null;
    }

    private static boolean doesStreamStartWith(final InputStream is, final byte[] bytes) throws IOException {
        for (final byte b : bytes) {
            if (is.read() != (0xFF & b)) {
                return false;
            }
        }
        return true;
    }
}
