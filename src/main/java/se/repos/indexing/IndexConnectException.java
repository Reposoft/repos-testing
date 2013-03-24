package se.repos.indexing;

import java.io.IOException;

public class IndexConnectException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public IndexConnectException(IOException e) {
		super(e);
	}

}
