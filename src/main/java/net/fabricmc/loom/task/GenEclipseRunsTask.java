/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2024 FabricMC
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import dev.architectury.loom.forge.dependency.ForgeModClassesService;
import groovy.xml.XmlUtil;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.RunConfiguration;
import net.fabricmc.loom.configuration.classpathgroups.ClasspathGroup;
import net.fabricmc.loom.configuration.ide.DefaultRunConfigurationSettings;
import net.fabricmc.loom.configuration.ide.RunConfigUtils;
import net.fabricmc.loom.configuration.ide.RuntimeLibraries;
import net.fabricmc.loom.configuration.ide.idea.IdeaSyncTask;
import net.fabricmc.loom.util.Arguments;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.gradle.SourceSetHelper;
import net.fabricmc.loom.util.service.ScopedServiceFactory;

@DisableCachingByDefault
public abstract class GenEclipseRunsTask extends AbstractLoomTask {
	@Nested
	protected abstract ListProperty<EclipseRunConfig> getEclipseRunConfigs();

	@ApiStatus.Internal
	@Nested
	@Optional
	protected abstract Property<ForgeModClassesService.Options> getModClassesOptions();

	@Inject
	public GenEclipseRunsTask() {
		setGroup(Constants.TaskGroup.IDE);
		getEclipseRunConfigs().set(getProject().provider(() -> getRunConfigs(getProject())));
		getModClassesOptions().set(ForgeModClassesService.createOptions(getProject(), getProject().provider(() -> ClasspathGroup.ClasspathType.ECLIPSE)));
	}

	@TaskAction
	public void genRuns() throws IOException {
		for (EclipseRunConfig runConfig : getEclipseRunConfigs().get()) {
			runConfig.writeLaunchFile();

			if (getModClassesOptions().isPresent()) {
				try (var serviceFactory = new ScopedServiceFactory()) {
					ForgeModClassesService modClassesService = serviceFactory.get(getModClassesOptions());
					Path launchFile = runConfig.getLaunchFile().get().getAsFile().toPath();
					IdeaSyncTask.setForgeModClasses(launchFile, modClassesService.getModClasses(runConfig.getName().get()));
				}
			}
		}
	}

	private static List<EclipseRunConfig> getRunConfigs(Project project) throws IOException {
		EclipseModel eclipseModel = project.getExtensions().getByType(EclipseModel.class);
		LoomGradleExtension extension = LoomGradleExtension.get(project);

		List<EclipseRunConfig> runConfigs = new ArrayList<>();

		for (RunConfiguration settings : extension.getRunConfigs()) {
			if (!settings.getGenerateRunConfig().get()) {
				continue;
			}

			final String name = settings.getName();
			final File configs = new File(project.getProjectDir(), eclipseModel.getProject().getName() + "_" + name + ".launch");
			final RunConfiguration runConfiguration = DefaultRunConfigurationSettings.finialise(settings, project);
			final String config;

			try {
				config = fromTemplate(runConfiguration, project);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to generate Eclipse run configuration", e);
			}

			EclipseRunConfig eclipseRunConfig = project.getObjects().newInstance(EclipseRunConfig.class);
			eclipseRunConfig.getLaunchContent().set(config);
			eclipseRunConfig.getLaunchFile().set(project.file(configs));
			eclipseRunConfig.getName().set(name);
			runConfigs.add(eclipseRunConfig);

			RunConfigUtils.createRunDirectory(settings);
		}

		return runConfigs;
	}

	private static String fromTemplate(RunConfiguration run, Project project) throws IOException {
		String dummyConfig;

		try (InputStream input = GenEclipseRunsTask.class.getClassLoader().getResourceAsStream("eclipse_run_config_template.xml")) {
			dummyConfig = new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}

		String runDir = RunConfigUtils.formatRunDir(run, project, File::getAbsolutePath, "${workspace_loc:%ECLIPSE_PROJECT%}/"::concat);
		String eclipseProjectName = project.getExtensions().getByType(EclipseModel.class).getProject().getName();

		dummyConfig = dummyConfig.replace("%MAIN_CLASS%", run.getDevLaunchMainClass().get());
		dummyConfig = dummyConfig.replace("%RUN_DIRECTORY%", XmlUtil.escapeXml(runDir));
		dummyConfig = dummyConfig.replace("%ECLIPSE_PROJECT%", XmlUtil.escapeXml(eclipseProjectName));
		dummyConfig = dummyConfig.replace("%PROGRAM_ARGS%", XmlUtil.escapeXml(Arguments.join(run.getProgramArguments().get())));
		dummyConfig = dummyConfig.replace("%VM_ARGS%", XmlUtil.escapeXml(Arguments.join(run.getJvmArguments().get())));
		dummyConfig = dummyConfig.replace("%ECLIPSE_ENV_VARS%", RunConfigUtils.formatEnvVars(run, "<mapEntry key=\"%s\" value=\"%s\"/>", "\n\t\t"));
		dummyConfig = dummyConfig.replace("%DEFAULT_CLASSPATH%", "false");
		dummyConfig = dummyConfig.replace("%CLASSPATH_ENTRIES%", String.join("\n\t\t", createClasspathEntries(run, project, eclipseProjectName)));

		return dummyConfig;
	}

	private static List<String> createClasspathEntries(RunConfiguration run, Project project, String eclipseProjectName) {
		JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
		SourceSet runSourceSet = SourceSetHelper.getSourceSetByName(run.getSourceSet().get(), project);
		List<String> excludedLibraries = RuntimeLibraries.getExcludedLibraryPaths(project, run);
		Set<File> projectOutputs = new HashSet<>();

		for (SourceSet sourceSet : java.getSourceSets()) {
			projectOutputs.addAll(sourceSet.getOutput().getFiles());
		}

		List<String> entries = new ArrayList<>();
		entries.add(runtimeClasspathEntry(
				"containerPath", "org.eclipse.jdt.launching.JRE_CONTAINER",
				"javaProject", eclipseProjectName,
				"path", "1",
				"type", "4"
		));
		entries.add(runtimeClasspathEntry(
				"path", "5",
				"projectName", eclipseProjectName,
				"type", "1"
		));

		FileCollection classpath = runSourceSet.getRuntimeClasspath()
				.filter(file -> !excludedLibraries.contains(file.getAbsolutePath()) && !projectOutputs.contains(file));

		for (File file : classpath) {
			entries.add(runtimeClasspathEntry(
					"externalArchive", file.getAbsolutePath().replace('\\', '/'),
					"path", "5",
					"type", "2"
			));
		}

		return entries;
	}

	private static String runtimeClasspathEntry(String... keyValuePairs) {
		StringBuilder sb = new StringBuilder("<runtimeClasspathEntry");

		for (int i = 0; i < keyValuePairs.length; i += 2) {
			sb.append(' ').append(keyValuePairs[i]).append("=\"").append(XmlUtil.escapeXml(keyValuePairs[i + 1])).append('"');
		}

		return "<listEntry value=\"" + XmlUtil.escapeXml(sb.append("/>").toString()) + "\"/>";
	}

	public interface EclipseRunConfig {
		@Input
		Property<String> getLaunchContent();

		@OutputFile
		RegularFileProperty getLaunchFile();

		@Input
		Property<String> getName();

		default void writeLaunchFile() throws IOException {
			Path launchFile = getLaunchFile().get().getAsFile().toPath();
			Files.writeString(launchFile, getLaunchContent().get(), StandardCharsets.UTF_8);
		}
	}
}
