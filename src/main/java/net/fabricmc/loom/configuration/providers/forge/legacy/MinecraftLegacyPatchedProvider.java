/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
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

package net.fabricmc.loom.configuration.providers.forge.legacy;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;

import com.google.common.base.Stopwatch;
import dev.architectury.loom.legacyforge.CoreModManagerTransformer;
import dev.architectury.loom.util.TempFiles;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import net.fabricmc.loom.build.IntermediaryNamespaces;
import net.fabricmc.loom.configuration.providers.forge.ForgeProvider;
import net.fabricmc.loom.configuration.providers.forge.MinecraftPatchedProvider;
import net.fabricmc.loom.configuration.providers.forge.mcpconfig.McpExecutor;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DependencyDownloader;
import net.fabricmc.loom.util.ForgeToolExecutor;
import net.fabricmc.loom.util.LoomVersions;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.loom.util.service.ScopedSharedServiceManager;

public class MinecraftLegacyPatchedProvider extends MinecraftPatchedProvider {
	private Path minecraftOfficialJar;
	private Path minecraftPatchedClientOfficialJar;
	private Path minecraftPatchedServerOfficialJar;
	private Path minecraftPatchedOfficialJar;

	public MinecraftLegacyPatchedProvider(Project project, MinecraftProvider minecraftProvider, Type type) {
		super(project, minecraftProvider, type);
	}

	@Override
	protected void initPatchedFiles() {
		String forgeVersion = getExtension().getForgeProvider().getVersion().getCombined();
		Path forgeWorkingDir = ForgeProvider.getForgeCache(project);
		// Note: strings used instead of platform id since FML requires one of these exact strings
		// depending on the loader to recognise Minecraft.
		String patchId = "forge-" + forgeVersion + "-";

		minecraftProvider.setJarPrefix(patchId);

		final String intermediateId = getExtension().isNeoForge() ? "mojang" : "srg";
		minecraftPatchedClientOfficialJar = forgeWorkingDir.resolve("minecraft-client-official-patched.jar");
		minecraftPatchedServerOfficialJar = forgeWorkingDir.resolve("minecraft-server-official-patched.jar");
		minecraftPatchedOfficialJar = forgeWorkingDir.resolve("minecraft-" + type.id + "-official-patched.jar");
		minecraftPatchedIntermediateJar = forgeWorkingDir.resolve("minecraft-" + type.id + "-" + intermediateId + "-patched.jar");
		minecraftPatchedIntermediateAtJar = forgeWorkingDir.resolve("minecraft-" + type.id + "-" + intermediateId + "-at-patched.jar");
		minecraftPatchedJar = forgeWorkingDir.resolve("minecraft-" + type.id + "-patched.jar");
		minecraftClientExtra = forgeWorkingDir.resolve("client-extra.jar");
	}

	@Override
	protected Path[] getGlobalCaches() {
		return type != Type.MERGED ? new Path[] {
				minecraftOfficialJar,
				minecraftPatchedOfficialJar,
				minecraftPatchedIntermediateJar,
				minecraftPatchedIntermediateAtJar,
				minecraftPatchedJar,
				minecraftClientExtra,
		} : new Path[] {
				minecraftPatchedClientOfficialJar,
				minecraftPatchedServerOfficialJar,
				minecraftPatchedOfficialJar,
				minecraftPatchedIntermediateJar,
				minecraftPatchedIntermediateAtJar,
				minecraftPatchedJar,
				minecraftClientExtra,
		};
	}

	@Override
	public void provide() throws Exception {
		initPatchedFiles();
		checkCache();

		this.dirty = false;
	}

	@Override
	public void remapJar() throws Exception {
		// MergeTool produce classes with hashes that do not match pre FG3 Forge patches.
		// We instead have to patch split jars first.
		// We do not strip the client jar because legacy mappings do not contain unobfuscated classes which Forge patches.
		if (type != Type.MERGED) {
			if (Files.notExists(minecraftPatchedOfficialJar)) {
				this.dirty = true;

				try (var tempFiles = new TempFiles()) {
					McpExecutor executor = createMcpExecutor(tempFiles.directory("loom-mcp"));
					Path output = executor.enqueue(type == Type.CLIENT_ONLY ? "downloadClient" : "strip").execute();
					patchJars(output, minecraftPatchedOfficialJar, type);
				}
			}
		} else {
			if (Files.notExists(minecraftPatchedClientOfficialJar) || Files.notExists(minecraftPatchedServerOfficialJar)) {
				this.dirty = true;

				try (var tempFiles = new TempFiles()) {
					McpExecutor executor = createMcpExecutor(tempFiles.directory("loom-mcp"), Type.CLIENT_ONLY);
					Path output = executor.enqueue("downloadClient").execute();
					patchJars(output, minecraftPatchedClientOfficialJar, Type.CLIENT_ONLY);
				}

				try (var tempFiles = new TempFiles()) {
					McpExecutor executor = createMcpExecutor(tempFiles.directory("loom-mcp"), Type.SERVER_ONLY);
					Path output = executor.enqueue("strip").execute();
					patchJars(output, minecraftPatchedServerOfficialJar, Type.SERVER_ONLY);
				}
			}

			// Step 2.1: Merge
			if (dirty || Files.notExists(minecraftPatchedOfficialJar)) {
				this.dirty = true;

				try (var serviceManager = new ScopedSharedServiceManager()) {
					mergePatchedJars();
					mergeForge(minecraftPatchedOfficialJar);
				}
			}
		}

		// Step 2.2: Remap
		if (dirty || Files.notExists(minecraftPatchedIntermediateJar)) {
			this.dirty = true;

			try (var serviceManager = new ScopedSharedServiceManager()) {
				String targetNamespace = IntermediaryNamespaces.intermediary(project);
				remapJar(serviceManager, minecraftPatchedOfficialJar, minecraftPatchedIntermediateJar, "official", targetNamespace);
			}
		}

		// Step 3: Access transform
		if (dirty || Files.notExists(minecraftPatchedIntermediateAtJar)) {
			this.dirty = true;
			accessTransformForge();
		}

		super.remapJar();
	}

	void mergePatchedJars() throws Exception {
		logger.info(":merging jars");
		Stopwatch stopwatch = Stopwatch.createStarted();

		FileCollection classpath = DependencyDownloader.download(project, LoomVersions.MERGETOOL.mavenNotation() + ":fatjar", false, true);

		ForgeToolExecutor.exec(project, spec -> {
			spec.setClasspath(classpath);
			spec.args(
					"--client", minecraftPatchedClientOfficialJar.toAbsolutePath().toString(),
					"--server", minecraftPatchedServerOfficialJar.toAbsolutePath().toString(),
					"--ann", minecraftProvider.minecraftVersion(),
					"--output", minecraftPatchedOfficialJar.toAbsolutePath().toString(),
					"--inject", "false"
			);
		});

		logger.info(":merged jars in " + stopwatch);
	}

	@Override
	protected void mergeForge(Path input) throws Exception {
		super.mergeForge(input);

		// Older versions of Forge rely on utility classes from log4j-core 2.0-beta9 but we'll upgrade the runtime to a
		// release version (so we can use the TerminalConsoleAppender) where some of those classes have been moved from
		// a `helpers` to a `utils` package.
		// To allow Forge to work regardless, we'll re-package those helper classes into the forge jar.
		Path log4jBeta9 = project.getConfigurations()
				.getByName(Constants.Configurations.MINECRAFT_COMPILE_LIBRARIES)
				.getFiles()
				.stream()
				.map(File::toPath)
				.filter(it -> it.getFileName().toString().equals("log4j-core-2.0-beta9.jar"))
				.findAny()
				.orElse(null);
		if (log4jBeta9 != null) {
			Predicate<Path> isHelper = path -> path.startsWith("/org/apache/logging/log4j/core/helpers");
			walkFileSystems(log4jBeta9, input, isHelper, this::copyReplacing);
		}

		// While Forge will discover mods on the classpath, it won't do the same for ATs, coremods or tweakers.
		// ForgeGradle "solves" this problem using a huge, gross hack (GradleForgeHacks and related classes), and only
		// for ATs and coremods, not tweakers.
		// We instead patch support directly into Forge.
		ZipUtils.UnsafeUnaryOperator<byte[]> transform = original -> {
			ClassReader reader = new ClassReader(original);
			ClassWriter writer = new ClassWriter(reader, 0);
			reader.accept(new CoreModManagerTransformer(writer, getExtension().getForgeProvider().getVersion()), 0);
			return writer.toByteArray();
		};
		ZipUtils.transform(input, Map.of(
				CoreModManagerTransformer.FORGE_FILE, transform,
				CoreModManagerTransformer.CPW_FILE, transform
		));
	}
}
