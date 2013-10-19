/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing;

import java.util.Set;

import se.repos.indexing.IndexingItemHandler;
import se.repos.indexing.Marker;
import se.repos.indexing.item.IndexingItemProgress;

public class MarkerNodeps implements Marker {

	private Marker actual;

	public MarkerNodeps(Marker actual) {
		this.actual = actual;
	}

	@Override
	public void handle(IndexingItemProgress progress) {
		actual.handle(progress);
	}

	@Override
	public Set<Class<? extends IndexingItemHandler>> getDependencies() {
		return actual.getDependencies();
	}

	@Override
	public void trigger() {
		actual.trigger();
	}

	@Override
	public void ignore() {
		actual.ignore();
	}

	@Override
	public String toString() {
		return "(nodeps)" + actual.toString();
	}

}
