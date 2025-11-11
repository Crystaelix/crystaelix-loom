package com.crystaelix.loom.mappings.srg;

import net.fabricmc.loom.api.mappings.layered.spec.FileSpec;

public class SrgMappingsSpecBuilderImpl implements SrgMappingsSpecBuilder {
	private FileSpec srgFileSpec;
	private String targetNamespace = "srg";

	private SrgMappingsSpecBuilderImpl(FileSpec srgFileSpec) {
		this.srgFileSpec = srgFileSpec;
	}

	public static SrgMappingsSpecBuilderImpl builder() {
		return builder(null);
	}

	public static SrgMappingsSpecBuilderImpl builder(FileSpec srg) {
		return new SrgMappingsSpecBuilderImpl(srg);
	}

	@Override
	public SrgMappingsSpecBuilder targetNamespace(String namespace) {
		targetNamespace = namespace;
		return this;
	}

	public SrgMappingsSpec build() {
		return new SrgMappingsSpec(srgFileSpec, targetNamespace);
	}
}
