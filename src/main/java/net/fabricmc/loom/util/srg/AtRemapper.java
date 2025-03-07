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

package net.fabricmc.loom.util.srg;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import java.util.jar.Manifest;

import dev.architectury.loom.metadata.ModMetadataFile;
import dev.architectury.loom.metadata.ModMetadataFiles;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.build.IntermediaryNamespaces;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.ModPlatform;
import net.fabricmc.loom.util.function.CollectionUtil;
import net.fabricmc.mappingio.tree.MappingTree;

public class AtRemapper {
	public static void remap(Project project, Path jar, MappingTree mappings) throws IOException {
		Logger logger = project.getLogger();
		String sourceNamespace = IntermediaryNamespaces.intermediary(project);
		LoomGradleExtension extension = LoomGradleExtension.get(project);

		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(jar, false)) {
			Set<Path> atPaths = new TreeSet<>();

			if (extension.isForge()) {
				atPaths.add(fs.getPath(Constants.Forge.ACCESS_TRANSFORMER_PATH));
			}

			if (extension.isNeoForge()) {
				ModMetadataFile modMetadata = ModMetadataFiles.fromJar(jar);

				if (modMetadata != null) {
					for (String atFile : modMetadata.getAccessTransformers(ModPlatform.NEOFORGE)) {
						atPaths.add(fs.getPath(atFile));
					}
				}
			}

			if (extension.isLegacyForgeLike()) {
				Path manifestPath = fs.getPath("META-INF", "MANIFEST.MF");

				if (Files.exists(manifestPath)) {
					Manifest manifest = new Manifest(new ByteArrayInputStream(Files.readAllBytes(manifestPath)));
					String atList = manifest.getMainAttributes().getValue(Constants.LegacyForge.ACCESS_TRANSFORMERS_MANIFEST_KEY);

					if (atList != null) {
						for (String atFile : atList.split(" ")) {
							atPaths.add(fs.getPath("META-INF", atFile));
						}
					}
				}

				Files.walk(fs.getPath("/"), 1).filter(path -> path.toString().endsWith("at.cfg")).forEach(atPaths::add);
			}

			for (Path atPath : atPaths) {
				if (Files.exists(atPath)) {
					String atContent = Files.readString(atPath, StandardCharsets.UTF_8);

					String[] lines = atContent.split("\n");
					List<String> output = new ArrayList<>();

					for (int i = 0; i < lines.length; i++) {
						String line = lines[i].split("#", 2)[0].trim();

						if (line.isBlank()) {
							continue;
						}

						String[] parts = line.split("\\s+");

						if (parts.length < 2) {
							logger.warn("Invalid AT Line: " + line);
							output.add(line);
							continue;
						}

						String className = parts[1].replace('.', '/');
						Optional<MappingTree.ClassMapping> classMapping = CollectionUtil.find(
								mappings.getClasses(),
								def -> def.getName(sourceNamespace).equals(className)
						);
						parts[1] = classMapping.map(def -> def.getName("named")).orElse(className).replace('/', '.');

						if (parts.length >= 3) {
							if (parts[2].contains("(")) {
								String methodName = parts[2].substring(0, parts[2].indexOf('('));
								String descriptor = parts[2].substring(parts[2].indexOf('('));
								parts[2] = classMapping.flatMap(def -> CollectionUtil.find(
										def.getMethods(),
										mDef -> mDef.getName(sourceNamespace).equals(methodName)
								)).map(def -> def.getName("named")).orElse(methodName) + remapDescriptor(descriptor, s -> {
									return CollectionUtil.find(
											mappings.getClasses(),
											def -> def.getName(sourceNamespace).equals(s)
									).map(def -> def.getName("named")).orElse(s);
								});
							} else {
								parts[2] = classMapping.flatMap(def -> CollectionUtil.find(
										def.getFields(),
										fDef -> fDef.getName(sourceNamespace).equals(parts[2])
								)).map(def -> def.getName("named")).orElse(parts[2]);
							}
						}

						output.add(String.join(" ", parts));
					}

					Files.write(atPath, String.join("\n", output).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
				}
			}
		}
	}

	private static String remapDescriptor(String original, UnaryOperator<String> classMappings) {
		try {
			StringReader reader = new StringReader(original);
			StringBuilder result = new StringBuilder();
			boolean insideClassName = false;
			StringBuilder className = new StringBuilder();

			while (true) {
				int c = reader.read();

				if (c == -1) {
					break;
				}

				if ((char) c == ';') {
					insideClassName = false;
					result.append(classMappings.apply(className.toString()));
				}

				if (insideClassName) {
					className.append((char) c);
				} else {
					result.append((char) c);
				}

				if (!insideClassName && (char) c == 'L') {
					insideClassName = true;
					className.setLength(0);
				}
			}

			return result.toString();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}
}
