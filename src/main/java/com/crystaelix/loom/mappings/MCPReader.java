package com.crystaelix.loom.mappings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
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
		MappingVisitor mappingVisitor = new ForwardingMappingVisitor(srgTree) {
			@Override
			public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) throws IOException {
				List<String> newDstNamespaces = new ArrayList<>(dstNamespaces);
				newDstNamespaces.set(0, MappingsNamespace.SRG.toString());
				super.visitNamespaces(MappingsNamespace.OFFICIAL.toString(), newDstNamespaces);
			}
		};

		if (!ZipUtils.isZip(srgPath)) {
			MappingReader.read(srgPath, mappingVisitor);
		} else {
			try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(srgPath)) {
				McpMappingsScanner scan = new McpMappingsScanner(fs.getPath("/"));
				Optional<Path> mappingPath = scan.get("joined.tsrg");

				if (mappingPath.isPresent()) {
					MappingReader.read(mappingPath.get(), mappingVisitor);
				} else {
					mappingPath = scan.get("joined.srg");

					if (mappingPath.isEmpty()) {
						mappingPath = scan.get("packaged.srg");
					}

					MappingReader.read(mappingPath.orElseThrow(() -> new RuntimeException("Could not resolve srg")), mappingVisitor);
				}
			}
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

	private static void readMcp(Path mcpPath, Map<String, String> memberMappings, Map<String, String> comments, Map<String, Map<Integer, String>> paramMappings) throws IOException {
		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(mcpPath)) {
			McpMappingsScanner scan = new McpMappingsScanner(fs.getPath("/"));
			Optional<Path> fields = scan.get("fields.csv");
			Optional<Path> methods = scan.get("methods.csv");
			Optional<Path> params = scan.get("params.csv");
			Pattern paramsPattern = Pattern.compile("p_(\\d+)_(\\d+)_?");

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
							paramMappings.computeIfAbsent("func_" + param.group(1), s -> new HashMap<>()).put(lvIndex, line[1]);
						}
					}
				} catch (CsvValidationException e) {
					throw new IOException(e);
				}
			}
		}
	}

	private static void mergeMcp(MemoryMappingTree mappingTree, Map<String, String> memberMappings, Map<String, String> comments, Map<String, Map<Integer, String>> paramMappings) {
		mappingTree.setDstNamespaces(List.of(MappingsNamespace.SRG.toString(), MappingsNamespace.NAMED.toString()));
		Pattern methodPattern = Pattern.compile("(func_\\d*)_.*");

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
				Matcher matcher = methodPattern.matcher(srgName);
				methodDef.setDstName(memberMappings.getOrDefault(srgName, srgName), 1);

				if (comments.containsKey(srgName)) {
					methodDef.setComment(comments.get(srgName));
				}

				if (matcher.matches() && paramMappings.containsKey(matcher.group(1))) {
					for (Map.Entry<Integer, String> entry : paramMappings.get(matcher.group(1)).entrySet()) {
						methodDef.addArg(new BasicMethodArg(methodDef, entry.getKey(), entry.getValue()));
					}
				}
			}
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
