package se.repos.indexing.testconfig;

import org.apache.solr.client.solrj.SolrServer;

import se.repos.indexing.ReposIndexing;
import se.repos.indexing.ReposIndexingImpl;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class IndexingTestModule extends AbstractModule {

	private SolrServer repositem;

	public IndexingTestModule(SolrServer solrRepositemCore) {
		this.repositem = solrRepositemCore;
	}
	
	@Override
	protected void configure() {
		bind(SolrServer.class).annotatedWith(Names.named("repositem"))
			.toInstance(repositem);
		
		bind(ReposIndexing.class).to(ReposIndexingImpl.class);
	}

}
