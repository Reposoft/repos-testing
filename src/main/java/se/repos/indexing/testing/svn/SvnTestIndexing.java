package se.repos.indexing.testing.svn;

import org.apache.solr.client.solrj.SolrServer;

import se.repos.indexing.testing.TestIndexOptions;
import se.repos.indexing.testing.TestIndexServer;
import se.repos.indexing.testing.solr.TestIndexServerSolrEmbedded;
import se.repos.search.SearchReposItem;
import se.simonsoft.cms.testing.svn.CmsTestRepository;


public class SvnTestIndexing {

	private static SvnTestIndexing instance = null;
	
	/**
	 * Current options, not thread safe of course.
	 */
	private TestIndexOptions options = null;

	private TestIndexServer server = null;
	
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
		instance.beforeTest(options); // we assume that getInstance is called before each test
		return instance;
	}
	
	protected void beforeTest(TestIndexOptions options) {
		this.options = options;
		this.server = new TestIndexServerSolrEmbedded();
		this.server.beforeTest(this.options);
	}
	
	/**
	 * @return Search abstraction for basic queries in the "repositem" core.
	 */
	public SearchReposItem getItem() {
		throw new UnsupportedOperationException("not implemented"); // needs a SearchReposItem impl
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
				

		
		return this;
	}
	
	public void tearDown() {
		instance.server.destroy();
		// repository and hook is removed by SvnTestSetup.tearDown
		instance.options = null;
		instance.server = null;
	}
	
}
