package net.fabricmc.loom.configuration.mods.extension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.function.Predicate;

import net.fabricmc.loom.configuration.mods.ArtifactMetadata;
import net.fabricmc.loom.configuration.mods.dependency.ModDependency;
import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension;

final class MixinRemapHardOnly implements ModProcessorExtension {
	static final MixinRemapHardOnly INSTANCE = new MixinRemapHardOnly();

	private MixinRemapHardOnly() {
	}

	@Override
	public boolean appliesTo(ModDependency modDependency) {
		return modDependency.getMetadata().mixinRemapType() == ArtifactMetadata.MixinRemapType.MIXIN;
	}

	@Override
	public TinyRemapper.Extension createExtension(Context ctx, Predicate<InputTag> applyPredicate) {
		return new MixinExtension(EnumSet.of(MixinExtension.AnnotationTarget.HARD), applyPredicate);
	}

	@Override
	public void finalise(ModDependency modDependency, Path path) throws IOException {
	}
}
