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

package net.fabricmc.loom.configuration.providers.mappings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import com.google.common.base.Stopwatch;
import org.gradle.api.provider.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.api.mappings.intermediate.IntermediateMappingsProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJarMerger;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.stitch.commands.CommandGenerateIntermediary;

public abstract class GeneratedIntermediateMappingsProvider extends IntermediateMappingsProvider {
	private static final Logger LOGGER = LoggerFactory.getLogger(GeneratedIntermediateMappingsProvider.class);

	public Supplier<MinecraftProvider> minecraftProvider;

	public abstract Property<Boolean> getRefreshDeps();

	@Override
	public void provide(Path tinyMappings) throws IOException {
		if (Files.exists(tinyMappings) && !getRefreshDeps().get()) {
			return;
		}

		Stopwatch stopwatch = Stopwatch.createStarted();
		LOGGER.info("Generating intermediate mappings");

		MinecraftProvider minecraftProvider = this.minecraftProvider.get();

		// create a temporary folder into which stitch will output the v1 file
		// we cannot just create a temporary file directly, cause stitch will try to read it if it exists
		Path tmpFolder = Files.createTempDirectory("dummy-intermediary");
		Path tinyV1 = tmpFolder.resolve("intermediary-v1.tiny");
		Path mergedJar = tmpFolder.resolve("merged.jar");

		try {
			File clientJar = minecraftProvider.getMinecraftClientJar();
			File serverJar = minecraftProvider.getMinecraftServerJar();

			try (var jarMerger = new MinecraftJarMerger(clientJar, serverJar, mergedJar.toFile())) {
				jarMerger.enableSyntheticParamsOffset();
				jarMerger.merge();
			}

			CommandGenerateIntermediary command = new CommandGenerateIntermediary();

			try {
				command.run(new String[] {mergedJar.toAbsolutePath().toString(), tinyV1.toAbsolutePath().toString()});
			} catch (IOException e) {
				throw e;
			} catch (Exception e) {
				throw new IOException("Failed to generate intermediary", e);
			}

			try (MappingWriter writer = MappingWriter.create(tinyMappings, MappingFormat.TINY_2_FILE)) {
				MappingReader.read(tinyV1, writer);
			}
		} finally {
			Files.deleteIfExists(mergedJar);
			Files.deleteIfExists(tinyV1);
			Files.delete(tmpFolder);
		}

		LOGGER.info("Generated intermediate mappings in {}", stopwatch.stop());
	}

	@Override
	public String getName() {
		return "generated";
	}
}
