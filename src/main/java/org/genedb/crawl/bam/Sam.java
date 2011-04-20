package org.genedb.crawl.bam;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import net.sf.samtools.AlignmentBlock;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMFileReader.ValidationStringency;
import net.sf.samtools.SAMRecord;
import net.sf.samtools.SAMRecordIterator;
import net.sf.samtools.SAMSequenceRecord;

import org.apache.log4j.Logger;
import org.genedb.crawl.model.Alignment;
import org.genedb.crawl.model.FileInfo;
import org.genedb.crawl.model.MappedCoverage;
import org.genedb.crawl.model.MappedQuery;
import org.genedb.crawl.model.MappedSAMHeader;
import org.genedb.crawl.model.MappedSAMSequence;
import org.genedb.crawl.model.Records;
import org.genedb.crawl.model.adapter.AlignmentBlockAdapter;
import org.genedb.crawl.model.adapter.AlignmentBlockAdapterList;



public class Sam {
	
	private Logger logger = Logger.getLogger(Sam.class);
	
	public AlignmentStore alignmentStore;
	
	private final String[] defaultProperties = {"alignmentStart", "alignmentEnd", "flags", "readName"};
	private final Method[] methods = SAMRecord.class.getDeclaredMethods();
	private final Field[] fields = Records.class.getDeclaredFields();
	
	//private final Field[] recordFields = Records.class.getFields();
	
	private Map<String, Field> beanFields = new HashMap<String, Field>();;
	Map<String,Method> samRecordMethodMap = new HashMap<String,Method>();
	
	public Sam() {
		
		for (Field f : fields) {
			beanFields.put(f.getName(), f);
			logger.debug(String.format("field %s %s", f.getName(), f));
		}
		
		
		
		for (Method method : methods) {
			String methodName = method.getName();
			if (methodName.startsWith("get")) {
				String propertyName = methodName.substring(3);
				propertyName = propertyName.substring(0, 1).toLowerCase() + propertyName.substring(1);
				
				if (! beanFields.containsKey(propertyName)) {
					continue;
				}
				
				
				samRecordMethodMap.put(propertyName, method);
				logger.debug(String.format("method %s %s", propertyName, method));
			}
		}
		
		
	}
	
	private SAMFileReader getSamOrBam(int fileID) throws Exception {
		final SAMFileReader inputSam = alignmentStore.getReader(fileID); 
		if (inputSam == null) {
			throw new Exception ("Could not find the file " + fileID);
		}
		inputSam.setValidationStringency(ValidationStringency.SILENT);
		return inputSam;
	}
	
	public MappedSAMHeader header(int fileID) throws Exception {
		return this.header(getSamOrBam(fileID));
	}
	
	public MappedSAMHeader header(SAMFileReader file) throws Exception {
		MappedSAMHeader model = new MappedSAMHeader();
		
		for (Map.Entry<String, String> entry : file.getFileHeader().getAttributes()) {
			model.attributes.put(entry.getKey(), entry.getValue().toString());
		}
		
		return model;
	}
	
	public List<MappedSAMSequence> sequence(int fileID) throws Exception {
		return this.sequence(getSamOrBam(fileID));
	}
	
	public List<MappedSAMSequence> sequence(SAMFileReader file) throws Exception {
		List<MappedSAMSequence> model = new ArrayList<MappedSAMSequence>();
		for (SAMSequenceRecord ssr : file.getFileHeader().getSequenceDictionary().getSequences()) {
			MappedSAMSequence mss = new MappedSAMSequence();
			mss.length = ssr.getSequenceLength();
			mss.name = ssr.getSequenceName();
			mss.index = ssr.getSequenceIndex();
			
			model.add(mss);
			
			
		}
		return model;
	}
	
	public List<FileInfo> listwithsequence(String sequence) throws Exception {
		
		
		Map<Integer, Alignment> map = new HashMap<Integer, Alignment>();
		
		for (Alignment alignment : alignmentStore.getAlignments()) {
			SAMFileReader file = alignment.getReader();
			Integer fileID = alignment.fileID;
			
			if (map.containsKey(fileID)) {
				continue;
			}
			
			String actualSequenceName = getActualSequenceName(fileID, sequence);
			
			for (SAMSequenceRecord ssr : file.getFileHeader().getSequenceDictionary().getSequences()) {
				
				if (actualSequenceName == null) {
					continue;
				}
				
				if (actualSequenceName.equals(ssr.getSequenceName())) {
					map.put(fileID, alignment);
				} 
			}
		}
		
		return list(new ArrayList<Alignment>(map.values()));
	}
	
	private List<FileInfo> list(List<Alignment> alignments) {
		
		List<FileInfo> files = new ArrayList<FileInfo>();
		
		for (Alignment alignment : alignments) {
			FileInfo file = new FileInfo(alignment.fileID, alignment.file, alignment.meta, alignment.organism);
			logger.info(alignment.file);
			logger.info(alignment.meta);
			files.add(file);
			
		}
		return files;
	}
	
	public List<FileInfo> list() {
		return list(alignmentStore.getAlignments());
	}
	
	public List<FileInfo> listfororganism(String organism) {
		
		List<Alignment> alignments = new ArrayList<Alignment>();
		
		for (Alignment alignment : alignmentStore.getAlignments()) {
			if (alignment.organism.equals(organism) || alignment.organism.equals("com:" + organism)) {
				alignments.add(alignment);
			}
		}
		
		return list(alignments);
	}
	
	public String getActualSequenceName(int fileID, String sequenceName) throws Exception {
		
		Alignment alignment = alignmentStore.getAlignment(fileID);
		SAMFileReader file = alignment.getReader();
		
		//List<AlignmentSequenceAlias> sequences = alignmentStore.getSequences();
		Map<String,String> sequences = alignmentStore.getSequences();
		
		for (SAMSequenceRecord ssr : file.getFileHeader().getSequenceDictionary().getSequences()) {
			String currentName = ssr.getSequenceName();
			
			//logger.info(String.format("%s = %s", currentName, sequenceName));
			
			if (currentName.equals(sequenceName)) {
				return currentName;
			}
			
			if (sequences.containsKey(sequenceName)) {
				return sequences.get(sequenceName);			}
			
//			for (AlignmentSequenceAlias sequenceAlias : sequences ) {
//				
//				//logger.info(String.format("-- %s = %s", sequenceAlias.alias, sequenceName));
//				
//				if (sequenceAlias.alias.equals(sequenceName)) {
//					return sequenceAlias.name;
//				}
//			}
			
		}
		return null;
		
	}
	
//	public synchronized MappedQuery query(int fileID, String sequence, int start,  int end, boolean contained, int filter) throws Exception {
//		return query(fileID, sequence, start, end, contained, defaultProperties, filter);		
//	}
	
	
	public synchronized MappedQuery query(int fileID, String sequence, int start,  int end, boolean contained, String[] properties, int filter ) throws Exception {
		logger.debug("FileID : " + fileID);
		sequence = getActualSequenceName(fileID, sequence);
		return this.query(getSamOrBam(fileID), sequence, start, end, contained, properties, filter);
	}
	
	private synchronized MappedQuery query(SAMFileReader file, String sequence, int start,  int end, boolean contained, String[] properties, int filter ) throws Exception {
		
		if (sequence == null) {
			throw new Exception ("Supplied sequence does not exist.");
		}
		
		if (properties == null) {
			properties = defaultProperties;
		}
		
		logger.debug(String.format("file: %s\tlocation: '%s:%d-%d'\tcontained?%s\tfilter: %d(%s)", file, sequence, start, end, contained, filter, padLeft(Integer.toBinaryString(filter), 8)));
		
		long startTime = System.currentTimeMillis();
		
		MappedQuery model = new MappedQuery();
		model.records = new Records()
		;		
		//Set<String> propertySet = new HashSet<String>(Arrays.asList(properties));
		//Map<Method,String> methods2properties = new HashMap<Method,String>();
		
		
//		Hashtable<String, Field> recordFieldSet = new Hashtable<String, Field>();
//		for (Field f : recordFields) {
//			recordFieldSet.put(f.getName(), f);
//		}
//		
		//Map<String, MappedQueryRecordElementList> map = new Hashtable<String, MappedQueryRecordElementList>();
		
		
		Set<String> props = new HashSet<String>();
		
		// initialise the relevant props 
		for (String propertyName : properties) {
			if (! beanFields.containsKey(propertyName)) {
				continue;
			}
			Field f = beanFields.get(propertyName);
			
			// currently the only field that is not a list
			if (! f.getName().equals("alignmentBlocks")) {
				f.set(model.records, new ArrayList());
			}
			
			props.add(propertyName);
		}
		
		
		
		
		logger.info(props);
		
		model.count = 0;
		
		
		
		// we're going to store any alignment blocks we find in there
		List<List<AlignmentBlockAdapter>> alignmentBlocks = new ArrayList<List<AlignmentBlockAdapter>>();
		int maxAlignmentBlocksInRead = 0;
		
		SAMRecordIterator i = null;
		try {
			
			/**
			 * 
			 * According to the BAMFileReader2 doc:
			 * 
		     * "Prepare to iterate through the SAMRecords in file order.
		     * Only a single iterator on a BAM file can be extant at a time.  If getIterator() or a query method has been called once,
		     * that iterator must be closed before getIterator() can be called again.
		     * A somewhat peculiar aspect of this method is that if the file is not seekable, a second call to
		     * getIterator() begins its iteration where the last one left off.  That is the best that can be
		     * done in that situation."
		     * 
		     * For this reason, we must make sure that we close the iterator at the end of the loop AND make sure that the methods
		     * that use this iterator are iterates is synchronized. 
		     * 
		     */
			
			i = file.query(sequence, start, end, contained);
			
			while ( i.hasNext() )  {
				SAMRecord record = i.next();
				
				
				
				/*
				 * int toFilter = record.getFlags() & filter;
				logger.debug(String.format("Read: %s, Filter: %s, Flags: %s, Result: %s", record.getReadName(), filter, record.getFlags(), toFilter ));
				logger.debug(padLeft(Integer.toBinaryString(filter), 8));
				logger.debug(padLeft(Integer.toBinaryString(record.getFlags()), 8));
				logger.debug(padLeft(Integer.toBinaryString(toFilter), 8));
				*/
				
				if ((record.getFlags() & filter) > 0) {
					//logger.debug("some matches ... skipping");
					continue;
				}
				
				for (String propertyName : props) {
					
					
					if (propertyName.endsWith("alignmentBlocks")) {
						
						List<AlignmentBlock> result = record.getAlignmentBlocks();
						
						@SuppressWarnings("unchecked")
						List<AlignmentBlock> blocks = (List<AlignmentBlock>) result; 
						AlignmentBlockAdapterList blockAdapters = new AlignmentBlockAdapterList();
						for (AlignmentBlock block : blocks) {
							//logger.info(String.format("Adding block %s to read %s", block, record.getReadName()));
							blockAdapters.add(new AlignmentBlockAdapter(block));
						}
						
//						AlignmentBlockAdapterList l = new AlignmentBlockAdapterList();
//						l.alignmentBlocks = blockAdapters;
						
						//logger.debug("length " + blockAdapters.size());
						
						alignmentBlocks.add(blockAdapters);
						
						int size = blockAdapters.size();
						
						if (size > maxAlignmentBlocksInRead) {
							maxAlignmentBlocksInRead = size;
						}
						
					} else {
						Method method = samRecordMethodMap.get(propertyName);
						Object result = method.invoke(record);
						Field f = beanFields.get(propertyName);
						ArrayList list = (ArrayList) f.get(model.records);
						list.add(result);
					}
				}
				
				
				model.count++;
				
			}
		
		} catch (Exception e) {
			logger.error(e.getMessage());
			e.printStackTrace();
			
		} finally {
			if (i != null) {
				i.close();
			}
		}
		
		if (props.contains("alignmentBlocks") && alignmentBlocks.size() > 0) {
			
			AlignmentBlockAdapter[][] blockArray = new AlignmentBlockAdapter[alignmentBlocks.size()][maxAlignmentBlocksInRead];
			
			int b = 0;
			for (List<AlignmentBlockAdapter> list : alignmentBlocks) {
				
				int bb = 0;
				for (AlignmentBlockAdapter block : list) {
					blockArray[b][bb] = block;
					bb++;
				}
				b++;
			}
			
			model.records.alignmentBlocks = blockArray;
			
		}
		
		alignmentBlocks = null;
		
		
		long endTime = System.currentTimeMillis() ;
		float time = (endTime - startTime) / (float) 1000 ;
		
		model.contained = contained;
		model.start = start;
		model.end = end;
		model.sequence = sequence;
		model.time = Float.toString(time);
		model.filter = filter;
		
		//model.records;// = new Records(); //new ArrayList(map.values());
		
		
		
		return model;
	}
	
	
	public synchronized MappedCoverage coverage(int fileID, String sequence, int start, int end, int window, int filter) throws Exception {
		sequence = getActualSequenceName(fileID, sequence);
		return this.coverage(getSamOrBam(fileID), sequence, start, end, window, filter);
	}
	
	public synchronized MappedCoverage coverage(SAMFileReader file, String sequence, int start, int end, int window, int filter) throws Exception {
		
		if (sequence == null) {
			throw new Exception ("Supplied sequence does not exist.");
		}
		
		long startTime = System.currentTimeMillis();
		
		int max = 0;
		final int nBins = Math.round((end-start+1.f)/window);
		
	    int coverage[] = new int[nBins];
	    
	    for(int i=0; i<coverage.length; i++) {
	    	coverage[i] = 0;  
	    }
		
	    logger.debug("starting iterations, filter: " + filter);
	    logger.debug(start + "," + end + "," + window + "," + nBins);
		
		SAMRecordIterator iter = null;
		
		
		logger.debug(startTime);
		
		try {
			iter = file.query(sequence, start, end, false);
			while (iter.hasNext()) {
				
				SAMRecord record = iter.next();
				
				if ((record.getFlags() & filter) > 0) {
					continue;
				}
				
				
				List<AlignmentBlock> blocks = record.getAlignmentBlocks();
				
				for (AlignmentBlock block : blocks) {
					for (int k = 0; k < block.getLength(); k++) {
						
						final int pos = block.getReferenceStart() + k - start;
						final float fbin =  pos / (float) window;
						
						if ((fbin < 0) || (fbin > nBins-1)) {
							continue;
						}
						
						final int ibin = (int) fbin;
                        
						coverage[ibin] += 1;
						
						if(coverage[ibin] > max) {
							max = coverage[ibin];
						}
					}
				}
			}
			
		} catch (Exception e) {
			logger.error(e.getMessage());
			e.printStackTrace();
			
		} finally {
			if (iter != null) {
				iter.close();
			}
		}
		
		long endTime = System.currentTimeMillis() ;
		float time = (endTime - startTime) / (float) 1000 ;
		
		
		
		logger.debug("ending iterations");
		
		MappedCoverage mc = new MappedCoverage();
		mc.data = coverage;
		mc.start = start;
		mc.end = end;
		mc.window = window;
		mc.max = max;
		mc.time = Float.toString(time);
		mc.bins = nBins;
		
		return mc;
		
	}
	
	private String padLeft(String s, int n) {
	    return String.format("%1$#" + n + "s", s);  
	}
	
}
