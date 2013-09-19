package se.repos.testing;

import se.repos.testing.indexing.SvnTestIndexing.PostCommitInvocation;

import com.google.inject.Injector;
import com.google.inject.Module;

public interface ReposTestBackend {

	Module getConfiguration();

	/**
	 * Install hooks in existing repositories and run them for already loaded revisions.
	 * @param context Configured services, including those from {@link #getConfiguration()}
	 * @param postcommit To be called for every present and future revision
	 */
	void activate(Injector context, PostCommitInvocation postcommit);
	
}
