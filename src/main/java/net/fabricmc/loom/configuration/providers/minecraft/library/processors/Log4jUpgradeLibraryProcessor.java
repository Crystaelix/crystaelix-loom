package net.fabricmc.loom.configuration.providers.minecraft.library.processors;

import java.util.function.Consumer;
import java.util.function.Predicate;

import net.fabricmc.loom.configuration.providers.minecraft.library.Library;
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryContext;
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryProcessor;
import net.fabricmc.loom.util.Platform;

/**
 * Upgrade version of log4j used in 13w39a to 17w15a from 2.0-beta9 to 2.5.
 */
public class Log4jUpgradeLibraryProcessor extends LibraryProcessor {
	private static final String LOG4J_GROUP = "org.apache.logging.log4j";
	private static final String LOG4J_VERSION_OLD = "2.0-beta9";
	private static final String LOG4J_VERSION = "2.5";

	public Log4jUpgradeLibraryProcessor(Platform platform, LibraryContext context) {
		super(platform, context);
	}

	@Override
	public ApplicationResult getApplicationResult() {
		return context.isLog4jBeta() ? ApplicationResult.MUST_APPLY : ApplicationResult.CAN_APPLY;
	}

	@Override
	public Predicate<Library> apply(Consumer<Library> dependencyConsumer) {
		return library -> {
			if (library.is(LOG4J_GROUP) && library.name().startsWith("log4j")) {
				dependencyConsumer.accept(library.withVersion(LOG4J_VERSION));
			}

			return true;
		};
	}
}
