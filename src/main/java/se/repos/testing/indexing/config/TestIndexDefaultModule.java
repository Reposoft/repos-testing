/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing.config;

import se.repos.indexing.IdStrategy;
import se.repos.indexing.IndexAdmin;
import se.repos.indexing.ReposIndexing;
import se.repos.indexing.item.IdStrategyDefault;
import se.repos.indexing.item.ItemContentBufferStrategy;
import se.repos.indexing.item.ItemPropertiesBufferStrategy;
import se.repos.indexing.repository.IndexAdminPerRepositoryRepositem;
import se.repos.indexing.repository.ReposIndexingPerRepository;
import se.repos.indexing.scheduling.IndexingSchedule;
import se.repos.indexing.scheduling.IndexingScheduleBlockingOnly;
import se.repos.indexing.twophases.ItemContentsMemory;
import se.repos.indexing.twophases.ItemPropertiesImmediate;

import com.google.inject.AbstractModule;

/**
 * Indexing configuration for our tests in this module.
 * See also the distributed standard config for unit tests, {@link TestIndexOptions#getIndexing()}.
 */
public class TestIndexDefaultModule extends AbstractModule {
	
	@Override
	protected void configure() {
		bind(IndexingSchedule.class).to(IndexingScheduleBlockingOnly.class);
		
		bind(IndexAdmin.class).to(IndexAdminPerRepositoryRepositem.class);
		bind(ReposIndexing.class).to(ReposIndexingPerRepository.class);
		
		bind(IdStrategy.class).to(IdStrategyDefault.class);
		bind(ItemPropertiesBufferStrategy.class).to(ItemPropertiesImmediate.class);
		bind(ItemContentBufferStrategy.class).to(ItemContentsMemory.class);
	}

}
