package htsjdk.samtools.util.hdfs;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PathDetail {
	static Properties properties;
	
	static {
		initial();
	}
	private static void initial() {
		String configPath = "hdfs.config.properties";
		InputStream in = PathDetail.class.getClassLoader().getResourceAsStream(configPath);
		properties = new Properties();
		try {
			properties.load(in);
		} catch (IOException e1) {
			e1.printStackTrace();
		} finally{
			try {
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
		
	public static String getHdpHdfsXml() {
		return properties.getProperty("hdfs-xml");
	}
	public static String getHdpCoreXml() {
		return properties.getProperty("hdfs-core-xml");
	}
	
	public static String getHdpHdfsHeadSymbol() {
		return properties.getProperty("hdfsHeadSymbol");
	}
	
}
