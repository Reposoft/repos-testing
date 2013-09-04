/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing.solr;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import se.repos.testing.indexing.TestIndexOptions;

/**
 * Base class for servers using a conventional Solr 4.3.1+ "solr.home" folder.
 * Creates a config file and extracts the cores in a standard solr stucture.
 */
public abstract class TestIndexServerSolrHome {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private static final String HOMECONFIG = "se/repos/indexing/solr/testing-home/**";
	
	/**
	 * Creates the cores and configuration needed for lookup.
	 * @return
	 */
	protected File createHome() {
		File instanceDir;
		try {
			instanceDir = File.createTempFile("testindexing-", ".dir");
		} catch (IOException e) {
			throw new RuntimeException("not handled", e);
		}
		instanceDir.delete();
		instanceDir.mkdir();
		logger.debug("Test server instance dir is {}", instanceDir);
		extractResourceFolder(HOMECONFIG, instanceDir);
		return instanceDir;
	}
	
	File createHomeWithCores(TestIndexOptions options) {
		if (options.hasCoreAliases()) {
			throw new IllegalStateException("core aliases not supported, yet");
		}
		File instanceDir = createHome();
		Map<String, String> cores = options.getCores();
		for (String c : cores.keySet()) {
			File coreFolder = new File(instanceDir, c);
			if (coreFolder.exists()) {
				onCoreExisting(coreFolder);
			}
			coreFolder.mkdir();
			extractResourceFolder(cores.get(c), coreFolder);
			logger.debug("Test server core {} added", c);
			onCoreCreated(coreFolder);
		}
		return instanceDir;
	}
	
	protected void onCoreExisting(File coreFolder) {
		throw new UnsupportedOperationException("Reuse of existing solr.home is not supported."); // Need to implement use of aliases
	}
	
	protected void onCoreCreated(File coreFolder) {
		logger.debug("No core reload needed for embedded");
	}
	
	void destroy(File instanceDir) {
		try {
			FileUtils.deleteDirectory(instanceDir);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Copy recursively from classpath to filesystem.
	 * @param pattern an ant pattern starting with a folder path relative to classpath root
	 * @param destination existing and usually empty folder to extract to
	 */
	protected void extractResourceFolder(String pattern, File destination) {
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
			if (extract.get(rel).toString().endsWith("/]")) { // detect folder entries
				if (!outfile.exists() && !outfile.mkdir()) {
					logger.warn("Failed to create target folder {} ({})", outfile, extract.get(rel));
				}
				logger.trace("Created folder {} ({})", rel, extract.get(rel));
				continue;
			}
			logger.trace("Extracting file {} ({})", rel, extract.get(rel));
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
