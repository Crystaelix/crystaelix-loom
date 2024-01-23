package dev.architectury.loom.forge;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.loom.configuration.providers.forge.ForgeRunTemplate;

public record UserdevConfig(
		String mcp,
		String universal,
		String sources,
		String patches,
		Optional<String> patchesOriginalPrefix,
		Optional<String> patchesModifiedPrefix,
		String binpatches,
		BinaryPatcherConfig binpatcher,
		List<String> libraries,
		Map<String, ForgeRunTemplate> runs,
		List<String> sass,
		List<String> ats,
		List<String> universalFilters
) {
	public static final Codec<List<String>> STRING_LIST_CODEC = Codec.either(Codec.STRING.listOf(), Codec.STRING)
			.xmap(either -> either.map(Function.identity(), List::of), Either::left);
	public static final Codec<UserdevConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("mcp").forGetter(UserdevConfig::mcp),
			Codec.STRING.fieldOf("universal").forGetter(UserdevConfig::universal),
			Codec.STRING.fieldOf("sources").forGetter(UserdevConfig::sources),
			Codec.STRING.fieldOf("patches").forGetter(UserdevConfig::patches),
			Codec.STRING.optionalFieldOf("patchesOriginalPrefix").forGetter(UserdevConfig::patchesOriginalPrefix),
			Codec.STRING.optionalFieldOf("patchesModifiedPrefix").forGetter(UserdevConfig::patchesModifiedPrefix),
			Codec.STRING.fieldOf("binpatches").forGetter(UserdevConfig::binpatches),
			BinaryPatcherConfig.CODEC.fieldOf("binpatcher").forGetter(UserdevConfig::binpatcher),
			Codec.STRING.listOf().fieldOf("libraries").forGetter(UserdevConfig::libraries),
			ForgeRunTemplate.MAP_CODEC.fieldOf("runs").forGetter(UserdevConfig::runs),
			Codec.STRING.listOf().optionalFieldOf("sass", List.of()).forGetter(UserdevConfig::sass),
			STRING_LIST_CODEC.optionalFieldOf("ats", List.of()).forGetter(UserdevConfig::ats),
			Codec.STRING.listOf().optionalFieldOf("universalFilters", List.of()).forGetter(UserdevConfig::universalFilters)
	).apply(instance, UserdevConfig::new));

	public record BinaryPatcherConfig(String dependency, List<String> args) {
		public static final Codec<BinaryPatcherConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("version").forGetter(BinaryPatcherConfig::dependency),
				Codec.STRING.listOf().fieldOf("args").forGetter(BinaryPatcherConfig::args)
		).apply(instance, BinaryPatcherConfig::new));
	}
}
