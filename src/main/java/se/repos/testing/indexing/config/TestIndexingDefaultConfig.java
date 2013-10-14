/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing.config;

import java.util.Set;

import org.apache.solr.client.solrj.SolrServer;

import se.repos.indexing.IdStrategy;
import se.repos.indexing.IndexingHandlers;
import se.repos.indexing.IndexingItemHandler;
import se.repos.indexing.ReposIndexing;
import se.repos.indexing.item.IdStrategyDefault;
import se.repos.indexing.item.ItemContentBufferStrategy;
import se.repos.indexing.item.ItemPropertiesBufferStrategy;
import se.repos.indexing.repository.ReposIndexingPerRepository;
import se.repos.indexing.scheduling.IndexingSchedule;
import se.repos.indexing.scheduling.IndexingScheduleBlockingOnly;
import se.repos.indexing.twophases.ItemContentsMemory;
import se.repos.indexing.twophases.ItemPropertiesImmediate;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
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
	 * @param handlers extra handlers, configured separately
	 */
	public TestIndexingDefaultConfig(SolrServer solrRepositemCore, Set<IndexingItemHandler> handlers, boolean includeItemDefaultHandlers) {
		if (!includeItemDefaultHandlers) {
			throw new UnsupportedOperationException("Support for running indexing without the default handlers has not been implemented. Enable itemDefaults in options.");
		}
		this.repositem = solrRepositemCore;
		this.handlers = handlers;
	}
	
	@Override
	protected void configure() {
		bind(SolrServer.class).annotatedWith(Names.named("repositem"))
			.toInstance(repositem);
		
		bind(IndexingSchedule.class).to(IndexingScheduleBlockingOnly.class);
		
		Multibinder<IndexingItemHandler> handlerconf = Multibinder.newSetBinder(binder(), IndexingItemHandler.class);
		IndexingHandlers.configureFirst(handlerconf);
		for (IndexingItemHandler h : handlers) {
			h = ignoreInjects(h);
			handlerconf.addBinding().toInstance(h);
		}
		IndexingHandlers.configureLast(handlerconf);
		
		bind(ReposIndexing.class).to(ReposIndexingPerRepository.class);
		
		bind(IdStrategy.class).to(IdStrategyDefault.class);
		bind(ItemPropertiesBufferStrategy.class).to(ItemPropertiesImmediate.class);
		bind(ItemContentBufferStrategy.class).to(ItemContentsMemory.class);
	}

	/**
	 * Guice tries to resolve dependencies for the instances given to the multibinder, but those are preconfigured.
	 * We also need a way to add configuration Modules and individual handler classess.
	 */
	private IndexingItemHandler ignoreInjects(final IndexingItemHandler h) {
		return (IndexingItemHandler) java.lang.reflect.Proxy.newProxyInstance(h.getClass().getClassLoader(), new Class[] { IndexingItemHandler.class },
				new java.lang.reflect.InvocationHandler() {
					@Override
					public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args)
							throws Throwable {
						return method.invoke(h, args);
					}
				});
	}

}
