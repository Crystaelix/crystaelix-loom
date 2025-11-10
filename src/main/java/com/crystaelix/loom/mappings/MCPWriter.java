package com.crystaelix.loom.mappings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

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

					if (!Objects.equals(srgName, name)) {
						fieldsWriter.writeNext(new String[] {srgName, name, "2", fieldDef.getComment()}, false);
					}
				}

				for (MappingTree.MethodMapping methodDef : classDef.getMethods()) {
					String srgName = methodDef.getName(srgIndex);
					String name = methodDef.getName(namedIndex);

					if (!Objects.equals(srgName, name)) {
						methodsWriter.writeNext(new String[] {srgName, name, "2", methodDef.getComment()}, false);
					}
				}
			}
		}
	}
}
