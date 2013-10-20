/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing.config;

import org.apache.solr.client.solrj.SolrServer;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class TestIndexSolrCoreModule extends AbstractModule {

	private String identifier;
	private SolrServer solrCore;

	public TestIndexSolrCoreModule(String identifier, SolrServer solrCore) {
		this.identifier = identifier;
		this.solrCore = solrCore;
	}
	
	@Override
	protected void configure() {
		bind(SolrServer.class).annotatedWith(Names.named(identifier)).toInstance(solrCore);
	}
	
}
