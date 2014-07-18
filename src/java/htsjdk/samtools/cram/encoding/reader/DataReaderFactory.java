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

public class DataReaderFactory {

	private boolean collectStats = false;

	public AbstractReader buildReader(AbstractReader reader,
			BitInputStream bis, Map<Integer, InputStream> inputMap,
			CompressionHeader h, int refId) throws IllegalArgumentException,
			IllegalAccessException {
		reader.captureReadNames = h.readNamesIncluded;
		reader.refId = refId;
		reader.AP_delta = h.AP_seriesDelta ;

		for (Field f : reader.getClass().getFields()) {
			if (f.isAnnotationPresent(DataSeries.class)) {
				DataSeries ds = f.getAnnotation(DataSeries.class);
				EncodingKey key = ds.key();
				DataSeriesType type = ds.type();
				if (h.eMap.get(key) == null) {
					System.err.println("Encoding not found for key: " + key);
				}
				f.set(reader,
						createReader(type, h.eMap.get(key), bis, inputMap));
			}

			if (f.isAnnotationPresent(DataSeriesMap.class)) {
				DataSeriesMap dsm = f.getAnnotation(DataSeriesMap.class);
				String name = dsm.name();
				if ("TAG".equals(name)) {
					IntHashMap map = new IntHashMap();
					for (Integer key : h.tMap.keySet()) {
						EncodingParams params = h.tMap.get(key);
						DataReader<byte[]> tagReader = createReader(
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

	private <T> DataReader<T> createReader(DataSeriesType valueType,
			EncodingParams params, BitInputStream bis,
			Map<Integer, InputStream> inputMap) {
		if (params.id == EncodingID.NULL)
			return collectStats ? new DataReaderWithStats(
					buildNullReader(valueType)) : buildNullReader(valueType);

		EncodingFactory f = new EncodingFactory();
		Encoding<T> encoding = f.createEncoding(valueType, params.id);
		if (encoding == null)
			throw new RuntimeException("Encoding not found for value type "
					+ valueType.name() + ", id=" + params.id);
		encoding.fromByteArray(params.params);

		return collectStats ? new DataReaderWithStats(new DefaultDataReader<T>(
				encoding.buildCodec(inputMap, null), bis))
				: new DefaultDataReader<T>(encoding.buildCodec(inputMap, null),
						bis);
	}

	private static <T> DataReader<T> buildNullReader(DataSeriesType valueType) {
		switch (valueType) {
		case BYTE:
			return (DataReader<T>) new SingleValueReader<Byte>(new Byte(
					(byte) 0));
		case INT:
			return (DataReader<T>) new SingleValueReader<Integer>(
					new Integer(0));
		case LONG:
			return (DataReader<T>) new SingleValueReader<Long>(new Long(0));
		case BYTE_ARRAY:
			return (DataReader<T>) new SingleValueReader<byte[]>(new byte[] {});

		default:
			throw new RuntimeException("Unknown data type: " + valueType.name());
		}
	}

	private static class DefaultDataReader<T> implements DataReader<T> {
		private BitCodec<T> codec;
		private BitInputStream bis;

		public DefaultDataReader(BitCodec<T> codec, BitInputStream bis) {
			this.codec = codec;
			this.bis = bis;
		}

		@Override
		public T readData() throws IOException {
			return codec.read(bis);
		}

		@Override
		public T readDataArray(int len) throws IOException {
			return codec.read(bis, len);
		}

		@Override
		public void skip() throws IOException {
			codec.read(bis);
		}

		@Override
		public void readByteArrayInto(byte[] dest, int offset, int len)
				throws IOException {
			codec.readInto(bis, dest, offset, len);
		}
	}

	private static class SingleValueReader<T> implements DataReader<T> {
		private T value;
		private Byte byteValue;

		public SingleValueReader(T value) {
			this.value = value;
			if (value instanceof Byte)
				byteValue = (Byte) value;
			else
				byteValue = null;
		}

		@Override
		public T readData() throws IOException {
			return value;
		}

		@Override
		public T readDataArray(int len) {
			return value;
		}

		@Override
		public void skip() throws IOException {
		}

		@Override
		public void readByteArrayInto(byte[] dest, int offset, int len)
				throws IOException {
			if (byteValue != null)
				for (int i = 0; i < len; i++)
					dest[i + offset] = byteValue;
			else
				throw new RuntimeException("Not a byte reader.");
		}

	}

	public static class DataReaderWithStats<T> implements DataReader<T> {
		public long nanos = 0;
		DataReader<T> delegate;

		public DataReaderWithStats(DataReader<T> delegate) {
			this.delegate = delegate;
		}

		@Override
		public T readData() throws IOException {
			long time = System.nanoTime();
			T value = delegate.readData();
			nanos += System.nanoTime() - time;
			return value;
		}

		@Override
		public T readDataArray(int len) throws IOException {
			long time = System.nanoTime();
			T value = delegate.readDataArray(len);
			nanos += System.nanoTime() - time;
			return value;
		}

		@Override
		public void skip() throws IOException {
			long time = System.nanoTime();
			delegate.skip();
			nanos += System.nanoTime() - time;
		}

		@Override
		public void readByteArrayInto(byte[] dest, int offset, int len)
				throws IOException {
			long time = System.nanoTime();
			delegate.readByteArrayInto(dest, offset, len);
			nanos += System.nanoTime() - time;
		}
	}

	public Map<String, DataReaderWithStats> getStats(CramRecordReader reader)
			throws IllegalArgumentException, IllegalAccessException {
		Map<String, DataReaderWithStats> map = new TreeMap<String, DataReaderFactory.DataReaderWithStats>();
		if (!collectStats)
			return map;

		for (Field f : reader.getClass().getFields()) {
			if (f.isAnnotationPresent(DataSeries.class)) {
				DataSeries ds = f.getAnnotation(DataSeries.class);
				EncodingKey key = ds.key();
				DataSeriesType type = ds.type();
				map.put(key.name(), (DataReaderWithStats) f.get(reader));
			}

			if (f.isAnnotationPresent(DataSeriesMap.class)) {
				DataSeriesMap dsm = f.getAnnotation(DataSeriesMap.class);
				String name = dsm.name();
				if ("TAG".equals(name)) {
					Map<Integer, DataReader<byte[]>> tagMap = (Map<Integer, DataReader<byte[]>>) f
							.get(reader);
					for (Integer key : tagMap.keySet()) {
						String tag = ReadTag.intToNameType4Bytes(key);
						map.put(tag, (DataReaderWithStats) tagMap.get(key));
					}
				}
			}
		}

		return map;
	}
}
