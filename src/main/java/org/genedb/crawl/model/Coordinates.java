package org.genedb.crawl.model;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

@XmlType(name="TheCoordinates")
public class Coordinates implements Serializable {
	
	@XmlAttribute(required=true)
	public String region;
	
	@XmlElement
	public Cvterm regionType;
	
	@XmlAttribute(required=true)
	public Integer fmin;
	
	@XmlAttribute(required=true)
	public Integer fmax;
	
	@XmlAttribute(required=true)
	public String phase;
	
	@XmlAttribute(required=true)
	public int strand;
	
	@XmlAttribute(required=false)
	public Boolean toplevel;
}
