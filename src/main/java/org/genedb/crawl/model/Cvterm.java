package org.genedb.crawl.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

public class Cvterm implements Serializable {
	
    public Cvterm() {}
    public Cvterm(String name) {
        this.name = name;
    }
    public Cvterm(String name,String cv) {
        this.name = name;
        this.cv = new Cv();
        this.cv.name = cv;
    }
	
	public Cv cv;
	
	@XmlAttribute
	public String name;
	
	@XmlAttribute
	public String accession;
	
	/**
	 *  This is not a primitive boolean so it can be nullable (or else the GSON sets it to false if unset).
	 */
	@XmlAttribute(name="is_not", required=false)
	public Boolean is_not;
	
	@XmlElement(name="dbxref")
	@XmlElementWrapper(name="dbxrefs", required=false)
	public List<Dbxref> dbxrefs;
	
	@XmlElement(name="prop")
	@XmlElementWrapper(name="props", required=false)
	public List<CvtermProp> props;
	
	@XmlElement(name="pub")
	@XmlElementWrapper(name="pubs", required=false)
	public List<Pub> pubs;

	@XmlAttribute(required=false)
	public Integer cvterm_id;
	
	@XmlAttribute(required=false)
    public Integer count;
	
	public void addPub(Pub pub) {
		if (pubs == null) {
			pubs = new ArrayList<Pub>();
		}
		pubs.add(pub);
	}
	
	@XmlAttribute(required=false)
	public String definition;
	
	@XmlElement(name="parent")
	@XmlElementWrapper(name="parents", required=false)
	public List<CvtermRelationship> parents;
	
	@XmlElement(name="child")
	@XmlElementWrapper(name="chilren", required=false)
	public List<CvtermRelationship> children;
	
	
	
}
