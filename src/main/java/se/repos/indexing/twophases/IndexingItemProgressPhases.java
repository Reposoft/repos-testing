package se.repos.indexing.twophases;

import java.io.InputStream;

import se.repos.indexing.IndexingDoc;
import se.repos.indexing.item.IndexingItemProgress;
import se.repos.indexing.item.ItemContentsBuffer;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.events.change.CmsChangesetItem;
import se.simonsoft.cms.item.properties.CmsItemProperties;

public class IndexingItemProgressPhases implements IndexingItemProgress {

	enum Phase {
		initial,
		update
	}
	
	private CmsRepository repository;
	private RepoRevision revision;
	private CmsChangesetItem item;
	private IndexingDocIncrementalSolrj fields;
	private CmsItemProperties properties;
	private ItemContentsBuffer contents;

	public IndexingItemProgressPhases(CmsRepository repository, RepoRevision revision,
			CmsChangesetItem item, IndexingDocIncrementalSolrj fields) {
		this.repository = repository;
		this.revision = revision;
		this.item = item;
		this.fields = fields;
	}
	
	public void setPhase(Phase phase) {
		switch (phase) {
		case initial:
			throw new IllegalArgumentException("Can't switch to " + phase);
		case update:
			setPhaseUpdate();
		}
	}
	
	private void setPhaseUpdate() {
		fields.setUpdateMode(true);
	}

	public IndexingItemProgressPhases setProperties(CmsItemProperties itemVersionedMetadata) {
		this.properties = itemVersionedMetadata;
		return this;
	}
	
	public IndexingItemProgressPhases setContents(ItemContentsBuffer buffer) {
		this.contents = buffer;
		return this;
	}
	
	@Override
	public CmsRepository getRepository() {
		return repository;
	}

	@Override
	public RepoRevision getRevision() {
		return revision;
	}

	@Override
	public IndexingDoc getFields() {
		return fields;
	}

	@Override
	public CmsChangesetItem getItem() {
		return item;
	}

	@Override
	public CmsItemProperties getProperties() {
		if (properties == null) {
			throw new UnsupportedOperationException("indexing of properties is not supported in this phase");
		}
		return properties;
	}

	@Override
	public InputStream getContents() {
		if (contents == null) {
			throw new UnsupportedOperationException("Item contents not available in this indexing phase");
		}
		return contents.getContents();
	}

}
