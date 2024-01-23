package dev.architectury.loom.util;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;

public enum MappingOption {
	DEFAULT(null),
	WITH_SRG(MappingsNamespace.SRG.toString()),
	WITH_MOJANG(MappingsNamespace.MOJANG.toString());

	private final String extraNamespace;

	MappingOption(@Nullable String extraNamespace) {
		this.extraNamespace = extraNamespace;
	}

	public MappingOption forNamespaces(String... namespaces) {
		if (extraNamespace == null) return this;

		for (String namespace : namespaces) {
			if (extraNamespace.equals(namespace)) {
				return this;
			}
		}

		return DEFAULT;
	}

	public static MappingOption forPlatform(LoomGradleExtensionAPI extension) {
		return switch (extension.getPlatform().get()) {
		case FABRIC, QUILT -> DEFAULT;
		case FORGE, LEGACYFORGE, CLEANROOM -> WITH_SRG;
		case NEOFORGE -> WITH_MOJANG;
		};
	}
}
