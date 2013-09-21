/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.cmstest;

import org.tmatesoft.svn.core.wc.admin.SVNLookClient;

import se.simonsoft.cms.backend.svnkit.svnlook.CmsChangesetReaderSvnkitLook;
import se.simonsoft.cms.backend.svnkit.svnlook.CmsContentsReaderSvnkitLook;
import se.simonsoft.cms.backend.svnkit.svnlook.CmsRepositoryLookupSvnkitLook;
import se.simonsoft.cms.backend.svnkit.svnlook.CommitRevisionCache;
import se.simonsoft.cms.backend.svnkit.svnlook.CommitRevisionCacheDefault;
import se.simonsoft.cms.backend.svnkit.svnlook.SvnlookClientProviderStateless;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;
import se.simonsoft.cms.item.inspection.CmsChangesetReader;
import se.simonsoft.cms.item.inspection.CmsContentsReader;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

class SingleRepoSvnkitIndexingModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(SVNLookClient.class).toProvider(SvnlookClientProviderStateless.class);
		bind(CmsChangesetReader.class).to(CmsChangesetReaderSvnkitLook.class);
		bind(CommitRevisionCache.class).to(CommitRevisionCacheDefault.class);
		bind(CmsContentsReader.class).to(CmsContentsReaderSvnkitLook.class);
		bind(CmsRepositoryLookup.class).annotatedWith(Names.named("inspection")).to(CmsRepositoryLookupSvnkitLook.class);
	}

}
