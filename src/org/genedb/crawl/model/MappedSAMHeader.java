package org.genedb.crawl.model;

import java.util.Hashtable;

import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias("header")
public class MappedSAMHeader extends BaseResult {
	
	@XStreamAlias("attributes")
	public Hashtable<String, String> attributes = new Hashtable<String, String>();
	
}