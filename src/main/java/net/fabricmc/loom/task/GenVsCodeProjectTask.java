/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2021 FabricMC
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

package net.fabricmc.loom.task;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.architectury.loom.util.ForgeSourceRootHelper;
import org.apache.tools.ant.taskdefs.condition.Os;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.ide.RunConfig;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.util.gradle.SourceSetHelper;
import net.fabricmc.loom.util.gradle.SyncTaskBuildService;

// Recommended vscode plugin pack:
// https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack
public abstract class GenVsCodeProjectTask extends AbstractLoomTask {
	// Prevent Gradle from running vscode task asynchronously
	@ServiceReference(SyncTaskBuildService.NAME)
	abstract Property<SyncTaskBuildService> getSyncTask();

	@TaskAction
	public void genRuns() throws IOException {
		clean(getProject());
		generate(getProject());
	}

	public static void clean(Project project) throws IOException {
		Path projectDir = project.getRootDir().toPath().resolve(".vscode");

		if (Files.notExists(projectDir)) {
			Files.createDirectories(projectDir);
		}

		final Path launchJson = projectDir.resolve("launch.json");
		Files.deleteIfExists(launchJson);
	}

	public static void generate(Project project) throws IOException {
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		Path projectDir = project.getRootDir().toPath().resolve(".vscode");

		if (Files.notExists(projectDir)) {
			Files.createDirectories(projectDir);
		}

		final Path launchJson = projectDir.resolve("launch.json");
		final Path tasksJson = projectDir.resolve("tasks.json");
		final JsonObject root;

		if (Files.exists(launchJson)) {
			root = LoomGradlePlugin.GSON.fromJson(Files.readString(launchJson, StandardCharsets.UTF_8), JsonObject.class);
		} else {
			root = new JsonObject();
			root.addProperty("version", "0.2.0");
		}

		final JsonArray configurations;

		if (root.has("configurations")) {
			configurations = root.getAsJsonArray("configurations");
		} else {
			configurations = new JsonArray();
			root.add("configurations", configurations);
		}

		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		List<VsCodeConfiguration> configurationObjects = new ArrayList<>();

		for (RunConfigSettings settings : extension.getRunConfigs()) {
			if (!settings.isIdeConfigGenerated()) {
				continue;
			}

			ForgeSourceRootHelper.addForgeSourceRoots(project, settings, SourceSetHelper::getVscodeClasspath);
			final RunConfig runConfig = RunConfig.runConfig(project, settings);
			final VsCodeConfiguration configuration = new VsCodeConfiguration(project, runConfig);

			if (!configuration.tasksBeforeRun.isEmpty()) {
				configuration.preLaunchTask = "generated_" + runConfig.configName;
			}

			configurationObjects.add(configuration);
			final JsonElement configurationJson = LoomGradlePlugin.GSON.toJsonTree(configuration);

			final List<JsonElement> toRemove = new LinkedList<>();

			// Remove any existing with the same name
			for (JsonElement jsonElement : configurations) {
				if (!jsonElement.isJsonObject()) {
					continue;
				}

				final JsonObject jsonObject = jsonElement.getAsJsonObject();

				if (jsonObject.has("name")) {
					if (jsonObject.get("name").getAsString().equalsIgnoreCase(configuration.name)) {
						toRemove.add(jsonElement);
					}
				}
			}

			toRemove.forEach(configurations::remove);

			configurations.add(configurationJson);
			settings.makeRunDir();
		}

		final String json = LoomGradlePlugin.GSON.toJson(root);

		Files.writeString(launchJson, json, StandardCharsets.UTF_8);

		VsCodeTasks tasks;

		if (Files.exists(tasksJson)) {
			try {
				tasks = gson.fromJson(Files.readString(tasksJson, StandardCharsets.UTF_8), VsCodeTasks.class);
			} catch (IOException e) {
				throw new RuntimeException("Failed to read launch.json", e);
			}
		} else {
			tasks = new VsCodeTasks();
		}

		for (VsCodeConfiguration configuration : configurationObjects) {
			if (configuration.preLaunchTask != null && configuration.tasksBeforeRun != null) {
				String prefix = Os.isFamily(Os.FAMILY_WINDOWS) ? "gradlew.bat" : "./gradlew";
				tasks.add(new VsCodeTask(configuration.preLaunchTask, prefix + " " + configuration.tasksBeforeRun.stream()
						.map(s -> {
							int i = s.indexOf('/');
							return i == -1 ? s : s.substring(i + 1);
						}).collect(Collectors.joining(" ")), "shell", new String[0]));
			}
		}

		if (!tasks.tasks.isEmpty()) {
			String jsonTasks = gson.toJson(tasks);

			try {
				Files.writeString(tasksJson, jsonTasks, StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new RuntimeException("Failed to write tasks.json", e);
			}
		}
	}

	private static class VsCodeTasks {
		public String version = "2.0.0";
		public List<VsCodeTask> tasks = new ArrayList<>();

		public void add(VsCodeTask vsCodeTask) {
			if (tasks.stream().noneMatch(task -> Objects.equals(task.label, vsCodeTask.label))) {
				tasks.add(vsCodeTask);
			}
		}
	}

	@SuppressWarnings("unused")
	private static class VsCodeConfiguration {
		public transient Project project;
		public String type = "java";
		public String name;
		public String request = "launch";
		public String cwd;
		public String console = "integratedTerminal";
		public boolean stopOnEntry = false;
		public String mainClass;
		public String vmArgs;
		public String args;
		public Map<String, Object> env;
		public String projectName;
		public transient List<String> tasksBeforeRun = new ArrayList<>();
		public String preLaunchTask = null;

		VsCodeConfiguration(Project project, RunConfig runConfig) {
			this.name = runConfig.configName;
			this.mainClass = runConfig.mainClass;
			this.vmArgs = RunConfig.joinArguments(runConfig.vmArgs);
			this.args = RunConfig.joinArguments(runConfig.programArgs);
			this.cwd = "${workspaceFolder}/" + runConfig.runDir;
			this.env = new HashMap<>(runConfig.environmentVariables);
			this.projectName = runConfig.projectName;
			this.tasksBeforeRun.addAll(runConfig.vscodeBeforeRun);

			if (project.getRootProject() != project) {
				Path rootPath = project.getRootDir().toPath();
				Path projectPath = project.getProjectDir().toPath();
				String relativePath = rootPath.relativize(projectPath).toString();

				this.cwd = "${workspaceFolder}/%s/%s".formatted(relativePath, runConfig.runDir);
			}
		}
	}

	private static class VsCodeTask {
		public String label;
		public String command;
		public String type;
		public String[] args;
		public String group = "build";

		VsCodeTask(String label, String command, String type, String[] args) {
			this.label = label;
			this.command = command;
			this.type = type;
			this.args = args;
		}
	}
}
