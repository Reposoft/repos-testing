package se.repos.indexing.twophases;

import java.util.concurrent.Executor;

public class BlockingExecutor implements Executor {

	@Override
	public void execute(Runnable command) {
		command.run();
	}

}
