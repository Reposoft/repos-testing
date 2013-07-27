package se.repos.indexing.testing.svn;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.CoreDescriptor;
import org.apache.solr.core.SolrCore;
import org.apache.solr.core.SolrResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import se.repos.indexing.testing.TestIndexOptions;
import se.repos.search.SearchReposItem;
import se.simonsoft.cms.testing.svn.CmsTestRepository;


public class SvnTestIndexing {

	private static SvnTestIndexing instance = null;
	
	/**
	 * Current options, not thread safe of course.
	 */
	private TestIndexOptions options = null;
	
	/**
	 * Enforce singleton, makes optimizations possible.
	 */
	private SvnTestIndexing() {
	}
	
	/**
	 * Instead of constructing the object in each test run, this allows reuse of solr setup.
	 * The instances are not threadsafe however, so tests should run in sequence.
	 * @return
	 */
	public static SvnTestIndexing getInstance() {
		return getInstance(new TestIndexOptions().itemDefaults());
	}
	
	public static SvnTestIndexing getInstance(TestIndexOptions options) {
		if (instance == null) {
			instance = new SvnTestIndexing();
		}
		instance.options = options;
		return instance;
	}
	
	/**
	 * @return Search abstraction for basic queries in the "repositem" core.
	 */
	public SearchReposItem getItem() {
		
		return null;
	}
	
	/**
	 * Loads or clears a solr core from {@link #getInstance(TestIndexOptions)}.
	 * To get live index data in tests a call to {@link #enable(CmsTestRepository)} is needed first.
	 * @param identifier our internal core name, though maybe suffixed in Solr
	 * @return direct Solr access to the core
	 */
	public SolrServer getCore(String identifier) {
		if (!options.hasCore(identifier)) {
			throw new IllegalArgumentException("Core '" + identifier + "' not found in test cores " + options.getCores().keySet());
		}
		return null; // TODO use TestIndexServer and either load or clear
	}
	
	/**
	 * Enable blocking hook call from a test repository,
	 * using index handlers from {@link #getInstance(TestIndexOptions)}.
	 * @param repo A repository
	 * @return for chaining, suggest
	 */
	public SvnTestIndexing enable(CmsTestRepository repo) {
				
		// Everything below is a big mess because we're investigating solr instantiation		
		
		// Trying to figure out how to load a solr core.
		//  - No manual copying or linking of core
		//  - Probably needs to support multiple cores some day
		//  - Tests run from modules that depend on repos-indexing as jar
		
		// http://lucene.apache.org/solr/4_4_0/solr-core/index.html?org/apache/solr/client/solrj/embedded/EmbeddedSolrServer.html
		// http://lucene.apache.org/solr/4_4_0/solr-core/org/apache/solr/core/CoreContainer.html
		// http://lucene.apache.org/solr/4_4_0/solr-core/org/apache/solr/core/SolrResourceLoader.html
		File instanceDir;
		try {
			instanceDir = File.createTempFile("testindexing-", ".dir");
		} catch (IOException e) {
			throw new RuntimeException("not handled", e);
		}
		instanceDir.delete();
		instanceDir.mkdir();
		System.out.println("Instance dir is " + instanceDir);
		SolrResourceLoader resourceLoader = new SolrResourceLoader(instanceDir.getAbsolutePath());
		CoreContainer coreContainer = new CoreContainer(resourceLoader);
		
		// from our SolrTestCaseJ4 concept
		String solrhome = "se/repos/indexing/solr";
		String config = solrhome + "/repositem/conf/solrconfig.xml";
		String schema = solrhome + "/repositem/conf/schema.xml";
		String testSolrHome = "src/test/resources/" + solrhome; // has to be in classpath because "collection1" is hardcoded in TestHarness initCore/createCore

		// https://code.google.com/p/gbif-occurrencestore/source/browse/occurrence/trunk/occurrence-index-builder/src/main/java/org/gbif/occurrence/index/hadoop/EmbeddedSolrServerBuilder.java
		CoreDescriptor repositemDescriptor = new CoreDescriptor(coreContainer, /*props*/ null);
		SolrCore repositem = new SolrCore("repositem", repositemDescriptor);
		EmbeddedSolrServer solrServer = new EmbeddedSolrServer(coreContainer, "-multicore-");
		
		// TestHarness used in SolrTestCaseJ4 has been rewritten in 4.4.0
		// http://svn.apache.org/repos/asf/lucene/dev/tags/lucene_solr_4_4_0/solr/test-framework/src/java/org/apache/solr/util/TestHarness.java
		// Now uses SolrConfig solrConfig, IndexSchema indexSchema - though only to do getResourceName()
		
		// http://grokbase.com/t/lucene/solr-user/10am1t4xff/embeddedsolrserver-with-one-core-and-schema-xml-loaded-via-classloader-is-it-possible
		
		return this;
	}
	
	public void extractRepositem(File destination) {
		extractCore("se/repos/indexing/solr/repositem/**", destination);
	}	
	
	public void extractCore(String pattern, File destination) {
		int pathlen = pattern.indexOf('*');
		String path = pattern.substring(0, pathlen > 0 ? pathlen : pattern.length());
		PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
		Resource[] resources;
		try {
			resources = resolver.getResources("classpath*:" + pattern);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		SortedMap<String, Resource> extract = new TreeMap<String, Resource>();
		for (Resource r : resources) {
			String full = r.getDescription();
			int relpos = full.indexOf(path);
			if (relpos < 0) {
				throw new IllegalStateException("Expected path not found in extracted resource " + r);
			}
			String rel = full.substring(relpos + path.length(), full.length() - 1);
			extract.put(rel, r);
		}
		for (String rel : extract.keySet()) {
			File outfile = new File(destination, rel);
			InputStream in;
			try {
				in = extract.get(rel).getInputStream();
			} catch (IOException e) {
				if (e.getMessage().contains("(Is a directory")) {
					outfile.mkdir();
					continue;
				}
				throw new RuntimeException(e);
			}
			FileOutputStream out;
			try {
				out = new FileOutputStream(outfile);
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
			try {
				IOUtils.copy(in, out);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			try {
				in.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			try {
				out.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}	
	
}
