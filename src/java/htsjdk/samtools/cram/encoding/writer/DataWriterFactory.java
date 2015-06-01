/**
 * ****************************************************************************
 * Copyright 2013 EMBL-EBI
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package htsjdk.samtools.cram.encoding.writer;

import htsjdk.samtools.cram.encoding.BitCodec;
import htsjdk.samtools.cram.encoding.DataSeries;
import htsjdk.samtools.cram.encoding.DataSeriesMap;
import htsjdk.samtools.cram.encoding.DataSeriesType;
import htsjdk.samtools.cram.encoding.Encoding;
import htsjdk.samtools.cram.encoding.EncodingFactory;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.cram.io.ExposedByteArrayOutputStream;
import htsjdk.samtools.cram.structure.CompressionHeader;
import htsjdk.samtools.cram.structure.EncodingKey;
import htsjdk.samtools.cram.structure.EncodingParams;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class DataWriterFactory {

    public Writer buildWriter(final BitOutputStream bitOutputStream,
                              final Map<Integer, ExposedByteArrayOutputStream> outputMap,
                              final CompressionHeader h, final int refId) throws IllegalArgumentException,
            IllegalAccessException {
        final Writer writer = new Writer();
        writer.setCaptureReadNames(h.readNamesIncluded);
        writer.refId = refId;
        writer.substitutionMatrix = h.substitutionMatrix;
        writer.AP_delta = h.APDelta;

        for (final Field f : writer.getClass().getFields()) {
            if (f.isAnnotationPresent(DataSeries.class)) {
                final DataSeries ds = f.getAnnotation(DataSeries.class);
                final EncodingKey key = ds.key();
                final DataSeriesType type = ds.type();

                f.set(writer,
                        createWriter(type, h.encodingMap.get(key), bitOutputStream, outputMap));
            }

            if (f.isAnnotationPresent(DataSeriesMap.class)) {
                final DataSeriesMap dsm = f.getAnnotation(DataSeriesMap.class);
                final String name = dsm.name();
                if ("TAG".equals(name)) {
                    final Map<Integer, DataWriter<byte[]>> map = new HashMap<Integer, DataWriter<byte[]>>();
                    for (final Integer key : h.tMap.keySet()) {
                        final EncodingParams params = h.tMap.get(key);
                        final DataWriter<byte[]> tagWriter = createWriter(
                                DataSeriesType.BYTE_ARRAY, params, bitOutputStream,
                                outputMap);
                        map.put(key, tagWriter);
                    }
                    f.set(writer, map);
                }
            }
        }

        return writer;
    }

    private <T> DataWriter<T> createWriter(final DataSeriesType valueType,
                                           final EncodingParams params, final BitOutputStream bitOutputStream,
                                           final Map<Integer, ExposedByteArrayOutputStream> outputMap) {
        final EncodingFactory f = new EncodingFactory();
        final Encoding<T> encoding = f.createEncoding(valueType, params.id);
        if (encoding == null)
            throw new RuntimeException("Encoding not found: value type="
                    + valueType.name() + ", encoding id=" + params.id.name());

        encoding.fromByteArray(params.params);

        return new DefaultDataWriter<T>(encoding.buildCodec(null, outputMap),
                bitOutputStream);
    }

    private static class DefaultDataWriter<T> implements DataWriter<T> {
        private final BitCodec<T> codec;
        private final BitOutputStream bitOutputStream;

        public DefaultDataWriter(final BitCodec<T> codec, final BitOutputStream bitOutputStream) {
            this.codec = codec;
            this.bitOutputStream = bitOutputStream;
        }

        @Override
        public long writeData(final T value) throws IOException {
            return codec.write(bitOutputStream, value);
        }

    }
}
