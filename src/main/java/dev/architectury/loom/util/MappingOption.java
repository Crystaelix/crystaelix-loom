package dev.architectury.loom.util;

import net.fabricmc.loom.api.LoomGradleExtensionAPI;

public enum MappingOption {
	DEFAULT,
	WITH_SRG,
	WITH_MOJANG;

	public static MappingOption forPlatform(LoomGradleExtensionAPI extension) {
		return switch (extension.getPlatform().get()) {
		case FABRIC, QUILT -> DEFAULT;
		case FORGE, LEGACYFORGE, CLEANROOM -> WITH_SRG;
		case NEOFORGE -> WITH_MOJANG;
		};
	}
}
