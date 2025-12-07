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

package com.crystaelix.loom.metadata;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.architectury.loom.metadata.JsonBackedModMetadataFile;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.configuration.ifaceinject.InterfaceInjectionProcessor.InjectedInterface;
import net.fabricmc.loom.util.ModPlatform;

public class McModInfo implements JsonBackedModMetadataFile {
	public static final String FILE_PATH = "mcmod.info";
	private final JsonObject json;

	private McModInfo(JsonElement json) {
		if (json.isJsonArray()) {
			this.json = new JsonObject();
			this.json.add("modList", json);
		} else {
			this.json = json.getAsJsonObject();
		}
	}

	public static McModInfo of(byte[] utf8) {
		return of(new String(utf8, StandardCharsets.UTF_8));
	}

	public static McModInfo of(String text) {
		return of(JsonParser.parseString(text));
	}

	public static McModInfo of(Path path) throws IOException {
		return of(Files.readString(path, StandardCharsets.UTF_8));
	}

	public static McModInfo of(File file) throws IOException {
		return of(file.toPath());
	}

	public static McModInfo of(JsonElement json) {
		return new McModInfo(json);
	}

	@Override
	public JsonObject getJson() {
		return json;
	}

	@Override
	public Set<String> getIds() {
		if (json.has("modList")) return Set.of();

		final List<String> modIds = new ArrayList<>();

		for (final JsonElement mod : json.getAsJsonArray("modList")) {
			if (mod.isJsonObject()) {
				JsonObject modObject = mod.getAsJsonObject();

				if (modObject.has("modid")) {
					modIds.add(modObject.get("modid").getAsString());
				}
			}
		}

		return Set.copyOf(modIds);
	}

	@Override
	public Set<String> getAccessWideners() {
		return Set.of();
	}

	@Override
	public Set<String> getAccessTransformers(ModPlatform platform) {
		return Set.of();
	}

	@Override
	public List<InjectedInterface> getInjectedInterfaces(@Nullable String modId) {
		return List.of();
	}

	@Override
	public String getFileName() {
		return FILE_PATH;
	}

	@Override
	public List<String> getMixinConfigs() {
		return List.of();
	}
}
