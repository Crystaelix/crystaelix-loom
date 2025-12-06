/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020-2025 FabricMC
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

package dev.architectury.loom.forge.dependency;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.crystaelix.loom.mappings.MCPReader;
import com.crystaelix.loom.util.McpMappingsScanner;
import dev.architectury.loom.util.Stopwatch;
import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingContext;
import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.configuration.providers.mappings.GradleMappingContext;
import net.fabricmc.loom.configuration.providers.mappings.mojmap.MojangMappingLayer;
import net.fabricmc.loom.configuration.providers.mappings.mojmap.MojangMappingsSpec;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public class SrgProvider extends DependencyProvider {
	private Path srg;
	private Boolean isTsrgV2;
	private Path mergedMojang;
	private static Map<String, Path> mojmapTsrg2Map = new HashMap<>();

	public SrgProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency) throws Exception {
		init(dependency.getDependency().getVersion());

		if (!Files.exists(srg) || MappingReader.detectFormat(srg) != MappingFormat.TSRG_2_FILE || refreshDeps()) {
			Path srgZip = dependency.resolveFile().orElseThrow(() -> new RuntimeException("Could not resolve srg")).toPath();

			try (
					FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(srgZip);
					MappingWriter writer = MappingWriter.create(srg, MappingFormat.TSRG_2_FILE)
			) {
				McpMappingsScanner scan = new McpMappingsScanner(fs.getPath("/"));
				MCPReader.readSrg(scan, writer);
			}
		}

		try (BufferedReader reader = Files.newBufferedReader(srg)) {
			isTsrgV2 = reader.readLine().startsWith("tsrg2 obf");
		}

		if (isTsrgV2) {
			if (!Files.exists(mergedMojang) || refreshDeps()) {
				Stopwatch stopwatch = Stopwatch.createStarted();
				getProject().getLogger().lifecycle(":merging mappings (srg + mojmap)");

				MemoryMappingTree tree = new MemoryMappingTree();
				MappingVisitor visitor = new MojmapWrappingVisitor(tree);
				MappingReader.read(srg, visitor);

				try (MappingWriter writer = MappingWriter.create(mergedMojang, MappingFormat.TSRG_2_FILE)) {
					tree.accept(writer);
				}

				getProject().getLogger().lifecycle(":merged mappings (srg + mojmap) in " + stopwatch.stop());
			}
		}
	}

	// Read mojmap and apply field descs to the tsrg2
	private class MojmapWrappingVisitor extends ForwardingMappingVisitor {
		private final Map<FieldKey, String> fieldDescMap = new HashMap<>();
		private final Map<String, String> classMap = new HashMap<>();
		private String lastClass;

		protected MojmapWrappingVisitor(MappingVisitor next) throws IOException {
			super(next);
			MemoryMappingTree mojmap = new MemoryMappingTree();
			MappingReader.read(getMojmapTsrg2(getProject(), getExtension()), mojmap);

			for (MappingTree.ClassMapping classMapping : mojmap.getClasses()) {
				classMap.put(classMapping.getSrcName(), classMapping.getDstName(0));

				for (MappingTree.FieldMapping fieldMapping : classMapping.getFields()) {
					fieldDescMap.put(new FieldKey(classMapping.getSrcName(), fieldMapping.getSrcName()), fieldMapping.getSrcDesc());
				}
			}
		}

		@Override
		public boolean visitClass(String srcName) throws IOException {
			if (super.visitClass(srcName)) {
				this.lastClass = srcName;
				return true;
			} else {
				return false;
			}
		}

		@Override
		public boolean visitField(String srcName, String srcDesc) throws IOException {
			if (srcDesc == null) {
				srcDesc = fieldDescMap.get(new FieldKey(lastClass, srcName));
			}

			return super.visitField(srcName, srcDesc);
		}

		@Override
		public void visitDstName(MappedElementKind targetKind, int namespace, String name) throws IOException {
			if (targetKind == MappedElementKind.CLASS && namespace == 0) {
				name = classMap.getOrDefault(lastClass, name);
			}

			super.visitDstName(targetKind, namespace, name);
		}

		private record FieldKey(String owner, String name) {
		}
	}

	private void init(String version) {
		File dir = getMinecraftProvider().dir("srg/" + version);
		srg = new File(dir, "srg.tsrg").toPath();
		mergedMojang = new File(dir, "srg-mojmap-merged.tsrg").toPath();
	}

	public Path getSrg() {
		return srg;
	}

	public Path getMergedMojang() {
		if (!isTsrgV2()) throw new IllegalStateException("May not access merged mojmap srg if not on modern Minecraft!");

		return mergedMojang;
	}

	public boolean isTsrgV2() {
		return isTsrgV2;
	}

	public static Path getMojmapTsrg2(Project project, LoomGradleExtension extension) throws IOException {
		String minecraftVersion = extension.getMinecraftProvider().minecraftVersion();
		if (mojmapTsrg2Map.containsKey(minecraftVersion)) return mojmapTsrg2Map.get(minecraftVersion);

		Path mojmapTsrg2 = extension.getMinecraftProvider().dir("forge").toPath().resolve("mojmap.tsrg2");

		if (Files.notExists(mojmapTsrg2) || extension.refreshDeps()) {
			try (MappingWriter writer = MappingWriter.create(mojmapTsrg2, MappingFormat.TSRG_2_FILE)) {
				GradleMappingContext context = new GradleMappingContext(project, "tmp-mojmap");
				MemoryMappingTree tree = new MemoryMappingTree();
				visitMojangMappings(tree, context);
				tree.accept(writer);
			}
		}

		mojmapTsrg2Map.put(minecraftVersion, mojmapTsrg2);
		return mojmapTsrg2;
	}

	public static void visitMojangMappings(MappingVisitor visitor, MappingContext context) {
		try {
			MojangMappingLayer layer = new MojangMappingsSpec(() -> true, true).createLayer(context);
			layer.visit(visitor);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public String getTargetConfig() {
		return Constants.Configurations.SRG;
	}
}
