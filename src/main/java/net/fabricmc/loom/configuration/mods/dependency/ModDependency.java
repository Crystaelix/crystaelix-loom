/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020-2021 FabricMC
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

package net.fabricmc.loom.configuration.mods.dependency;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.api.Project;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotComponentIdentifier;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.mods.ArtifactMetadata;
import net.fabricmc.loom.configuration.mods.ArtifactRef;

public abstract sealed class ModDependency permits SplitModDependency, SimpleModDependency {
	private final ArtifactRef artifact;
	private final ArtifactMetadata metadata;
	protected final String group;
	protected final String name;
	protected final String version;
	@Nullable
	protected final String classifier;
	protected final ModDependencyOptions options;

	public ModDependency(ArtifactRef artifact, ArtifactMetadata metadata, ModDependencyOptions options) {
		this.artifact = artifact;
		this.metadata = metadata;
		this.group = artifact.group();
		this.name = artifact.name();
		this.version = artifact.version();
		this.classifier = artifact.classifier();
		this.options = options;
	}

	/**
	 * Returns true when the cache is invalid.
	 */
	public abstract boolean isCacheInvalid(Project project, @Nullable String variant);

	/**
	 * Returns true when the cache is invalid.
	 */
	public boolean isCacheInvalid(Project project) {
		return isCacheInvalid(project, null);
	}

	/**
	 * Write an artifact to the cache.
	 */
	public abstract void copyToCache(Project project, Path path, @Nullable String variant) throws IOException;

	/**
	 * Write an artifact to the cache.
	 */
	public void copyToCache(Project project, Path path) throws IOException {
		copyToCache(project, path, null);
	}

	/**
	 * Apply the dependency to the project.
	 */
	public abstract void applyToProject(Project project);

	/**
	 * Create a maven helper for the local cache.
	 * @param type The jar type, e.g "common" or "client" for split dependencies.
	 */
	protected LocalMavenHelper createMavenHelper(Project project, @Nullable String type) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final Path root = extension.getFiles().getRemappedModCache().toPath();
		final String fullName = getName() + (type != null ? "-" + type : "");
		return new LocalMavenHelper(getGroup(), fullName, this.version, this.classifier, root, getSnapshotVersion());
	}

	private @Nullable String getSnapshotVersion() {
		if (artifact instanceof ArtifactRef.ResolvedArtifactRef resolvedArtifactRef) {
			ComponentIdentifier componentIdentifier = resolvedArtifactRef.artifact().getId().getComponentIdentifier();

			if (componentIdentifier instanceof MavenUniqueSnapshotComponentIdentifier mavenUniqueId) {
				return mavenUniqueId.getSnapshotVersion();
			}
		}

		return null;
	}

	public ArtifactRef getInputArtifact() {
		return artifact;
	}

	public ArtifactMetadata getMetadata() {
		return metadata;
	}

	protected String getName() {
		return name;
	}

	protected String getGroup() {
		return "%s.%s".formatted(options.getCacheKey(), group);
	}

	protected String getVersion() {
		return version;
	}

	public Path getInputFile() {
		return artifact.path();
	}

	public Path getWorkingFile(Project project, @Nullable String classifier) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final String fileName = classifier == null ? String.format("%s-%s-%s.jar", getGroup(), getName(), version)
													: String.format("%s-%s-%s-%s.jar", getGroup(), getName(), version, classifier);

		return extension.getFiles().getProjectBuildCache().toPath().resolve("remapped_working").resolve(fileName);
	}

	public void deleteWorkingFile(Project project, @Nullable String classifier) throws IOException {
		Files.deleteIfExists(getWorkingFile(project, classifier));
	}

	public ModDependencyOptions getOptions() {
		return options;
	}

	@Override
	public String toString() {
		return "ModDependency{" + "group='" + group + '\'' + ", name='" + name + '\'' + ", version='" + version + '\'' + ", classifier='" + classifier + '\'' + '}';
	}
}
