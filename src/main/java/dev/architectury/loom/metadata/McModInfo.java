package dev.architectury.loom.metadata;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

		final ImmutableSet.Builder<String> modIds = ImmutableSet.builder();

		for (final JsonElement mod : json.getAsJsonArray("modList")) {
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
