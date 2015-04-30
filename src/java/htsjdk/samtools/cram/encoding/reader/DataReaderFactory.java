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
package htsjdk.samtools.cram.encoding.reader;

import htsjdk.samtools.cram.common.IntHashMap;
import htsjdk.samtools.cram.encoding.BitCodec;
import htsjdk.samtools.cram.encoding.DataSeries;
import htsjdk.samtools.cram.encoding.DataSeriesMap;
import htsjdk.samtools.cram.encoding.DataSeriesType;
import htsjdk.samtools.cram.encoding.Encoding;
import htsjdk.samtools.cram.encoding.EncodingFactory;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.structure.CompressionHeader;
import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.cram.structure.EncodingKey;
import htsjdk.samtools.cram.structure.EncodingParams;
import htsjdk.samtools.cram.structure.ReadTag;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.TreeMap;

@SuppressWarnings("unchecked")
public class DataReaderFactory {

    private final static boolean collectStats = false;

    public AbstractReader buildReader(final AbstractReader reader,
                                      final BitInputStream bis, final Map<Integer, InputStream> inputMap,
                                      final CompressionHeader h, final int refId) throws IllegalArgumentException,
            IllegalAccessException {
        reader.captureReadNames = h.readNamesIncluded;
        reader.refId = refId;
        reader.AP_delta = h.AP_seriesDelta;

        for (final Field f : reader.getClass().getFields()) {
            if (f.isAnnotationPresent(DataSeries.class)) {
                final DataSeries ds = f.getAnnotation(DataSeries.class);
                final EncodingKey key = ds.key();
                final DataSeriesType type = ds.type();
                if (h.eMap.get(key) == null) {
                    System.err.println("Encoding not found for key: " + key);
                }
                f.set(reader,
                        createReader(type, h.eMap.get(key), bis, inputMap));
            }

            if (f.isAnnotationPresent(DataSeriesMap.class)) {
                final DataSeriesMap dsm = f.getAnnotation(DataSeriesMap.class);
                final String name = dsm.name();
                if ("TAG".equals(name)) {
                    final IntHashMap map = new IntHashMap();
                    for (final Integer key : h.tMap.keySet()) {
                        final EncodingParams params = h.tMap.get(key);
                        final DataReader<byte[]> tagReader = createReader(
                                DataSeriesType.BYTE_ARRAY, params, bis,
                                inputMap);
                        map.put(key, tagReader);
                    }
                    f.set(reader, map);
                }
            }
        }

        reader.tagIdDictionary = h.dictionary;
        return reader;
    }

    private <T> DataReader<T> createReader(final DataSeriesType valueType,
                                           final EncodingParams params, final BitInputStream bis,
                                           final Map<Integer, InputStream> inputMap) {
        if (params.id == EncodingID.NULL)
            //noinspection ConstantConditions
            return collectStats ? new DataReaderWithStats(
                    buildNullReader(valueType)) : buildNullReader(valueType);

        final EncodingFactory f = new EncodingFactory();
        final Encoding<T> encoding = f.createEncoding(valueType, params.id);
        if (encoding == null)
            throw new RuntimeException("Encoding not found for value type "
                    + valueType.name() + ", id=" + params.id);
        encoding.fromByteArray(params.params);

        //noinspection ConstantConditions
        return collectStats ? new DataReaderWithStats(new DefaultDataReader<T>(
                encoding.buildCodec(inputMap, null), bis))
                : new DefaultDataReader<T>(encoding.buildCodec(inputMap, null),
                bis);
    }

    private static <T> DataReader<T> buildNullReader(final DataSeriesType valueType) {
        switch (valueType) {
            case BYTE:
                return (DataReader<T>) new SingleValueReader<Byte>((byte) 0);
            case INT:
                return (DataReader<T>) new SingleValueReader<Integer>(
                        0);
            case LONG:
                return (DataReader<T>) new SingleValueReader<Long>((long) 0);
            case BYTE_ARRAY:
                return (DataReader<T>) new SingleValueReader<byte[]>(new byte[]{});

            default:
                throw new RuntimeException("Unknown data type: " + valueType.name());
        }
    }

    private static class DefaultDataReader<T> implements DataReader<T> {
        private final BitCodec<T> codec;
        private final BitInputStream bis;

        public DefaultDataReader(final BitCodec<T> codec, final BitInputStream bis) {
            this.codec = codec;
            this.bis = bis;
        }

        @Override
        public T readData() throws IOException {
            return codec.read(bis);
        }

        @Override
        public T readDataArray(final int len) throws IOException {
            return codec.read(bis, len);
        }

    }

    private static class SingleValueReader<T> implements DataReader<T> {
        private final T value;

        public SingleValueReader(final T value) {
            this.value = value;
        }

        @Override
        public T readData() throws IOException {
            return value;
        }

        @Override
        public T readDataArray(final int len) {
            return value;
        }
    }

    public static class DataReaderWithStats<T> implements DataReader<T> {
        public long nanos = 0;
        final DataReader<T> delegate;

        public DataReaderWithStats(final DataReader<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public T readData() throws IOException {
            final long time = System.nanoTime();
            final T value = delegate.readData();
            nanos += System.nanoTime() - time;
            return value;
        }

        @Override
        public T readDataArray(final int len) throws IOException {
            final long time = System.nanoTime();
            final T value = delegate.readDataArray(len);
            nanos += System.nanoTime() - time;
            return value;
        }
    }

    public Map<String, DataReaderWithStats> getStats(final CramRecordReader reader)
            throws IllegalArgumentException, IllegalAccessException {
        final Map<String, DataReaderWithStats> map = new TreeMap<String, DataReaderFactory.DataReaderWithStats>();
        //noinspection ConstantConditions,PointlessBooleanExpression
        if (!collectStats)
            return map;

        for (final Field f : reader.getClass().getFields()) {
            if (f.isAnnotationPresent(DataSeries.class)) {
                final DataSeries ds = f.getAnnotation(DataSeries.class);
                final EncodingKey key = ds.key();
                map.put(key.name(), (DataReaderWithStats) f.get(reader));
            }

            if (f.isAnnotationPresent(DataSeriesMap.class)) {
                final DataSeriesMap dsm = f.getAnnotation(DataSeriesMap.class);
                final String name = dsm.name();
                if ("TAG".equals(name)) {
                    final Map<Integer, DataReader<byte[]>> tagMap = (Map<Integer, DataReader<byte[]>>) f
                            .get(reader);
                    for (final Integer key : tagMap.keySet()) {
                        final String tag = ReadTag.intToNameType4Bytes(key);
                        map.put(tag, (DataReaderWithStats) tagMap.get(key));
                    }
                }
            }
        }

        return map;
    }

}
