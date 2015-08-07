package htsjdk.samtools.util.hdfs;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.apache.hadoop.fs.FSDataInputStream;

public interface RandomFileInt {
	public void seek(long site) throws IOException;
	
	int read(byte[] b, int off, int len) throws IOException;

	public int read(byte[] byteinfo) throws IOException;
	
	public void close() throws IOException;
	public void readFully(byte[] signatureBytes) throws IOException;
	public void readFully(byte[] mBuffer, int i, int readLength) throws IOException;
	public long length() throws IOException;
	public int read() throws IOException; 
	public int skipBytes(int n) throws IOException;
	
	public static class RandomFileFactory {
		/** return corresponding object by judge the input fileName is FileHadoop or local file */
		public static RandomFileInt createInstance(String fileName) {
			try {
				if (FileHadoop.isHdfs(fileName)) {
					return new RandomFileHdfs(fileName);
				} else {
					return new RandomFileLocal(fileName);
				}
			} catch (Exception e) {
				
			}
		
			return null;
		}
		
		public static RandomFileInt createInstance(File file) {
			try {
				if (file instanceof FileHadoop) {
					return new RandomFileHdfs((FileHadoop)file);
				} else {
					return new RandomFileLocal(file);
				}
			} catch (Exception e) {
				
			}
		
			return null;
		}
	}
}

class RandomFileLocal extends RandomAccessFile implements RandomFileInt {
	public RandomFileLocal(String file) throws FileNotFoundException {
		super(file, "r");
	}
	public RandomFileLocal(File file) throws FileNotFoundException {
		super(file, "r");
	}
}

class RandomFileHdfs implements RandomFileInt {
	FSDataInputStream fsDataInputStream;
	FileHadoop fileHadoop;
	public RandomFileHdfs(String file) throws IOException {
		fileHadoop = new FileHadoop(file);
		fsDataInputStream = fileHadoop.getInputStream();
	}
	
	public RandomFileHdfs(FileHadoop file) throws IOException {
		fileHadoop = file;
		fsDataInputStream = file.getInputStream();
	}
	
	@Override
	public void seek(long site) throws IOException {
		fsDataInputStream.seek(site);
	}

	@Override
	public int read(byte[] byteinfo) throws IOException {
		return fsDataInputStream.read(byteinfo);
	}

	@Override
	public void close() throws IOException {
		fsDataInputStream.close();
	}
	
    /**
     * Reads <code>b.length</code> bytes from this file into the byte
     * array, starting at the current file pointer. This method reads
     * repeatedly from the file until the requested number of bytes are
     * read. This method blocks until the requested number of bytes are
     * read, the end of the stream is detected, or an exception is thrown.
     *
     * @param      b   the buffer into which the data is read.
     * @exception  EOFException  if this file reaches the end before reading
     *               all the bytes.
     * @exception  IOException   if an I/O error occurs.
     */
    public final void readFully(byte b[]) throws IOException {
    	fsDataInputStream.readFully(b);
    }

    /**
     * Reads exactly <code>len</code> bytes from this file into the byte
     * array, starting at the current file pointer. This method reads
     * repeatedly from the file until the requested number of bytes are
     * read. This method blocks until the requested number of bytes are
     * read, the end of the stream is detected, or an exception is thrown.
     *
     * @param      b     the buffer into which the data is read.
     * @param      off   the start offset of the data.
     * @param      len   the number of bytes to read.
     * @exception  EOFException  if this file reaches the end before reading
     *               all the bytes.
     * @exception  IOException   if an I/O error occurs.
     */
    public final void readFully(byte b[], int off, int len) throws IOException {
    	fsDataInputStream.readFully(b, off, len);
    }

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		return fsDataInputStream.read(b, off, len);
	}

	@Override
	public long length() throws IOException {
		return fileHadoop.length();
	}

	@Override
	public int read() throws IOException {
		return fsDataInputStream.read();
	}

	@Override
	public int skipBytes(int n) throws IOException {
		return fsDataInputStream.skipBytes(n);
	}
    
}

