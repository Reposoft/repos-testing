/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.cmstest;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import javax.inject.Provider;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.io.SVNRepository;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.name.Names;

import se.repos.testing.ReposTestBackend;
import se.simonsoft.cms.backend.svnkit.CmsRepositorySvn;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;
import se.simonsoft.cms.testing.svn.CmsTestRepository;

/**
 * Actual svn repository with cms-backend-svnkit as backend.
 */
public class ReposTestBackendCmsTestingSvn implements ReposTestBackend {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	
	// test may run as different user than svn so we might need to override umask
	private static final String MKFIFO_OPTIONS = " --mode=0666";	
	//private static final String MKFIFO_OPTIONS = " -m 0666"; // BSD / macOS does not support "--mode".	
	
	private final CmsRepositorySvn repository;
	private final Provider<SVNRepository> svnkitProvider;

	public ReposTestBackendCmsTestingSvn(CmsTestRepository repo) {
		this(CmsRepositorySvn.fromTesting(repo), repo.getSvnkitProvider());
	}

	public ReposTestBackendCmsTestingSvn(CmsRepositorySvn repo, Provider<SVNRepository> svnkitProvider) {
		this.repository = repo;
		this.svnkitProvider = svnkitProvider;
	}	
	
	@Override
	public Module getConfiguration() {
		return new SingleRepoSvnkitIndexingModule(repository, svnkitProvider);
	}

	@Override
	public void activate(Injector context, HookInvocation postcommit) {
		CmsRepositoryLookup lookup = context.getInstance(Key.get(CmsRepositoryLookup.class, Names.named("inspection")));
		HookInvocationLookupProxy hooks = new HookInvocationLookupProxy(repository, lookup, postcommit);
		installHooks(repository, hooks);
		syncHead(repository, lookup, hooks);
	}

	void syncHead(CmsRepository repository, CmsRepositoryLookup lookup, HookInvocationLookupProxy hooks) {
		RepoRevision head = lookup.getYoungest(repository);
		if (head.getNumber() > 0) {
			logger.debug("Repository's revision is {} at enable", head);
			hooks.hasPreloaded(head);
		}
	}
	
	private void installHooks(CmsRepositorySvn repository, final HookInvocationLookupProxy hooks) {
		File repositoryLocalPath = repository.getAdminPath();
		File hooksdir = new File(repositoryLocalPath, "hooks");
		if (!hooksdir.exists()) {
			throw new IllegalArgumentException("No hooks folder found in repository path " + repositoryLocalPath);
		}
		if (!hooksdir.canWrite()) {
			throw new IllegalArgumentException("Hooks folder not writable at " + hooksdir);
		}
		File postCommitSh = new File(hooksdir, "post-commit");
		try {
			postCommitSh.createNewFile();
		} catch (IOException e) {
			throw new RuntimeException("Failed to create post-commit script " + postCommitSh, e);
		}
		postCommitSh.setExecutable(true, false);
		
		// Set up named pipe file for communication
		final File pipe = new File(hooksdir, "post-commit.pipe");
		createPipe(pipe);
		
		// Set up hook that writes revision number to named pipe
		try {
			FileWriter hookbridge = new FileWriter(postCommitSh);
			hookbridge.write("#!/bin/sh\n");
			hookbridge.write("echo $2 > " + pipe.getAbsolutePath() + "\n");
			hookbridge.write("cat " + pipe.getAbsolutePath() + " >&2\n");			
			hookbridge.close();
		} catch (IOException e) {
			throw new RuntimeException("Failed to write to post-commit hook " + postCommitSh, e);
		}
		
		// Listen to hook calls
		Thread t = new Thread() {
			@Override
			public void run() {
				while (true) {
					logger.info("Starting hook wait at {}", pipe);
					runRevision();
					logger.info("Hook wait loop ended for {}", pipe);
				}
			}
			
			void runRevision() {
				logger.trace("Awaiting next revision");
				Long revision = null;
				BufferedReader r = null;
				try {
					r = new BufferedReader(new FileReader(pipe));
					String echoed = r.readLine();
					try {
						revision = Long.parseLong(echoed);
					} catch (NumberFormatException e) {
						logger.error("Invalid revision number from hook, got '" + echoed + "'", e);
					}
				} catch (IOException e) {
					logger.error("Reading from hook pipe failed", e);
					throw new RuntimeException("Aborting because of hook communication error at reading", e);
				} finally {
					if (r != null) {
						try {
							r.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
				try {
					hooks.postCommit(revision);
					logger.trace("Hook handler complete, writing confirmation to {}", pipe);
				} finally {
					BufferedWriter w = null;
					try {
						w = new BufferedWriter(new FileWriter(pipe));
						w.write("Test indexing completed revision " + revision + "\n");
					} catch (IOException e) {
						logger.error("Writing to hook pipe failed", e);
						throw new RuntimeException("Aborting because of hook communication error at confirmation", e);
					} finally {
						if (w != null) {
							try {
								w.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
					logger.debug("Revision {} hook completed for {}", revision, pipe);
				}
			}
		};
	    t.start();
	}

	protected void createPipe(final File pipe) {
		final String pipecmd = "mkfifo" + MKFIFO_OPTIONS;
		try {
			Process exec = Runtime.getRuntime().exec(pipecmd + " " + pipe.getAbsolutePath());
			InputStream err = exec.getErrorStream();
			IOUtils.copy(err, System.out);
		} catch (IOException e) {
			throw new RuntimeException("Failed to create named pipe using command line " + pipecmd, e);
		}
	}
	
	class HookInvocationLookupProxy {
		
		private CmsRepository repository;
		private CmsRepositoryLookup revisionDateLookup;
		private HookInvocation actual;

		HookInvocationLookupProxy(CmsRepository repository, CmsRepositoryLookup revisionDateLookup, ReposTestBackend.HookInvocation actual) {
			this.repository = repository;
			this.revisionDateLookup = revisionDateLookup;
			this.actual = actual;
		}
		
		void hasPreloaded(RepoRevision rev) {
			actual.hasPreloaded(repository, rev);
		}
		
		void postCommit(long rev) {
			Date revtime = revisionDateLookup.getRevisionTimestamp(repository, rev);
			RepoRevision revision = new RepoRevision(rev, revtime);
			actual.postCommit(repository, revision);
		}
		
	}
	
}
