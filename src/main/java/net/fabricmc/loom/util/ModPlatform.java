/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2023 FabricMC
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

package net.fabricmc.loom.util;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.LoomGradleExtensionAPI;

public enum ModPlatform {
	FABRIC("Fabric", false),
	FORGE("Forge", false),
	QUILT("Quilt", false),
	NEOFORGE("NeoForge", false),
	LEGACYFORGE("LegacyForge", true),
	CLEANROOM("Cleanroom", true);

	public static final ModPlatform[] FABRIC_LIKE = {FABRIC, QUILT};
	public static final ModPlatform[] FORGE_LIKE = {FORGE, NEOFORGE, LEGACYFORGE, CLEANROOM};
	public static final ModPlatform[] MODERN_FORGE_LIKE = {FORGE, NEOFORGE};
	public static final ModPlatform[] SRG_FORGE_LIKE = {FORGE, LEGACYFORGE, CLEANROOM};
	public static final ModPlatform[] LEGACY_FORGE_LIKE = {LEGACYFORGE, CLEANROOM};

	private final String displayName;
	final boolean experimental;

	ModPlatform(String displayName, boolean experimental) {
		this.displayName = displayName;
		this.experimental = experimental;
	}

	/**
	 * Returns the lowercase ID of this mod platform.
	 */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String displayName() {
		return displayName;
	}

	public boolean isExperimental() {
		return experimental;
	}

	public boolean isFabricLike() {
		return ArrayUtils.contains(FABRIC_LIKE, this);
	}

	public boolean isForgeLike() {
		return ArrayUtils.contains(FORGE_LIKE, this);
	}

	public boolean isModernForgeLike() {
		return ArrayUtils.contains(MODERN_FORGE_LIKE, this);
	}

	public boolean isSrgForgeLike() {
		return ArrayUtils.contains(SRG_FORGE_LIKE, this);
	}

	public boolean isLegacyForgeLike() {
		return ArrayUtils.contains(LEGACY_FORGE_LIKE, this);
	}

	public static void assertPlatform(Project project, ModPlatform... platforms) {
		assertPlatform(LoomGradleExtension.get(project), platforms);
	}

	public static void assertPlatform(LoomGradleExtensionAPI extension, ModPlatform... platforms) {
		if (platforms.length == 1) {
			assertPlatform(extension, () -> {
				String currentPlatform = extension.getPlatform().get().displayName();
				String msg = "Loom is running on %s and not %s.%nYou can switch to it by adding 'loom.platform = %s' to your gradle.properties";
				return msg.formatted(currentPlatform, platforms[0].displayName(), platforms[0].id());
			}, platforms);
			return;
		}

		assertPlatform(extension, () -> {
			String msg = "Loom is running on %s and not any of %s.%nYou can switch to it by any of the following: Add any of %s to your gradle.properties";
			String currentPlatform = extension.getPlatform().get().displayName();
			String platformList = Arrays.stream(platforms).map(ModPlatform::displayName).collect(Collectors.joining(", "));
			String loomPlatform = Arrays.stream(platforms).map(ModPlatform::id).collect(Collectors.joining(", "));
			return msg.formatted(currentPlatform, "[" + platformList + "]", "[" + loomPlatform + "]");
		}, platforms);
	}

	public static void assertPlatform(LoomGradleExtensionAPI extension, Supplier<String> message, ModPlatform... platforms) {
		if (!ArrayUtils.contains(platforms, extension.getPlatform().get())) {
			throw new GradleException(message.get());
		}
	}

	public static void assertForgeLike(LoomGradleExtensionAPI extension) {
		assertForgeLike(extension, () -> {
			String msg = "Loom is running on %s and not a Forge-like platform (Forge or NeoForge).";
			return msg.formatted(extension.getPlatform().get().displayName());
		});
	}

	public static void assertForgeLike(LoomGradleExtensionAPI extension, Supplier<String> message) {
		assertPlatform(extension, message, FORGE_LIKE);
	}
}
