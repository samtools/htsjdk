package htsjdk.beta.codecs.reads.cram.cramV3_0;

import htsjdk.beta.codecs.reads.cram.CRAMCodec;
import htsjdk.beta.codecs.reads.cram.CRAMDecoder;
import htsjdk.beta.codecs.reads.cram.CRAMDecoderOptions;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.HtsCodecVersion;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.samtools.CRAMFileReader;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;

/**
 * CRAM v3.0 decoder.
 */
public class CRAMDecoderV3_0 extends CRAMDecoder {
    private final CRAMDecoderOptions cramDecoderOptions;
    private final CRAMFileReader cramReader;
    private final SAMFileHeader samFileHeader;

    public CRAMDecoderV3_0(final Bundle bundle, final ReadsDecoderOptions readsDecoderOptions) {
        super(bundle, readsDecoderOptions);
        this.cramDecoderOptions = readsDecoderOptions instanceof CRAMDecoderOptions ?
                (CRAMDecoderOptions) readsDecoderOptions :
                null;
        cramReader = getCRAMReader();
        samFileHeader = cramReader.getFileHeader();
    }

    @Override
    public HtsCodecVersion getVersion() {
        return CRAMCodecV3_0.VERSION_3_0;
    }

    @Override
    public SAMFileHeader getHeader() {
        return samFileHeader;
    }

    @Override
    public Iterator<SAMRecord> iterator() {
        return cramReader.getIterator();
    }

    @Override
    public void close() {
        cramReader.close();
    }

    private CRAMFileReader getCRAMReader() {
        //TODO: honor decoderOptions
        final CRAMFileReader cramFileReader;

        final BundleResource readsInput = inputBundle.getOrThrow(BundleResourceType.READS);
        final Optional<IOPath> readsPath = readsInput.getIOPath();
        if (!readsPath.isPresent()) {
            throw new IllegalArgumentException("Currently onlyIOPaths are supported for reads input bundles");
        }
        final SamInputResource readsResource = SamInputResource.of(readsPath.get().toPath());

        final Optional<BundleResource> indexInput = inputBundle.get(BundleResourceType.READS_INDEX);
        if (indexInput.isPresent()) {
            final Optional<IOPath> indexPath = indexInput.get().getIOPath();
            if (!indexPath.isPresent()) {
                throw new IllegalArgumentException("Currently only IOPaths are supported for index input bundles");
            }
            readsResource.index(indexPath.get().toPath());
        }
        try {
            cramFileReader = new CRAMFileReader(
                    readsPath.get().getInputStream(),
                    (SeekableStream) null,
                    readsDecoderOptions.getReferencePath() == null ?
                            ReferenceSource.getDefaultCRAMReferenceSource() :
                            CRAMCodec.getCRAMReferenceSource(readsDecoderOptions.getReferencePath()),
                    readsDecoderOptions.getSamReaderFactory().validationStringency());
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
        return cramFileReader;
    }

}
