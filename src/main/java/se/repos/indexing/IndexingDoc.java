package se.repos.indexing;

public interface IndexingDoc {

	/**
	 * Supports incremental adding to multivalue fields.
	 * @param name
	 * @param value
	 */
	public void addField(String name, Object value);
	
	public void setField(String name, Object value);
	
	public Object getFieldValue(String name);	
	
}
