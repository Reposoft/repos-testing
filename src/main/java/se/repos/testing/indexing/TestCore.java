/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing;

import org.apache.solr.client.solrj.SolrServer;

import se.repos.testing.indexing.config.TestIndexSolrCoreModule;

import com.google.inject.Module;

public class TestCore {

	private String identifier;
	private String resourcePattern;

	public TestCore(String identifier) {
		this.identifier = identifier;
	}
	
	public TestCore setResources(String resourcePattern) {
		this.resourcePattern = resourcePattern;
		return this;
	}
	
	public Module getConfiguration(SolrServer coreInstance) {
		return new TestIndexSolrCoreModule(this.identifier, coreInstance);
	}

	public String getIdentifier() {
		return identifier;
	}
	
	public String getResourcePattern() {
		return resourcePattern;
	}
	
}
