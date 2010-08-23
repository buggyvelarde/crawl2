package org.genedb.crawl;

import java.io.File;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.genedb.crawl.business.postgres.QueryMap;

public class Main {
	
	private static Logger logger = Logger.getLogger(Main.class);
	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		
	    BasicConfigurator.configure();
	    
		QueryMap queryMap = new QueryMap();
		
		File dir = new File("/Users/gv1/Documents/workspace/crawl/sql");
		
		// System.out.println(dir.getPath());
		
		queryMap.setSqlFolder(dir);
		
		
		
		BasicDataSource dataSource = new BasicDataSource();
		dataSource.setDriverClassName("org.postgresql.Driver");
		dataSource.setUrl("jdbc:postgresql://localhost:5437/pathogens");
		dataSource.setUsername("pathdb");
		dataSource.setPassword("pathdb");
		
		
	}

}
