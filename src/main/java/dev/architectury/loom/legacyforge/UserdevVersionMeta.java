package dev.architectury.loom.legacyforge;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.architectury.loom.forge.ForgeVersion;
import dev.architectury.loom.forge.UserdevConfig;

import net.fabricmc.loom.configuration.providers.forge.ConfigValue;
import net.fabricmc.loom.configuration.providers.forge.ForgeRunTemplate;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.LoomVersions;
import net.fabricmc.loom.util.Platform;

public record UserdevVersionMeta(
		Optional<String> inheritsFrom,
		List<Library> libraries
) {
	private static Map<Platform.OperatingSystem, String> OS_NAMES = Map.of(
			Platform.OperatingSystem.WINDOWS, "windows",
			Platform.OperatingSystem.MAC_OS, "osx",
			Platform.OperatingSystem.LINUX, "linux"
	);
	public static final Codec<UserdevVersionMeta> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.optionalFieldOf("inheritsFrom").forGetter(UserdevVersionMeta::inheritsFrom),
			Library.CODEC.listOf().optionalFieldOf("libraries", List.of()).forGetter(UserdevVersionMeta::libraries)
	).apply(instance, UserdevVersionMeta::new));
	public static final UserdevConfig.BinaryPatcherConfig DEFAULT_BINPATCHER_CONFIG = new UserdevConfig.BinaryPatcherConfig(
			LoomVersions.BINARYPATCHER.mavenNotation() + ":fatjar",
			List.of(
					"--clean", "{clean}",
					"--output", "{output}",
					"--apply", "{patch}"
			)
	);

	public UserdevConfig toUserdevConfig(String mcVersion, String depString, ForgeVersion forgeVersion) {
		return new UserdevConfig(
				"de.oceanlabs.mcp:mcp:" + inheritsFrom().orElse(mcVersion) + ":srg@zip",
				depString + ":universal",
				"", // Handle elsewhere
				"", // Handle elsewhere
				Optional.empty(),
				Optional.empty(),
				"", // Unused
				DEFAULT_BINPATCHER_CONFIG,
				libraries().stream().map(Library::getDepString).filter(Objects::nonNull).toList(),
				Map.of(
						"client", new ForgeRunTemplate(
								"client",
								Constants.LegacyForge.LAUNCH_WRAPPER,
								Stream.of(
										"--tweakClass",
										forgeVersion.cpwFml() ? Constants.LegacyForge.CPW_FML_TWEAKER : Constants.LegacyForge.FML_TWEAKER
								).map(ConfigValue::of).toList(),
								List.of(),
								Map.of(),
								Map.of()
						),
						"server", new ForgeRunTemplate(
								"server",
								Constants.LegacyForge.LAUNCH_WRAPPER,
								Stream.of(
										"--tweakClass",
										forgeVersion.cpwFml() ? Constants.LegacyForge.CPW_FML_SERVER_TWEAKER : Constants.LegacyForge.FML_SERVER_TWEAKER
								).map(ConfigValue::of).toList(),
								List.of(),
								Map.of(),
								Map.of()
						)
				),
				List.of(),
				new UserdevConfig.AccessTransformerLocation.FileList(List.of("merged_at.cfg", "src/main/resources/forge_at.cfg", "src/main/resources/fml_at.cfg")),
				List.of("^(?!binpatches\\.pack\\.lzma$).*$")
		);
	}

	public record Library(String name, Map<String, String> natives, List<Rule> rules) {
		public static final Codec<Library> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("name").forGetter(Library::name),
				Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("natives", Map.of()).forGetter(Library::natives),
				Rule.CODEC.listOf().optionalFieldOf("rules", List.of()).forGetter(Library::rules)
		).apply(instance, Library::new));

		public String getDepString() {
			if (hasNatives()) {
				if (hasNativesForCurrentOS()) {
					return name + ":" + classifierForCurrentOS();
				} else {
					return null;
				}
			}

			if (isValidForCurrentOS()) {
				return name;
			}

			return null;
		}

		public boolean isValidForCurrentOS() {
			if (rules().isEmpty()) {
				// No rules allow everything.
				return true;
			}

			boolean valid = false;

			for (Rule rule : rules()) {
				if (rule.appliesToCurrentOS()) {
					valid = rule.isAllowed();
				}
			}

			return valid;
		}

		public boolean hasNatives() {
			return !natives().isEmpty();
		}

		public boolean hasNativesForCurrentOS() {
			if (!hasNatives()) {
				return false;
			}

			if (classifierForCurrentOS() == null) {
				return false;
			}

			return isValidForCurrentOS();
		}

		public String classifierForCurrentOS() {
			String classifier = natives().get(OS_NAMES.get(Platform.CURRENT.getOperatingSystem()));

			if (classifier == null) {
				return null;
			}

			// Used in the twitch library in 1.7.10
			classifier = classifier.replace("${arch}", Platform.CURRENT.getArchitecture().is64Bit() ? "64" : "32");

			return classifier;
		}
	}

	public record Rule(String action, Optional<OS> os) {
		public static final Codec<Rule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("action").forGetter(Rule::action),
				OS.CODEC.optionalFieldOf("os").forGetter(Rule::os)
		).apply(instance, Rule::new));

		public boolean appliesToCurrentOS() {
			return os().isEmpty() || os().get().isValidForCurrentOS();
		}

		public boolean isAllowed() {
			return action().equals("allow");
		}
	}

	public record OS(String name) {
		public static final Codec<OS> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("name").forGetter(OS::name)
		).apply(instance, OS::new));

		public boolean isValidForCurrentOS() {
			return name() == null || name().equalsIgnoreCase(OS_NAMES.get(Platform.CURRENT.getOperatingSystem()));
		}
	}
}
