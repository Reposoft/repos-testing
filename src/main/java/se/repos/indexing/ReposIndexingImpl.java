package se.repos.indexing;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.repos.indexing.item.IndexingItemHandler;
import se.repos.indexing.item.IndexingItemProgress;
import se.simonsoft.cms.admin.CmsChangesetReader;
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.events.change.CmsChangeset;
import se.simonsoft.cms.item.events.change.CmsChangesetItem;
import se.simonsoft.cms.item.inspection.CmsRepositoryInspection;
import se.simonsoft.cms.testing.svn.CmsTestRepository;

public class ReposIndexingImpl implements ReposIndexing {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private SolrServer repositem;
	private CmsChangesetReader changesetReader;
	
	private Map<CmsRepository, RepoRevision> running = new HashMap<CmsRepository, RepoRevision>();
	private Map<CmsRepository, RepoRevision> completed = new HashMap<CmsRepository, RepoRevision>();

	private Iterable<IndexingItemHandler> itemBlocking = new LinkedList<IndexingItemHandler>();
	private Iterable<IndexingItemHandler> itemBackground = new LinkedList<IndexingItemHandler>();
	
	@Inject
	public void setSolrRepositem(@Named("repositem") SolrServer repositem) {
		this.repositem = repositem;
	}
	
	@Inject
	public void setCmsChangesetReader(CmsChangesetReader changesetReader) {
		this.changesetReader = changesetReader;
	}
	
	@Inject
	public void setItemBlocking(@Named("blocking") Iterable<IndexingItemHandler> handlersSync) {
		this.itemBlocking = handlersSync;
	}
	
	@Inject
	public void setItemBackground(@Named("background") Iterable<IndexingItemHandler> handlersAsync) {
		this.itemBackground = handlersAsync;
	}
	
	/**
	 * Polls indexing status, forwards indexing task to {@link #sync(CmsRepositoryInspection, CmsChangesetReader, RepoRevision)}.
	 */
	@Override
	public void sync(CmsRepository repository, RepoRevision revision) {
		if (completed.containsKey(repository)) {
			
		} else {
			logger.info("Indexing status unknown for repository {}. Polling.");
			completed.put(repository, null);
		}
		
				

		
		
		// end of changeset indexing (i.e. after all background work too)

		try {
			repositem.commit();
		} catch (SolrServerException e) {
			throw new RuntimeException("error not handled", e);
		} catch (IOException e) {
			throw new RuntimeException("error not handled", e);
		}
	}
	
	/**
	 * Handles indexing status.
	 * @param repository
	 * @param changesets
	 * @param toRevision
	 */
	void sync(CmsRepositoryInspection repository, CmsChangesetReader changesets, Iterable<RepoRevision> range) {
		for (RepoRevision rev : range) {
			running.put(repository, rev);
			
			
		}
	}
	
	/**
	 * Runs without validation of status.
	 * @param repository
	 * @param changeset
	 */
	void index(CmsRepository repository, CmsChangeset changeset) {
		Runnable onComplete = indexRevStart(repository, changeset);
		// we may want to extract an item visitor pattern from indexing to generic hook processing
		List<CmsChangesetItem> items = changeset.getItems();
		for (final CmsChangesetItem item : items) {
			// TODO buffer, chose strategy depending on file size
			CmsChangesetItemVisit visit = new CmsChangesetItemVisit() {
				@Override
				public CmsChangesetItem getItem() {
					return item;
				}
				@Override
				public InputStream getContents() {
					// TODO Auto-generated method stub
					return null;
				}
			};
			
		}
		
	}
	
	void indexItemVisit(CmsChangesetItemVisit itemVisit) {
		
	}
	
	void indexItemSync() {
		
	}
	
	void indexItemBackground() {
		
	}
	
	/**
	 * Index only revprops and only for a single revision, helper to avoid reindex after revprops.
	 * @param repository
	 * @param changeset
	 */
	public void indexRevprops(CmsRepositoryInspection repository, RepoRevision revision) {
		CmsChangeset changeset = changesetReader.read(repository, revision);
		Runnable onComplete = indexRevStart(repository, changeset);
		onComplete.run();
	}

	/**
	 * Index a revision and end with a complete=false status so that items can be indexed.
	 * @param repository
	 * @param changeset
	 * @return task to execute when all indexing for this revision is completed
	 */
	Runnable indexRevStart(CmsRepository repository, CmsChangeset changeset) {
		RepoRevision revision = changeset.getRevision();
		String id = getIdCommit(repository, revision);
		SolrInputDocument docStart = new SolrInputDocument();
		docStart.addField("id", id);
		docStart.addField("type", "commit");
		docStart.addField("rev", getIdRevision(revision));
		docStart.addField("complete", false);
		try {
			repositem.add(docStart);
		} catch (SolrServerException e) {
			throw new IndexWriteException(e);
		} catch (IOException e) {
			throw new IndexConnectException(e);
		}
		return new RunRevComplete(id);
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
		return completed.get(repository);
	}

	@Override
	public RepoRevision getRevProgress(CmsTestRepository repo) {
		return null;
	}

	class RunRevComplete implements Runnable {

		// http://mail-archives.apache.org/mod_mbox/lucene-solr-user/201209.mbox/%3C7E0464726BD046488B66D661770F9C2F01B02EFF0C@TLVMBX01.nice.com%3E
		@SuppressWarnings("serial")
		final Map<String, Boolean> partialUpdateToTrue = new HashMap<String, Boolean>() {{
			put("set", true);
		}};
		
		private String id;
		
		private RunRevComplete(String id) {
			this.id = id;
		}
		
		@Override
		public void run() {
			SolrInputDocument docComplete = new SolrInputDocument();
			docComplete.addField("id", id);
			docComplete.setField("complete", partialUpdateToTrue);
			try {
				repositem.add(docComplete);
			} catch (SolrServerException e) {
				throw new RuntimeException("error not handled", e);
			} catch (IOException e) {
				throw new RuntimeException("error not handled", e);
			}
		}
		
	}
	
	class RunBackgroundIndexing implements Runnable {

		public RunBackgroundIndexing(Iterable<IndexingItemHandler> indexers, IndexingItemProgress item) {
			
		}
		
		@Override
		public void run() {
			// TODO Auto-generated method stub
			
		}
		
	}
	
}
