package htsjdk.samtools.util.hdfs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Calendar;

import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.log4j.Logger;

/**
 * Generate File or FileHadoop and get the corresponding InputStream and
 * OutputStream
 * 
 * @author Zong Jie 20150329
 *
 */
public class FileOperate {
	private static Logger logger = Logger.getLogger(FileOperate.class);

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

	/**
	 * give a fileName, return the parent path, with the seperator "/" at end of
	 * the path <br>
	 * <br>
	 * example:<br>
	 * <b>fileName</b> "/home/picard/htsjdk/file.txt" <b>return</b> "/home/picard/htsjdk/"<br>
	 * <b>fileName</b> "/home/picard/htsjdk/file/" <b>return</b> "/home/picard/htsjdk/"<br>
	 * <b>fileName</b> "picard" <b>return</b> ""; <br>
	 * <br>
	 * can set path that is not exist.
	 * 
	 * @param fileName
	 * @return
	 */
	public static String getParentPathNameWithSep(String fileName) {
		if (fileName == null) return null;
		File file = new File(fileName);
		String fileParent = file.getParent();
		if (fileParent == null) {
			return "";
		} else {
			return addSep(fileParent);
		}
	}

	public static String addSep(String path) {
		path = path.trim();
		if (!path.endsWith(File.separator)) {
			path = path + File.separator;
		}
		return path;
	}
	/**
	 * 
	 * give a fileName, return the closest path, with the seperator "/" at end
	 * of the path <br>
	 * <br>
	 * example:<br>
	 * <b>fileName</b> "/home/picard/htsjdk/file.txt" <b>return</b> "/home/picard/htsjdk/"<br>
	 * <b>fileName</b> "/home/picard/htsjdk/file/" <b>return</b>  "/home/picard/htsjdk/file/"<br>
	 * if the path is relative path as "picard", then return ""; <br>
	 * <br>
	 * can set path that is not exist.
	 * @param fileName
	 * @return
	 * @throws IOException
	 */
	public static String getPathName(String fileName) {
		if (fileName == null) return null;
		if (fileName.endsWith("/") || fileName.endsWith("\\")) {
			return fileName;
		}
		return getParentPathNameWithSep(fileName);
	}

	/**
	 * get the last modify time of the given fileName
	 * 
	 * @param fileName
	 * @return
	 */
	public static String getTimeLastModifyStr(String fileName) {
		File f = getFile(fileName);
		if (f == null) {
			return null;
		}
		Calendar cal = Calendar.getInstance();
		long time = f.lastModified();
		cal.setTimeInMillis(time);
		DateFormat formatter = DateFormat.getDateTimeInstance();
		String data = formatter.format(cal.getTime());
		return data;
	}

	public static long getTimeLastModify(String fileName) {
		File f = getFile(fileName);
		if (f == null) {
			return -1;
		}
		return f.lastModified();
	}

	public static long getTimeLastModify(File file) {
		if (file == null || !file.exists()) {
			return 0;
		}
		return file.lastModified();
	}

	/**
	 * remove the "//" on head of a fileName<br><br>
	 * example: <br>
	 * <b>fileName</b> "///home/htsjdk" <b>keepOne</b> false, <b>return</b> home/htsjdk<br>
	 * <b>fileName</b> "///home/htsjdk" <b>keepOne</b> true, <b>return</b> /home/htsjdk<br>
 	 * @param fileName
	 * @param keepOne whether we should keep one slash on the head of the fileName
	 * @return
	 */
	public static String removeSplashHead(String fileName, boolean keepOne) {
		if (fileName == null) {
			return null;
		}
		String fileNameThis = fileName;
		fileNameThis = fileNameThis.replace("\\", "/");
		String head = "//";
		if (!keepOne) {
			head = "/";
		}
		while (true) {
			if (fileNameThis.startsWith(head)) {
				fileNameThis = fileNameThis.substring(1);
			} else {
				break;
			}
		}
		return fileNameThis;
	}

	
	/**
	 * remove the "//" on tail of a fileName<br><br>
	 * example: <br>
	 * <b>fileName</b> "/home/htsjdk///" <b>keepOne</b> false, <b>return</b> home/htsjdk<br>
	 * <b>fileName</b> "/home/htsjdk///" <b>keepOne</b> true, <b>return</b> /home/htsjdk/<br>
 	 * @param fileName
	 * @param keepOne whether we should keep one slash on the tail of the fileName
	 * @return
	 */
	public static String removeSplashTail(String fileName, boolean keepOne) {
		if (fileName == null) {
			return null;
		}
		String fileNameThis = fileName;
		fileNameThis = fileNameThis.replace("\\", "/");
		String tail = "//";
		if (!keepOne) {
			tail = "/";
		}
		while (true) {
			if (fileNameThis.endsWith(tail)) {
				fileNameThis = fileNameThis.substring(0, fileNameThis.length() - 1);
			} else {
				break;
			}
		}
		return fileNameThis;
	}

	/**
	 * create folder and return true if the folder was created sucessfully.
	 * 
	 * @param folderPath
	 * @return
	 */
	private static boolean createFolder(String folderPath) {
		File myFilePath = getFile(folderPath);
		if (!myFilePath.exists()) {
			return myFilePath.mkdir();
		}
		return true;
	}

	/**
	 * create multi-level folder and return true if the folders were created sucessfully.
	 * 
	 * @param folderPath just like "/home/htsjdk/myFile/"
	 * @return
	 */
	public static boolean createFolders(String folderPath) {
		if (isFileExist(folderPath)) return false;
		if (isFileDirectory(folderPath)) return true;

		String foldUpper = folderPath;
		String creatPath = "";
		boolean flag = true;
		while (flag) {
			if (foldUpper.equals("")) {
				return false;
			}
			if (isFileDirectory(foldUpper)) {
				flag = false;
				break;
			}
			File file = new File(foldUpper);
			creatPath =  file.getName() + File.separator + creatPath;
			foldUpper = getParentPathNameWithSep(foldUpper);
		}
		foldUpper = addSep(foldUpper);
		String subFold = "";
		String[] sepID = creatPath.split(getSep());
		String firstPath = foldUpper + sepID[0];
		for (int i = 0; i < sepID.length; i++) {
			subFold = subFold + sepID[i] + File.separator;
			if (!createFolder(foldUpper + subFold)) {
				logger.error("create folder error: " + foldUpper + subFold);
//				DeleteFileFolder(firstPath);
				return false;
			}
		}
		return true;
	}
	
	private static boolean isFileExist(String fileName) {
		if (fileName == null || fileName.trim().equals("")) {
			return false;
		}
		
		File file = getFile(fileName);
		if (file.exists() && !file.isDirectory()) {// 没有文件，则返回空
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * 根据路径删除指定的目录或文件，无论存在与否
	 * 
	 * @param sPath
	 *            要删除的目录或文件
	 * @return 删除成功返回 true，否则返回 false
	 * 不存在文件也返回true
	 */
	private static boolean DeleteFileFolder(String sPath) {
		if (isRealNull(sPath)) {
			return true;
		}
		File file = getFile(sPath);
		// 判断目录或文件是否存在
		if (file.exists()) {
			if (file.isDirectory()) {
				return deleteDirectory(file);
			} else {
				return file.delete();
			}
		}
		return true;
	}
	/**
	 * 判断文件是否为文件夹,null直接返回false
	 * 
	 * @param fileName
	 * @return
	 */
	private static boolean isFileDirectory(String fileName) {
		if (fileName == null) {
			return false;
		}
		File file = getFile(fileName);
		if (file.isDirectory()) {// 没有文件，则返回空
			return true;
		} else {
			return false;
		}
	}
	private static String getSep() {
		if (File.separator.equals("\\")) {
			return "\\\\";
		} else {
			return File.separator;
		}
	}

	private static boolean deleteDirectory(File dirFile) {
		if (dirFile instanceof FileHadoop) {
			return dirFile.delete();
		}
		if (!dirFile.exists() || !dirFile.isDirectory()) {
			return false;
		}
		boolean flag = true;
		// delete all files and subfolders
		File[] files = dirFile.listFiles();
		for (int i = 0; i < files.length; i++) {
			// delete sub files
			if (files[i].isFile()) {
				flag = files[i].delete();;
				if (!flag)
					break;
			} // delete sub folders
			else {
				flag = deleteDirectory(files[i]);
				if (!flag)
					break;
			}
		}
		if (!flag)
			return false;
		// delete current file
		if (dirFile.delete()) {
			return true;
		} else {
			return false;
		}
	}
	public static InputStream getInputStream(String filePath) throws IOException {
		if (FileHadoop.isHdfs(filePath)) {
			return new FileHadoop(filePath).getInputStream();
		} else {
			return new FileInputStream(new File(filePath));
		}
	}

	public static InputStream getInputStream(File file) throws FileNotFoundException {
		if (file instanceof FileHadoop) {
			return ((FileHadoop) file).getInputStream();
		} else {
			return new FileInputStream(file);
		}
	}

	public static OutputStream getOutputStream(String filePath) {
		return getOutputStream(filePath, true);
	}

	public static OutputStream getOutputStream(String filePath, boolean cover) {
		try {
			File file = getFile(filePath);
			OutputStream fs = null;
			if (file instanceof FileHadoop) {
				FileHadoop fileHadoop = (FileHadoop) file;
				FSDataOutputStream fsHdfs = fileHadoop.getOutputStreamNew(cover);
				fs = new OutputStreamHdfs(fsHdfs);
			} else {
				fs = new FileOutputStream(file, !cover);
			}

			return fs;
		} catch (Exception e) {
			logger.error("get output stream error: " + filePath + "   is cover: " + cover, e);
			e.printStackTrace();
			return null;
		}
	}

	public static OutputStream getOutputStream(File file) throws FileNotFoundException {
		return getOutputStream(file, true);
	}

	public static OutputStream getOutputStream(File file, boolean cover) throws FileNotFoundException {
		OutputStream fs = null;
		if (file instanceof FileHadoop) {
			FileHadoop fileHadoop = (FileHadoop) file;
			FSDataOutputStream fsHdfs = fileHadoop.getOutputStreamNew(cover);
			fs = new OutputStreamHdfs(fsHdfs);
		} else {
			fs = new FileOutputStream(file, !cover);
		}

		return fs;
	}

	/**
	 * 删除文件分割符
	 * 
	 * @param path
	 * @return
	 */
	public static String removeSep(String path) {
		path = path.trim();
		if (path.equals("/") || path.equals("\\")) {
			return path;
		}
		if (path.endsWith(File.separator)) {
			path = path.substring(0, path.length() - 1);
		}
		return path;
	}

}
