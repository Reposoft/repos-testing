package se.repos.testing;

import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;

import com.google.inject.Injector;
import com.google.inject.Module;

public interface ReposTestBackend {

	Module getConfiguration();

	/**
	 * Install hooks in existing repositories and run {@link HookInvocation#hasPreloaded(RepoRevision)} for already revisions already loaded.
	 * @param context Configured services, including those from {@link #getConfiguration()}
	 * @param postcommit To be called future revision
	 */
	void activate(Injector context, HookInvocation postcommit);

	/**
	 * Backends are responsible for invoking all hooks in this interface on corresponding events.
	 */
	interface HookInvocation {
		
		void hasPreloaded(CmsRepository repository, RepoRevision revision);
		
		void postCommit(CmsRepository repository, RepoRevision revision);
		
	}
}
