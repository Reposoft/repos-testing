/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.cmstest;

import org.tmatesoft.svn.core.wc.admin.SVNLookClient;

import se.simonsoft.cms.backend.svnkit.CmsRepositorySvn;
import se.simonsoft.cms.backend.svnkit.svnlook.CmsChangesetReaderSvnkitLookRepo;
import se.simonsoft.cms.backend.svnkit.svnlook.CmsContentsReaderSvnkitLookRepo;
import se.simonsoft.cms.backend.svnkit.svnlook.CmsRepositoryLookupSvnkitLook;
import se.simonsoft.cms.backend.svnkit.svnlook.CommitRevisionCache;
import se.simonsoft.cms.backend.svnkit.svnlook.CommitRevisionCacheDefault;
import se.simonsoft.cms.backend.svnkit.svnlook.SvnlookClientProviderStateless;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;
import se.simonsoft.cms.item.inspection.CmsChangesetReader;
import se.simonsoft.cms.item.inspection.CmsContentsReader;
import se.simonsoft.cms.testing.svn.CmsTestRepository;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

class SingleRepoSvnkitIndexingModule extends AbstractModule {

	private CmsRepositorySvn repository;

	public SingleRepoSvnkitIndexingModule(CmsTestRepository repository) {
		this(CmsRepositorySvn.fromTesting(repository));
	}

	public SingleRepoSvnkitIndexingModule(CmsRepositorySvn repository) {
		this.repository = repository;
	}	
	
	@Override
	protected void configure() {
		bind(CmsRepository.class).toInstance(repository);
		bind(CmsRepositorySvn.class).toInstance(repository);
		
		bind(SVNLookClient.class).toProvider(SvnlookClientProviderStateless.class);
		bind(CmsChangesetReader.class).to(CmsChangesetReaderSvnkitLookRepo.class);
		bind(CommitRevisionCache.class).to(CommitRevisionCacheDefault.class);
		bind(CmsContentsReader.class).to(CmsContentsReaderSvnkitLookRepo.class);
		bind(CmsRepositoryLookup.class).annotatedWith(Names.named("inspection")).to(CmsRepositoryLookupSvnkitLook.class); // deprecated, should not be in user-level context at all
		bind(CmsRepositoryLookup.class).to(CmsRepositoryLookupSvnkitLook.class);
	}

}
