package htsjdk.samtools.cram.encoding.core;

import htsjdk.samtools.cram.encoding.CramCodec;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;

public abstract class CoreBitCodec<T> implements CramCodec<T> {
    protected final BitInputStream coreBlockInputStream;
    protected final BitOutputStream coreBlockOutputStream;

    public CoreBitCodec(final BitInputStream coreBlockInputStream, final BitOutputStream coreBlockOutputStream) {
        this.coreBlockInputStream = coreBlockInputStream;
        this.coreBlockOutputStream = coreBlockOutputStream;
    }
}