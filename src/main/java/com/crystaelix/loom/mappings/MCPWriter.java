/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

package com.crystaelix.loom.mappings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.opencsv.CSVWriter;

import net.fabricmc.mappingio.tree.MappingTree;

public class MCPWriter {
	private final Path dir;

	public MCPWriter(Path dir) {
		this.dir = dir;
	}

	public void write(MappingTree mappings) throws IOException {
		Path fields = dir.resolve("fields.csv");
		Path methods = dir.resolve("methods.csv");

		int srgIndex = mappings.getNamespaceId("srg");
		int namedIndex = mappings.getNamespaceId("named");

		Set<String> written = new HashSet<>();

		try (
				CSVWriter fieldsWriter = new CSVWriter(Files.newBufferedWriter(fields, StandardCharsets.UTF_8));
				CSVWriter methodsWriter = new CSVWriter(Files.newBufferedWriter(methods, StandardCharsets.UTF_8));
		) {
			fieldsWriter.writeNext(new String[] {"searge", "name", "side", "desc"}, false);
			methodsWriter.writeNext(new String[] {"searge", "name", "side", "desc"}, false);

			for (MappingTree.ClassMapping classDef : mappings.getClasses()) {
				for (MappingTree.FieldMapping fieldDef : classDef.getFields()) {
					String srgName = fieldDef.getName(srgIndex);
					String name = fieldDef.getName(namedIndex);

					if (!Objects.equals(srgName, name) && !written.contains(srgName)) {
						fieldsWriter.writeNext(new String[] {srgName, name, "2", fieldDef.getComment()}, false);
						written.add(srgName);
					}
				}

				for (MappingTree.MethodMapping methodDef : classDef.getMethods()) {
					String srgName = methodDef.getName(srgIndex);
					String name = methodDef.getName(namedIndex);

					if (!Objects.equals(srgName, name) && !written.contains(srgName)) {
						methodsWriter.writeNext(new String[] {srgName, name, "2", methodDef.getComment()}, false);
						written.add(srgName);
					}
				}
			}
		}
	}
}
