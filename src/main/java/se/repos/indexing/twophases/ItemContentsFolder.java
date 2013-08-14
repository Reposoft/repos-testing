package se.repos.indexing.twophases;

import java.io.InputStream;

import se.repos.indexing.item.ItemContentsBuffer;

public class ItemContentsFolder implements ItemContentsBuffer {

	@Override
	public InputStream getContents() {
		throw new IllegalStateException("Contents retrieval attempted on a folder");
	}

}
