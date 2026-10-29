package htsjdk.samtools;

import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Throughput of {@link BAMRecordCodec}: the time to decode, or to decode and re-encode, a fixed set of synthetic
 * records held in memory as uncompressed BAM, so that BGZF and I/O are not measured.
 *
 * <p>A record decoded from BAM keeps its variable-length data (name, CIGAR, bases, qualities, tags) as bytes and
 * decodes each part when it is first asked for. When it is encoded again, those bytes are written as they are unless
 * something marked them stale. The {@code encode*} benchmarks differ only in what is done to each record between
 * decoding and encoding, so each one, less {@link #decode}, is the cost of encoding a record left in that state.
 *
 * <pre>
 * ./gradlew benchesJar
 * java -jar build/libs/htsjdk-*-benches.jar BamRecordCodec                    # everything
 * java -jar build/libs/htsjdk-*-benches.jar BamRecordCodec.decode -p shape=SHORT
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(value = 2, jvmArgsAppend = "-Xmx4g")
public class BamRecordCodecBenchmark {

    /** The records to code: their number, read length and tags. */
    public enum Shape {
        /** Paired 150 bp reads with the tags an aligner and duplicate marking leave. */
        SHORT(200_000, 150, true),
        /** 15 kb single reads with few tags, as from long-read sequencing. */
        LONG(2_000, 15_000, false);

        final int records;
        final int readLength;
        final boolean shortReadTags;

        Shape(final int records, final int readLength, final boolean shortReadTags) {
            this.records = records;
            this.readLength = readLength;
            this.shortReadTags = shortReadTags;
        }
    }

    /** The records, encoded once as a run of uncompressed BAM records. */
    @State(Scope.Benchmark)
    public static class Input {
        @Param({"SHORT", "LONG"})
        public Shape shape;

        SAMFileHeader header;
        byte[] encoded;
        /** {@link #encoded}, block-compressed as in a BAM file. */
        byte[] bgzf;

        @Setup
        public void encodeRecords() {
            header = new SAMFileHeader();
            header.addSequence(new SAMSequenceRecord("chr1", 248_956_422));
            header.addReadGroup(new SAMReadGroupRecord("rg1"));
            final Random random = new Random(42);
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            final BAMRecordCodec codec = new BAMRecordCodec(header);
            codec.setOutputStream(bytes);
            for (int i = 0; i < shape.records; i++) {
                codec.encode(syntheticRecord(header, shape, i, random));
            }
            encoded = bytes.toByteArray();
            final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (BlockCompressedOutputStream out = new BlockCompressedOutputStream(compressed, (Path) null)) {
                out.write(encoded);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
            bgzf = compressed.toByteArray();
        }
    }

    private static final byte[] BASES = {'A', 'C', 'G', 'T'};

    private static SAMRecord syntheticRecord(
            final SAMFileHeader header, final Shape shape, final int index, final Random random) {
        final SAMRecord rec = new SAMRecord(header);
        rec.setReadName("A00123:45:HXXXXXXXX:1:1101:" + (10_000 + index / 2) + ":" + (1_000 + random.nextInt(30_000)));
        rec.setReferenceIndex(0);
        rec.setAlignmentStart(1 + random.nextInt(200_000_000));
        rec.setMappingQuality(60);
        rec.setCigarString(shape.readLength + "M");
        final byte[] bases = new byte[shape.readLength];
        final byte[] quals = new byte[shape.readLength];
        for (int i = 0; i < bases.length; i++) {
            bases[i] = BASES[random.nextInt(4)];
            quals[i] = (byte) (2 + random.nextInt(39));
        }
        rec.setReadBases(bases);
        rec.setBaseQualities(quals);
        rec.setAttribute("RG", "rg1");
        rec.setAttribute("NM", random.nextInt(5));
        if (shape.shortReadTags) {
            rec.setReadPairedFlag(true);
            rec.setFirstOfPairFlag(index % 2 == 0);
            rec.setSecondOfPairFlag(index % 2 == 1);
            rec.setMateReferenceIndex(0);
            rec.setMateAlignmentStart(rec.getAlignmentStart() + 200);
            rec.setInferredInsertSize(350);
            rec.setAttribute("MD", String.valueOf(shape.readLength));
            rec.setAttribute("AS", shape.readLength - random.nextInt(10));
            rec.setAttribute("XS", random.nextInt(shape.readLength));
            rec.setAttribute("MC", shape.readLength + "M");
            rec.setAttribute("MQ", 60);
        }
        return rec;
    }

    /** An output stream that keeps only a count, so that encoding is measured without copying into a buffer. */
    private static final class CountingSink extends OutputStream {
        long count;

        @Override
        public void write(final int b) {
            count++;
        }

        @Override
        public void write(final byte[] b, final int off, final int len) {
            count += len;
        }
    }

    /** A record-at-a-time step between decoding and encoding; returns a value so that the work is not optimised away. */
    private interface Touch {
        long apply(SAMRecord rec);
    }

    private static BAMRecordCodec decoder(final Input input) {
        final BAMRecordCodec codec = new BAMRecordCodec(input.header);
        codec.setInputStream(new ByteArrayInputStream(input.encoded));
        return codec;
    }

    private static long decodeTouchEncode(final Input input, final Touch touch) {
        final BAMRecordCodec decoder = decoder(input);
        final CountingSink sink = new CountingSink();
        final BAMRecordCodec encoder = new BAMRecordCodec(input.header);
        encoder.setOutputStream(sink);
        long total = 0;
        for (SAMRecord rec = decoder.decode(); rec != null; rec = decoder.decode()) {
            total += touch.apply(rec);
            encoder.encode(rec);
        }
        return total + sink.count;
    }

    /** Decodes each record's fixed-length fields only; the variable-length data stays as bytes. */
    @Benchmark
    public long decode(final Input input) {
        final BAMRecordCodec decoder = decoder(input);
        long total = 0;
        for (SAMRecord rec = decoder.decode(); rec != null; rec = decoder.decode()) {
            total += rec.getAlignmentStart() + rec.getFlags() + rec.getReadLength();
        }
        return total;
    }

    /**
     * As {@link #decode}, but through a {@link BlockCompressedInputStream}, as a BAM file is read; the time includes
     * inflating the blocks.
     */
    @Benchmark
    public long decodeFromBgzf(final Input input) {
        final BAMRecordCodec decoder = new BAMRecordCodec(input.header);
        decoder.setInputStream(new BlockCompressedInputStream(new ByteArrayInputStream(input.bgzf)));
        long total = 0;
        for (SAMRecord rec = decoder.decode(); rec != null; rec = decoder.decode()) {
            total += rec.getAlignmentStart() + rec.getFlags() + rec.getReadLength();
        }
        return total;
    }

    /** Decodes every field of every record, as a reader that looks at everything does. */
    @Benchmark
    public long decodeEverything(final Input input) {
        final BAMRecordCodec decoder = decoder(input);
        long total = 0;
        for (SAMRecord rec = decoder.decode(); rec != null; rec = decoder.decode()) {
            total += rec.getReadName().length()
                    + rec.getCigar().numCigarElements()
                    + rec.getReadBases().length
                    + rec.getBaseQualities().length
                    + rec.getAttributes().size();
        }
        return total;
    }

    /** Re-encodes records as they were decoded, which writes their variable-length bytes unchanged. */
    @Benchmark
    public long encodeUntouched(final Input input) {
        return decodeTouchEncode(input, rec -> 0);
    }

    /** Re-encodes records after a flag change, which leaves the variable-length bytes unchanged too. */
    @Benchmark
    public long encodeAfterFlagChange(final Input input) {
        return decodeTouchEncode(input, rec -> {
            rec.setDuplicateReadFlag(true);
            return 1;
        });
    }

    /** Re-encodes records after setting a string tag, as duplicate marking sets PG on every read. */
    @Benchmark
    public long encodeAfterTagSet(final Input input) {
        return decodeTouchEncode(input, rec -> {
            rec.setAttribute("PG", "MarkDuplicates");
            return 1;
        });
    }

    /** Re-encodes records after removing a tag they don't have, as duplicate marking clears DT on every read. */
    @Benchmark
    public long encodeAfterAbsentTagRemoved(final Input input) {
        return decodeTouchEncode(input, rec -> {
            rec.setAttribute("DT", null);
            return 1;
        });
    }

    /** Re-encodes records after decoding every field, which leaves them stale and so encodes every field. */
    @Benchmark
    public long encodeAfterDecodingEverything(final Input input) {
        return decodeTouchEncode(input, rec -> {
            rec.eagerDecode();
            return 1;
        });
    }

    /** Encodes records built in memory, which have no bytes to reuse; the records are built once, before timing. */
    @State(Scope.Benchmark)
    public static class InMemoryRecords {
        List<SAMRecord> records;

        @Setup
        public void build(final Input input) {
            final Random random = new Random(42);
            records = new ArrayList<>(input.shape.records);
            for (int i = 0; i < input.shape.records; i++) {
                records.add(syntheticRecord(input.header, input.shape, i, random));
            }
        }
    }

    @Benchmark
    public long encodeFromMemory(final Input input, final InMemoryRecords inMemory) {
        final CountingSink sink = new CountingSink();
        final BAMRecordCodec encoder = new BAMRecordCodec(input.header);
        encoder.setOutputStream(sink);
        for (final SAMRecord rec : inMemory.records) {
            encoder.encode(rec);
        }
        return sink.count;
    }
}
