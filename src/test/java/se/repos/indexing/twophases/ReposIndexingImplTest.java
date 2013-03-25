package se.repos.indexing.twophases;

import static org.junit.Assert.*;

import java.util.Date;
import java.util.Iterator;

import org.junit.Test;

import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;

public class ReposIndexingImplTest {

	@Test
	public void testGetId() {
		CmsRepository repo = new CmsRepository("http://host.name/svn/repo");
		RepoRevision rev = new RepoRevision(123, new Date(123456));
		ReposIndexingImpl impl = new ReposIndexingImpl();
		// always index hostname, useful for resolving URLs
		// don't do a root marker etc, there'll be repo fileds for parent, name etc
		// use numeric revision if available, shorter and better uniqueness
		assertEquals("host.name/svn/repo/dir@123", impl.getId(repo, rev, new CmsItemPath("/dir")));
		assertEquals("repo root", "host.name/svn/repo@123", impl.getId(repo, rev, null));
		assertEquals("commit ids should not be confused with root items", "host.name/svn/repo#123", impl.getIdCommit(repo, rev));
		assertEquals("repository ids are not used directly but useful for query on commit status", "host.name/svn/repo#", impl.getIdRepository(repo));
	}
	
}
