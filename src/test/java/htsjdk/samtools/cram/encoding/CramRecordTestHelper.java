package htsjdk.samtools.cram.encoding;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.build.CompressionHeaderFactory;
import htsjdk.samtools.cram.build.Sam2CramRecordFactory;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.encoding.writer.CramRecordWriter;
import htsjdk.samtools.cram.io.*;
import htsjdk.samtools.cram.structure.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class CramRecordTestHelper extends HtsjdkTest {

    // attempt at minimal initialization for tests

    CompressionHeader createHeader(final List<CramCompressionRecord> records, final boolean sorted) {
        return new CompressionHeaderFactory().build(records, new SubstitutionMatrix(new long[256][256]), sorted);
    }

    List<CramCompressionRecord> createRecords() {
        final List<CramCompressionRecord> list = new ArrayList<>(4);

        // some arbitrary values

        final CramCompressionRecord record1 = createRecord();
        record1.index = 4;
        record1.readLength = 2;
        record1.readBases = "ATCG".getBytes();
        record1.qualityScores = new byte[] { 0x10, 0x20, 0x30, 0x40 };
        record1.readFeatures = new ArrayList<>();
        record1.readGroupID = 6;
        record1.mateSequenceID = 5;
        record1.mateAlignmentStart = 30;
        record1.mappingQuality = 30;
        record1.sequenceName = "SEQNAME";
        record1.sequenceId = 3;
        record1.templateSize = 60;
        record1.sliceIndex = 7;
        list.add(record1);

        final CramCompressionRecord record2 = createRecord();
        record2.index = 2;
        record2.readLength = 9;
        record2.readBases = "AAAAAAAAAAA".getBytes();
        record2.qualityScores = new byte[] { 0x01, 0x22, 0, 0x7F };
        record2.readFeatures = new ArrayList<>();
        record2.readGroupID = 1;
        record2.mateSequenceID = 33;
        record2.mateAlignmentStart = 3;
        record2.mappingQuality = 15;
        record2.sequenceName = "SEQNAME2";
        record2.sequenceId = 1;
        record2.templateSize = 30;
        record2.sliceIndex = 0;
        list.add(record2);

        final CramCompressionRecord record3 = createRecord();
        record3.index = 1;
        record3.readLength = 75;
        record3.readBases = "G".getBytes();
        record3.qualityScores = new byte[] { 100, 50 };
        record3.readFeatures = new ArrayList<>();
        record3.readGroupID = 2;
        record3.mateSequenceID = 1;
        record3.mateAlignmentStart = 20;
        record3.mappingQuality = 20;
        record3.sequenceName = "SEQNAME3";
        record3.sequenceId = 10;
        record3.templateSize = 200;
        record3.sliceIndex = 1;
        list.add(record3);

        final CramCompressionRecord record4 = createRecord();
        record4.index = 4;
        record4.readLength = 2;
        record4.readBases = "GATTACA".getBytes();
        record4.qualityScores = new byte[] { 0x1, 0x2, 0x3, 0x4 };
        record4.readFeatures = new ArrayList<>();
        record4.readGroupID = 3;
        record4.mateSequenceID = 0;
        record4.mateAlignmentStart = 11;
        record4.mappingQuality = 50;
        record4.sequenceName = "SEQNAME4";
        record4.sequenceId = 0;
        record4.templateSize = 450;
        record4.sliceIndex = 15;
        list.add(record4);

        return list;
    }

    CramCompressionRecord createRecord() {
        final SAMFileHeader fileHeader = new SAMFileHeader();
        final SAMRecord record = new SAMRecord(fileHeader);

        final Sam2CramRecordFactory sam2CramRecordFactory = new Sam2CramRecordFactory(null, fileHeader, CramVersions.CRAM_v3);
        return sam2CramRecordFactory.createCramRecord(record);
    }

    Map<Integer, ByteArrayOutputStream> createOutputMap(final CompressionHeader header) {
        return header.encodingMap.values()
                .stream()
                .filter(params -> params.id == EncodingID.EXTERNAL)
                .collect(Collectors.toMap(
                        params -> ITF8.readUnsignedITF8(params.params),
                        params -> new ByteArrayOutputStream()));
    }

    Map<Integer,InputStream> createInputMap(final Map<Integer, ByteArrayOutputStream> outputMap) {
        return outputMap.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new ByteArrayInputStream(e.getValue().toByteArray())));
    }

    public byte[] write(final List<CramCompressionRecord> records,
                        final CompressionHeader header,
                        final int refId,
                        final Map<Integer, ByteArrayOutputStream> outputMap) throws IOException {
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream();
             final BitOutputStream bos = new DefaultBitOutputStream(os)) {

            final CramRecordWriter writer = new CramRecordWriter(bos, outputMap, header, refId);
            writer.writeCramCompressionRecords(records, 0);

            return os.toByteArray();
        }
    }

}
