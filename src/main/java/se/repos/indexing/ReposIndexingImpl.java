package se.repos.indexing;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
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
import se.simonsoft.cms.item.properties.CmsItemProperties;
import se.simonsoft.cms.testing.svn.CmsTestRepository;

public class ReposIndexingImpl implements ReposIndexing {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private SolrServer repositem;
	private CmsChangesetReader changesetReader;
	
	// TODO this service should be per-repository to allow different indexers
	// but for now it can be both, as the only extra complexity is handling in memory the latest revision indexed
	
	private Map<CmsRepository, RepoRevision> scheduledHighest = new HashMap<CmsRepository, RepoRevision>();

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
	 * Polls indexing status, forwards indexing task to {@link #sync(CmsRepositoryInspection, CmsChangesetReader, Iterable)}
	 * 
	 * 
	 */
	@Override
	public void sync(CmsRepository repository, RepoRevision revision) {
		logger.info("Sync requested {} rev {}", repository, revision);
		
		/*
		At large sync operations, do we run all blocking indexing first and then all background, or do we need more sophistication?
		Do we completely rule out other ongoing tasks than those executed by this instance?
		How do we handle indexing errors so we don't index that revision again and again?
		
		 */
		
		if (!scheduledHighest.containsKey(repository)) {
			logger.info("Unknown index completeion status for repository {}. Polling.", repository);
			RepoRevision c = getIndexedRevisionHighestCompleted(repository);
			RepoRevision pl = getIndexedRevisionLowestStarted(repository);
			RepoRevision ph = getIndexedRevisionHighestStarted(repository);
			logger.info("Completed {}, progress from {} to {}", new Object[]{c, pl, ph});
			// for now the simplest solution is to assume that all in-progress operations have actually completed
			//if (pl != null) {
			//	logger.warn("Index contains unfinished revisions between {} and {} at first sync. Reindexing those.", pl, ph);
			//}
			scheduledHighest.put(repository, ph);
		}
		
		// running may be null if everything is completed
		List<RepoRevision> range = new LinkedList<RepoRevision>();
		RepoRevision r = scheduledHighest.get(repository);
		if (r == null) {
			logger.debug("No revision status in index. Starting from 0.");
			r = new RepoRevision(0, null);
			range.add(r);
		}
		while(r.getNumber() < revision.getNumber() - 1) {
			r = new RepoRevision(revision.getNumber() + 1, null);
			range.add(r);
		}
		range.add(revision);
		logger.debug("Index range: {}", range);
		
		// run
		scheduledHighest.put(repository, revision);
		if (repository instanceof CmsRepositoryInspection) {
			sync((CmsRepositoryInspection) repository, changesetReader, range);
		} else {
			throw new AssertionError("Admin repository instance required for indexing. Got " + repository.getClass());
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
			if (rev.getNumber() == 0) {
				logger.debug("Revprops indexing for rev 0 not implemented"); // changeset reader couldn't handle 0
				indexRevStart(repository, rev, null).run();
				continue;
			}
			logger.debug("Reading revision {} from repository {}", rev, repository);
			CmsChangeset changeset = changesets.read(repository, rev);
			index(repository, changeset);
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
			logger.debug("Indexing item {}", item);
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
		// end revision
		onComplete.run();
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
		return indexRevStart(repository, revision, null);
	}
	
	Runnable indexRevStart(CmsRepository repository, RepoRevision revision, CmsItemProperties revprops) {
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
	
	protected String escape(String fieldValue) {
		return fieldValue.replaceAll("([:^\\(\\)!~/ ])", "\\\\$1");
	}
	
	protected String quote(String fieldValue) {
		return '"' + fieldValue.replace("\"", "\\\"") + '"';
	}
	
	protected RepoRevision getIndexedRevisionHighestCompleted(CmsRepository repository) {
		return getIndexedRevision(repository, "true", ORDER.desc);
	}
	
	protected RepoRevision getIndexedRevisionHighestStarted(CmsRepository repository) {
		return getIndexedRevision(repository, "false", ORDER.desc);
	}
	
	protected RepoRevision getIndexedRevisionLowestStarted(CmsRepository repository) {
		return getIndexedRevision(repository, "false", ORDER.asc);
	}
	
	private RepoRevision getIndexedRevision(CmsRepository repository, String valComplete, ORDER order) {
		SolrQuery query = new SolrQuery("type:commit AND complete:" + valComplete + " AND id:" + escape(getIdRepository(repository)) + "*");
		query.setRows(1);
		query.setFields("rev", "revt");
		query.setSort("rev", order); // the timestamp might be in a different order in svn, if revprops or loading has been used irregularly
		QueryResponse resp;
		try {
			resp = repositem.query(query);
		} catch (SolrServerException e) {
			throw new IndexWriteException(e);
		}
		SolrDocumentList results = resp.getResults();
		if (results.getNumFound() == 0) {
			return null;
		}
		SolrDocument r = results.get(0);
		Long rev = (Long) r.getFieldValue("rev");
		Date revt = (Date) r.getFieldValue("revt");
		return new RepoRevision(rev, revt);
	}
	
	String getId(CmsRepository repository, RepoRevision revision, CmsItemPath path) {
		return repository.getHost() + repository.getUrlAtHost() + (path == null ? "" : path) + "@" + getIdRevision(revision); 
	}

	String getIdRepository(CmsRepository repository) {
		return repository.getHost() + repository.getUrlAtHost() + "#";
	}	
	
	String getIdCommit(CmsRepository repository, RepoRevision revision) {
		return getIdRepository(repository) + getIdRevision(revision);
	}
	
	private String getIdRevision(RepoRevision revision) {
		return Long.toString(revision.getNumber());
	}
	
	@Override
	public RepoRevision getRevComplete(CmsRepository repository) {
		return scheduledHighest.get(repository); // not handling progress for now
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
