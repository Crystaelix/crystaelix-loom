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

package net.fabricmc.loom;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.InstallerData;
import net.fabricmc.loom.configuration.LoomDependencyManager;
import net.fabricmc.loom.configuration.accesswidener.AccessWidenerFile;
import net.fabricmc.loom.configuration.providers.forge.DependencyProviders;
import net.fabricmc.loom.configuration.providers.forge.ForgeProvider;
import net.fabricmc.loom.configuration.providers.forge.ForgeRunsProvider;
import net.fabricmc.loom.configuration.providers.forge.ForgeUniversalProvider;
import net.fabricmc.loom.configuration.providers.forge.ForgeUserdevProvider;
import net.fabricmc.loom.configuration.providers.forge.PatchProvider;
import net.fabricmc.loom.configuration.providers.forge.SrgProvider;
import net.fabricmc.loom.configuration.providers.forge.mcpconfig.McpConfigProvider;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingsFactory;
import net.fabricmc.loom.configuration.providers.mappings.MappingConfiguration;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftMetadataProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryProcessorManager;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.IntermediaryMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.MojangMappedMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.NamedMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.SrgMinecraftProvider;
import net.fabricmc.loom.extension.LoomFiles;
import net.fabricmc.loom.extension.MixinExtension;
import net.fabricmc.loom.extension.RemapperExtensionHolder;
import net.fabricmc.loom.util.ModPlatform;
import net.fabricmc.loom.util.download.DownloadBuilder;

@ApiStatus.Internal
public interface LoomGradleExtension extends LoomGradleExtensionAPI {
	static LoomGradleExtension get(Project project) {
		return (LoomGradleExtension) project.getExtensions().getByName("loom");
	}

	LoomFiles getFiles();

	ConfigurableFileCollection getUnmappedModCollection();

	void setInstallerData(InstallerData data);

	InstallerData getInstallerData();

	void setDependencyManager(LoomDependencyManager dependencyManager);

	LoomDependencyManager getDependencyManager();

	MinecraftMetadataProvider getMetadataProvider();

	void setMetadataProvider(MinecraftMetadataProvider metadataProvider);

	MinecraftProvider getMinecraftProvider();

	void setMinecraftProvider(MinecraftProvider minecraftProvider);

	MappingConfiguration getMappingConfiguration();

	void setMappingConfiguration(MappingConfiguration mappingConfiguration);

	NamedMinecraftProvider<?> getNamedMinecraftProvider();

	IntermediaryMinecraftProvider<?> getIntermediaryMinecraftProvider();

	void setNamedMinecraftProvider(NamedMinecraftProvider<?> namedMinecraftProvider);

	void setIntermediaryMinecraftProvider(IntermediaryMinecraftProvider<?> intermediaryMinecraftProvider);

	SrgMinecraftProvider<?> getSrgMinecraftProvider();

	void setSrgMinecraftProvider(SrgMinecraftProvider<?> srgMinecraftProvider);

	MojangMappedMinecraftProvider<?> getMojangMappedMinecraftProvider();

	void setMojangMappedMinecraftProvider(MojangMappedMinecraftProvider<?> srgMinecraftProvider);

	default List<Path> getMinecraftJars(MappingsNamespace mappingsNamespace) {
		return switch (mappingsNamespace) {
		case NAMED -> getNamedMinecraftProvider().getMinecraftJarPaths();
		case INTERMEDIARY -> getIntermediaryMinecraftProvider().getMinecraftJarPaths();
		case OFFICIAL, CLIENT_OFFICIAL, SERVER_OFFICIAL -> getMinecraftProvider().getMinecraftJars();
		case SRG -> {
			ModPlatform.assertPlatform(this, () -> "SRG jars are only available on Forge.", ModPlatform.SRG_FORGE_LIKE);
			yield getSrgMinecraftProvider().getMinecraftJarPaths();
		}
		case MOJANG -> {
			if (!this.isForgeLike() || !this.getForgeProvider().usesMojangAtRuntime()) {
				throw new GradleException("Mojang-mapped jars are only available on NeoForge / Forge 50+.");
			}

			yield getMojangMappedMinecraftProvider().getMinecraftJarPaths();
		}
		};
	}

	FileCollection getMinecraftJarsCollection(MappingsNamespace mappingsNamespace);

	boolean isRootProject();

	@Override
	MixinExtension getMixin();

	List<AccessWidenerFile> getTransitiveAccessWideners();

	void addTransitiveAccessWideners(List<AccessWidenerFile> accessWidenerFiles);

	DownloadBuilder download(String url);

	boolean refreshDeps();

	void setRefreshDeps(boolean refreshDeps);

	ListProperty<LibraryProcessorManager.LibraryProcessorFactory> getLibraryProcessors();

	ListProperty<RemapperExtensionHolder> getRemapperExtensions();

	Collection<LayeredMappingsFactory> getLayeredMappingFactories();

	boolean isConfigurationCacheActive();

	boolean isProjectIsolationActive();

	// ===================
	//  Architectury Loom
	// ===================
	default PatchProvider getPatchProvider() {
		return getDependencyProviders().getProvider(PatchProvider.class);
	}

	default McpConfigProvider getMcpConfigProvider() {
		return getDependencyProviders().getProvider(McpConfigProvider.class);
	}

	default boolean isDataGenEnabled() {
		return isForge() && !getForge().getDataGenMods().isEmpty();
	}

	default boolean isForgeLikeAndOfficial() {
		return isForgeLike() && getMcpConfigProvider().isOfficial();
	}

	default boolean isForgeLikeAndNotOfficial() {
		return isForgeLike() && !getMcpConfigProvider().isOfficial();
	}

	DependencyProviders getDependencyProviders();

	void setDependencyProviders(DependencyProviders dependencyProviders);

	default SrgProvider getSrgProvider() {
		return getDependencyProviders().getProvider(SrgProvider.class);
	}

	default ForgeUniversalProvider getForgeUniversalProvider() {
		return getDependencyProviders().getProvider(ForgeUniversalProvider.class);
	}

	default ForgeUserdevProvider getForgeUserdevProvider() {
		return getDependencyProviders().getProvider(ForgeUserdevProvider.class);
	}

	default ForgeProvider getForgeProvider() {
		return getDependencyProviders().getProvider(ForgeProvider.class);
	}

	ForgeRunsProvider getForgeRunsProvider();
	void setForgeRunsProvider(ForgeRunsProvider forgeRunsProvider);

	/**
	 * The mapping file that is specific to the platform settings.
	 * It contains SRG (Forge/common) or Mojang mappings (NeoForge) as needed.
	 *
	 * @return the platform mapping file path
	 */
	default Path getPlatformMappingFile() {
		return getMappingConfiguration().getPlatformMappingFile(this);
	}
}
