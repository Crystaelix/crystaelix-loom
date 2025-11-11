package com.crystaelix.loom.mappings.mcp;

import dev.architectury.loom.forge.dependency.SrgProvider;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.api.mappings.layered.MappingContext;
import net.fabricmc.loom.api.mappings.layered.spec.FileSpec;
import net.fabricmc.loom.api.mappings.layered.spec.MappingsSpec;
import net.fabricmc.loom.configuration.providers.mappings.MappingConfiguration;
import net.fabricmc.loom.util.Constants;

public record MCPMappingsSpec(
		@Nullable FileSpec srgFileSpec,
		FileSpec mcpFileSpec
) implements MappingsSpec<MCPMappingLayer> {
	@Override
	public MCPMappingLayer createLayer(MappingContext context) {
		FileSpec srgFileSpec = this.srgFileSpec;

		if (srgFileSpec == null) {
			SrgProvider srgProvider = context.srgProvider();

			if (srgProvider != null) {
				srgFileSpec = FileSpec.create(MappingConfiguration.getRawSrgFile(srgProvider));
			} else {
				srgFileSpec = FileSpec.create("de.oceanlabs.mcp:mcp_config:" + context.minecraftProvider().minecraftVersion() + "@zip");
			}
		}

		return new MCPMappingLayer(
				srgFileSpec.get(context),
				mcpFileSpec.get(context),
				context.hasProperty(Constants.Properties.DROP_NON_INTERMEDIATE_ROOT_METHODS),
				context.isUsingIntermediateMappings() ? context.intermediaryTree() : null
		);
	}
}
