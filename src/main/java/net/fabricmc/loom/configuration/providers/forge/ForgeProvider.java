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
import java.util.Set;

import dev.architectury.loom.forge.ForgeVersion;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleDependency;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.LoomVersions;
import net.fabricmc.loom.util.ModPlatform;

public class ForgeProvider extends DependencyProvider {
	private final ModPlatform platform;
	private ForgeVersion version = new ForgeVersion("unresolved", "unresolved", "unresolved");
	private Set<ExcludeRule> excludeRules = Set.of();
	private File globalCache;

	public ForgeProvider(Project project) {
		super(project);
		platform = getExtension().getPlatform().get();
	}

	@Override
	public void provide(DependencyInfo dependency) throws Exception {
		Dependency dep = dependency.getDependency();
		version = new ForgeVersion(dep.getGroup(), dep.getName(), dependency.getResolvedVersion());

		if (dep instanceof ModuleDependency moduleDependency) {
			excludeRules = Set.copyOf(moduleDependency.getExcludeRules());
		}

		if (version.userdev3()) {
			addDependency(dependency.getDepString() + ":userdev3", Constants.Configurations.FORGE_USERDEV);
		} else {
			addDependency(dependency.getDepString() + ":userdev", Constants.Configurations.FORGE_USERDEV);
		}

		addDependency(dependency.getDepString() + ":installer", Constants.Configurations.FORGE_INSTALLER);

		if (getExtension().isForge() && version.getMajorVersion() >= Constants.Forge.MIN_UNION_RELAUNCHER_VERSION) {
			addDependency(LoomVersions.UNION_RELAUNCHER.mavenNotation(), Constants.Configurations.FORGE_EXTRA);
		}
	}

	public ForgeVersion getVersion() {
		return version;
	}

	public boolean usesMojangAtRuntime() {
		return platform == ModPlatform.NEOFORGE || version.getMajorVersion() >= Constants.Forge.MIN_USE_MOJANG_NS_VERSION;
	}

	public File getGlobalCache() {
		if (globalCache == null) {
			globalCache = getMinecraftProvider().dir(platform.id() + "/" + version.getCombined());
			globalCache.mkdirs();
		}

		return globalCache;
	}

	@Override
	public String getTargetConfig() {
		return switch (platform) {
		case FORGE -> Constants.Configurations.FORGE;
		case NEOFORGE -> Constants.Configurations.NEOFORGE;
		case LEGACYFORGE -> Constants.Configurations.LEGACYFORGE;
		case CLEANROOM -> Constants.Configurations.CLEANROOM;
		default -> throw new GradleException("Forge provider can only be used on Forge-like platforms!");
		};
	}

	/**
	 * {@return the Forge cache directory}.
	 *
	 * @param project the project
	 */
	public static Path getForgeCache(Project project) {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final ModPlatform platform = extension.getPlatform().get();
		final String version = extension.getForgeProvider().getVersion().getCombined();
		return LoomGradleExtension.get(project).getMinecraftProvider().dir(platform.id() + "/" + version).toPath();
	}
}
