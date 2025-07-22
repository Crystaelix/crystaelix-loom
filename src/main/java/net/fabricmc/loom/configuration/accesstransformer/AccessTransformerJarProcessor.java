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

package net.fabricmc.loom.configuration.accesstransformer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.Manifest;

import javax.inject.Inject;

import com.google.common.hash.Hashing;
import com.google.common.io.MoreFiles;
import dev.architectury.at.AccessTransformSet;
import dev.architectury.at.io.AccessTransformFormats;
import dev.architectury.loom.forge.tool.ForgeToolValueSource;
import dev.architectury.loom.util.TempFiles;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.api.processor.MinecraftJarProcessor;
import net.fabricmc.loom.api.processor.ProcessorContext;
import net.fabricmc.loom.api.processor.SpecContext;
import net.fabricmc.loom.build.IntermediaryNamespaces;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DependencyDownloader;
import net.fabricmc.loom.util.ExceptionUtil;
import net.fabricmc.loom.util.LoomVersions;
import net.fabricmc.loom.util.fmj.FabricModJson;
import net.fabricmc.loom.util.fmj.ModMetadataFabricModJson;

public class AccessTransformerJarProcessor implements MinecraftJarProcessor<AccessTransformerJarProcessor.Spec> {
	private static final Logger LOGGER = Logging.getLogger(AccessTransformerJarProcessor.class);
	private final String name;
	private final Project project;
	private final boolean includeTransitive;
	private final Iterable<File> localAccessTransformers;

	@Inject
	public AccessTransformerJarProcessor(String name, Project project, boolean includeTransitive, Iterable<File> localAccessTransformers) {
		this.name = name;
		this.project = project;
		this.includeTransitive = includeTransitive;
		this.localAccessTransformers = localAccessTransformers;
	}

	@Override
	public @Nullable AccessTransformerJarProcessor.Spec buildSpec(SpecContext context) {
		final List<AccessTransformerEntry> entries = new ArrayList<>();

		for (File atFile : localAccessTransformers) {
			final Path atPath = atFile.toPath();
			final String hash;

			try {
				hash = MoreFiles.asByteSource(atPath).hash(Hashing.sha256()).toString();
			} catch (IOException e) {
				throw new UncheckedIOException("Could not compute AT hash", e);
			}

			entries.add(new AccessTransformerEntry.Standalone(atPath, hash));
		}

		for (FabricModJson localMod : context.localMods()) {
			addEntries(localMod, entries);
		}

		if (includeTransitive) {
			for (FabricModJson fabricModJson : context.modDependencies()) {
				addEntries(fabricModJson, entries);
			}
		}

		return !entries.isEmpty() ? new Spec(entries) : null;
	}

	private void addEntries(FabricModJson fabricModJson, List<AccessTransformerEntry> entries) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		Set<String> atPaths = new TreeSet<>();
		atPaths.add(Constants.Forge.ACCESS_TRANSFORMER_PATH);

		if (fabricModJson instanceof ModMetadataFabricModJson modMetadataFabricModJson) {
			atPaths.addAll(modMetadataFabricModJson.getModMetadata().getAccessTransformers(extension.getPlatform().get()));
		}

		try {
			Manifest manifest = new Manifest(new ByteArrayInputStream(fabricModJson.getSource().read("META-INF/MANIFEST.MF")));
			String atList = manifest.getMainAttributes().getValue(Constants.LegacyForge.ACCESS_TRANSFORMERS_MANIFEST_KEY);

			if (atList != null) {
				for (String atFile : atList.split(" ")) {
					atPaths.add("META-INF/" + atFile);
				}
			}
		} catch (FileNotFoundException | NoSuchFileException ignored) {
			;
		} catch (IOException e) {
			throw ExceptionUtil.createDescriptiveWrapper(UncheckedIOException::new, "Could not read manifest", e);
		}

		for (String atPath : atPaths) {
			try {
				byte[] bytes = fabricModJson.getSource().read(atPath);
				String hash = Hashing.sha256().hashBytes(bytes).toString();
				entries.add(new AccessTransformerEntry.Mod(fabricModJson, atPath, hash));
			} catch (FileNotFoundException | NoSuchFileException ignored) {
				;
			} catch (IOException e) {
				throw ExceptionUtil.createDescriptiveWrapper(UncheckedIOException::new, "Could not read accesstransformer.cfg", e);
			}
		}
	}

	@Override
	public void processJar(Path jar, Spec spec, ProcessorContext context) throws IOException {
		try (var tempFiles = new TempFiles()) {
			LOGGER.lifecycle(":applying project access transformers");
			final Path tempInput = tempFiles.file("input", ".jar");
			Files.copy(jar, tempInput, StandardCopyOption.REPLACE_EXISTING);
			final Path atPath = mergeAndRemapAccessTransformers(context, spec.accessTransformers(), tempFiles);

			executeAt(project, tempInput, jar, args -> {
				args.add("--atFile");
				args.add(atPath.toAbsolutePath().toString());
			});
		} catch (IOException e) {
			throw ExceptionUtil.createDescriptiveWrapper(UncheckedIOException::new, "Could not access transform " + jar.toAbsolutePath(), e);
		}
	}

	private Path mergeAndRemapAccessTransformers(ProcessorContext context, List<AccessTransformerEntry> accessTransformers, TempFiles tempFiles) throws IOException {
		AccessTransformSet accessTransformSet = AccessTransformSet.create();

		for (AccessTransformerEntry entry : accessTransformers) {
			try (Reader reader = entry.openReader()) {
				accessTransformSet.merge(AccessTransformFormats.FML.read(reader));
			} catch (IOException e) {
				throw new IOException("Could not read access transformer " + entry, e);
			}
		}

		accessTransformSet = accessTransformSet.remap(context.getMappings(), IntermediaryNamespaces.intermediary(project), MappingsNamespace.NAMED.toString());

		final Path accessTransformerPath = tempFiles.file("accesstransformer-merged", ".cfg");

		try {
			AccessTransformFormats.FML.write(accessTransformerPath, accessTransformSet);
		} catch (IOException e) {
			throw new IOException("Could not write access transformers to " + accessTransformerPath, e);
		}

		return accessTransformerPath;
	}

	@Override
	public String getName() {
		return name;
	}

	public static void executeAt(Project project, Path input, Path output, AccessTransformerConfiguration configuration) throws IOException {
		LoomVersions accessTransformer = chooseAccessTransformer(project);
		String mainClass = accessTransformer == LoomVersions.ACCESS_TRANSFORMERS_NEO ? "net.neoforged.accesstransformer.cli.TransformerProcessor"
				: "net.minecraftforge.accesstransformer.TransformerProcessor";
		FileCollection classpath = new DependencyDownloader(project)
				.add(accessTransformer.mavenNotation())
				.add(LoomVersions.ASM.mavenNotation())
				.platform(LoomVersions.ACCESS_TRANSFORMERS_LOG4J_BOM.mavenNotation())
				.download();
		List<String> args = new ArrayList<>();
		args.add("--inJar");
		args.add(input.toAbsolutePath().toString());
		args.add("--outJar");
		args.add(output.toAbsolutePath().toString());

		configuration.apply(args);

		ForgeToolValueSource.exec(project, spec -> {
			spec.getMainClass().set(mainClass);
			spec.setArgs(args);
			spec.setClasspath(classpath);
		});
	}

	private static LoomVersions chooseAccessTransformer(Project project) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);

		if (extension.isNeoForge()) {
			MinecraftVersionMeta.JavaVersion javaVersion = extension.getMinecraftProvider().getVersionInfo().javaVersion();

			if (javaVersion != null && javaVersion.majorVersion() >= 21) {
				return LoomVersions.ACCESS_TRANSFORMERS_NEO;
			}
		}

		return LoomVersions.ACCESS_TRANSFORMERS;
	}

	@FunctionalInterface
	public interface AccessTransformerConfiguration {
		void apply(List<String> args) throws IOException;
	}

	public record Spec(List<AccessTransformerEntry> accessTransformers) implements MinecraftJarProcessor.Spec {
	}
}
