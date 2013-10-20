/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing.config;

import java.util.Set;

import se.repos.indexing.IndexingItemHandler;

import com.google.inject.multibindings.Multibinder;

public class TestIndexHandlersModuleWithExtraInstances extends
		TestIndexHandlersModule {
	
	private Set<IndexingItemHandler> handlers;

	public TestIndexHandlersModuleWithExtraInstances(Set<IndexingItemHandler> handlers) {
		this.handlers = handlers;
	}

	@Override
	protected void configureExtra(Multibinder<IndexingItemHandler> handlerconf) {
		for (IndexingItemHandler h : handlers) {
			handlerconf.addBinding().toInstance(h);
		}
	}

}
