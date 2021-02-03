/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.cmstest;

import se.simonsoft.cms.backend.svnkit.CmsRepositorySvn;
import se.simonsoft.cms.backend.svnkit.info.CmsRepositoryLookupSvnkit;
import se.simonsoft.cms.backend.svnkit.info.change.CmsChangesetReaderSvnkit;
import se.simonsoft.cms.backend.svnkit.info.change.CmsContentsReaderSvnkit;
import se.simonsoft.cms.backend.svnkit.info.change.CommitRevisionCache;
import se.simonsoft.cms.backend.svnkit.info.change.CommitRevisionCacheRepo;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;
import se.simonsoft.cms.item.inspection.CmsChangesetReader;
import se.simonsoft.cms.item.inspection.CmsContentsReader;
import se.simonsoft.cms.testing.svn.CmsTestRepository;

import javax.inject.Provider;

import org.tmatesoft.svn.core.io.SVNRepository;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

class SingleRepoSvnkitIndexingModule extends AbstractModule {

	private final CmsRepositorySvn repository;
	private final Provider<SVNRepository> svnkitProvider;

	/*
	public SingleRepoSvnkitIndexingModule(CmsTestRepository repository) {
		this(CmsRepositorySvn.fromTesting(repository));
	}
	*/

	public SingleRepoSvnkitIndexingModule(CmsRepositorySvn repository, Provider<SVNRepository> svnkitProvider) {
		this.repository = repository;
		this.svnkitProvider = svnkitProvider;
	}	
	
	@Override
	protected void configure() {
		bind(CmsRepository.class).toInstance(repository);
		bind(CmsRepositorySvn.class).toInstance(repository);
		
		// Need to inject SVNRepository now when no are no longer using svnlook.
		bind(SVNRepository.class).toInstance(svnkitProvider.get());
		
		bind(CmsChangesetReader.class).to(CmsChangesetReaderSvnkit.class);
		bind(CommitRevisionCache.class).to(CommitRevisionCacheRepo.class);
		bind(CmsContentsReader.class).to(CmsContentsReaderSvnkit.class);
		bind(CmsRepositoryLookup.class).annotatedWith(Names.named("inspection")).to(CmsRepositoryLookupSvnkit.class); // deprecated, should not be in user-level context at all
		bind(CmsRepositoryLookup.class).to(CmsRepositoryLookupSvnkit.class);
	}

}
