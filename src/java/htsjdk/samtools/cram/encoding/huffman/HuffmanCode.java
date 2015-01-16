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
package htsjdk.samtools.cram.encoding.huffman;

import java.io.PrintStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;

public class HuffmanCode {

	@Deprecated
	public static <T> HuffmanTree<T> buildTreeUsingPriorityQueue(
			int[] charFreqs, T[] values) {
		PriorityQueue<HuffmanTree<T>> queue = new PriorityQueue<HuffmanTree<T>>();

		for (int i = 0; i < charFreqs.length; i++)
			if (charFreqs[i] > 0)
				queue.offer(new HuffmanLeaf<T>(charFreqs[i], values[i]));

		while (queue.size() > 1) {
			HuffmanTree<T> a = queue.poll();
			HuffmanTree<T> b = queue.poll();

			queue.offer(new HuffmanNode<T>(a, b));
		}
		return queue.poll();
	}

	public static <T> HuffmanTree<T> buildTree(int[] charFreqs, T[] values) {
		LinkedList<HuffmanTree<T>> list = new LinkedList<HuffmanTree<T>>();
		for (int i = 0; i < charFreqs.length; i++)
			if (charFreqs[i] > 0)
				list.add(new HuffmanLeaf<T>(charFreqs[i], values[i]));

		Comparator<HuffmanTree<T>> c = new Comparator<HuffmanTree<T>>() {

			@Override
			public int compare(HuffmanTree<T> o1, HuffmanTree<T> o2) {
				return o1.frequency - o2.frequency;
			}
		};

		while (list.size() > 1) {
			Collections.sort(list, c);
			// dumpList(list) ;
			HuffmanTree<T> left = list.remove();
			HuffmanTree<T> right = list.remove();
			list.add(new HuffmanNode<T>(left, right));
		}
		return list.isEmpty() ? null : list.remove();
	}

	public static <T> void getValuesAndBitLengths(List<T> values,
			List<Integer> lens, HuffmanTree<T> tree) {
		TreeMap<T, HuffmanBitCode<T>> codes = new TreeMap<T, HuffmanBitCode<T>>();
		getBitCode(tree, new HuffmanBitCode<T>(), codes);

		for (T value : codes.keySet()) {
			HuffmanBitCode<T> code = codes.get(value);
			values.add(value);
			lens.add(code.bitLentgh);
		}
	}

	public static void printTree(HuffmanTree<?> tree, StringBuffer prefix,
			PrintStream ps) {
		if (tree instanceof HuffmanLeaf) {
			HuffmanLeaf<?> leaf = (HuffmanLeaf<?>) tree;

			ps.println(leaf.value + "\t" + leaf.frequency + "\t" + prefix);

		} else if (tree instanceof HuffmanNode) {
			HuffmanNode<?> node = (HuffmanNode<?>) tree;

			// traverse left
			prefix.append('0');
			printTree(node.left, prefix, ps);
			prefix.deleteCharAt(prefix.length() - 1);

			// traverse right
			prefix.append('1');
			printTree(node.right, prefix, ps);
			prefix.deleteCharAt(prefix.length() - 1);
		}
	}

	public static <T> boolean equal(HuffmanTree<T> tree1, HuffmanTree<T> tree2) {
		if (tree1.compareTo(tree2) != 0)
			return false;

		if (tree1 instanceof HuffmanLeaf && tree2 instanceof HuffmanLeaf) {
			T value1 = ((HuffmanLeaf<T>) tree1).value;
			T value2 = ((HuffmanLeaf<T>) tree2).value;
			if (value1 == null && value2 == null)
				return true;
			if (value1 != null && value1.equals(value2))
				return true;

			return false;
		} else if (tree1 instanceof HuffmanNode && tree2 instanceof HuffmanNode) {
			HuffmanNode<T> node1 = (HuffmanNode<T>) tree1;
			HuffmanNode<T> node2 = (HuffmanNode<T>) tree2;

			if (!equal(node1.left, node2.left))
				return false;
			if (!equal(node1.right, node2.right))
				return false;

			return true;
		}

		return false;
	}

	private static class HuffmanBitCode<T> {
		long bitCode;
		int bitLentgh;
		T value;
	}

	private static <T> void getBitCode(HuffmanTree<T> tree,
			HuffmanBitCode<T> code, Map<T, HuffmanBitCode<T>> codes) {
		if (tree instanceof HuffmanLeaf) {
			HuffmanLeaf<T> leaf = (HuffmanLeaf<T>) tree;
			HuffmanBitCode<T> readyCode = new HuffmanBitCode<T>();
			readyCode.bitCode = code.bitCode;
			readyCode.bitLentgh = code.bitLentgh;
			codes.put(leaf.value, readyCode);
			return;

		} else if (tree instanceof HuffmanNode) {
			HuffmanNode<T> node = (HuffmanNode<T>) tree;

			// traverse left
			code.bitCode = code.bitCode << 1;
			code.bitLentgh++;

			getBitCode(node.left, code, codes);
			code.bitCode = code.bitCode >>> 1;
			code.bitLentgh--;

			// traverse right
			code.bitCode = code.bitCode << 1 | 1;
			code.bitLentgh++;

			getBitCode(node.right, code, codes);
			code.bitCode = code.bitCode >>> 1;
			code.bitLentgh--;
		}
	}
}
