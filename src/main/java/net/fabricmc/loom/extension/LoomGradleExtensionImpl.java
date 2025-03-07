/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2024 FabricMC
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

package net.fabricmc.loom.extension;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import javax.inject.Inject;

import com.google.common.base.Suppliers;
import org.gradle.api.Project;
import org.gradle.api.configuration.BuildFeatures;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.ForgeExtensionAPI;
import net.fabricmc.loom.api.NeoForgeExtensionAPI;
import net.fabricmc.loom.api.mappings.intermediate.IntermediateMappingsProvider;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.InstallerData;
import net.fabricmc.loom.configuration.LoomDependencyManager;
import net.fabricmc.loom.configuration.accesswidener.AccessWidenerFile;
import net.fabricmc.loom.configuration.providers.forge.DependencyProviders;
import net.fabricmc.loom.configuration.providers.forge.ForgeRunsProvider;
import net.fabricmc.loom.configuration.providers.mappings.IntermediaryMappingsProvider;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingsFactory;
import net.fabricmc.loom.configuration.providers.mappings.MappingConfiguration;
import net.fabricmc.loom.configuration.providers.mappings.NoOpIntermediateMappingsProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftMetadataProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryProcessorManager;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.IntermediaryMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.MojangMappedMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.NamedMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.SrgMinecraftProvider;
import net.fabricmc.loom.util.ModPlatform;
import net.fabricmc.loom.util.download.Download;
import net.fabricmc.loom.util.download.DownloadBuilder;

public abstract class LoomGradleExtensionImpl extends LoomGradleExtensionApiImpl implements LoomGradleExtension {
	private final Project project;
	private final MixinExtension mixinApExtension;
	private final LoomFiles loomFiles;
	private final ConfigurableFileCollection unmappedMods;

	private final List<AccessWidenerFile> transitiveAccessWideners = new ArrayList<>();

	private LoomDependencyManager dependencyManager;
	private MinecraftMetadataProvider metadataProvider;
	private MinecraftProvider minecraftProvider;
	private MappingConfiguration mappingConfiguration;
	private NamedMinecraftProvider<?> namedMinecraftProvider;
	private IntermediaryMinecraftProvider<?> intermediaryMinecraftProvider;
	private SrgMinecraftProvider<?> srgMinecraftProvider;
	private MojangMappedMinecraftProvider<?> mojangMappedMinecraftProvider;
	private InstallerData installerData;
	private boolean refreshDeps;
	private final ListProperty<LibraryProcessorManager.LibraryProcessorFactory> libraryProcessorFactories;
	private final boolean configurationCacheActive;
	private final boolean isolatedProjectsActive;

	// +-------------------+
	// | Architectury Loom |
	// +-------------------+
	private DependencyProviders dependencyProviders;
	private ForgeRunsProvider forgeRunsProvider;
	private final Supplier<ForgeExtensionAPI> forgeExtension;
	private final Supplier<NeoForgeExtensionAPI> neoForgeExtension;

	@Inject
	protected abstract BuildFeatures getBuildFeatures();

	@Inject
	public LoomGradleExtensionImpl(Project project, LoomFiles files) {
		super(project, files);
		this.project = project;
		// Initiate with newInstance to allow gradle to decorate our extension
		this.mixinApExtension = project.getObjects().newInstance(MixinExtensionImpl.class, project);
		this.loomFiles = files;
		this.unmappedMods = project.files();
		this.forgeExtension = Suppliers.memoize(() -> isSrgForgeLike() ? project.getObjects().newInstance(ForgeExtensionImpl.class, project, this) : null);
		this.neoForgeExtension = Suppliers.memoize(() -> isNeoForge() ? project.getObjects().newInstance(NeoForgeExtensionImpl.class, project) : null);

		// Setup the default intermediate mappings provider.
		setIntermediateMappingsProvider(IntermediaryMappingsProvider.class, provider -> {
			provider.getIntermediaryUrl()
					.convention(getIntermediaryUrl())
					.finalizeValueOnRead();

			provider.getRefreshDeps().set(project.provider(() -> LoomGradleExtension.get(project).refreshDeps()));
		});

		refreshDeps = manualRefreshDeps();
		libraryProcessorFactories = project.getObjects().listProperty(LibraryProcessorManager.LibraryProcessorFactory.class);
		libraryProcessorFactories.addAll(LibraryProcessorManager.DEFAULT_LIBRARY_PROCESSORS);
		libraryProcessorFactories.finalizeValueOnRead();

		configurationCacheActive = getBuildFeatures().getConfigurationCache().getActive().get();
		isolatedProjectsActive = getBuildFeatures().getIsolatedProjects().getActive().get();

		if (refreshDeps) {
			project.getLogger().lifecycle("Refresh dependencies is in use, loom will be significantly slower.");
		}

		if (isolatedProjectsActive) {
			project.getLogger().lifecycle("Isolated projects is enabled, Loom support is highly experimental, not all features will be enabled.");
		}
	}

	@Override
	protected Project getProject() {
		return project;
	}

	@Override
	public LoomFiles getFiles() {
		return loomFiles;
	}

	@Override
	public void setDependencyManager(LoomDependencyManager dependencyManager) {
		this.dependencyManager = dependencyManager;
	}

	@Override
	public LoomDependencyManager getDependencyManager() {
		return Objects.requireNonNull(dependencyManager, "Cannot get LoomDependencyManager before it has been setup");
	}

	@Override
	public MinecraftMetadataProvider getMetadataProvider() {
		return Objects.requireNonNull(metadataProvider, "Cannot get MinecraftMetadataProvider before it has been setup");
	}

	@Override
	public void setMetadataProvider(MinecraftMetadataProvider metadataProvider) {
		this.metadataProvider = metadataProvider;
	}

	@Override
	public MinecraftProvider getMinecraftProvider() {
		return Objects.requireNonNull(minecraftProvider, "Cannot get MinecraftProvider before it has been setup");
	}

	@Override
	public void setMinecraftProvider(MinecraftProvider minecraftProvider) {
		this.minecraftProvider = minecraftProvider;
	}

	@Override
	public MappingConfiguration getMappingConfiguration() {
		return Objects.requireNonNull(mappingConfiguration, "Cannot get MappingsProvider before it has been setup");
	}

	@Override
	public void setMappingConfiguration(MappingConfiguration mappingConfiguration) {
		this.mappingConfiguration = mappingConfiguration;
	}

	@Override
	public NamedMinecraftProvider<?> getNamedMinecraftProvider() {
		return Objects.requireNonNull(namedMinecraftProvider, "Cannot get NamedMinecraftProvider before it has been setup");
	}

	@Override
	public IntermediaryMinecraftProvider<?> getIntermediaryMinecraftProvider() {
		return Objects.requireNonNull(intermediaryMinecraftProvider, "Cannot get IntermediaryMinecraftProvider before it has been setup");
	}

	@Override
	public void setNamedMinecraftProvider(NamedMinecraftProvider<?> namedMinecraftProvider) {
		this.namedMinecraftProvider = namedMinecraftProvider;
	}

	@Override
	public void setIntermediaryMinecraftProvider(IntermediaryMinecraftProvider<?> intermediaryMinecraftProvider) {
		this.intermediaryMinecraftProvider = intermediaryMinecraftProvider;
	}

	@Override
	public void noIntermediateMappings() {
		setIntermediateMappingsProvider(NoOpIntermediateMappingsProvider.class, p -> { });
	}

	@Override
	public SrgMinecraftProvider<?> getSrgMinecraftProvider() {
		return Objects.requireNonNull(srgMinecraftProvider, "Cannot get SrgMinecraftProvider before it has been setup");
	}

	@Override
	public void setSrgMinecraftProvider(SrgMinecraftProvider<?> srgMinecraftProvider) {
		this.srgMinecraftProvider = srgMinecraftProvider;
	}

	@Override
	public MojangMappedMinecraftProvider<?> getMojangMappedMinecraftProvider() {
		return Objects.requireNonNull(mojangMappedMinecraftProvider, "Cannot get MojangMappedMinecraftProvider before it has been setup");
	}

	@Override
	public void setMojangMappedMinecraftProvider(MojangMappedMinecraftProvider<?> mojangMappedMinecraftProvider) {
		this.mojangMappedMinecraftProvider = mojangMappedMinecraftProvider;
	}

	@Override
	public FileCollection getMinecraftJarsCollection(MappingsNamespace mappingsNamespace) {
		return getProject().files(
			getProject().provider(() ->
				getProject().files(getMinecraftJars(mappingsNamespace).stream().map(Path::toFile).toList())
			)
		);
	}

	@Override
	public ConfigurableFileCollection getUnmappedModCollection() {
		return unmappedMods;
	}

	public void setInstallerData(InstallerData object) {
		this.installerData = object;
	}

	@Override
	public InstallerData getInstallerData() {
		return installerData;
	}

	@Override
	public boolean isRootProject() {
		return project.getRootProject() == project;
	}

	@Override
	public MixinExtension getMixin() {
		return this.mixinApExtension;
	}

	@Override
	public List<AccessWidenerFile> getTransitiveAccessWideners() {
		return transitiveAccessWideners;
	}

	@Override
	public void addTransitiveAccessWideners(List<AccessWidenerFile> accessWidenerFiles) {
		transitiveAccessWideners.addAll(accessWidenerFiles);
	}

	@Override
	public DownloadBuilder download(String url) {
		DownloadBuilder builder;

		try {
			builder = Download.create(url);
		} catch (URISyntaxException e) {
			throw new RuntimeException("Failed to create downloader for: " + e);
		}

		if (project.getGradle().getStartParameter().isOffline()) {
			builder.offline();
		}

		if (manualRefreshDeps()) {
			builder.forceDownload();
		}

		return builder;
	}

	private boolean manualRefreshDeps() {
		return project.getGradle().getStartParameter().isRefreshDependencies() || Boolean.getBoolean("loom.refresh");
	}

	@Override
	public boolean refreshDeps() {
		return refreshDeps;
	}

	@Override
	public void setRefreshDeps(boolean refreshDeps) {
		this.refreshDeps = refreshDeps;
	}

	@Override
	public ListProperty<LibraryProcessorManager.LibraryProcessorFactory> getLibraryProcessors() {
		return libraryProcessorFactories;
	}

	@Override
	public ListProperty<RemapperExtensionHolder> getRemapperExtensions() {
		return remapperExtensions;
	}

	@Override
	public Collection<LayeredMappingsFactory> getLayeredMappingFactories() {
		hasEvaluatedLayeredMappings = true;
		return Collections.unmodifiableCollection(layeredMappingsDependencyMap.values());
	}

	@Override
	protected <T extends IntermediateMappingsProvider> void configureIntermediateMappingsProviderInternal(T provider) {
		provider.getMinecraftVersion().set(getProject().provider(() -> getMinecraftProvider().minecraftVersion()));
		provider.getMinecraftVersion().disallowChanges();

		provider.getDownloader().set(this::download);
		provider.getDownloader().disallowChanges();

		provider.getIsLegacyMinecraft().set(getProject().provider(() -> getMinecraftProvider().isLegacyVersion()));
		provider.getIsLegacyMinecraft().disallowChanges();
	}

	@Override
	public boolean isConfigurationCacheActive() {
		return configurationCacheActive;
	}

	@Override
	public boolean isProjectIsolationActive() {
		return isolatedProjectsActive;
	}

	@Override
	public ForgeExtensionAPI getForge() {
		ModPlatform.assertPlatform(this, ModPlatform.SRG_FORGE_LIKE);
		return forgeExtension.get();
	}

	@Override
	public NeoForgeExtensionAPI getNeoForge() {
		ModPlatform.assertPlatform(this, ModPlatform.NEOFORGE);
		return neoForgeExtension.get();
	}

	@Override
	public DependencyProviders getDependencyProviders() {
		return dependencyProviders;
	}

	@Override
	public void setDependencyProviders(DependencyProviders dependencyProviders) {
		this.dependencyProviders = dependencyProviders;
	}

	@Override
	public ForgeRunsProvider getForgeRunsProvider() {
		ModPlatform.assertForgeLike(this);
		return forgeRunsProvider;
	}

	@Override
	public void setForgeRunsProvider(ForgeRunsProvider forgeRunsProvider) {
		ModPlatform.assertForgeLike(this);
		this.forgeRunsProvider = forgeRunsProvider;
	}
}
