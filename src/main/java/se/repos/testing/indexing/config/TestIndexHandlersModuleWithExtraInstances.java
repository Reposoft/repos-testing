/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing.config;

import java.util.Set;

import se.repos.indexing.IndexingHandlers;
import se.repos.indexing.IndexingItemHandler;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

public class TestIndexHandlersModuleWithExtraInstances extends
		AbstractModule {
	
	private Set<IndexingItemHandler> handlers;

	public TestIndexHandlersModuleWithExtraInstances(Set<IndexingItemHandler> handlers) {
		this.handlers = handlers;
	}

	@Override
	protected void configure() {
		Multibinder<IndexingItemHandler> handlerconf = Multibinder.newSetBinder(binder(), IndexingItemHandler.class);
		IndexingHandlers.configureFirst(handlerconf);
		for (IndexingItemHandler h : handlers) {
			handlerconf.addBinding().toInstance(h);
		}
		IndexingHandlers.configureLast(handlerconf);
	}

}
