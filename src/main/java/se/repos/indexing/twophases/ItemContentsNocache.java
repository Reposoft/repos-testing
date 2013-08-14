package se.repos.indexing.twophases;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.repos.indexing.IndexingDoc;
import se.repos.indexing.item.ItemContentsBuffer;
import se.repos.indexing.item.ItemContentsBufferStrategy;
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.inspection.CmsContentsReader;
import se.simonsoft.cms.item.inspection.CmsRepositoryInspection;

public class ItemContentsNocache implements ItemContentsBufferStrategy {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private CmsContentsReader reader;
	
	@Inject
	public ItemContentsNocache setCmsContentsReader(CmsContentsReader reader) {
		this.reader = reader;
		return this;
	}
	
	@Override
	public ItemContentsBuffer getBuffer(CmsRepositoryInspection repository,
			RepoRevision revision, CmsItemPath path, IndexingDoc pathinfo) {
		Long size = (Long) pathinfo.getFieldValue("size");
		if (size == null) {
			logger.warn("Lacking size information for item");
		}
		return new BufferMinimizeMemoryUse(repository, revision, path);
	}	
	
	/**
	 * Unless we adapt to different file sizes we need this kind of buffer.
	 */
	public class BufferMinimizeMemoryUse implements ItemContentsBuffer {

		private CmsRepositoryInspection repository;
		private RepoRevision revision;
		private CmsItemPath path;

		public BufferMinimizeMemoryUse(CmsRepositoryInspection repository,
				RepoRevision revision, CmsItemPath path) {
			this.repository = repository;
			this.revision = revision;
			this.path = path;
		}

		@Override
		public InputStream getContents() {
			// Found
			// http://ostermiller.org/convert_java_outputstream_inputstream.html
			// https://code.google.com/p/io-tools/wiki/Tutorial_EasyStream
			// But we'd better wait until we start adapting to file size
			File tempfile;
			try {
				tempfile = File.createTempFile("repos-indexing-contents-buffer", ".tmp");
			} catch (IOException e2) {
				throw new IllegalStateException("Failed to produce temp file destination for contents buffer");
			}
			tempfile.deleteOnExit(); // TODO does this work? Can we get notified on stream close? Do we reuse the file for subsequent reads?
			OutputStream out;
			try {
				out = new FileOutputStream(tempfile);
			} catch (FileNotFoundException e1) {
				throw new IllegalStateException("Failed to produce temp file destination for contents buffer");
			}
			reader.getContents(repository, revision, path, out);
			try {
				return new FileInputStream(tempfile);
			} catch (FileNotFoundException e) {
				throw new IllegalStateException("Failed to produce readable input from temp file");
			}
		}
		
	}
	
}
