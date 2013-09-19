/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Test;

import se.simonsoft.cms.backend.filexml.FilexmlAccess;
import se.simonsoft.cms.backend.filexml.FilexmlAccessClasspath;
import se.simonsoft.cms.backend.filexml.FilexmlCommit;
import se.simonsoft.cms.backend.filexml.FilexmlRepository;
import se.simonsoft.cms.item.CmsRepository;

public class SvnTestIndexingFileBackendTest {

	@After
	public void tearDown() {
		SvnTestIndexing.getInstance().tearDown(); // TODO make static and set up + tear down only once?
	}
	
	@Test
	public void testReadonly() {
		CmsRepository repo = new CmsRepository("http://localhost/svn/t");
		FilexmlAccess access = new FilexmlAccessClasspath("se/repos/testing/indexing/datasets/test1");
		FilexmlRepository repository = new FilexmlRepository(repo, access);
		
		TestIndexOptions options = new TestIndexOptions().itemDefaults();
		SvnTestIndexing.getInstance();
	}

}
