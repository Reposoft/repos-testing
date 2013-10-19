/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing.solr;

import java.io.File;
import java.io.IOException;

import org.apache.solr.client.solrj.SolrServer;

import se.repos.lgr.Lgr;
import se.repos.lgr.LgrFactory;
import se.repos.restclient.HttpStatusError;
import se.repos.restclient.ResponseHeaders;
import se.repos.restclient.RestClient;
import se.repos.restclient.RestResponseBean;
import se.repos.restclient.RestURL;
import se.repos.restclient.javase.RestClientJavaNet;
import se.repos.testing.indexing.TestIndexOptions;
import se.repos.testing.indexing.TestIndexServer;

/**
 * Uses the example webapp from Solr distribution for dynamically adding cores, removing existing.
 * 
 * This example webapp is assumed to be for test purposes only, so existing cores are deleted and created cores are kept after test,
 * with the benefit that indexed content can be inspected manually.
 */
public class TestIndexServerSolrJettyExample extends TestIndexServerSolrHome implements TestIndexServer {

	private static final Lgr logger = LgrFactory.getLogger();

	/**
	 * After designing this class I found in https://wiki.apache.org/solr/CoreAdmin that cores can be created from any path,
	 * so we could probably use the setup that base class provides, and just use MOVE if a core of the same name already exists.
	 * We could even use SWAP back and forth, so that the original core is restored after test.
	 * 
	 * We can also specify, at CREATE, which config and schema to use so we won't need to extract core folders.
	 */
	public static final String[] TRY_PATHS = {
		"/home/cmsadmin/testsolr/example/solr",
		"/opt/testsolr/example/solr",
		System.getProperty("user.home") + "/Downloads/solr-4.5.0/example/solr",
		System.getProperty("user.home") + "/Downloads/solr-4.4.0/example/solr"
	};
	
	public static final String[] TRY_URLS = {
		"http://localhost:18983/solr/",
		"http://localhost:8983/solr/"
	};

	static File trySolrParentPaths() {
		StringBuffer tried = new StringBuffer();
		for (String p : TRY_PATHS) {
			tried.append(", ").append(p);
			File f = new File(p);
			if (f.exists() && f.isDirectory() && f.canWrite()) {
				return f;
			}
		}
		logger.info("Solr test setup failed because none of these paths were found: " + tried.substring(2));
		return null;
	}	
	
	static String trySolrUrls() {
		StringBuffer tried = new StringBuffer();
		for (String u : TRY_URLS) {
			tried.append(", ").append(u);
			if (isHttpUrlSolrRoot(u)) return u;
		}
		logger.info("Solr test setup failed because none of these URLS responded: " + tried.substring(2));
		return null;
	}
	
	static boolean isHttpUrlSolrRoot(String httpUrl) {
		RestURL restUrl = new RestURL(httpUrl);
		RestClient restClientJavaNet = new RestClientJavaNet(restUrl.r(), null);
		ResponseHeaders head;
		try {
			head = restClientJavaNet.head(restUrl.p());
		} catch (java.net.ConnectException ec) {
			logger.debug("Rejecting URL", restUrl, "due to connection error:", ec.toString());
			return false;
		} catch (IOException e) {
			throw new RuntimeException("Svn test setup failed", e);
		}
		if (head.getStatus() != 200 && head.getStatus() != 401) {
			logger.debug("Rejecting URL", restUrl, "due to status", head.getStatus());
			return false;
		}
		logger.debug("URL", restUrl, "ok with content type", head.getContentType());
		return true;
	}
	
	public static TestIndexServer locate() {
		File path = trySolrParentPaths();
		if (path == null) {
			return null;
		}
		logger.debug("Found Solr example server structure at {}", path);
		String url = trySolrUrls();
		if (url == null) {
			return null;
		}
		return new TestIndexServerSolrJettyExample(path, url);
	}

	private File home;
	private String url;
	
	public TestIndexServerSolrJettyExample(File path, String url) {
		this.home = path;
		this.url = url;
	}
	
	void coreDelete(String coreName) {
		coreUnload(coreName);
		
	}
	
	void coreUnload(String coreName) {
		// https://wiki.apache.org/solr/CoreAdmin
		RestURL restUrl = new RestURL(url + "admin/cores?action=UNLOAD&core=" + coreName + "&deleteInstanceDir=true");
		RestClient restClientJavaNet = new RestClientJavaNet(restUrl.r(), null);
		RestResponseBean response = new RestResponseBean();
		try {
			restClientJavaNet.get(restUrl.p(), response);
		} catch (HttpStatusError e) {
			logger.error("Core unload failed using URL " + restUrl + " " + response.getBody(), e);
			// see if we can proceed without unload
			//throw new RuntimeException("Core reload failed using URL " + restUrl + " " + response.getBody(), e);
		} catch (IOException e) {
			throw new RuntimeException("Solr communication failed at core unload using URL " + restUrl + " " + response.getBody(), e);
		}
	}
	
	void coreLoad(String coreName) {
		// https://wiki.apache.org/solr/CoreAdmin
		
		RestURL restUrl = new RestURL(url + "admin/cores?action=CREATE&name=" + coreName);
		RestClient restClientJavaNet = new RestClientJavaNet(restUrl.r(), null);
		RestResponseBean response = new RestResponseBean();
		try {
			restClientJavaNet.get(restUrl.p(), response);
		} catch (HttpStatusError e) {
			throw new RuntimeException("Core reload failed using URL " + restUrl + " " + response.getBody(), e);
		} catch (IOException e) {
			throw new RuntimeException("Core reload failed using URL " + restUrl + " " + response.getBody(), e);
		}
		if (response.getHeaders().getStatus() != 200) {
			throw new AssertionError("Failed to create test core " + coreName + " " + response.getBody());
		};
	}
	
	@Override
	protected File createHome() {
		return home;
	}

	@Override
	protected void onCoreExisting(File coreFolder) {
		logger.debug("Deleting existing core {} ({})", coreFolder.getName(), coreFolder);
		coreUnload(coreFolder.getName());
		if (coreFolder.exists()) {
			logger.error("Solr failed to delete instanceDir " + coreFolder);
		}
	}

	@Override
	protected void onCoreCreated(File coreFolder) {
		File props = new File(coreFolder, "core.properties");
		if (props.delete()) { // Solr 4.5.0 won't create a core when this exists
			logger.debug("Deleted core.propeties as preparatoin for create");
		} else {
			throw new RuntimeException("Failed to delete " + props);
		}
		coreLoad(coreFolder.getName());
	}

	@Override
	public void beforeTest(TestIndexOptions options) {
		createHomeWithCores(options);
	}

	@Override
	public SolrServer getCore(String identifier) {
		return new se.repos.indexing.solrj.HttpSolrServerNamed(getCoreUrl(identifier)).setName(identifier);
	}
	
	@Override
	public String getCoreUrl(String identifier) {
		return url + identifier;
	}

	@Override
	public void destroy() {
		// TODO Auto-generated method stub
		
	}
	
}
