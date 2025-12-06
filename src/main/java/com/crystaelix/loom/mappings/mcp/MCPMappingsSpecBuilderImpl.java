package com.crystaelix.loom.mappings.mcp;

import net.fabricmc.loom.api.mappings.layered.spec.FileSpec;

public class MCPMappingsSpecBuilderImpl implements MCPMappingsSpecBuilder {
	private FileSpec srg = null;
	private FileSpec mcp;

	private MCPMappingsSpecBuilderImpl(FileSpec mcp) {
		this.mcp = mcp;
	}

	public static MCPMappingsSpecBuilderImpl builder(FileSpec mcp) {
		return new MCPMappingsSpecBuilderImpl(mcp);
	}

	@Override
	public MCPMappingsSpecBuilder srg(Object file) {
		srg = file == null ? null : FileSpec.create(file);
		return this;
	}

	public MCPMappingsSpec build() {
		return new MCPMappingsSpec(srg, mcp);
	}
}
