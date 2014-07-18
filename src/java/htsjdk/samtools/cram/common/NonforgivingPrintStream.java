package htsjdk.samtools.cram.common;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

public class NonforgivingPrintStream extends PrintStream {

	public NonforgivingPrintStream(File file) throws FileNotFoundException {
		super(file);
	}

	public NonforgivingPrintStream(File file, String csn) throws FileNotFoundException, UnsupportedEncodingException {
		super(file, csn);
	}

	public NonforgivingPrintStream(OutputStream out, boolean autoFlush, String encoding)
			throws UnsupportedEncodingException {
		super(out, autoFlush, encoding);
	}

	public NonforgivingPrintStream(OutputStream out, boolean autoFlush) {
		super(out, autoFlush);
	}

	public NonforgivingPrintStream(OutputStream out) {
		super(out);
	}

	public NonforgivingPrintStream(String fileName, String csn) throws FileNotFoundException,
			UnsupportedEncodingException {
		super(fileName, csn);
	}

	public NonforgivingPrintStream(String fileName) throws FileNotFoundException {
		super(fileName);
	}

	@Override
	public void write(byte[] b) throws IOException {
		if (checkError())
			throw new PrintStreamError(this);
		super.write(b);
	}

	@Override
	public void write(int b) {
		if (checkError())
			throw new PrintStreamError(this);
		super.write(b);
	}

	@Override
	public void write(byte[] buf, int off, int len) {
		if (checkError())
			throw new PrintStreamError(this);
		super.write(buf, off, len);
	}

	public static class PrintStreamError extends RuntimeException {
		private transient PrintStream printStream;

		public PrintStream getPrintStream() {
			return printStream;
		}

		public void setPrintStream(PrintStream printStream) {
			this.printStream = printStream;
		}

		public PrintStreamError(PrintStream ps) {
			super();
		}
	}
}
