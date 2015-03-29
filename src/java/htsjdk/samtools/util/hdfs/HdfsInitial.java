package htsjdk.samtools.util.hdfs;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileContext;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.UnsupportedFileSystemException;

public class HdfsInitial {

	static FileSystem fsHDFS;
	static Configuration conf;
	static FileContext fileContext;
	static {
		initial();
	}

	public static void initial() {
		IntHdfsBaseHolder hdfsBase = new HdfsBaseHolderHadoop();
		((HdfsBaseHolderHadoop) hdfsBase).setCorexml(PathDetail.getHdpCoreXml());
		((HdfsBaseHolderHadoop) hdfsBase).setHdfsxml(PathDetail.getHdpHdfsXml());
		conf = hdfsBase.getConf();
		try {
			fsHDFS = FileSystem.get(conf);
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			fileContext = FileContext.getFileContext(conf);
		} catch (UnsupportedFileSystemException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static String getSymbol() {
		return PathDetail.getHdpHdfsHeadSymbol();
	}

	public static FileSystem getFileSystem() {
		return fsHDFS;
	}

	public static FileContext getFileContext() {
		return fileContext;
	}

	static interface IntHdfsBaseHolder {
		Configuration getConf();
	}

	static class HdfsBaseHolderHadoop implements IntHdfsBaseHolder {
		String hdfsxml;
		String corexml;
		Configuration conf;

		public void setCorexml(String corexml) {
			this.corexml = corexml;
		}

		public void setHdfsxml(String hdfsxml) {
			this.hdfsxml = hdfsxml;
		}

		public synchronized Configuration getConf() {
			conf = new Configuration();
			conf.addResource(new Path(corexml));
			conf.addResource(new Path(hdfsxml));

			conf.set("dfs.permissions.enabled", "false");
			conf.set("dfs.client.block.write.replace-datanode-on-failure.policy", "NEVER");
			conf.set("dfs.client.block.write.replace-datanode-on-failure.enable", "true");

			return conf;
		}

	}

}
