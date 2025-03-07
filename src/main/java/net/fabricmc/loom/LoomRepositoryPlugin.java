/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2022 FabricMC
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

import java.util.List;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.PluginAware;
import org.jetbrains.annotations.NotNull;

import net.fabricmc.loom.extension.LoomFiles;
import net.fabricmc.loom.util.MirrorUtil;

public class LoomRepositoryPlugin implements Plugin<PluginAware> {
	private static final List<String> FORGE_GROUPS = List.of(
			"net.minecraftforge",
			"cpw.mods",
			"de.oceanlabs",
			"net.jodah",
			"org.mcmodlauncher"
	);

	@Override
	public void apply(@NotNull PluginAware target) {
		if (target instanceof Settings settings) {
			declareRepositories(settings.getDependencyResolutionManagement().getRepositories(), LoomFiles.create(settings), settings);

			// leave a marker so projects don't try to override these
			settings.getGradle().getPluginManager().apply(LoomRepositoryPlugin.class);
		} else if (target instanceof Project project) {
			if (project.getGradle().getPlugins().hasPlugin(LoomRepositoryPlugin.class)) {
				return;
			}

			declareRepositories(project.getRepositories(), LoomFiles.create(project), project);
		} else if (target instanceof Gradle) {
			return;
		} else {
			throw new IllegalArgumentException("Expected target to be a Project or Settings, but was a " + target.getClass());
		}
	}

	private void declareRepositories(RepositoryHandler repositories, LoomFiles files, ExtensionAware target) {
		declareLocalRepositories(repositories, files);

		repositories.maven(repo -> {
			repo.setName("Architectury");
			repo.setUrl("https://maven.architectury.dev/");
			repo.mavenContent(content -> {
				content.includeGroup("dev.architectury");
			});
		});
		repositories.maven(repo -> {
			repo.setName("Fabric");
			repo.setUrl(MirrorUtil.getFabricRepository(target));
		});

		MavenArtifactRepository mojangRepo = repositories.maven(repo -> {
			repo.setName("Mojang");
			repo.setUrl(MirrorUtil.getLibrariesBase(target));

			// Don't use the gradle module metadata. It has unintended side effects.
			repo.metadataSources(sources -> {
				sources.mavenPom();
				sources.artifact();
				sources.ignoreGradleMetadataRedirection();
			});

			// Fallback to maven central for artifacts such as sources or javadocs that are not mirrored on Mojang's repo.
			// See: https://github.com/FabricMC/fabric-loom/issues/1032
			repo.artifactUrls(ArtifactRepositoryContainer.MAVEN_CENTRAL_URL);
		});
		repositories.maven(repo -> {
			repo.setName("Forge");
			repo.setUrl("https://maven.minecraftforge.net/");

			repo.content(descriptor -> {
				// Only include these groups to avoid slowing down/hanging the build,
				// or downloading incorrect artifacts.
				for (String group : FORGE_GROUPS) {
					descriptor.includeGroupAndSubgroups(group);
				}

				// Specifically include the scala mirrors
				descriptor.includeGroup("org.scala-lang");
				descriptor.includeGroup("org.scala-lang.plugins");
			});
			repo.metadataSources(sources -> {
				sources.mavenPom();
				sources.artifact();
				sources.ignoreGradleMetadataRedirection();
			});
		});
		repositories.ivy(repo -> {
			// Old MCP data does not have POMs
			repo.setName("Legacy MCP");
			repo.setUrl("https://maven.minecraftforge.net/");
			repo.patternLayout(layout -> {
				layout.artifact("[orgPath]/[artifact]/[revision]/[artifact]-[revision](-[classifier])(.[ext])");
				// also check the zip so people do not have to explicitly specify the extension for older versions
				layout.artifact("[orgPath]/[artifact]/[revision]/[artifact]-[revision](-[classifier]).zip");
			});
			repo.content(descriptor -> {
				descriptor.includeGroup("de.oceanlabs.mcp");
			});
			repo.metadataSources(sources -> {
				sources.artifact();
			});
		});

		repositories.maven(repo -> {
			repo.setName("NeoForge");
			repo.setUrl("https://maven.neoforged.net/releases/");

			repo.content(descriptor -> {
				descriptor.excludeGroupByRegex("org\\.eclipse\\.?.*");
				descriptor.excludeGroup("org.ow2.asm");
			});
		});

		// If a mavenCentral repo is already defined, remove the mojang repo and add it back before the mavenCentral repo so that it will be checked first.
		// See: https://github.com/FabricMC/fabric-loom/issues/621
		ArtifactRepository mavenCentral = repositories.findByName(ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME);

		if (mavenCentral != null) {
			repositories.remove(mojangRepo);
			repositories.add(repositories.indexOf(mavenCentral), mojangRepo);
		}

		repositories.mavenCentral();
	}

	private void declareLocalRepositories(RepositoryHandler repositories, LoomFiles files) {
		repositories.maven(repo -> {
			repo.setName("LoomLocalRemappedMods");
			repo.setUrl(files.getRemappedModCache());
		});

		repositories.maven(repo -> {
			repo.setName("LoomGlobalMinecraft");
			repo.setUrl(files.getGlobalMinecraftRepo());
		});

		repositories.maven(repo -> {
			repo.setName("LoomLocalMinecraft");
			repo.setUrl(files.getLocalMinecraftRepo());
		});

		repositories.maven(repo -> {
			repo.setName("LoomTransformedForgeDependencies");
			repo.setUrl(files.getForgeDependencyRepo());
		});
	}

	public static void setupForLegacyVersions(RepositoryHandler repositories) {
		// 1.4.7 contains an LWJGL version with an invalid maven pom, set the metadata sources to not use the pom for this version.
		repositories.named("Mojang", MavenArtifactRepository.class, repo -> {
			repo.metadataSources(sources -> {
				// Only use the maven artifact and not the pom or gradle metadata.
				sources.artifact();
				sources.ignoreGradleMetadataRedirection();
			});
		});
	}

	public static void forceLWJGLFromMavenCentral(RepositoryHandler repositories) {
		if (repositories.findByName("MavenCentralLWJGL") != null) {
			// Already applied.
			return;
		}

		// Force LWJGL from central, as it contains all the platform natives.
		MavenArtifactRepository central = repositories.maven(repo -> {
			repo.setName("MavenCentralLWJGL");
			repo.setUrl(ArtifactRepositoryContainer.MAVEN_CENTRAL_URL);
			repo.content(content -> {
				content.includeGroup("org.lwjgl");
			});
		});

		repositories.exclusiveContent(repository -> {
			repository.forRepositories(central);
			repository.filter(filter -> {
				filter.includeGroup("org.lwjgl");
			});
		});
	}
}
