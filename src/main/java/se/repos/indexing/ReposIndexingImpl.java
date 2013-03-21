package se.repos.indexing;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;

import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;

public class ReposIndexingImpl implements ReposIndexing {

	private SolrServer repositem;

	private Map<CmsRepository, RepoRevision> complete = new HashMap<CmsRepository, RepoRevision>();
	
	@Inject
	public void setSolrRepositem(@Named("repositem") SolrServer repositem) {
		this.repositem = repositem;
	}
	
	@Override
	public void sync(CmsRepository repository, RepoRevision revision) {
		SolrInputDocument docStart = new SolrInputDocument();
		docStart.addField("id", getIdCommit(repository, revision));
		docStart.addField("type", "commit");
		docStart.addField("rev", getIdRevision(revision));
		docStart.addField("inprogress", true);
		try {
			repositem.add(docStart);
		} catch (SolrServerException e) {
			throw new RuntimeException("error not handled", e);
		} catch (IOException e) {
			throw new RuntimeException("error not handled", e);
		}
		complete.put(repository, revision);
		
		
		// end of changeset indexing (i.e. after all background work too)
		SolrInputDocument docComplete = new SolrInputDocument();
		docComplete.addField("id", docStart.getFieldValue("id"));
		// http://mail-archives.apache.org/mod_mbox/lucene-solr-user/201209.mbox/%3C7E0464726BD046488B66D661770F9C2F01B02EFF0C@TLVMBX01.nice.com%3E
		@SuppressWarnings("serial")
		Map<String, Boolean> partialUpdateToFalse = new HashMap<String, Boolean>() {{
			put("set", false);
		}};
		docComplete.setField("inprogress", partialUpdateToFalse);
		try {
			repositem.add(docComplete);
		} catch (SolrServerException e) {
			throw new RuntimeException("error not handled", e);
		} catch (IOException e) {
			throw new RuntimeException("error not handled", e);
		}
		try {
			repositem.commit();
		} catch (SolrServerException e) {
			throw new RuntimeException("error not handled", e);
		} catch (IOException e) {
			throw new RuntimeException("error not handled", e);
		}
	}

	String getId(CmsRepository repository, RepoRevision revision, CmsItemPath path) {
		return repository.getHost() + repository.getUrlAtHost() + (path == null ? "" : path) + "@" + getIdRevision(revision); 
	}
	
	String getIdCommit(CmsRepository repository, RepoRevision revision) {
		return repository.getHost() + repository.getUrlAtHost() + "#" + getIdRevision(revision);
	}
	
	private String getIdRevision(RepoRevision revision) {
		return Long.toString(revision.getNumber());
	}
	
	@Override
	public RepoRevision getRevComplete(CmsRepository repository) {
		return complete.get(repository);
	}

}
