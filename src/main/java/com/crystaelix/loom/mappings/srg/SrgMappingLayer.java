package com.crystaelix.loom.mappings.srg;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.crystaelix.loom.mappings.MCPReader;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.api.mappings.layered.MappingLayer;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.intermediary.IntermediaryMappingLayer;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.MappingNsRenamer;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public record SrgMappingLayer(
		Path srgPath,
		String targetNamespace,
		boolean dropNoneIntermediaryRoots,
		@Nullable Supplier<MemoryMappingTree> intermediarySupplier
) implements MappingLayer {
	@Override
	public void visit(MappingVisitor mappingVisitor) throws IOException {
		mappingVisitor = new MappingNsRenamer(mappingVisitor, Map.of(MappingsNamespace.SRG.toString(), targetNamespace));
		MCPReader.readSrg(srgPath, dropNoneIntermediaryRoots, intermediarySupplier).accept(mappingVisitor);
	}

	@Override
	public MappingsNamespace getSourceNamespace() {
		return MappingsNamespace.OFFICIAL;
	}

	@Override
	public List<Class<? extends MappingLayer>> dependsOn() {
		return List.of(IntermediaryMappingLayer.class);
	}
}
