/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020-2022 FabricMC
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

package net.fabricmc.loom.configuration.providers.forge.mcpconfig;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.architectury.loom.util.McpMappingsScanner;
import org.cadixdev.lorenz.io.srg.SrgReader;
import org.cadixdev.lorenz.io.srg.tsrg.TSrgWriter;
import org.gradle.api.Project;

import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.configuration.providers.forge.ConfigValue;
import net.fabricmc.loom.configuration.providers.forge.DependencyProvider;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DeletingFileVisitor;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.ZipUtils;

public class McpConfigProvider extends DependencyProvider {
	private Path mcp;
	private Path configJson;
	private Path unpacked;
	private McpConfigData data;

	public McpConfigProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency) throws Exception {
		init(dependency.getDependency().getVersion());

		Path mcpZip = dependency.resolveFile().orElseThrow(() -> new RuntimeException("Could not resolve MCPConfig")).toPath();

		if (!Files.exists(mcp) || !Files.exists(unpacked) || refreshDeps()) {
			Files.copy(mcpZip, mcp, StandardCopyOption.REPLACE_EXISTING);

			// Delete existing files
			if (Files.exists(unpacked)) {
				Files.walkFileTree(unpacked, new DeletingFileVisitor());
			}

			Files.createDirectory(unpacked);

			if (ZipUtils.contains(mcp, "config.json")) {
				ZipUtils.unpackAll(mcp, unpacked);
			}
		}

		if (Files.notExists(configJson)) {
			String version = getExtension().getMinecraftProvider().minecraftVersion();
			data = new McpConfigData(
					version,
					new JsonObject(),
					"srg.tsrg",
					false,
					Map.of(
							"client", List.of(
									new McpConfigStep("downloadClient"),
									new McpConfigStep(
											"strip",
											Map.of("input", ConfigValue.of("{downloadClientOutput}"))
									)
							),
							"server", List.of(
									new McpConfigStep("downloadServer"),
									new McpConfigStep(
											"strip",
											Map.of("input", ConfigValue.of("{downloadServerOutput}"))
									)
							)
					),
					Map.of()
			);

			try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(mcp)) {
				McpMappingsScanner scan = new McpMappingsScanner(fs);
				Optional<Path> srgPath = scan.get("joined.tsrg");
				srgPath = scan.get("joined.srg");

				if (srgPath.isEmpty()) {
					srgPath = scan.get(getExtension().getMinecraftProvider().provideServer() ? "server.srg" : "client.srg");
				}

				try (
						SrgReader reader = new SrgReader(Files.newBufferedReader(srgPath.orElseThrow(() -> new RuntimeException("Could not resolve srg")), StandardCharsets.UTF_8));
						TSrgWriter writer = new TSrgWriter(Files.newBufferedWriter(unpacked.resolve("srg.tsrg")))
				) {
					writer.write(reader.read());
				}
			}

			return;
		}

		JsonObject json;

		try (Reader reader = Files.newBufferedReader(configJson)) {
			json = new Gson().fromJson(reader, JsonObject.class);
		}

		data = McpConfigData.fromJson(json);
	}

	private void init(String version) {
		String mcpName = getExtension().isNeoForge() ? "neoform" : "mcp";
		Path dir = getMinecraftProvider().dir(mcpName + "/" + version).toPath();
		mcp = dir.resolve("mcp.zip");
		unpacked = dir.resolve("unpacked");
		configJson = unpacked.resolve("config.json");
	}

	public Path getMappings() {
		return unpacked.resolve(getMappingsPath());
	}

	public Path getUnpackedZip() {
		return unpacked;
	}

	public Path getMcp() {
		return mcp;
	}

	public boolean isOfficial() {
		return data.official();
	}

	public String getMappingsPath() {
		return data.mappingsPath();
	}

	@Override
	public String getTargetConfig() {
		return Constants.Configurations.MCP_CONFIG;
	}

	public McpConfigData getData() {
		return data;
	}
}
