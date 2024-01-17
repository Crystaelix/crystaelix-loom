package dev.architectury.loom.metadata;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loom.configuration.ifaceinject.InterfaceInjectionProcessor;

public class McModInfo implements ModMetadataFile {
	public static final String FILE_PATH = "mcmod.info";
	private final JsonArray json;

	private McModInfo(JsonElement json) {
		if (json.isJsonArray()) {
			this.json = json.getAsJsonArray();
		} else {
			this.json = json.getAsJsonObject().getAsJsonArray("modList");
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
	public Set<String> getIds() {
		if (json.isEmpty()) return Set.of();
		final ImmutableSet.Builder<String> modIds = ImmutableSet.builder();

		for (JsonElement mod : json.asList()) {
			if (mod.isJsonObject()) {
				JsonObject modObject = mod.getAsJsonObject();

				if (modObject.has("modid")) {
					modIds.add(modObject.get("modid").getAsString());
				}
			}
		}

		return modIds.build();
	}

	@Override
	public Set<String> getAccessWideners() {
		return Set.of();
	}

	@Override
	public List<InterfaceInjectionProcessor.InjectedInterface> getInjectedInterfaces(@Nullable String modId) {
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

	@Override
	public boolean equals(Object o) {
		return o == this || o instanceof McModInfo modInfoJson && modInfoJson.json.equals(json);
	}

	@Override
	public int hashCode() {
		return json.hashCode();
	}
}
