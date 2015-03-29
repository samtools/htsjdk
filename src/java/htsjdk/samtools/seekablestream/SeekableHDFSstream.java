package htsjdk.samtools.seekablestream;

import htsjdk.samtools.util.hdfs.FileHadoop;

import java.io.IOException;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.log4j.Logger;

/**
 * wrape the hdfs InputStream as SeekableStream
 * @author zong0jie
 *
 */
public class SeekableHDFSstream extends SeekableStream {
	
	private static final Logger logger = Logger.getLogger(SeekableHDFSstream.class);
	
	FileHadoop fileHadoop;
	FSDataInputStream fsDataInputStream;
	long fileLength;
	
	public SeekableHDFSstream(FileHadoop fileHadoop) {
		this.fileHadoop = fileHadoop;
		this.fsDataInputStream = fileHadoop.getInputStream();
		fileLength = fileHadoop.length();
	}
	
	@Override
	public long length() {
		return fileHadoop.length();
	}

	@Override
	public long position() throws IOException {
		return fsDataInputStream.getPos();
	}

	@Override
	public void seek(long position) throws IOException {
		fsDataInputStream.seek(position);
	}
	
	@Override
	public int read(byte[] buffer, int offset, int length) throws IOException {
		return fsDataInputStream.read(buffer, offset, length);
	}
	
	public void readFully(byte[] buffer) throws IOException {
		fsDataInputStream.readFully(buffer);
	}
	
	@Override
	public void close() throws IOException {
		fsDataInputStream.close();
	}

	@Override
	public boolean eof() throws IOException {
		return fileLength == fsDataInputStream.getPos();
	}
	
	/**
	 * 在sam-1.87版本中，仅用来显示报错信息
	 */
	@Override
	public String getSource() {
		return fileHadoop.getName();
	}

	@Override
	public int read() throws IOException {
		return fsDataInputStream.read();
	}

}
