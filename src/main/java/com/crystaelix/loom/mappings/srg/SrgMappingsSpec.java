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

package com.crystaelix.loom.mappings.srg;

import dev.architectury.loom.forge.dependency.SrgProvider;
import org.jspecify.annotations.Nullable;

import net.fabricmc.loom.api.mappings.layered.MappingContext;
import net.fabricmc.loom.api.mappings.layered.spec.FileSpec;
import net.fabricmc.loom.api.mappings.layered.spec.MappingsSpec;
import net.fabricmc.loom.configuration.providers.mappings.MappingConfiguration;
import net.fabricmc.loom.util.Constants;

public record SrgMappingsSpec(
		@Nullable FileSpec srgFileSpec,
		String targetNamespace
) implements MappingsSpec<SrgMappingLayer> {
	@Override
	public SrgMappingLayer createLayer(MappingContext context) {
		FileSpec srgFileSpec = this.srgFileSpec;

		if (srgFileSpec == null) {
			SrgProvider srgProvider = context.srgProvider();

			if (srgProvider != null) {
				srgFileSpec = FileSpec.create(MappingConfiguration.getRawSrgFile(srgProvider));
			} else {
				srgFileSpec = FileSpec.create("de.oceanlabs.mcp:mcp_config:" + context.minecraftProvider().minecraftVersion() + "@zip");
			}
		}

		return new SrgMappingLayer(
				srgFileSpec.get(context),
				targetNamespace,
				context.hasProperty(Constants.Properties.DROP_NON_INTERMEDIATE_ROOT_METHODS),
				context.isUsingIntermediateMappings() ? context.intermediaryTree() : null
		);
	}
}
