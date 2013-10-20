/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.testing.indexing;

import static org.junit.Assert.*;

import org.junit.Test;

import se.repos.testing.indexing.TestIndexOptions;

public class TestIndexOptionsTest {

	@Test
	public void testCores() {
		TestIndexOptions o = new TestIndexOptions()
			.addCore("mycore", "se/my/core/**")
			.addCore("myothercore", "se/my/other/**");
		assertTrue(o.hasCore("myothercore"));
	}
	
	@Test
	public void testDefaults() {
		TestIndexOptions o = new TestIndexOptions().itemDefaults();
		assertTrue(o.hasCore("repositem"));
	}

}
