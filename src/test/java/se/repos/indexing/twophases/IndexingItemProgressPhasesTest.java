package se.repos.indexing.twophases;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Test;

public class IndexingItemProgressPhasesTest {

	@Test
	public void testSetPhase() {
		IndexingDocIncrementalSolrj doc = mock(IndexingDocIncrementalSolrj.class);
		IndexingItemProgressPhases progress = new IndexingItemProgressPhases(null, null, null, doc);
		progress.setPhase(IndexingItemProgressPhases.Phase.update);
		verify(doc).setUpdateMode(true);
	}

}
