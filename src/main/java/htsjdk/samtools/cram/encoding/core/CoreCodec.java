package htsjdk.samtools.cram.encoding.core;

import htsjdk.samtools.cram.encoding.CramCodec;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;

public abstract class CoreCodec<T> implements CramCodec<T> {
    protected final BitInputStream coreBlockInputStream;
    protected final BitOutputStream coreBlockOutputStream;

    public CoreCodec(final BitInputStream coreBlockInputStream, final BitOutputStream coreBlockOutputStream) {
        this.coreBlockInputStream = coreBlockInputStream;
        this.coreBlockOutputStream = coreBlockOutputStream;
    }
}