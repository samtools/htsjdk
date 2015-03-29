package htsjdk.samtools.util.hdfs;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

public class FileHadoop extends File {
	private static final long serialVersionUID = 8341313247682247317L;
	FileSystem fsHDFS;
	Path dst;
	FileStatus fileStatus;
	String fileName;
	public Path getDst() {
		return dst;
	}
	
	/**
	 * 输入另一个fileHadoop的内容，仅获得其配置信息，不获得其具体文件名
	 * @param fileHadoop
	 * @throws IOException 
	 */
	public FileHadoop(String hdfsFilePath) {
		super(hdfsFilePath = copeToHdfsHeadSymbol(hdfsFilePath));
		this.fsHDFS = HdfsInitial.getFileSystem();
		hdfsFilePath = hdfsFilePath.replace(FileHadoop.getHdfsSymbol(), "");
		//TODO 以后就应该是
		dst = new Path(hdfsFilePath);
		this.fileName = hdfsFilePath;
		init();
	}
	
	/**
	 * 输入另一个fileHadoop的内容，仅获得其配置信息，不获得其具体文件名
	 * @param fileHadoop
	 * @throws IOException 
	 */
	public FileHadoop(String fileName, Path path) {
		super(copeToHdfsHeadSymbol(fileName));
		this.fsHDFS = HdfsInitial.getFileSystem();
		//TODO 以后就应该是
		dst = path;
		this.fileName = fileName;
		init();
	}
	
	/** 初始化 */
	private void init() {
		try{
			if(fileStatus != null)
				return;
			if (fsHDFS.exists(dst)) {
				fileStatus = fsHDFS.getFileStatus(dst);
			}
		} catch(Exception e) { 
			e.printStackTrace();
		}
	}
	
	private static String copeToHdfsHeadSymbol(String hdfsFilePath) {
		if (!hdfsFilePath.startsWith(FileHadoop.getHdfsSymbol())) {
			hdfsFilePath = FileHadoop.addHdfsHeadSymbol(hdfsFilePath);
		}
		return hdfsFilePath;
	}
	
	public short getReplication() {
		return fileStatus.getReplication();
	}
	
	public FSDataInputStream getInputStream() {
		try {
			return fsHDFS.open(dst);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * <b>如果本方法长时间卡死，清check /etc/hosts中是否配置了相关的yarn-master</b><br>
	 * 根据文件产生一个流
	 * @param overwrite  false：如果文件不存在，则返回nulll
	 * @return
	 */
	public FSDataOutputStream getOutputStreamNew(boolean overwrite) {
		try {
			return fsHDFS.create(dst, overwrite);
		} catch (IOException e) {
			if (!overwrite) {
				try {
					return fsHDFS.append(dst);
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
					return null;
				}
			}
			return null;
		}
	}
	
    public FileHadoop getParentFile() {
        String p = this.getParent();
        if (p == null) return null;
        return new FileHadoop(p);
    }

	/**
	 * 根据文件产生一个流，如果文件不存在则返回null
	 * 如果文件存在则衔接上去
	 * @param overwrite
	 * @return
	 */
	public FSDataOutputStream getOutputStreamAppend() {
		try {
			return fsHDFS.append(dst);
		} catch (IOException e) {
			return null;
		}
	}
	
	public String getModificationTime() {
		String PATTERN_DATETIME = "yyyy-MM-dd HH:mm:ss";
		Date date = new Date(fileStatus.getModificationTime());
		SimpleDateFormat sf = new SimpleDateFormat(PATTERN_DATETIME);
		return sf.format(date);
	}
	
	/**
	 * 找到上级文件全路径
	 * @param fileName
	 * @return
	 */
	@Override
	public String getParent() {
		try {
			return super.getParent();
		} catch (NullPointerException e) {
			return null;
		}
	}
	
	/**
	 * 是不是目录
	 */
	@Override
	public boolean isDirectory() {
		if(fileStatus == null){
			try {
				return fsHDFS.isDirectory(dst);
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		} else {
			//TODO hadoop2
			return fileStatus.isDirectory();
			//mapr
//			return fileStatus.isDir();
		}
			
	}
	/**
	 * 存不存在此文件
	 * @return
	 */
	@Override
	public boolean exists() {
		if(fileStatus == null) {
			try {
				return fsHDFS.exists(dst);
			} catch (IOException e) {
				return false;
			}
		}else
			return true;
	}
	public String getAbsolutePath() {
		return copeToHdfsHeadSymbol(fileName);
	}
	@Deprecated
    public FileHadoop getAbsoluteFile() {
        String absPath = getAbsolutePath();
        return new FileHadoop(absPath);
    }
	@Deprecated
	public FileHadoop getCanonicalFile() throws IOException {
		return new FileHadoop(getCanonicalPath());
	}
	
	/**
	 * 列出子文件名，相对文件名
	 * @return
	 */
	@Override
	public String[] list() {
		FileStatus[] childrenFileStatus;
		try {
			childrenFileStatus = fsHDFS.listStatus(dst);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		String[] files = {};
		if (childrenFileStatus.length != 0) {
			files = new String[childrenFileStatus.length];
		}
		for (int i = 0; i < childrenFileStatus.length; i++) {
			files[i] = childrenFileStatus[i].getPath().getName();
		}
		return files;
	}

	@Override
	public boolean isFile() {
		//TODO hadoop2
//		return fileStatus == null? false : fileStatus.isFile();
		//map3
		if (isDirectory()) {
			return false;
		}
		return fileStatus == null? false : true;
	}
	
	@Override
	public long length() {
		return fileStatus == null? 0 : fileStatus.getLen();
	}

	@Override
	@Deprecated
	public URL toURL() throws MalformedURLException {
		return super.toURL();
	}
	
	@Deprecated
	public boolean isHidden() {
		 throw new ExceptionFile("No support method");
	}
	
	/** 出错返回 -1000 */
	@Override
	public long lastModified() {
		return fileStatus == null? 0 : fileStatus.getModificationTime();
	}

	@Override
	public boolean createNewFile() throws IOException {
		return fsHDFS.createNewFile(dst);
	}
	
	@Override
	public boolean delete() {
		if (fileStatus == null) {
			return true;
		}
		try {
			return fsHDFS.delete(dst, true);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	public void deleteOnExit() {
		DeleteOnExitHookHadoop.add(getAbsolutePath());
	}
	@Override
	public FileHadoop[] listFiles() {
		FileStatus[] childrenFileStatus;
		try {
			childrenFileStatus = fsHDFS.listStatus(dst);
		} catch (IOException e) {
			e.printStackTrace();
			return new FileHadoop[]{};
		}
		if (childrenFileStatus == null || childrenFileStatus.length == 0) {
			return new FileHadoop[]{};
		}
		FileHadoop[] files = {};
		if (childrenFileStatus.length != 0) {
			files = new FileHadoop[childrenFileStatus.length];
		}
		for (int i = 0; i < childrenFileStatus.length; i++) {
			Path childPath = childrenFileStatus[i].getPath();
			files[i] = new FileHadoop(FileOperate.addSep(fileName) + childPath.getName(), childPath);
		}
		return files;
	}
	
	public boolean canWrite() {
		return true;
	}
	
	public boolean canRead() {
		return true;
	}

	public FileHadoop[] listFiles(FilenameFilter filter) {
        FileHadoop ss[] = listFiles();
        if (ss == null) return null;
        ArrayList<FileHadoop> files = new ArrayList<>();
        for (FileHadoop s : ss)
            if ((filter == null) || filter.accept(this, s.dst.getName()))
                files.add(s);
        return files.toArray(new FileHadoop[files.size()]);
    }
    
    public FileHadoop[] listFiles(FileFilter filter) {
    	FileHadoop ss[] = listFiles();
        if (ss == null) return null;
        ArrayList<FileHadoop> files = new ArrayList<>();
        for (FileHadoop s : ss) {
            if ((filter == null) || filter.accept(s))
                files.add(s);
        }
        return files.toArray(new FileHadoop[files.size()]);
    }
    
	@Override
	public boolean mkdir() {
		try {
			return fsHDFS.mkdirs(dst);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public boolean mkdirs() {
		try {
			return fsHDFS.mkdirs(dst);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}
	@Deprecated
    public boolean setLastModified(long time) {
        if (time < 0) throw new IllegalArgumentException("Negative time");
        try {
			fsHDFS.setTimes(dst, time, time);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
        return true;
    }
    @Deprecated
    public boolean setReadOnly() {
    	 throw new ExceptionFile("No support method");
    }
    @Deprecated
    public boolean setWritable(boolean writable, boolean ownerOnly) {
    	return true;
    	
//    	 throw new ExceptionFile("No support method");
    }
    @Deprecated
    public boolean setWritable(boolean writable) {
    	 throw new ExceptionFile("No support method");
    }
    @Deprecated
    public boolean setReadable(boolean readable, boolean ownerOnly) {
    	return true;
//    	 throw new ExceptionFile("No support method");
    }
    @Deprecated
    public boolean setReadable(boolean readable) {
    	 throw new ExceptionFile("No support method");
    }
    @Deprecated
    public boolean setExecutable(boolean executable, boolean ownerOnly) {
    	 throw new ExceptionFile("No support method");
    }
    @Deprecated
    public boolean setExecutable(boolean executable) {
    	 throw new ExceptionFile("No support method");
    }
    public boolean canExecute() {
    	 throw new ExceptionFile("No support method");
    }
    
	/**
	 * 未测试
	 */
	@Override
	public boolean renameTo(File dest) {
		try {
			String path = dest.getPath();
			if (FileHadoop.isHdfs(path) || FileHadoop.isHdfs(path.substring(1, path.length()-2))) {
				path = path.replace(getHdfsSymbol(), "");
			}
			return fsHDFS.rename(dst, new Path(path));
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	/** 
	 * 用{@link com.novelbio.base.fileOperate.FileHadoop#getHdfsSymbol()}替换<br>
	 * 文件名前添加的HDFS的头，末尾没有"/" */
	public static String getHdfsSymbol() {
		return PathDetail.getHdpHdfsHeadSymbol();
	}
	
	/** 
	 * 用{@link com.novelbio.base.fileOperate.FileHadoop#addHdfsHeadSymbol(path)}替换<br>
	 * 在输入的文件名前添加的HDFS的头<br>
	 * <b>务必输入绝对路径，也就是要以"/"开头</b>
	 * @param path
	 * @return
	 */
	public static String addHdfsHeadSymbol(String path) {
		return getHdfsSymbol() + path;
	}
	
	public static FileSystem getHadoopFileSystem() {
		return HdfsInitial.getFileSystem();
	}
	
	public static boolean isHdfs(String fileName) {
		if (fileName == null || fileName.equals("")) {
			return false;
		}
		fileName = fileName.toLowerCase();
		if (FileOperate.isRealNull(getHdfsSymbol())) {
			return false;
		}
		return fileName.startsWith(getHdfsSymbol()) ? true : false;
	}
	

	@Deprecated
	public static File[] listRoots() {
        return null;
    }
	
	/**
	 * 类似 /hdfs:/apps/test 这种文件名
	 * @param hdfsFile
	 * @return
	 */
	public static Path getPath(String hdfsFile) {
		hdfsFile = hdfsFile.replace(FileHadoop.getHdfsSymbol(), "");
		//TODO 以后就应该是
		return new Path(hdfsFile);
	}

   @Deprecated
    public long getTotalSpace() {
	   throw new ExceptionFile("No support method");
    }

   @Deprecated
    public long getFreeSpace() {
    	throw new ExceptionFile("No support method");
    }

    @Deprecated
    public long getUsableSpace() {
    	 throw new ExceptionFile("No support method");
    }
    
    /* -- Basic infrastructure -- */

    /**
     * Compares two abstract pathnames lexicographically.  The ordering
     * defined by this method depends upon the underlying system.  On UNIX
     * systems, alphabetic case is significant in comparing pathnames; on Microsoft Windows
     * systems it is not.
     *
     * @param   pathname  The abstract pathname to be compared to this abstract
     *                    pathname
     *
     * @return  Zero if the argument is equal to this abstract pathname, a
     *          value less than zero if this abstract pathname is
     *          lexicographically less than the argument, or a value greater
     *          than zero if this abstract pathname is lexicographically
     *          greater than the argument
     *
     * @since   1.2
     */
    public int compareTo(File pathname) {
        return super.compareTo(pathname);
    }

    /**
     * Tests this abstract pathname for equality with the given object.
     * Returns <code>true</code> if and only if the argument is not
     * <code>null</code> and is an abstract pathname that denotes the same file
     * or directory as this abstract pathname.  Whether or not two abstract
     * pathnames are equal depends upon the underlying system.  On UNIX
     * systems, alphabetic case is significant in comparing pathnames; on Microsoft Windows
     * systems it is not.
     *
     * @param   obj   The object to be compared with this abstract pathname
     *
     * @return  <code>true</code> if and only if the objects are the same;
     *          <code>false</code> otherwise
     */
    public boolean equals(Object obj) {
        if ((obj != null) && (obj instanceof File)) {
            return compareTo((File)obj) == 0;
        }
        return false;
    }

    /**
     * Computes a hash code for this abstract pathname.  Because equality of
     * abstract pathnames is inherently system-dependent, so is the computation
     * of their hash codes.  On UNIX systems, the hash code of an abstract
     * pathname is equal to the exclusive <em>or</em> of the hash code
     * of its pathname string and the decimal value
     * <code>1234321</code>.  On Microsoft Windows systems, the hash
     * code is equal to the exclusive <em>or</em> of the hash code of
     * its pathname string converted to lower case and the decimal
     * value <code>1234321</code>.  Locale is not taken into account on
     * lowercasing the pathname string.
     *
     * @return  A hash code for this abstract pathname
     */
    public int hashCode() {
        return super.hashCode();
    }

    /**
     * Returns the pathname string of this abstract pathname.  This is just the
     * string returned by the <code>{@link #getPath}</code> method.
     *
     * @return  The string form of this abstract pathname
     */
    public String toString() {
        return getPath();
    }
    
}


