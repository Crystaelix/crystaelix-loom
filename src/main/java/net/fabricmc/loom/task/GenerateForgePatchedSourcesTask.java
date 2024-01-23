/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022-2023 FabricMC
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

package net.fabricmc.loom.task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import codechicken.diffpatch.cli.CliOperation;
import codechicken.diffpatch.cli.PatchOperation;
import codechicken.diffpatch.util.LoggingOutputStream;
import codechicken.diffpatch.util.PatchMode;
import com.google.common.base.Stopwatch;
import dev.architectury.loom.util.TempFiles;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.configuration.processors.MinecraftJarProcessorManager;
import net.fabricmc.loom.configuration.providers.forge.ForgeUserdevProvider;
import net.fabricmc.loom.configuration.providers.forge.MinecraftPatchedProvider;
import net.fabricmc.loom.configuration.providers.forge.mcpconfig.McpExecutor;
import net.fabricmc.loom.configuration.providers.forge.mcpconfig.steplogic.ConstantLogic;
import net.fabricmc.loom.configuration.sources.ForgeSourcesRemapper;
import net.fabricmc.loom.util.DependencyDownloader;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.ForgeToolExecutor;
import net.fabricmc.loom.util.LoomVersions;
import net.fabricmc.loom.util.SourceRemapper;
import net.fabricmc.loom.util.service.ScopedSharedServiceManager;
import net.fabricmc.loom.util.service.SharedServiceManager;

// TODO: NeoForge support
public abstract class GenerateForgePatchedSourcesTask extends AbstractLoomTask {
	/**
	 * The SRG Minecraft file produced by the MCP executor.
	 */
	@InputFile
	public abstract RegularFileProperty getInputJar();

	/**
	 * The runtime Minecraft file.
	 */
	@InputFile
	public abstract RegularFileProperty getRuntimeJar();

	/**
	 * The source jar.
	 */
	@OutputFile
	public abstract RegularFileProperty getOutputJar();

	public GenerateForgePatchedSourcesTask() {
		getOutputs().upToDateWhen((o) -> false);
		getOutputJar().fileProvider(getProject().provider(() -> GenerateSourcesTask.getMappedJarFileWithSuffix(getRuntimeJar(), "-sources.jar")));
	}

	@TaskAction
	public void run() throws IOException {
		// Check that the jar is not processed
		final @Nullable MinecraftJarProcessorManager jarProcessorManager = MinecraftJarProcessorManager.create(getProject());

		if (jarProcessorManager != null) {
			throw new UnsupportedOperationException("Cannot run Forge's patched decompilation with a processed Minecraft jar");
		}

		try (var tempFiles = new TempFiles(); var serviceManager = new ScopedSharedServiceManager()) {
			Path cache = tempFiles.directory("loom-decompilation");

			// Transform game jar before decompiling
			Path accessTransformed = cache.resolve("access-transformed.jar");
			MinecraftPatchedProvider.accessTransform(getProject(), getInputJar().get().getAsFile().toPath(), accessTransformed);
			Path sideAnnotationStripped = cache.resolve("side-annotation-stripped.jar");
			stripSideAnnotations(accessTransformed, sideAnnotationStripped);

			// Step 1: decompile and patch with MCP patches
			Path rawDecompiled = decompileAndPatch(cache, sideAnnotationStripped);
			// Step 2: patch with Forge patches
			getLogger().lifecycle(":applying Forge patches");
			Path patched = sourcePatch(cache, rawDecompiled);
			// Step 3: remap
			remap(patched, serviceManager);
			// Step 4: add Forge's own sources
			ForgeSourcesRemapper.addForgeSources(getProject(), serviceManager, getOutputJar().get().getAsFile().toPath());
		}
	}

	private Path decompileAndPatch(Path cache, Path gameJar) throws IOException {
		Path mcpCache = cache.resolve("mcp");
		Files.createDirectory(mcpCache);

		MinecraftPatchedProvider patchedProvider = MinecraftPatchedProvider.get(getProject());
		McpExecutor mcp = patchedProvider.createMcpExecutor(mcpCache);
		mcp.setStepLogicProvider((name, type) -> {
			if (name.equals("rename")) {
				return Optional.of(new ConstantLogic(() -> gameJar));
			}

			return Optional.empty();
		});
		mcp.enqueue("decompile");
		mcp.enqueue("patch");
		return mcp.execute();
	}

	private Path sourcePatch(Path cache, Path rawDecompiled) throws IOException {
		ForgeUserdevProvider userdev = getExtension().getForgeUserdevProvider();
		String patchPathInZip = userdev.getConfig().patches();
		Path output = cache.resolve("patched.jar");
		Path rejects = cache.resolve("rejects");

		CliOperation.Result<PatchOperation.PatchesSummary> result = PatchOperation.builder()
				.logTo(new LoggingOutputStream(getLogger(), LogLevel.INFO))
				.basePath(rawDecompiled)
				.patchesPath(userdev.getUserdevJar().toPath())
				.patchesPrefix(patchPathInZip)
				.outputPath(output)
				.mode(PatchMode.ACCESS)
				.rejectsPath(rejects)
				.aPrefix(userdev.getConfig().patchesOriginalPrefix().orElseThrow())
				.bPrefix(userdev.getConfig().patchesModifiedPrefix().orElseThrow())
				.build()
				.operate();

		if (result.exit != 0) {
			throw new RuntimeException("Could not patch " + rawDecompiled + "; rejects saved to " + rejects.toAbsolutePath());
		}

		return output;
	}

	private void remap(Path input, SharedServiceManager serviceManager) {
		SourceRemapper remapper = new SourceRemapper(getProject(), serviceManager, "srg", "named");
		remapper.scheduleRemapSources(input.toFile(), getOutputJar().get().getAsFile(), false, true, () -> {
		});
		remapper.remapAll();
	}

	private void stripSideAnnotations(Path input, Path output) throws IOException {
		final ForgeUserdevProvider userdevProvider = getExtension().getForgeUserdevProvider();
		final List<String> sass = userdevProvider.getConfig().sass();

		if (sass.isEmpty()) {
			Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
			return;
		}

		final Stopwatch stopwatch = Stopwatch.createStarted();
		getLogger().lifecycle(":stripping side annotations");

		try (var tempFiles = new TempFiles()) {
			final List<Path> sasPaths = new ArrayList<>();

			try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(userdevProvider.getUserdevJar(), false)) {
				for (String sasPath : sass) {
					try {
						final Path from = fs.getPath(sasPath);
						final Path to = tempFiles.file(null, ".sas");
						Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
						sasPaths.add(to);
					} catch (IOException e) {
						throw new IOException("Could not extract SAS " + sasPath);
					}
				}
			}

			final FileCollection classpath = DependencyDownloader.download(getProject(), LoomVersions.MERGETOOL.mavenNotation() + ":fatjar", false, true);

			ForgeToolExecutor.exec(getProject(), spec -> {
				spec.setClasspath(classpath);
				spec.args(
						"--strip",
						"--input", input.toAbsolutePath().toString(),
						"--output", output.toAbsolutePath().toString()
				);

				for (Path sasPath : sasPaths) {
					spec.args("--data", sasPath.toAbsolutePath().toString());
				}
			});
		}

		getLogger().lifecycle(":side annotations stripped in " + stopwatch.stop());
	}
}
