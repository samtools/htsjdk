package htsjdk.samtools.util.hdfs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.hadoop.fs.FSDataOutputStream;

/**
 * Generate File or FileHadoop and get the corresponding InputStream and
 * OutputStream
 * 
 * @author Zong Jie 20150329
 *
 */
public class FileOperate {

	protected static boolean isRealNull(String string) {
		if (string == null) {
			return true;
		} else if (string.trim().equals("")) {
			return true;
		} else if (string.equals("null")) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * get File or FileHadoop
	 * 
	 * @param filePathParent
	 * @param name
	 * @return
	 */
	public static File getFile(String filePathParent, String name) {
		if (!isRealNull(filePathParent.trim())) {
			filePathParent = FileOperate.addSep(filePathParent);
		}
		String path = filePathParent + name;
		return getFile(path);
	}

	/**
	 * get File or FileHadoop
	 * 
	 * @param filePath
	 * @return
	 */
	public static File getFile(File fileParent, String name) {
		if (fileParent instanceof FileHadoop) {
			return new FileHadoop(FileOperate.addSep(fileParent.getAbsolutePath()) + name);
		} else {
			return new File(fileParent, name);
		}
	}

	/**
	 * @param filePath
	 * @return
	 */
	public static File getFile(String filePath) {
		File file = null;
		boolean isHdfs = FileHadoop.isHdfs(filePath);
		if (isHdfs) {
			file = new FileHadoop(filePath);
		} else {
			file = new File(filePath);
		}
		return file;
	}

	public static String addSep(String path) {
		path = path.trim();
		if (!path.endsWith(File.separator)) {
			path = path + File.separator;
		}
		return path;
	}

	public static InputStream getInputStream(File file) throws FileNotFoundException {
		if (file instanceof FileHadoop) {
			return ((FileHadoop) file).getInputStream();
		} else {
			return new FileInputStream(file);
		}
	}

	public static OutputStream getOutputStream(File file) throws FileNotFoundException {
		return getOutputStream(file, false);
	}

	public static OutputStream getOutputStream(File file, boolean append) throws FileNotFoundException {
		boolean cover = !append;
		OutputStream fs = null;
		if (file instanceof FileHadoop) {
			FileHadoop fileHadoop = (FileHadoop) file;
			FSDataOutputStream fsHdfs = fileHadoop.getOutputStream(cover);
			fs = new OutputStreamHdfs(fsHdfs);
		} else {
			fs = new FileOutputStream(file, append);
		}
		
		return fs;
	}

}
