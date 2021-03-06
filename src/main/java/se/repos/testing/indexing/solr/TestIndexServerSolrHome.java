/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing.solr;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import se.repos.testing.indexing.TestCore;
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
		Iterable<TestCore> cores = options.getCores();
		for (TestCore c : cores) {
			File coreFolder = new File(instanceDir, c.getIdentifier() + "/");
			if (coreFolder.exists()) {
				logger.debug("Found existing core folder {}", coreFolder);
				onCoreExisting(coreFolder);
			}
			coreFolder.mkdir();
			extractResourceFolder(c.getResourcePattern(), coreFolder);
			logger.debug("Test server core {} added at {}", c, coreFolder);
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
		if (instanceDir == null) {
			logger.debug("Cleanup skipped because instanceDir is null");
			return;
		}
		if (instanceDir.exists()) {
			try {
				FileUtils.deleteDirectory(instanceDir);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} else {
			logger.debug("Cleanup not needed because instanceDir " + instanceDir + " does not exist");
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
		if (resources == null) {
			throw new IllegalArgumentException("Failed to resolve resources with pattern " + pattern);
		}
		if (resources.length == 0) {
			throw new IllegalArgumentException("No resources found in extraction pattern " + pattern);
		}
		logger.debug("Resource pattern {} matched {}", pattern, resources);
		SortedMap<String, Resource> extract = new TreeMap<String, Resource>();
		for (Resource r : resources) {
            String full = r.getDescription().replace("\\", "/");
			int relpos = full.indexOf(path);
			if (relpos < 0) {
				throw new IllegalStateException("Expected path not found in extracted resource " + r);
			}
			String rel = full.substring(relpos + path.length(), full.length() - 1);
			extract.put(rel, r);
		}
		for (String rel : extract.keySet()) {
			Resource r = extract.get(rel);
			try {
				File outfile = new File(destination, rel);
				// don't assume that entries come in order
				if (outfile.getParentFile() != null) {
				    outfile.getParentFile().mkdirs();
				}
				if (r.getDescription().endsWith("/]")) { // only some folders look like this but those are the ones that will produce files instead of folders
					outfile.mkdir();
					logger.debug("Detected folder {}, created {}", rel, outfile);
				}
	            // try to detect folders, can't use getFile because that throws exception for classpath resources
//	            if (r.getFile() != null && r.getFile().isDirectory()) {
//	            	outfile.mkdir();
//	            	continue;
//	            }
				InputStream in = r.getInputStream();
				FileOutputStream out = new FileOutputStream(outfile);
				IOUtils.copy(in, out);
				in.close();
				out.close();
				logger.debug("Extracted file {} to {} from {}", rel, outfile, r.getURL());
			} catch (Exception e) {
				if (e.getMessage().endsWith("(Is a directory)")) {
					logger.debug("Ignoring suspected folder {} due to error {}", r.getDescription(), e.toString());
					continue;
				}
				logger.warn("Failed to extract {} at {}", r.getDescription(), rel, e);
			}
		}
	}
}