package se.repos.indexing.twophases;

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.Map;

import org.apache.solr.common.SolrInputDocument;
import org.junit.Test;

public class IndexingDocIncrementalSolrjTest {

	@Test
	public void testAddField() {
		IndexingDocIncrementalSolrj doc = new IndexingDocIncrementalSolrj();
		doc.setField("id", "test@1");
		doc.setField("fb", false);
		doc.addField("f1", 1L);
		doc.addField("f1", 2L);
		assertEquals(false, doc.getFieldValue("fb"));
		Collection<Object> f1 = doc.getFieldValues("f1");
		assertEquals("addField should append multi values", 2, f1.size());
		assertTrue(doc.containsKey("f1"));
		assertTrue("clone should contain all fields", doc.deepCopy().containsKey("f1"));
		assertTrue("clone should contain all fields", doc.deepCopy().containsKey("fb"));
		doc.setField("fb", true);
		assertEquals("setField should overwrite", true, doc.getFieldValue("fb"));
		doc.setField("f1", 3L);
		assertEquals("setField should remove old multiValue values", 3L, doc.getFieldValue("f1"));
		doc.setUpdateMode(true);
		assertNotNull("clone should be allowed after update mode is changed to true", doc.deepCopy());
		doc.setField("fb", false);
		assertEquals("field value should still be retrievable in normal form after update mode is switched on, for use from other indexers",
				false, doc.getFieldValue("fb"));
		Object fb = doc.getSolrDoc().getFieldValue("fb");
		assertTrue("In updated mode new fields should use 'set' syntax, got " + fb.getClass(), fb instanceof Map);
		@SuppressWarnings({ "unchecked", "rawtypes" })
		Map<String, Object> fbset = (Map) fb;
		assertEquals("solrj partial update syntax", 1, fbset.size());
		assertEquals("solrj partial update syntax", "set", fbset.keySet().iterator().next());
		assertEquals(false, fbset.get("set"));
		SolrInputDocument updateDoc = doc.getSolrDoc();
		assertTrue("solr doc should contain updated fields", updateDoc.containsKey("fb"));
		assertFalse("after update the solr doc should not contain unchanged values", updateDoc.containsKey("f1"));
		assertEquals("doc should always contain id", "test@1", updateDoc.getFieldValue("id"));
		assertTrue("solr doc should have the partial update syntax", updateDoc.getFieldValue("fb") instanceof Map);
		try {
			doc.deepCopy();
			fail("Expecting deepCopy to fail after field update until we have decided what to do with update fields");
		} catch (UnsupportedOperationException e) {
			// expected
		}
		doc.setField("fx", "new");
		assertTrue("new fields since update mode true was set should go into next solr doc", doc.getSolrDoc().containsKey("fx"));
		// TODO assertEquals("fields that do not exist in solr, should they get the update syntax?", "new", doc.getFieldValue("fx"));		
		doc.addField("f1", 4L);
		// TODO Collection<Object> f1u = doc.getFieldValues("f1");
		//assertTrue("Should have the value from before update", f1u.contains(3L));
		//assertTrue("Should have the value from after update", f1u.contains(4L));
		Collection<Object> f1solr = doc.getSolrDoc().getFieldValues("f1");
		assertEquals("Solr doc should only have the value from after update", 1, f1solr.size());
		assertTrue("The value should be a partial update", f1solr.iterator().next() instanceof Map);
		@SuppressWarnings("rawtypes")
		Map f1update = (Map) f1solr.iterator().next();
		assertEquals(4L, f1update.values().iterator().next());
		assertEquals("Solr add syntax", "add", f1update.keySet().iterator().next());
	}

	@Test
	public void testUpdateModeNoChanges() {
		IndexingDocIncrementalSolrj doc = new IndexingDocIncrementalSolrj();
		doc.setField("id", "test@2");
		doc.setField("x", "y");
		doc.setUpdateMode(true);
		SolrInputDocument nochanges = doc.getSolrDoc();
		assertEquals("Can not have any fields if there are no updates because solr would overwrite the id", nochanges.size());
		assertTrue("Solr can't handle empty document so we should specifically identify this case", nochanges == IndexingDocIncrementalSolrj.UPDATE_MODE_NO_CHANGES);
	}
	
}
