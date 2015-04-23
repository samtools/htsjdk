/*******************************************************************************
 * Copyright 2013 EMBL-EBI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package htsjdk.samtools.cram.build;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.cram.encoding.reader.CramRecordReader;
import htsjdk.samtools.cram.encoding.reader.DataReaderFactory;
import htsjdk.samtools.cram.encoding.reader.DataReaderFactory.DataReaderWithStats;
import htsjdk.samtools.cram.io.DefaultBitInputStream;
import htsjdk.samtools.cram.structure.CompressionHeader;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.Slice;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.Log.LogLevel;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ContainerParser {
    private static final Log log = Log.getInstance(ContainerParser.class);

    private final SAMFileHeader samFileHeader;
    private final Map<String, Long> nanosecondsMap = new TreeMap<String, Long>();

    public ContainerParser(SAMFileHeader samFileHeader) {
        this.samFileHeader = samFileHeader;
    }

    public List<CramCompressionRecord> getRecords(Container container,
                                                  ArrayList<CramCompressionRecord> records) throws IllegalArgumentException,
            IllegalAccessException, IOException {
        long time1 = System.nanoTime();
        if (records == null)
            records = new ArrayList<CramCompressionRecord>(container.nofRecords);

        for (Slice s : container.slices)
            records.addAll(getRecords(s, container.h));

        long time2 = System.nanoTime();

        container.parseTime = time2 - time1;

        if (log.isEnabled(LogLevel.DEBUG)) {
            for (String key : nanosecondsMap.keySet()) {
                log.debug(String.format("%s: %dms.", key, nanosecondsMap.get(key) / 1000000));
            }
        }

        return records;
    }

    ArrayList<CramCompressionRecord> getRecords(ArrayList<CramCompressionRecord> records,
                                                Slice s, CompressionHeader h) throws IllegalArgumentException,
            IllegalAccessException {
        String seqName = SAMRecord.NO_ALIGNMENT_REFERENCE_NAME;
        switch (s.sequenceId) {
            case SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX:
            case -2:
                break;

            default:
                SAMSequenceRecord sequence = samFileHeader
                        .getSequence(s.sequenceId);
                seqName = sequence.getSequenceName();
                break;
        }

        DataReaderFactory f = new DataReaderFactory();
        Map<Integer, InputStream> inputMap = new HashMap<Integer, InputStream>();
        for (Integer exId : s.external.keySet()) {
            log.debug("Adding external data: " + exId);
            inputMap.put(exId, new ByteArrayInputStream(s.external.get(exId)
                    .getRawContent()));
        }

        long time ;
        CramRecordReader reader = new CramRecordReader();
        f.buildReader(reader, new DefaultBitInputStream(
                        new ByteArrayInputStream(s.coreBlock.getRawContent())),
                inputMap, h, s.sequenceId);

        if (records == null)
            records = new ArrayList<CramCompressionRecord>(s.nofRecords);

        long readNanos = 0;
        int prevStart = s.alignmentStart;
        for (int i = 0; i < s.nofRecords; i++) {
            CramCompressionRecord r = new CramCompressionRecord();
            r.sliceIndex = s.index;
            r.index = i;

            time = System.nanoTime();
            reader.read(r);
            readNanos += System.nanoTime() - time;

            if (r.sequenceId == s.sequenceId) {
                r.sequenceName = seqName;
                r.sequenceId = s.sequenceId;
            } else {
                if (r.sequenceId == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX)
                    r.sequenceName = SAMRecord.NO_ALIGNMENT_REFERENCE_NAME;
                else {
                    r.sequenceName = samFileHeader.getSequence(r.sequenceId)
                            .getSequenceName();
                }
            }

            records.add(r);

            if (h.AP_seriesDelta) {
                prevStart += r.alignmentDelta;
                r.alignmentStart = prevStart;
            }
        }
        log.debug("Slice records read time: " + readNanos / 1000000);

        Map<String, DataReaderWithStats> statMap = f.getStats(reader);
        for (String key : statMap.keySet()) {
            long value ;
            if (!nanosecondsMap.containsKey(key)) {
                nanosecondsMap.put(key, 0L);
                value = 0;
            } else
                value = nanosecondsMap.get(key);
            nanosecondsMap.put(key, value + statMap.get(key).nanos);
        }
        return records;
    }

    List<CramCompressionRecord> getRecords(Slice s, CompressionHeader h)
            throws IllegalArgumentException, IllegalAccessException {
        return getRecords(null, s, h);
    }
}
