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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.crystaelix.loom.util.McpMappingsScanner;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.jspecify.annotations.Nullable;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public class MCPReader {
	public static MemoryMappingTree read(Path srgPath, Path mcpPath, boolean dropNoneIntermediaryRoots, @Nullable Supplier<MemoryMappingTree> intermediarySupplier) throws IOException {
		MemoryMappingTree mcpTree = readSrg(srgPath, false, null);

		Map<String, String> memberMappings = new HashMap<>();
		Map<String, String> comments = new HashMap<>();
		Map<String, Map<Integer, String>> paramMappings = new HashMap<>();
		readMcp(mcpPath, memberMappings, comments, paramMappings);
		mergeMcp(mcpTree, memberMappings, comments, paramMappings);

		if (intermediarySupplier == null) {
			return mcpTree;
		}

		MemoryMappingTree mappingTree = new MemoryMappingTree();
		intermediarySupplier.get().accept(mappingTree);
		mcpTree.accept(mappingTree);

		if (!dropNoneIntermediaryRoots) {
			return mappingTree;
		}

		MemoryMappingTree droppedTree = new MemoryMappingTree();
		MappingVisitor officialSwitch = new MappingSourceNsSwitch(droppedTree, MappingsNamespace.OFFICIAL.toString(), false);
		MappingVisitor intermediarySwitch = new MappingSourceNsSwitch(officialSwitch, MappingsNamespace.INTERMEDIARY.toString(), true);
		mappingTree.accept(intermediarySwitch);
		return droppedTree;
	}

	public static MemoryMappingTree readSrg(Path srgPath, boolean dropNoneIntermediaryRoots, @Nullable Supplier<MemoryMappingTree> intermediarySupplier) throws IOException {
		MemoryMappingTree srgTree = new MemoryMappingTree();

		if (ZipUtils.isZip(srgPath)) {
			try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(srgPath)) {
				readSrg(new McpMappingsScanner(fs.getPath("/")), srgTree);
			}
		} else if (Files.isDirectory(srgPath)) {
			readSrg(new McpMappingsScanner(srgPath), srgTree);
		} else {
			MappingReader.read(srgPath, new SrcDstRenamingVisitor(srgTree));
		}

		if (intermediarySupplier == null) {
			return srgTree;
		}

		MemoryMappingTree mappingTree = new MemoryMappingTree();
		intermediarySupplier.get().accept(mappingTree);
		srgTree.accept(mappingTree);

		if (!dropNoneIntermediaryRoots) {
			return mappingTree;
		}

		MemoryMappingTree droppedTree = new MemoryMappingTree();
		MappingVisitor officialSwitch = new MappingSourceNsSwitch(droppedTree, MappingsNamespace.OFFICIAL.toString(), false);
		MappingVisitor intermediarySwitch = new MappingSourceNsSwitch(officialSwitch, MappingsNamespace.INTERMEDIARY.toString(), true);
		mappingTree.accept(intermediarySwitch);
		return droppedTree;
	}

	public static void readSrg(McpMappingsScanner scan, MappingVisitor mappingVisitor) throws IOException {
		MemoryMappingTree srgTree = new MemoryMappingTree();
		Optional<Path> mappingPath = scan.get("joined.tsrg");

		if (mappingPath.isPresent()) {
			if (MappingReader.detectFormat(mappingPath.get()) == MappingFormat.TSRG_2_FILE) {
				MappingReader.read(mappingPath.get(), new ArgDroppingVisitor(mappingVisitor));
				return;
			} else {
				MappingReader.read(mappingPath.get(), new SrcDstRenamingVisitor(srgTree));
			}
		} else {
			mappingPath = scan.get("joined.srg");

			if (mappingPath.isEmpty()) {
				mappingPath = scan.get("packaged.srg");
			}

			MappingReader.read(mappingPath.orElseThrow(() -> new IllegalArgumentException("Could not resolve srg")), new SrcDstRenamingVisitor(srgTree));
		}

		srgTree.setDstNamespaces(List.of(MappingsNamespace.SRG.toString(), "id"));

		Optional<Path> constructorsPath = scan.get("constructors.txt");

		if (constructorsPath.isPresent()) {
			Files.lines(constructorsPath.get()).forEach(line -> {
				String[] split = line.split(" ", 3);
				MappingTree.ClassMapping classDef = srgTree.getClass(split[1], 0);

				if (classDef != null) {
					classDef.addMethod(new BasicConstructor(classDef, srgTree.mapDesc(split[2], 0, -1), split[0]));
				}
			});
		} else {
			constructorsPath = scan.get("joined.exc");

			if (constructorsPath.isEmpty()) {
				constructorsPath = scan.get("packaged.exc");
			}

			if (constructorsPath.isPresent()) {
				Pattern initExcPattern = Pattern.compile("(.*)\\.<init>(\\(.*)=\\|p_i(\\d+).*");
				Files.lines(constructorsPath.get()).forEach(line -> {
					Matcher matcher = initExcPattern.matcher(line);

					if (matcher.matches()) {
						MappingTree.ClassMapping classDef = srgTree.getClass(matcher.group(1), 0);

						if (classDef != null) {
							classDef.addMethod(new BasicConstructor(classDef, srgTree.mapDesc(matcher.group(2), 0, -1), matcher.group(3)));
						}
					}
				});
			}
		}

		Pattern fieldPattern = Pattern.compile("field_(\\d+)_.*");
		Pattern methodPattern = Pattern.compile("func_(\\d+)_.*");

		for (MappingTree.ClassMapping classDef : srgTree.getClasses()) {
			if (classDef.getDstName(1) == null) {
				classDef.setDstName("_", 1);
			}

			for (MappingTree.FieldMapping fieldDef : classDef.getFields()) {
				if (fieldDef.getDstName(1) == null) {
					Matcher matcher = fieldPattern.matcher(fieldDef.getDstName(0));
					fieldDef.setDstName(matcher.matches() ? matcher.group(1) : "_", 1);
				}
			}

			for (MappingTree.MethodMapping methodDef : classDef.getMethods()) {
				if (methodDef.getDstName(1) == null) {
					Matcher matcher = methodPattern.matcher(methodDef.getDstName(0));
					methodDef.setDstName(matcher.matches() ? matcher.group(1) : "_", 1);
				}
			}
		}

		srgTree.accept(mappingVisitor);
	}

	private static void readMcp(Path mcpPath, Map<String, String> memberMappings, Map<String, String> comments, Map<String, Map<Integer, String>> paramMappings) throws IOException {
		if (ZipUtils.isZip(mcpPath)) {
			try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(mcpPath)) {
				readMcp(new McpMappingsScanner(fs.getPath("/")), memberMappings, comments, paramMappings);
			}
		} else if (Files.isDirectory(mcpPath)) {
			readMcp(new McpMappingsScanner(mcpPath), memberMappings, comments, paramMappings);
		} else {
			throw new IllegalArgumentException("Can only read MCP mappings from archive or directory");
		}
	}

	private static void readMcp(McpMappingsScanner scan, Map<String, String> memberMappings, Map<String, String> comments, Map<String, Map<Integer, String>> paramMappings) throws IOException {
		Optional<Path> fields = scan.get("fields.csv");
		Optional<Path> methods = scan.get("methods.csv");
		Optional<Path> params = scan.get("params.csv");
		Pattern paramsPattern = Pattern.compile("p_i?(\\d+)_(\\d+)_?");

		if (fields.isPresent()) {
			try (CSVReader reader = new CSVReader(Files.newBufferedReader(fields.get(), StandardCharsets.UTF_8))) {
				reader.readNext();
				String[] line;

				while ((line = reader.readNext()) != null) {
					memberMappings.put(line[0], line[1]);

					if (!line[3].isBlank()) {
						comments.put(line[0], line[3]);
					}
				}
			} catch (CsvValidationException e) {
				throw new IOException(e);
			}
		}

		if (methods.isPresent()) {
			try (CSVReader reader = new CSVReader(Files.newBufferedReader(methods.get(), StandardCharsets.UTF_8))) {
				reader.readNext();
				String[] line;

				while ((line = reader.readNext()) != null) {
					memberMappings.put(line[0], line[1]);

					if (!line[3].isBlank()) {
						comments.put(line[0], line[3]);
					}
				}
			} catch (CsvValidationException e) {
				throw new IOException(e);
			}
		}

		if (params.isPresent()) {
			try (CSVReader reader = new CSVReader(Files.newBufferedReader(params.get(), StandardCharsets.UTF_8))) {
				reader.readNext();
				String[] line;

				while ((line = reader.readNext()) != null) {
					Matcher param = paramsPattern.matcher(line[0]);

					if (param.matches()) {
						int lvIndex = Integer.parseInt(param.group(2));
						paramMappings.computeIfAbsent(param.group(1), s -> new HashMap<>()).put(lvIndex, line[1]);
					}
				}
			} catch (CsvValidationException e) {
				throw new IOException(e);
			}
		}
	}

	private static void mergeMcp(MemoryMappingTree mappingTree, Map<String, String> memberMappings, Map<String, String> comments, Map<String, Map<Integer, String>> paramMappings) {
		mappingTree.setDstNamespaces(List.of(MappingsNamespace.SRG.toString(), MappingsNamespace.NAMED.toString(), "id"));

		for (MappingTree.ClassMapping classDef : mappingTree.getClasses()) {
			classDef.setDstName(classDef.getName(0), 1);

			for (MappingTree.FieldMapping fieldDef : classDef.getFields()) {
				String srgName = fieldDef.getDstName(0);
				fieldDef.setDstName(memberMappings.getOrDefault(srgName, srgName), 1);

				if (comments.containsKey(srgName)) {
					fieldDef.setComment(comments.get(srgName));
				}
			}

			for (MappingTree.MethodMapping methodDef : classDef.getMethods()) {
				String srgName = methodDef.getDstName(0);
				String id = methodDef.getDstName(2);
				methodDef.setDstName(memberMappings.getOrDefault(srgName, srgName), 1);

				if (comments.containsKey(srgName)) {
					methodDef.setComment(comments.get(srgName));
				}

				if (paramMappings.containsKey(id)) {
					for (Map.Entry<Integer, String> entry : paramMappings.get(id).entrySet()) {
						methodDef.addArg(new BasicMethodArg(methodDef, entry.getKey(), entry.getValue()));
					}
				}
			}
		}

		mappingTree.setDstNamespaces(List.of(MappingsNamespace.SRG.toString(), MappingsNamespace.NAMED.toString()));
	}

	private static final class SrcDstRenamingVisitor extends ForwardingMappingVisitor {
		SrcDstRenamingVisitor(MappingVisitor next) {
			super(next);
		}

		@Override
		public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) throws IOException {
			List<String> newDstNamespaces = new ArrayList<>(dstNamespaces);
			newDstNamespaces.set(0, MappingsNamespace.SRG.toString());
			super.visitNamespaces(MappingsNamespace.OFFICIAL.toString(), newDstNamespaces);
		}
	}

	private static final class ArgDroppingVisitor extends ForwardingMappingVisitor {
		ArgDroppingVisitor(MappingVisitor next) {
			super(next);
		}

		@Override
		public boolean visitMethodArg(int argPosition, int lvIndex, @Nullable String srcName) throws IOException {
			return false;
		}
	}

	private record BasicConstructor(MappingTree.ClassMapping parent, String srcDesc, String id) implements MappingTree.MethodMapping {
		@Override
		public MappingTree.ClassMapping getOwner() {
			return parent;
		}

		@Override
		public MappingTree getTree() {
			return parent.getTree();
		}

		@Override
		public String getSrcName() {
			return "<init>";
		}

		@Override
		public @Nullable String getSrcDesc() {
			return srcDesc;
		}

		@Override
		public @Nullable String getDstName(int namespace) {
			return namespace == 0 ? "<init>" : namespace == 1 ? id : null;
		}

		@Override
		public Collection<? extends MappingTree.MethodArgMapping> getArgs() {
			return List.of();
		}

		@Override
		public MappingTree.@Nullable MethodArgMapping getArg(int argPosition, int lvIndex, @Nullable String srcName) {
			return null;
		}

		@Override
		public Collection<? extends MappingTree.MethodVarMapping> getVars() {
			return List.of();
		}

		@Override
		public MappingTree.@Nullable MethodVarMapping getVar(int lvtRowIndex, int lvIndex, int startOpIdx, int endOpIdx, @Nullable String srcName) {
			return null;
		}

		@Override
		public @Nullable String getComment() {
			return null;
		}

		@Override
		public void setSrcDesc(String desc) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setDstName(String name, int namespace) {
			throw new UnsupportedOperationException();
		}

		@Override
		public MappingTree.MethodArgMapping addArg(MappingTree.MethodArgMapping arg) {
			throw new UnsupportedOperationException();
		}

		@Override
		public MappingTree.@Nullable MethodArgMapping removeArg(int argPosition, int lvIndex, @Nullable String srcName) {
			throw new UnsupportedOperationException();
		}

		@Override
		public MappingTree.MethodVarMapping addVar(MappingTree.MethodVarMapping var) {
			throw new UnsupportedOperationException();
		}

		@Override
		public MappingTree.@Nullable MethodVarMapping removeVar(int lvtRowIndex, int lvIndex, int startOpIdx, int endOpIdx, @Nullable String srcName) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setComment(String comment) {
			throw new UnsupportedOperationException();
		}
	}

	// Used for MethodMapping.addArg
	private record BasicMethodArg(MappingTree.MethodMapping parent, int lvIndex, String name) implements MappingTree.MethodArgMapping {
		@Override
		public MappingTree.MethodMapping getMethod() {
			return parent;
		}

		@Override
		public MappingTree getTree() {
			return parent.getTree();
		}

		@Override
		public int getArgPosition() {
			return -1;
		}

		@Override
		public int getLvIndex() {
			return lvIndex;
		}

		@Override
		public String getSrcName() {
			return null;
		}

		@Override
		public @Nullable String getDstName(int namespace) {
			return namespace == 1 ? name : null;
		}

		@Override
		public @Nullable String getComment() {
			return null;
		}

		@Override
		public void setArgPosition(int position) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setLvIndex(int index) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setDstName(String name, int namespace) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setComment(String comment) {
			throw new UnsupportedOperationException();
		}
	}
}
