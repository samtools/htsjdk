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
package htsjdk.samtools.cram.mask;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;

public class SingleLineMaskReader implements Closeable, ReadMaskReader {
	private final BufferedReader reader;
	private final ReadMaskFactory<String> readMaskFactory;

	public SingleLineMaskReader(BufferedReader reader, ReadMaskFactory<String> readMaskFactory) {
		this.reader = reader;
		this.readMaskFactory = readMaskFactory;
	}

	@Override
	public PositionMask readNextMask() throws IOException, ReadMaskFormatException {
		String line = reader.readLine();
		if (line == null)
			return null;

		return readMaskFactory.createMask(line);
	}

	@Override
	public void close() throws IOException {
		reader.close();
	}

	// private String line;
	// private void readLine() {
	// try {
	// line = reader.readLine();
	// } catch (Exception e) {
	// throw new RuntimeException(e);
	// }
	// }
	//
	// @Override
	// public boolean hasNext() {
	// if (line != null)
	// return true;
	//
	// readLine();
	// return line != null;
	// }
	//
	// @Override
	// public PositionMask next() throws ReadMaskFormatException {
	// if (line == null)
	// readLine();
	//
	// if (line == null)
	// throw new RuntimeException("Iterator is empty.");
	//
	// PositionMask mask = readMaskFactory.createMask(line);
	// line = null;
	// return mask;
	// }

}
