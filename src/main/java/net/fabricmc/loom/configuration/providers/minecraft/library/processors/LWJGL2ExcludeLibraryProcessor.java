package net.fabricmc.loom.configuration.providers.minecraft.library.processors;

import java.util.function.Consumer;
import java.util.function.Predicate;

import net.fabricmc.loom.configuration.providers.minecraft.library.Library;
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryContext;
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryProcessor;
import net.fabricmc.loom.util.Platform;

public class LWJGL2ExcludeLibraryProcessor extends LibraryProcessor {
	private static final String LWJGL_2_GROUP = "org.lwjgl.lwjgl";

	public LWJGL2ExcludeLibraryProcessor(Platform platform, LibraryContext context) {
		super(platform, context);
	}

	@Override
	public ApplicationResult getApplicationResult() {
		return context.usesLWJGL2() ? ApplicationResult.CAN_APPLY : ApplicationResult.DONT_APPLY;
	}

	@Override
	public Predicate<Library> apply(Consumer<Library> dependencyConsumer) {
		return library -> {
			if (library.is(LWJGL_2_GROUP)) {
				return false;
			}

			return true;
		};
	}
}
