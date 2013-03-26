package se.repos.indexing.twophases;

import se.repos.indexing.item.IndexingItemHandler;

public class IndexingItemHandlerRunnable implements Runnable {

	private IndexingItemHandler handler;
	private IndexingItemProgressPhases progress;

	public IndexingItemHandlerRunnable(IndexingItemHandler handler,
			IndexingItemProgressPhases progress) {
		this.handler = handler;
		this.progress = progress;
	}

	@Override
	public void run() {
		handler.handle(progress);
	}

}
