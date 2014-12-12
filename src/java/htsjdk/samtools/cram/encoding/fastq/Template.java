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
package htsjdk.samtools.cram.encoding.fastq;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Template {
	public byte[] name;
	public Segment segment;
	private int hashCode;
	public byte size = 0;
	public long counter = 0;

	public Template(byte[] name) {
		this.name = name;
		calculateHashCode();
	}

	public void append(byte[] bases, byte[] scores) {
		Segment appendix = new Segment(bases, scores);

		Segment lastSegment = getLastSegment();
		if (lastSegment == null)
			segment = appendix;
		else {
			lastSegment.next = appendix;
			appendix.prev = lastSegment;
		}
		size++;
	}

	public void prepend(byte[] bases, byte[] scores) {
		Segment appendix = new Segment(bases, scores);

		Segment firstSegment = getFirstSegment();
		if (firstSegment == null)
			segment = appendix;
		else {
			firstSegment.prev = appendix;
			appendix.next = firstSegment;
		}
		size++;
	}

	public Segment getLastSegment() {
		if (segment == null)
			return null;

		Segment last = segment;
		while (last.next != null)
			last = last.next;
		return last;
	}

	public Segment getFirstSegment() {
		if (segment == null)
			return null;

		Segment first = segment;
		while (first.prev != null)
			first = first.prev;
		return first;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Template)
			return Arrays.equals(name, ((Template) obj).name);
		return false;
	}

	@Override
	public int hashCode() {
		return hashCode;
	}

	protected int calculateHashCode() {
		for (int i = 0; i < 4 && i < name.length; i++) {
			hashCode <<= 8;
			hashCode |= name[name.length - 1 - i];
		}
		return 0;
	}

	public static class Segment {
		public byte[] bases, scores;
		public Segment prev, next;

		public Segment(byte[] bases, byte[] scores) {
			this.bases = bases;
			this.scores = scores;
		}

	}

	public static class ByteArrayHashWrapper {
		private byte[] array;
		private int hashcode;

		public ByteArrayHashWrapper(byte[] array) {
			setArray(array);
		}

		public void setArray(byte[] array) {
			this.array = array;
			calculateHashCode();
		}

		protected int calculateHashCode() {
			for (int i = 0; i < 4 && i < array.length; i++) {
				hashcode <<= 8;
				hashcode |= array[array.length - 1 - i];
			}
			return 0;
		}

		@Override
		public int hashCode() {
			return hashcode;
		}

		@Override
		public boolean equals(Object obj) {
			return Arrays.equals(array, ((ByteArrayHashWrapper) obj).array);
		}
	}

	public static class TemplateHash {
		private HashMap<ByteArrayHashWrapper, Template> map = new HashMap<ByteArrayHashWrapper, Template>();
		public long counter = 0;
		public long min = 0;

		private ByteArrayHashWrapper tmpRemoveWrapper = new ByteArrayHashWrapper(
				new byte[0]);

		public Template add(byte[] name, byte[] bases, byte[] scores) {
			ByteArrayHashWrapper w = new ByteArrayHashWrapper(name);
			Template t = map.get(w);
			if (t == null) {
				t = new Template(name);
				t.counter = ++counter;
				map.put(w, t);
			}
			t.append(bases, scores);
			return t;
		}

		public List<Template> purgeUpto(long max) {
			List<Template> list = new LinkedList<Template>();
			for (Template t : map.values())
				if (t.counter < max)
					list.add(t);

			for (Template t : list)
				remove(t.name);
			return list;
		}

		public void remove(byte[] name) {
			tmpRemoveWrapper.setArray(name);
			map.remove(tmpRemoveWrapper);
		}

		public int size() {
			return map.size();
		}
	}

	public static abstract class TemplateAssembler {
		public TemplateHash hash = new TemplateHash();
		private int maxHashMapSize;

		public TemplateAssembler(int maxHashMapSize) {
			this.maxHashMapSize = maxHashMapSize;
		}

		protected abstract boolean isComplete(Template t);

		protected abstract void templateComplete(Template t);

		protected abstract void giveupIncomplete(List<Template> list);

		public void addSegment(byte[] name, byte[] bases, byte[] scores) {
			System.out.println("Template.TemplateAssembler.addSegment()");
			System.out.println(hash.map.size());
			Template t = hash.add(name, bases, scores);
			System.out.println(hash.map.size());
			if (isComplete(t)) {
				System.out.println("complete");
				hash.remove(t.name);
				templateComplete(t);
			} else {
				System.out.println("incomplete");
				if (hash.map.size() > maxHashMapSize) {
					List<Template> list = hash
							.purgeUpto((hash.counter - hash.min) / 2 + hash.min
									+ 1);
					if (!list.isEmpty())
						giveupIncomplete(list);
				}
			}
			System.out.println(hash.map.size());
		}

		public void finish() {
			List<Template> list = hash.purgeUpto(hash.counter + 1);
			if (!list.isEmpty())
				giveupIncomplete(list);
		}
	}

	public static void main(String[] args) {
		int max = 4;
		List<byte[]> list = new ArrayList<byte[]>(max * 2);
		for (int i = 0; i < max; i++) {
			list.add(String.valueOf(i).getBytes());
			list.add(String.valueOf(i).getBytes());
		}
		Collections.shuffle(list);

		final AtomicInteger counter = new AtomicInteger(0) ;
		TemplateAssembler a = new TemplateAssembler(4*max) {

			@Override
			protected void templateComplete(Template t) {
				System.out.println("template complete: " + new String(t.name));
				counter.incrementAndGet() ;
			}

			@Override
			protected boolean isComplete(Template t) {
				return t.size > 1;
			}

			@Override
			protected void giveupIncomplete(List<Template> list) {
				System.out.println("Giving up on: " + list.size());
				for (Template t : list)
					System.out.println(new String(t.name));
			}
		};

		// a.addSegment("1".getBytes(), "A".getBytes(), "Q".getBytes());
		// a.addSegment("2".getBytes(), "A".getBytes(), "Q".getBytes());
		// a.addSegment("1".getBytes(), "A".getBytes(), "Q".getBytes());

		for (byte[] name : list)
			a.addSegment(name, "A".getBytes(), "Q".getBytes());
		
		System.out.println("Finishing, complete=" + counter.get());
		a.finish();
	}
}
