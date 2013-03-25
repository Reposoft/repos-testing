package se.repos.indexing;

public interface IndexingDocIncremental extends IndexingDoc {

	/**
	 * Activates the "partial update" mode for all subsequent calls to {@link #addField(String, Object)} and {@link #setField(String, Object)},
	 * letting callers avoid the cumbersome solrj syntax for that.
	 * Note that both set and add has the same effect as in the original solrj API,
	 * but that the underlying impl knows if the fields are already set or not.
	 * @param fieldSetIsPartialUpdate
	 */
	public void setUpdateMode(boolean fieldSetIsPartialUpdate);	
	
}
