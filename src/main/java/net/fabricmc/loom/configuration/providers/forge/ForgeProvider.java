/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020-2023 FabricMC
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

package net.fabricmc.loom.configuration.providers.forge;

import java.io.File;
import java.nio.file.Path;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.util.Constants;

public class ForgeProvider extends DependencyProvider {
	private ForgeVersion version = new ForgeVersion(null);
	private File globalCache;
	private File projectCache;

	public ForgeProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency) throws Exception {
		version = new ForgeVersion(dependency.getResolvedVersion());
		Dependency dep = dependency.getDependency();

		if ("net.minecraftforge".equals(dep.getGroup()) && "forge".equals(dep.getName()) && "1.12.2".equals(version.getMinecraftVersion()) && version.getBuildNumber() > 2847) {
			addDependency(dependency.getDepString() + ":userdev3", Constants.Configurations.FORGE_USERDEV);
		} else {
			addDependency(dependency.getDepString() + ":userdev", Constants.Configurations.FORGE_USERDEV);
		}

		addDependency(dependency.getDepString() + ":installer", Constants.Configurations.FORGE_INSTALLER);
	}

	public ForgeVersion getVersion() {
		return version;
	}

	public File getGlobalCache() {
		if (globalCache == null) {
			globalCache = getMinecraftProvider().dir("forge/" + version.getCombined());
			globalCache.mkdirs();
		}

		return globalCache;
	}

	public File getProjectCache() {
		if (projectCache == null) {
			projectCache = new File(getDirectories().getRootProjectPersistentCache(), getMinecraftProvider().minecraftVersion() + "/forge/" + getExtension().getForgeProvider().getVersion().getCombined() + "/project-" + getProject().getPath().replace(':', '@'));
			projectCache.mkdirs();
		}

		return projectCache;
	}

	@Override
	public String getTargetConfig() {
		return Constants.Configurations.FORGE;
	}

	/**
	 * {@return the Forge cache directory}.
	 *
	 * @param project the project
	 */
	public static Path getForgeCache(Project project) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final String version = extension.getForgeProvider().getVersion().getCombined();
		return LoomGradleExtension.get(project).getMinecraftProvider().dir("forge/" + version).toPath();
	}

	public static final class ForgeVersion {
		private final String combined;
		private final String minecraftVersion;
		private final String forgeVersion;
		private final int buildNumber;

		public ForgeVersion(String combined) {
			this.combined = combined;

			if (combined == null) {
				this.minecraftVersion = "NO_VERSION";
				this.forgeVersion = "NO_VERSION";
				this.buildNumber = -1;
				return;
			}

			int hyphenIndex = combined.indexOf('-');

			if (hyphenIndex != -1) {
				this.minecraftVersion = combined.substring(0, hyphenIndex);
				this.forgeVersion = combined.substring(hyphenIndex + 1);
			} else {
				this.minecraftVersion = "NO_VERSION";
				this.forgeVersion = combined;
			}

			int dotIndex = forgeVersion.lastIndexOf('.');
			int build;

			try {
				if (dotIndex >= 0) {
					build = Integer.parseInt(forgeVersion.substring(dotIndex + 1, forgeVersion.length()));
				} else {
					build = -1;
				}
			} catch (NumberFormatException e) {
				build = -1;
			}

			this.buildNumber = build;
		}

		public String getCombined() {
			return combined;
		}

		public String getMinecraftVersion() {
			return minecraftVersion;
		}

		public String getForgeVersion() {
			return forgeVersion;
		}

		public int getBuildNumber() {
			return buildNumber;
		}
	}
}
