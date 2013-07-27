package se.repos.indexing.testing;

import java.util.List;
import java.util.Map;

import se.repos.indexing.item.IndexingItemHandler;
import se.repos.indexing.item.ItemPathinfo;

public class TestIndexOptions {

	List<IndexingItemHandler> handlers;

	Map<String, String> cores;
	
	Map<String, String> aliases;
	
	/**
	 * Set up for basic "repositem" blocking indexing, i.e. structure but not contents.
	 * @return for chaining
	 */
	public TestIndexOptions itemDefaults() {
		this.addHandler(new ItemPathinfo());
		this.addCore("repositem", "se/repos/indexing/solr/repositem/**");
		return this;
	}
	
	public TestIndexOptions addHandler(IndexingItemHandler handler) {
		this.handlers.add(handler);
		return this;
	}
	
	public TestIndexOptions addCore(String identifier, String resourcePattern) {
		cores.put(identifier, resourcePattern);
		return this;
	}
	
	public boolean hasCore(String identifier) {
		return cores.containsKey(identifier);
	}
	
	/**
	 * Can be used by framework during configuration if the core needs to be stored under a different name.
	 * We're not sure yet how this works with solr.properties though.
	 * @param identifier
	 * @param actualCoreNameInSolr
	 */
	public void addCoreAlias(String identifier, String actualCoreNameInSolr) {
		
	}
	
	public String getCoreAlias(String identifier) {
		return null;
	}
	
	public boolean hasCoreAliases() {
		return false;
	}
	
	public boolean hasCoreAlias(String identifier) {
		return false;
	}
	
	public List<IndexingItemHandler> getHandlers() {
		return handlers;
	}

	public Map<String, String> getCores() {
		return cores;
	}	
	
}
