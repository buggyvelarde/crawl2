package org.genedb.crawl.elasticsearch.mappers;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


import org.apache.log4j.Logger;
import org.elasticsearch.action.count.CountResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.action.search.SearchRequestBuilder;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.xcontent.BoolQueryBuilder;
import org.elasticsearch.index.query.xcontent.FieldQueryBuilder;
import org.elasticsearch.index.query.xcontent.QueryBuilders;
import org.elasticsearch.index.query.xcontent.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.SortBuilders;
import org.genedb.crawl.model.Coordinates;
import org.genedb.crawl.model.Cvterm;
import org.genedb.crawl.model.Feature;
import org.genedb.crawl.model.LocatedFeature;
import org.genedb.crawl.model.LocationBoundaries;
import org.genedb.crawl.model.Sequence;
import org.gmod.cat.RegionsMapper;
import org.springframework.stereotype.Component;

@Component
public class ElasticSearchRegionsMapper extends ElasticSearchBaseMapper implements RegionsMapper {

	private Logger logger = Logger.getLogger(ElasticSearchRegionsMapper.class);
	
	private int getTotalInRegion(String region) {
		FieldQueryBuilder regionQuery = QueryBuilders.fieldQuery("region", region);
		
		logger.debug(String.format("Index %s, Type %s", connection.getIndex(), connection.getFeatureType()));
		
		CountResponse cr = connection.getClient()
		 	.prepareCount()
		 	.setIndices(connection.getIndex())
		 	.setTypes(connection.getFeatureType())
		 	.setQuery(regionQuery)
		 	.execute()
	        .actionGet();
		
		long count = cr.count();
		
		
		logger.debug(String.format("Count in %s : %s", region, count));
		
		return (int) count;
	}
	
	private int getTotalRegionsInOrganism(int organism_id) {
		FieldQueryBuilder regionQuery = QueryBuilders.fieldQuery("organism_id", organism_id);
		
		CountResponse cr = connection.getClient()
		 	.prepareCount(connection.getIndex())
		 	.setTypes(connection.getRegionType())
		 	.setQuery(regionQuery)
		 	.execute()
	        .actionGet();
		
		long count = cr.count();
		
		logger.debug(String.format("Count in organism %s : %s", organism_id, count));
		
		return (int) count;
	}
	
	
	private BoolQueryBuilder isOverlap(String region, int start, int end, List<String> exclude) {
		
		RangeQueryBuilder startLowerThanRequested = 
			QueryBuilders.rangeQuery("fmin")
				.lte(start);
		
		RangeQueryBuilder endHigherThanRequested = 
			QueryBuilders.rangeQuery("fmax")
				.gte(end);
		
		// (fmin <= start) && (end <= fmax)
		BoolQueryBuilder spansBothSides = 
			QueryBuilders.boolQuery()
				.must(startLowerThanRequested)
				.must(endHigherThanRequested);
		
		RangeQueryBuilder startInRange = 
			QueryBuilders.rangeQuery("fmin")
				.from(start)
				.to(end);
		
		RangeQueryBuilder endInRange = 
			QueryBuilders.rangeQuery("fmax")
				.from(start)
				.to(end);
		
		// (start <= fmin <= end) || (start <= fmax <= end) 
		BoolQueryBuilder isInsideRange = 
			QueryBuilders.boolQuery()
				.should(startInRange)
				.should(endInRange);
		
		
		
		BoolQueryBuilder isOverlap = 
			QueryBuilders.boolQuery()
				.should(spansBothSides)
				.should(isInsideRange);
		
		
		FieldQueryBuilder regionQuery = 
			QueryBuilders.fieldQuery("region", region);
		
		BoolQueryBuilder isOverlapOnRegion =
			QueryBuilders.boolQuery()
			.must(isOverlap)
			.must(regionQuery);
		
		if (exclude != null) {
			for (String exclude_type : exclude) {
				FieldQueryBuilder excludeQuery = 
					QueryBuilders.fieldQuery("type.name", exclude_type);
				isOverlapOnRegion.mustNot(excludeQuery);
			}
		}
		
		return isOverlapOnRegion;
	}
	
	public static String toString(ToXContent tmp) {
	       try {
	           return
	           tmp.toXContent(JsonXContent.unCachedContentBuilder(),
	        		   ToXContent.EMPTY_PARAMS).
	                   prettyPrint().
	                   string();
	       } catch (Exception ex) {
	           return "<ERROR:" + ex.getMessage() + ">";
	       }
	   }
	
	@Override
	public LocationBoundaries locationsMinAndMaxBoundaries(String region,
			int start, int end, List<Integer> types) {
		
		BoolQueryBuilder isOverlap = isOverlap(region, start, end, new ArrayList<String>());
		
		SearchRequestBuilder builder = 
			connection
			.getClient()
			.prepareSearch(connection.getIndex())
			.setTypes(connection.getFeatureType());
		
		
		SearchResponse response = builder
			.setQuery(isOverlap)
			.setExplain(true)
			.setSize(getTotalInRegion(region))
			.execute()
			.actionGet();
	
		logger.info(toString(builder.internalBuilder()));
		
		LocationBoundaries lb = new LocationBoundaries();
		lb.start = start;
		lb.end = end;
		
		for (SearchHit hit : response.getHits()) {
			
			String source = hit.sourceAsString();
			
			//logger.debug(source);
			
			LocatedFeature feature = this.getFeatureFromJson(source);
			
			
			if (feature != null) {
				
				
				if (feature.fmax != null && feature.fmin != null && feature.region != null) {
					if (feature.region.equals(region)) {
						
						if (feature.fmin < lb.start) {
							lb.start = feature.fmin;
						} 
						
						if (feature.fmax > lb.end) {
							lb.end = feature.fmax;
						} 
					}
				}
				
				else if (feature.coordinates != null) {
					for (Coordinates co : feature.coordinates) {
						if (co.region.equals(region)) {
							
							if (co.fmin < lb.start) {
								lb.start = co.fmin;
							} 
							
							if (co.fmax > lb.end) {
								lb.end = co.fmax;
							} 
								
							break;
						}
					}
				}
				
				
				
			}
		}
		
		
		logger.debug(String.format("Actual start: %s. Actual end %s", lb.start, lb.end));
		
		return lb;
		
	}
	
	
	@Override
	public List<LocatedFeature> locationsPaged(String region, int limit,
			int offset, List<String> exclude) {
		
		SearchRequestBuilder builder = connection.getClient()
			.prepareSearch(connection.getIndex())
			.setTypes(connection.getFeatureType())
			.addSort(SortBuilders.fieldSort("fmin"))
			.addSort(SortBuilders.fieldSort("fmax"));
		
		FieldQueryBuilder regionQuery = 
			QueryBuilders.fieldQuery("region", region);
		
		RangeQueryBuilder rangeQuery = 
			QueryBuilders.rangeQuery("fmin").from(0);
		
		BoolQueryBuilder locations =
			QueryBuilders.boolQuery()
				.must(rangeQuery)
				.must(regionQuery);
		
		SearchResponse response = builder
			.setQuery(locations)
			.setExplain(true)
			.setSize(limit)
			.setFrom(offset)
			.execute()
			.actionGet();
		
		return parseLocations(region, response);
	}
	
	
	
	@Override
	public List<LocatedFeature> locations(String region, int start, int end,
			List<String> exclude) {
		
		BoolQueryBuilder isOverlap = isOverlap(region, start, end, exclude);
		
		SearchRequestBuilder builder = connection.getClient()
			.prepareSearch(connection.getIndex())
			.setTypes(connection.getFeatureType())
			.addSort(SortBuilders.fieldSort("fmin"))
			.addSort(SortBuilders.fieldSort("fmax"));
		
		SearchResponse response = builder
			.setQuery(isOverlap)
			.setExplain(true)
			.setSize(getTotalInRegion(region))
			.execute()
			.actionGet();
		
		logger.info(toString(builder.internalBuilder()));
		
		
		return parseLocations(region, response);
		
	}
	
	private List<LocatedFeature> parseLocations(String region, SearchResponse response) {
		List<LocatedFeature> features = new ArrayList<LocatedFeature>();
		
		String[] fieldNames = new String[] {"uniqueName", "fmin", "fmax", "isObsolete", "parent", "phase", "type", "strand"};
		
		for (SearchHit hit : response.getHits()) {
		
			String source = hit.sourceAsString();
			//logger.debug(source);
			
			LocatedFeature feature = this.getFeatureFromJson(source);
			if (feature != null) {
				
				
				if (feature.fmax != null && feature.fmin != null && feature.region != null) {
					if (feature.region.equals(region)) {
						try {
							features.add(copy(feature, fieldNames, LocatedFeature.class));
						} catch (InstantiationException e) {
							logger.error(e);
							e.printStackTrace();
						} catch (IllegalAccessException e) {
							logger.error(e);
							e.printStackTrace();
						}
					}
				}
				else if (feature.coordinates != null) {
					for (Coordinates co : feature.coordinates) {
						if (co.region.equals(region)) {
							
							try {
								features.add(copy(feature, fieldNames, LocatedFeature.class));
							} catch (InstantiationException e) {
								logger.error(e);
								e.printStackTrace();
							} catch (IllegalAccessException e) {
								logger.error(e);
								e.printStackTrace();
							}
							break;
						}
					}
				}
				
			}
		}
		
		return features;
	}

	@Override
	public Sequence sequence(String region) {
		
		try {
			logger.debug(String.format("%s %s %s %s %s",connection.getIndex(), connection.getRegionType(), region, connection, connection.getClient()));
			String json = connection
							.getClient()
							.prepareGet()
							.setIndex(connection.getIndex())
							.setType(connection.getRegionType())
							.setId(region)
							.execute()
							.actionGet()
							.sourceAsString();
			Feature regionFeature = (Feature) jsonIzer.fromJson(json, Feature.class);
			
			Sequence sequence = new Sequence();
			sequence.dna = regionFeature.residues;
			sequence.length = regionFeature.residues.length();
			sequence.organism_id = regionFeature.organism_id;
			
			return sequence;
			
		} catch (Exception e) {
			throw new RuntimeException(e);
			
		} 
		
	}

	@Override
	public List<Feature> inorganism(int organismid, Integer limit, Integer offset, String type_name) {
		
		logger.debug(String.format("%s %s %s", "sequences", "organism_id", String.valueOf(organismid)));
		
		BoolQueryBuilder regionInOrganismQuery = QueryBuilders.boolQuery();
		
		FieldQueryBuilder organismQuery = 
			QueryBuilders.fieldQuery("organism_id", organismid);
		
		regionInOrganismQuery.must(organismQuery);
			
		if (type_name != null) {
			FieldQueryBuilder typeQuery =
				QueryBuilders.fieldQuery("type.name", type_name);
			regionInOrganismQuery.must(typeQuery);
		}
		
		SearchRequestBuilder srb = connection.getClient()
			.prepareSearch(connection.getIndex())
			.setTypes(connection.getRegionType())
			.setQuery(regionInOrganismQuery);
		
		if (limit == null) {
			limit = getTotalRegionsInOrganism(organismid); 
		}
		
		srb.setSize(limit);
		
		if (offset != null) {
			srb.setFrom(offset);
		}
		
		//srb.addFields ( new String[] {"uniqueName", "organism_id" } );
		
		logger.info(toString(srb.internalBuilder()));
		
		
		SearchResponse response = srb.execute()
			.actionGet();
		
		logger.info(response);
		
		SearchHits hits = response.getHits();
		
		List<Feature> regions = new ArrayList<Feature>();
		
		for (SearchHit hit : hits) {
			
			logger.info(hit.id());
			
			// we want to avoid pulling in the sequence here, so we do a manual map to feature conversion
			Map<String, Object> source = hit.getSource();
			
			Feature region = new Feature();
			region.uniqueName = hit.getId();
			region.type = new Cvterm();
			region.type.name = (String) ((Map) source.get("type")).get("name"); 
			region.organism_id = (Integer) source.get("organism_id");
			
			regions.add(region);
			
		}
		
		
		//List<Feature> regions = this.getAllMatches(response, Feature.class);
		
		for (Feature region : regions) {
			region.residues = null;
		}
		
		return regions;

	}
	
//	QueryBuilder regionInOrganismQuery(int organismid) {
//		FieldQueryBuilder organismQuery = 
//			QueryBuilders.fieldQuery("organism_id", organismid);
//		
////		FieldQueryBuilder regionQuery =
////			QueryBuilders.fieldQuery("topLevel", true);
//		
////		BoolQueryBuilder regionInOrganismQuery = 
////			QueryBuilders.boolQuery()
////				.must(organismQuery)
////				.must(regionQuery);
//		
//		return organismQuery;
//	}

	@Override
	public List<Cvterm> typesInOrganism(int organismid) {
		FieldQueryBuilder organismQuery = 
			QueryBuilders.fieldQuery("organism_id", organismid);
		
		SearchResponse response = 
			connection.getClient()
			.prepareSearch()
			.setQuery(organismQuery).execute().actionGet();
		
		List<Feature> regions = this.getAllMatches(response, Feature.class);
		Set<Cvterm> terms = new HashSet<Cvterm>();
		for (Feature region : regions) {
			terms.add(region.type);
		}
		
		return new ArrayList<Cvterm>(terms);
	}


	
//	public static String getIndex() {
//		return "regions";
//	}
//
//
//	
//	public static String getType() {
//		return "Region";
//	}


	

}
