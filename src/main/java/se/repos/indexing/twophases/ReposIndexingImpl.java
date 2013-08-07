package se.repos.indexing.twophases;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

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

import se.repos.indexing.CmsChangesetItemVisit;
import se.repos.indexing.IndexConnectException;
import se.repos.indexing.IndexWriteException;
import se.repos.indexing.ReposIndexing;
import se.repos.indexing.item.IndexingItemHandler;
import se.repos.indexing.item.IndexingItemProgress;
import se.repos.indexing.item.ItemContentsBuffer;
import se.repos.indexing.twophases.IndexingItemProgressPhases.Phase;
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.events.change.CmsChangeset;
import se.simonsoft.cms.item.events.change.CmsChangesetItem;
import se.simonsoft.cms.item.inspection.CmsChangesetReader;
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

	protected Executor getExecutorBlocking() {
		return new BlockingExecutor();
	}	
	
	/**
	 * For now we run everything as blocking, to simplify things.
	 * @param handler
	 * @return
	 */
	protected Executor getExecutorBackground() {
		return new BlockingExecutor();
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
		
		/*
		To facilitate fast indexing of HEAD-only content it would be useful to get repo "tree" with revisions from HEAD
		as reference when reading changeset, and setting a flag if we know that an item is not current HEAD.
		When sync is complete, that information should be removed and upcoming revisions should be handled as HEAD.
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
		// TODO find the proper revision dates because those are indexed, needed in SvnTestIndexing too
		List<RepoRevision> range = new LinkedList<RepoRevision>();
		RepoRevision r = scheduledHighest.get(repository);
		if (r == null) {
			logger.debug("No revision status in index. Starting from 0.");
			r = new RepoRevision(0, null);
		}
		for (long i = r.getNumber(); i <= revision.getNumber(); i++) {
			range.add(new RepoRevision(i, null));
		}
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
		logger.info("Indexing revision {} with blocking handlers {} and background handlers {}", new Object[]{changeset.getRevision(), itemBlocking, itemBackground});
		Runnable onComplete = indexRevStart(repository, changeset);
		
		// TODO update existing docs affected by changeset to head=false,
		//  unless in HEAD-referenced changeset mode (typically reindex) where docs get head=false at first indexing
		
		// we may want to extract an item visitor pattern from indexing to generic hook processing
		List<CmsChangesetItem> items = changeset.getItems();
		for (final CmsChangesetItem item : items) {
			logger.debug("Indexing item {}", item);
			indexItemVisit(repository, changeset.getRevision(), item);
			
		}
		// TODO as long as our only executor is #getExecutorBlocking() we can run the end handler here
		// when we have background we need to run it at the end of each changeset, after all items have completed in background
		// This means we need to pass the executors to the item visit above
		onComplete.run();
		
		// guess we need different commit strategies for resync and post-commit indexing, but lets commit every revision for now
		try {
			repositem.commit();
		} catch (SolrServerException e) {
			throw new IndexWriteException(e);
		} catch (IOException e) {
			throw new IndexConnectException(e);
		}
		// guess we also need an optimize strategy
	}

	/**
	 * Item Visit is the future event handling concept for single items.
	 * Needs either access to an "inspection" backed CmsItem,
	 * with the drawback that it provides contents as OutputStream,
	 * or to {@link CmsChangesetItem}, item properties buffer {@link CmsItemProperties}, {@link ItemContentsBuffer}.
	 */
	void indexItemVisit(CmsChangesetItemVisit itemVisit) {
	}
	
	/**
	 * Alternative from our local changeset iteration.
	 * @param progress
	 */
	private void indexItemVisit(CmsRepository repository, RepoRevision revision, CmsChangesetItem item) {
		indexItemMarkPrevious(repository, revision, item);
		
		IndexingDocIncrementalSolrj doc = new IndexingDocIncrementalSolrj();
		// TODO handle contents, buffer, chose strategy depending on file size
		IndexingItemProgressPhases progress = new IndexingItemProgressPhases(repository, revision, item, doc);	
		
		doc.addField("head", true); // TODO adapt to cms cahngeset mode, in reindex (with HEAD reference) we could index as non-head immediately
		
		Executor blocking = getExecutorBlocking();
		indexItemProcess(blocking, progress, itemBlocking);
		solrAdd(doc.getSolrDoc());
		
		progress.setPhase(Phase.update);
		// for simplicity, continue using the same executor service
		// we need status reporting before we start fiddling with background
		// Note that onComplete needs to run after each changeset if we start running in background
		// This method should probably move back to index
		indexItemProcess(blocking, progress, itemBackground);
		solrAdd(doc.getSolrDoc());
		// TODO run the end handler after all items
	}
	
	private void indexItemMarkPrevious(CmsRepository repository, RepoRevision revision, CmsChangesetItem item) {
		CmsItemPath path = item.getPath();
		// TODO this is just a test that we can update records, both path and revision may be different however
		String query = repository.getHost() + repository.getUrlAtHost() + (path == null ? "" : path) + "@" + (revision.getNumber() - 1); // From ItemPathInfo
		IndexingDocIncrementalSolrj mark = new IndexingDocIncrementalSolrj();
		mark.addField("id", query);		
		mark.setUpdateMode(true);
		mark.setField("head", false);
		solrAdd(mark.getSolrDoc());
	}
	
	/**
	 * Runs all indexing handlers in a phase using the same executor service.
	 * @param executor {@link ExecutorService}
	 * @param progress 
	 * @param handler
	 */
	void indexItemProcess(Executor executor, IndexingItemProgress progress, Iterable<IndexingItemHandler> handlers) {
		for (IndexingItemHandler handler : handlers) {
			Runnable r = new IndexingItemHandlerRunnable(handler, progress);
			executor.execute(r);
		}		
	}
	
	/**
	 * Index only revprops and only for a single revision; helper to avoid reindex after revprops.
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
		solrAdd(docStart);
		return new RunRevComplete(id);
	}

	protected void solrAdd(SolrInputDocument doc) {
		if (doc == IndexingDocIncrementalSolrj.UPDATE_MODE_NO_CHANGES) {
			logger.warn("Index add was attempted in update mode but no changes have been made");
			return;
		}
		try {
			repositem.add(doc);
		} catch (SolrServerException e) {
			throw new IndexWriteException(e);
		} catch (IOException e) {
			throw new IndexConnectException(e);
		}
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
	
	/* moved to ItemPathinfo
	String getId(CmsRepository repository, RepoRevision revision, CmsItemPath path) {
		return repository.getHost() + repository.getUrlAtHost() + (path == null ? "" : path) + "@" + getIdRevision(revision); 
	}
	*/

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
			solrAdd(docComplete);
		}
		
	}
	
}
