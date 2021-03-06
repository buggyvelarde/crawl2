package org.genedb.crawl.json;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;
import org.codehaus.jackson.map.introspect.JacksonAnnotationIntrospector;
import org.codehaus.jackson.type.JavaType;
import org.codehaus.jackson.type.TypeReference;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;

public class JsonIzer {
	
	private ObjectMapper mapper;
	
	//private static JsonIzer inst;
	
	public boolean isPretty() {
        return pretty;
    }

    public void setPretty(boolean pretty) {
        this.pretty = pretty;
    }

    private boolean pretty = false;
	
//	public static final JsonIzer getJsonIzer() {
//		if (inst == null) {
//			inst = new JsonIzer();
//		}
//		return inst;
//	}
	
	public final ObjectMapper getMapper() {
		return mapper;
	}
	
	public JsonIzer () {
		mapper = new ObjectMapper();
	    AnnotationIntrospector jaxbIntrospector = new JaxbAnnotationIntrospector();
	    
	    AnnotationIntrospector jacksonIntrospector = new JacksonAnnotationIntrospector();
	    AnnotationIntrospector introspector = AnnotationIntrospector.pair(jaxbIntrospector, jacksonIntrospector);
	    
	    mapper.getDeserializationConfig().setAnnotationIntrospector(introspector);
	    mapper.getSerializationConfig().setAnnotationIntrospector(introspector);
	    mapper.getSerializationConfig().setSerializationInclusion(Inclusion.NON_DEFAULT);
	    
	}
	
	@SuppressWarnings("unchecked")
	public <T> T fromStringOrFile(String json, Class<T> cls) throws JsonMappingException, IOException {
		T object = null;
		
		File jsonFile = new File(json);
		
		if (jsonFile.isFile()) {
			object = (T) fromJson(jsonFile, cls);
		} else {
			object = (T) fromJson(json, cls);
		}
		
		return object;
	}
	
	public JsonParser getParser(String json) throws JsonParseException, IOException {
	    
	    //JsonFactory jFact = mapper.getJsonFactory();
	    JsonFactory jFact = new JsonFactory();
	    return jFact.createJsonParser(json);
	}
	
	public Object fromJson(String string, @SuppressWarnings("rawtypes") Class cls) throws JsonParseException, JsonMappingException, IOException {
		@SuppressWarnings("unchecked")
		Object obj =  mapper.readValue(string, cls);
		return obj;
	}
	
	public Object fromJson(File file, @SuppressWarnings("rawtypes") Class cls) throws JsonParseException, JsonMappingException, IOException {
		@SuppressWarnings("unchecked")
		Object obj =  mapper.readValue(file, cls);
		return obj;
	}
	
	public Object fromJson(String string, @SuppressWarnings("rawtypes") TypeReference type) throws JsonParseException, JsonMappingException, IOException {
		Object obj = mapper.readValue(string, type);
		return obj;
	}
	
	public Object fromJson(String string, JavaType type) throws JsonParseException, JsonMappingException, IOException {
        Object obj = mapper.readValue(string, type);
        return obj;
    }
	
	/*
	 * This method can be used to load up files that are plain lists. It has been used like this :
	 * 		List<Alignment> alignments = (List<Alignment>) jsonIzer.fromJson(alignmentFile,  new TypeReference<List<Alignment>>() {} );
	 */
	public Object fromJson(File file, @SuppressWarnings("rawtypes") TypeReference type) throws JsonParseException, JsonMappingException, IOException {
		Object obj = mapper.readValue(file, type);
		return obj;
	}
	
	
	
	public String toJson(Object object) throws IOException {
		
		JsonFactory jFact = new JsonFactory();
		
		StringWriter writer = new StringWriter();
		
		JsonGenerator jGen = jFact.createJsonGenerator(writer);
		if (pretty)
		    jGen.useDefaultPrettyPrinter();
		
		mapper.writeValue(jGen, object);
		
		return writer.toString();
	}
	
	public void toJson(Object object, Writer writer) throws IOException {
		JsonFactory jFact = new JsonFactory();
		JsonGenerator jGen = jFact.createJsonGenerator(writer);
		mapper.writeValue(jGen, object);
	}
}
