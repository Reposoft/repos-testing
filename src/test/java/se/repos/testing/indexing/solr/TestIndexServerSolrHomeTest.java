package se.repos.testing.indexing.solr;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import se.repos.testing.indexing.TestIndexOptions;
import se.repos.testing.indexing.solr.TestIndexServerSolrHome;

public class TestIndexServerSolrHomeTest {

	@Test
	public void testExtractResourceFolder() throws IOException {
		TestIndexServerSolrHome server = new TestIndexServerSolrHome() {
		};
		File test = null;
		try {
			test = File.createTempFile("test-" + this.getClass().getName(), "");
			test.delete();
			test.mkdir();
			server.extractResourceFolder("se/repos/indexing/solr/repositem/**", test);
			for (String s : new String[]{"core.properties", "conf/schema.xml", "conf/solrconfig.xml"}) {
				assertTrue("shold extract " + s, new File(test, s).exists());
			}
		} finally {
			FileUtils.deleteDirectory(test);
		}
	}
	
	@Test
	public void testCreateHome() {
		TestIndexServerSolrHome server = new TestIndexServerSolrHome() {
		};
		File solrhome = server.createHome();
		assertTrue("Should create solr config file", new File(solrhome, "solr.xml").exists());
		server.destroy(solrhome);
	}
	
	@Test
	public void testCores() {
		TestIndexOptions options = new TestIndexOptions().itemDefaults();
		TestIndexServerSolrHome server = new TestIndexServerSolrHome() {
		};
		File solrhome = server.createHomeWithCores(options);
		assertTrue("Should create solr config file", new File(solrhome, "solr.xml").exists());
		assertTrue("Should create core folder", new File(solrhome, "repositem").isDirectory());
		assertTrue("Should extract core.properties", new File(solrhome, "repositem/core.properties").exists());
		assertTrue("Should create core subfolders", new File(solrhome, "repositem/conf").isDirectory());
		server.destroy(solrhome);
	}

}
