package se.repos.indexing.testing.solr;

import java.io.File;

import se.repos.indexing.testing.TestIndexOptions;

/**
 * Base class for servers using a conventional Solr 4.3.1+ "solr.home" folder.
 */
public abstract class TestIndexServerSolrHome {

	/**
	 * Creates the cores and configuration needed for lookup.
	 * @param options
	 * @return
	 */
	File setUpHome(TestIndexOptions options) {
		if (options.hasCoreAliases()) {
			throw new IllegalStateException("core aliases not supported, yet");
		}
		return null;
	}
	
}
