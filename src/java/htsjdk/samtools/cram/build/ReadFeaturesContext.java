package htsjdk.samtools.cram.build;

import htsjdk.samtools.Cigar;
import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.cram.structure.SubstitutionMatrix;

import java.util.ArrayList;
import java.util.List;

public class ReadFeaturesContext {
	private List<CigarElement> cels = new ArrayList<CigarElement>();
	private int positionInReference = 1;
	private byte[] referenceBases;
	private byte[] readBases;
	private SubstitutionMatrix substitutionMatrix;
	private PositionedCigarElement lastCigarElement;

	public ReadFeaturesContext(int positionInReference, byte[] referenceBases, byte[] readBases,
			SubstitutionMatrix substitutionMatrix) {
		this.positionInReference = positionInReference;
		this.referenceBases = referenceBases;
		this.readBases = readBases;
		this.substitutionMatrix = substitutionMatrix;
	}

	public byte[] getReadBases() {
		return readBases;
	}

	public Cigar createCigar() {
		return new Cigar(cels);
	}

	private void setLastTo(int at, CigarOperator o, int len) {
		if (lastCigarElement == null)
			lastCigarElement = new PositionedCigarElement(at, o, len);
		else {
			lastCigarElement.operator = o;
			lastCigarElement.posInRead = at;
			lastCigarElement.len = len;
		}
		if (o.consumesReadBases() && o.consumesReferenceBases())
			System.arraycopy(referenceBases, positionInReference - 1, readBases, at - 1, len);
	}

	private void extendLastBy(int delta) {
		if (lastCigarElement.operator.consumesReadBases() && lastCigarElement.operator.consumesReferenceBases())
			System.arraycopy(referenceBases, positionInReference - 1 + lastCigarElement.len, readBases,
					lastCigarElement.posInRead - 1 + lastCigarElement.len, delta);

		lastCigarElement.len += delta;
	}

	private void flushLast() {
		cels.add(new CigarElement(lastCigarElement.len, lastCigarElement.operator));
		positionInReference += (lastCigarElement.operator.consumesReferenceBases() ? lastCigarElement.len : 0);
	}

	public void addCigarElementAt(int newPosInRead, CigarOperator newOperator, int newLength) {
		boolean M = (newOperator == CigarOperator.M);

		if (lastCigarElement == null) {
			if (M) {
				setLastTo(1, CigarOperator.M, newPosInRead + newLength - 1);
				return;
			}

			if (newPosInRead > 1) {
				setLastTo(1, CigarOperator.M, newPosInRead - 1);
				flushLast();
			}
			setLastTo(newPosInRead, newOperator, newLength);

			return;
		}

		boolean prevM = (lastCigarElement.operator == CigarOperator.M);

		if (prevM && M) {
			extendLastBy(newPosInRead + newLength - (lastCigarElement.posInRead + lastCigarElement.len));
			return;
		}

		int lastReadPos = (lastCigarElement.operator.consumesReadBases() ? lastCigarElement.posInRead
				+ lastCigarElement.len - 1 : lastCigarElement.posInRead - 1);
		int unaccountedBases = newPosInRead - lastReadPos - 1;
		if (prevM && !M) {
			if (unaccountedBases > 0)
				extendLastBy(unaccountedBases);
			flushLast();
			setLastTo(newPosInRead, newOperator, newLength);
			return;
		}

		if (!prevM && M) {
			flushLast();

			setLastTo(newPosInRead - unaccountedBases, CigarOperator.M, unaccountedBases + newLength);
			return;
		}

		{ // !prevM && !M:
			if (unaccountedBases > 0) {
				flushLast();
				setLastTo(lastReadPos + 1, CigarOperator.M, unaccountedBases);
				flushLast();
				setLastTo(newPosInRead, newOperator, newLength);
				return;
			}

			if (lastCigarElement.operator == newOperator) {
				lastCigarElement.len += newLength;
				return;
			}

			{ // last cigar op != new op
				flushLast();
				setLastTo(newPosInRead, newOperator, newLength);
			}
		}
	}

	public void finish() {
		if (lastCigarElement == null) {
			setLastTo(1, CigarOperator.M, readBases.length);
			flushLast();
		} else {
			if (lastCigarElement.operator != CigarOperator.M) {
				flushLast();

				int lastReadPos = (lastCigarElement.operator.consumesReadBases() ? lastCigarElement.posInRead
						+ lastCigarElement.len - 1 : lastCigarElement.posInRead - 1);
				if (lastReadPos < readBases.length) {
					setLastTo(lastReadPos + 1, CigarOperator.M, readBases.length - lastReadPos);
					flushLast();
				}
			} else {
				int lastReadPos = (lastCigarElement.operator.consumesReadBases() ? lastCigarElement.posInRead
						+ lastCigarElement.len - 1 : lastCigarElement.posInRead - 1);
				extendLastBy(readBases.length - lastReadPos);
				flushLast();
			}
		}
	}

	private static class PositionedCigarElement {
		int len;
		CigarOperator operator;
		int posInRead;

		public PositionedCigarElement(int at, CigarOperator o, int len) {
			this.posInRead = at;
			this.operator = o;
			this.len = len;
		}

		@Override
		public String toString() {
			return String.format("%s, read=%d, len=%d", operator.name(), posInRead, len);
		}
	}

	public void addMismatch(int pos, Byte base) {
		addCigarElementAt(pos, CigarOperator.M, 1);
		readBases[pos - 1] = base;
	}

	public void addSubs(int pos, Byte code) {
		addCigarElementAt(pos, CigarOperator.M, 1);
		byte refBase = referenceBases[positionInReference - 1
				+ (lastCigarElement.operator.consumesReferenceBases() ? lastCigarElement.len - 1 : 0)];
		byte base = substitutionMatrix.base(refBase, code);
		readBases[pos - 1] = base;
	}

	public void addInsert(int pos, byte[] insert) {
		addCigarElementAt(pos, CigarOperator.I, insert.length);
		injectBases(pos, insert);
	}

	public void addInsert(int pos, byte insert) {
		addCigarElementAt(pos, CigarOperator.I, 1);
		readBases[pos - 1] = insert;
	}

	public void injectBases(int pos, byte[] b) {
		System.arraycopy(b, 0, readBases, pos - 1, b.length);
	}

	public void injectBase(int pos, byte base) {
		readBases[pos - 1] = base;
	}
}