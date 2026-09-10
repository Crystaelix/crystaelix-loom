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

package com.crystaelix.loom.mappings.mcp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import com.crystaelix.loom.mappings.MCPReader;
import org.jspecify.annotations.Nullable;

import net.fabricmc.loom.api.mappings.layered.MappingLayer;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.intermediary.IntermediaryMappingLayer;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public record MCPMappingLayer(
		Path srgPath,
		Path mcpPath,
		boolean dropNoneIntermediaryRoots,
		@Nullable Supplier<MemoryMappingTree> intermediarySupplier
) implements MappingLayer {
	@Override
	public void visit(MappingVisitor mappingVisitor) throws IOException {
		MCPReader.read(srgPath, mcpPath, dropNoneIntermediaryRoots, intermediarySupplier).accept(mappingVisitor);
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
