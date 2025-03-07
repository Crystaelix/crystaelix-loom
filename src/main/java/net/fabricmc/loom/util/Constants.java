/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2022 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.util;

import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.Opcodes;

public class Constants {
	public static final String PLUGIN_ID = "com.crystaelix.loom";
	public static final boolean PLUGIN_BETA = false;
	public static final boolean PLUGIN_DEPRECATED = false;
	public static final String LIBRARIES_BASE = "https://libraries.minecraft.net/";
	public static final String RESOURCES_BASE = "https://resources.download.minecraft.net/";
	public static final String VERSION_MANIFESTS = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
	public static final String EXPERIMENTAL_VERSIONS = "https://maven.fabricmc.net/net/minecraft/experimental_versions.json";
	public static final String FABRIC_REPOSITORY = "https://maven.fabricmc.net/";

	public static final int ASM_VERSION = Opcodes.ASM9;
	public static final String RELEASE_TIME_1_3 = "2012-07-25T22:00:00+00:00";

	private Constants() {
	}

	/**
	 * Constants related to configurations.
	 */
	public static final class Configurations {
		public static final String MOD_COMPILE_CLASSPATH = "modCompileClasspath";
		public static final String MOD_COMPILE_CLASSPATH_MAPPED = "modCompileClasspathMapped";
		public static final String INCLUDE = "include";
		public static final String INCLUDE_INTERNAL = "includeInternal";
		public static final String MINECRAFT = "minecraft";

		public static final String MINECRAFT_COMPILE_LIBRARIES = "minecraftLibraries";
		public static final String MINECRAFT_RUNTIME_LIBRARIES = "minecraftRuntimeLibraries";

		/**
		 * These configurations contain the minecraft client libraries.
		 */
		public static final String MINECRAFT_CLIENT_COMPILE_LIBRARIES = "minecraftClientLibraries";
		public static final String MINECRAFT_CLIENT_RUNTIME_LIBRARIES = "minecraftClientRuntimeLibraries";

		/**
		 * The server specific configurations will be empty when using a legacy (pre 21w38a server jar)
		 * find the client only dependencies on the "minecraftLibraries" config.
		 */
		public static final String MINECRAFT_SERVER_COMPILE_LIBRARIES = "minecraftServerLibraries";
		public static final String MINECRAFT_SERVER_RUNTIME_LIBRARIES = "minecraftServerRuntimeLibraries";
		/**
		 * Before Minecraft 1.19-pre1 this contains libraries that need to be extracted otherwise this goes on the runtime classpath.
		 */
		public static final String MINECRAFT_NATIVES = "minecraftNatives";
		public static final String MAPPINGS = "mappings";
		public static final String MAPPINGS_FINAL = "mappingsFinal";
		public static final String LOADER_DEPENDENCIES = "loaderLibraries";
		public static final String LOOM_DEVELOPMENT_DEPENDENCIES = "loomDevelopmentDependencies";
		public static final String SRG = "srg";
		public static final String MCP_CONFIG = "mcp";
		public static final String FORGE = "forge";
		public static final String NEOFORGE = "neoForge";
		public static final String LEGACYFORGE = "legacyForge";
		public static final String CLEANROOM = "cleanroom";
		public static final String FORGE_USERDEV = "forgeUserdev";
		public static final String FORGE_INSTALLER = "forgeInstaller";
		public static final String FORGE_UNIVERSAL = "forgeUniversal";
		/**
		 * Forge's own dependencies. Not intended to be used by users,
		 * {@link #FORGE_RUNTIME_LIBRARY forgeRuntimeLibrary} is for that instead.
		 */
		public static final String FORGE_DEPENDENCIES = "forgeDependencies";
		/**
		 * "Extra" runtime dependencies on Forge. Contains the Minecraft resources
		 * and the Architectury Loom runtime.
		 */
		public static final String FORGE_EXTRA = "forgeExtra";
		/**
		 * The configuration used to create the Forge runtime classpath file list.
		 * Users can also directly add files to this config.
		 *
		 * @see net.fabricmc.loom.configuration.providers.forge.ForgeUserdevProvider
		 */
		public static final String FORGE_RUNTIME_LIBRARY = "forgeRuntimeLibrary";
		public static final String MAPPING_CONSTANTS = "mappingsConstants";
		public static final String UNPICK_CLASSPATH = "unpick";
		/**
		 * A configuration that behaves like {@code runtimeOnly} but is not
		 * exposed in {@code runtimeElements} to dependents. A bit like
		 * {@code testRuntimeOnly}, but for mods.
		 */
		public static final String LOCAL_RUNTIME = "localRuntime";
		public static final String NAMED_ELEMENTS = "namedElements";

		private Configurations() {
		}
	}

	public static final class MixinArguments {
		public static final String IN_MAP_FILE_NAMED_INTERMEDIARY = "inMapFileNamedIntermediary";
		public static final String OUT_MAP_FILE_NAMED_INTERMEDIARY = "outMapFileNamedIntermediary";
		public static final String OUT_REFMAP_FILE = "outRefMapFile";
		public static final String DEFAULT_OBFUSCATION_ENV = "defaultObfuscationEnv";
		public static final String QUIET = "quiet";
		public static final String SHOW_MESSAGE_TYPES = "showMessageTypes";

		private MixinArguments() {
		}
	}

	public static final class Knot {
		public static final String KNOT_CLIENT = "net.fabricmc.loader.launch.knot.KnotClient";
		public static final String KNOT_SERVER = "net.fabricmc.loader.launch.knot.KnotServer";

		private Knot() {
		}
	}

	public static final class TaskGroup {
		public static final String FABRIC = "loom";
		public static final String IDE = "ide";

		private TaskGroup() {
		}
	}

	public static final class Task {
		public static final String PROCESS_INCLUDE_JARS = "processIncludeJars";

		private Task() {
		}
	}

	public static final class CustomModJsonKeys {
		public static final String INJECTED_INTERFACE = "loom:injected_interfaces";
		public static final String PROVIDED_JAVADOC = "loom:provided_javadoc";
	}

	public static final class Properties {
		public static final String DONT_REMAP = "fabric.loom.dontRemap";
		public static final String DISABLE_REMAPPED_VARIANTS = "fabric.loom.disableRemappedVariants";
		public static final String DISABLE_PROJECT_DEPENDENT_MODS = "fabric.loom.disableProjectDependentMods";
		public static final String LIBRARY_PROCESSORS = "fabric.loom.libraryProcessors";
		@ApiStatus.Experimental
		public static final String SANDBOX = "fabric.loom.experimental.sandbox";
		/**
		 * When set the version of java that will be assumed that the game will run on, this defaults to the current java version.
		 * Only set this when you have a good reason to do so, the default should be fine for almost all cases.
		 */
		public static final String RUNTIME_JAVA_COMPATIBILITY_VERSION = "fabric.loom.runtimeJavaCompatibilityVersion";
		public static final String DECOMPILE_CACHE_MAX_FILES = "fabric.loom.decompileCacheMaxFiles";
		public static final String DECOMPILE_CACHE_MAX_AGE = "fabric.loom.decompileCacheMaxAge";
		public static final String ALLOW_MISMATCHED_PLATFORM_VERSION = "loom.allowMismatchedPlatformVersion";
		public static final String IGNORE_DEPENDENCY_LOOM_VERSION_VALIDATION = "loom.ignoreDependencyLoomVersionValidation";
	}

	public static final class Manifest {
		public static final String PATH = "META-INF/MANIFEST.MF";

		public static final String REMAP_KEY = "Fabric-Loom-Remap";
		public static final String MIXIN_REMAP_TYPE = "Fabric-Loom-Mixin-Remap-Type";
		public static final String MAPPING_NAMESPACE = "Fabric-Mapping-Namespace";
		public static final String SPLIT_ENV = "Fabric-Loom-Split-Environment";
		public static final String SPLIT_ENV_NAME = "Fabric-Loom-Split-Environment-Name";
		public static final String CLIENT_ENTRIES = "Fabric-Loom-Client-Only-Entries";
		public static final String JAR_TYPE = "Fabric-Jar-Type";
		public static final String GRADLE_VERSION = "Fabric-Gradle-Version";
		public static final String LOOM_VERSION = "Fabric-Loom-Version";
		public static final String MIXIN_COMPILE_EXTENSIONS_VERSION = "Fabric-Mixin-Compile-Extensions-Version";
		public static final String MINECRAFT_VERSION = "Fabric-Minecraft-Version";
		public static final String TINY_REMAPPER_VERSION = "Fabric-Tiny-Remapper-Version";
		public static final String FABRIC_LOADER_VERSION = "Fabric-Loader-Version";
		public static final String MIXIN_VERSION = "Fabric-Mixin-Version";
		public static final String MIXIN_GROUP = "Fabric-Mixin-Group";
		public static final String KNOWN_IDY_BSMS = "Fabric-Loom-Known-Indy-BSMS";
	}

	public static final class Forge {
		public static final String UNDETERMINED_MAIN_CLASS = "[Forge] Main class has not been determined yet!";
		public static final String ACCESS_TRANSFORMER_PATH = "META-INF/accesstransformer.cfg";
		public static final String MIXIN_CONFIGS_MANIFEST_KEY = "MixinConfigs";

		/**
		 * The minimum Forge version that needs bootstrap-dev to use {@code MOD_CLASSES}.
		 */
		public static final int MIN_BOOTSTRAP_DEV_VERSION = 49;

		/**
		 * The minimum version of Forge that uses "mojang" as the namespace in production.
		 */
		public static final int MIN_USE_MOJANG_NS_VERSION = 50;
		public static final int MIN_FORCE_LOGGER_CONFIG_VERSION = 47;

		private Forge() {
		}
	}

	public static final class LegacyForge {
		public static final String LAUNCH_WRAPPER = "net.minecraft.launchwrapper.Launch";
		public static final String FML_TWEAKER = "net.minecraftforge.fml.common.launcher.FMLTweaker";
		public static final String CPW_FML_TWEAKER = "cpw.mods.fml.common.launcher.FMLTweaker";
		public static final String FML_SERVER_TWEAKER = "net.minecraftforge.fml.common.launcher.FMLServerTweaker";
		public static final String CPW_FML_SERVER_TWEAKER = "cpw.mods.fml.common.launcher.FMLServerTweaker";
		public static final String MIXIN_TWEAKER = "org.spongepowered.asm.launch.MixinTweaker";
		public static final String ACCESS_TRANSFORMERS_MANIFEST_KEY = "FMLAT";
		public static final String TWEAK_CLASS_MANIFEST_KEY = "TweakClass";
		public static final String FORCE_LOAD_AS_MOD_MANIFEST_KEY = "ForceLoadAsMod";
	}
}
