package se.repos.indexing;

import se.simonsoft.cms.item.RepoRevision;

/**
 * What's the type of reporting needed?
 *  - Service initialized
 *  - Sync requested (method?)
 *  - 
 *
 *
 *
 * It is useful to be able to reindex all the easily processed data quickly.
 * TODO in a long sync operation background queue becomes extremely long.
 * Should we instead query index for next rev to handle in background, and index phase?
 */
public interface IndexingState {

	/**
	 * @return true if index is complete, disregarding a single in-progress revision that is ==HEAD
	 */
	boolean isIndexComplete();
	
	/**
	 * @return like {@link #isIndexComplete()} but requires only structure and item properties to be indexed
	 */
	boolean isIndexCompleteProps();
	
	void initializing(RepoRevision existingVerified, RepoRevision existingOverwriteFrom);

	void syncRequest(RepoRevision revision);
	
	void blockingBegin(RepoRevision revision);
	
	void blockingComplete(RepoRevision revision);
	
	void backgroundBegin(RepoRevision revision);
	
	void backgroundPaused();
	
	void backgroundResumed();
	
	void backgroundComplete(RepoRevision revision);

}
