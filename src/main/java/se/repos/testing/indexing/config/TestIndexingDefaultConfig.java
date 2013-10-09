/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing.config;

import java.util.HashSet;
import java.util.Set;

import org.apache.solr.client.solrj.SolrServer;

import se.repos.indexing.IdStrategy;
import se.repos.indexing.IndexingItemHandler;
import se.repos.indexing.ReposIndexing;
import se.repos.indexing.item.IdStrategyDefault;
import se.repos.indexing.item.ItemContentBufferStrategy;
import se.repos.indexing.item.ItemPropertiesBufferStrategy;
import se.repos.indexing.twophases.IndexingEventAware;
import se.repos.indexing.twophases.ItemContentsMemoryChoiceDeferred;
import se.repos.indexing.twophases.ItemPropertiesImmediate;
import se.repos.indexing.twophases.ReposIndexingImpl;
import se.repos.testing.indexing.TestIndexOptions;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

/**
 * Indexing configuration for our tests in this module.
 * See also the distributed standard config for unit tests, {@link TestIndexOptions#getIndexing()}.
 */
public class TestIndexingDefaultConfig extends AbstractModule {

	private SolrServer repositem;
	private Set<IndexingItemHandler> handlers;

	/**
	 * @param solrRepositemCore
	 * @param handlers separately configured handlers (if the default handlers start to have dependencies we must accept classes too)
	 */
	public TestIndexingDefaultConfig(SolrServer solrRepositemCore, Set<IndexingItemHandler> handlers) {
		this.repositem = solrRepositemCore;
		this.handlers = handlers;
	}
	
	@Override
	protected void configure() {
		bind(SolrServer.class).annotatedWith(Names.named("repositem"))
			.toInstance(repositem);
		
		bind(ReposIndexing.class).to(ReposIndexingImpl.class);

		bind(new TypeLiteral<Set<IndexingItemHandler>>(){}).annotatedWith(Names.named("blocking")).toInstance(handlers);
		bind(new TypeLiteral<Set<IndexingItemHandler>>(){}).annotatedWith(Names.named("background")).toInstance(new HashSet<IndexingItemHandler>());
		
		bind(new TypeLiteral<Set<IndexingEventAware>>(){}).toInstance(new HashSet<IndexingEventAware>());
		
		bind(IdStrategy.class).to(IdStrategyDefault.class);
		bind(ItemPropertiesBufferStrategy.class).to(ItemPropertiesImmediate.class);
		bind(Integer.class).annotatedWith(Names.named("indexingFilesizeInMemoryLimitBytes")).toInstance(100000); // optimize for test run performance, but we should test the file cache also
		//bind(ItemContentsBufferStrategy.class).to(ItemContentsMemorySizeLimit.class);		
		bind(ItemContentBufferStrategy.class).to(ItemContentsMemoryChoiceDeferred.class);
	}

}
