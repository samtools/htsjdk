package htsjdk.samtools.cram.encoding.core;

import htsjdk.samtools.cram.encoding.CRAMCodec;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;

public abstract class CoreCodec<T> implements CRAMCodec<T> {
    protected final BitInputStream coreBlockInputStream;
    protected final BitOutputStream coreBlockOutputStream;

    public CoreCodec(final BitInputStream coreBlockInputStream, final BitOutputStream coreBlockOutputStream) {
        this.coreBlockInputStream = coreBlockInputStream;
        this.coreBlockOutputStream = coreBlockOutputStream;
    }
}