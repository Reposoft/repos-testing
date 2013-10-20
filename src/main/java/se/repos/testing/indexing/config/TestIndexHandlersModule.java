/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing.config;

import se.repos.indexing.IndexingHandlers;
import se.repos.indexing.IndexingItemHandler;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

/**
 * Makes it rather easy to add handlers to the middle of the chain,
 * but a custom module for handlers might often be a better option.
 */
public class TestIndexHandlersModule extends AbstractModule {

	@Override
	protected void configure() {
		Multibinder<IndexingItemHandler> handlerconf = Multibinder.newSetBinder(binder(), IndexingItemHandler.class);
		IndexingHandlers.configureFirst(handlerconf);
		configureExtra(handlerconf);
		IndexingHandlers.configureLast(handlerconf);
	}

	/**
	 * Can add handlers in the middle of the chain, where content is enabled.
	 */
	protected void configureExtra(Multibinder<IndexingItemHandler> handlerconf) {
	}

}
