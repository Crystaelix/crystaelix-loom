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

package net.fabricmc.loom.configuration.classpathgroups;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.loom.api.ModSettings;
import net.fabricmc.loom.util.gradle.SourceSetHelper;
import net.fabricmc.loom.util.gradle.SourceSetReference;

public record ClasspathGroup(String name, List<String> paths, List<ExternalClasspathGroup> externalGroups) implements Serializable {
	public enum ClasspathType {
		GRADLE, IDEA, IDEA_MODULE, ECLIPSE, VSCODE
	}

	public static List<ClasspathGroup> fromModSettings(Set<ModSettings> modSettings) {
		return modSettings.stream().map(s -> new ClasspathGroup(s.getName(), getPaths(s), s.getExternalGroups().get())).toList();
	}

	public static List<ClasspathGroup> fromModSettings(Set<ModSettings> modSettings, Provider<ClasspathType> type) {
		return modSettings.stream().map(s -> {
			List<String> paths;

			if (type.isPresent() && s.getModSourceSet().isPresent()) {
				SourceSetReference ref = s.getModSourceSet().get();
				paths = switch (type.get()) {
				case GRADLE -> getPaths(SourceSetHelper.getGradleClasspath(ref));
				case IDEA -> getPaths(SourceSetHelper.getIdeaClasspath(ref));
				case IDEA_MODULE -> getPaths(SourceSetHelper.getIdeaModuleCompileOutput(ref));
				case ECLIPSE -> getPaths(SourceSetHelper.getEclipseClasspath(ref));
				case VSCODE -> getPaths(SourceSetHelper.getVscodeClasspath(ref));
				};
			} else {
				paths = getPaths(s);
			}

			return new ClasspathGroup(s.getName(), paths, s.getExternalGroups().get());
		}).toList();
	}

	// TODO remove this constructor when updating to Gradle 9.0, works around an issue where config cache cannot serialize immutable lists
	public ClasspathGroup(String name, List<String> paths, List<ExternalClasspathGroup> externalGroups) {
		this.name = name;
		this.paths = new ArrayList<>(paths);
		this.externalGroups = new ArrayList<>(externalGroups);
	}

	private static List<String> getPaths(ModSettings modSettings) {
		return modSettings.getModFiles()
				.getFiles()
				.stream()
				.map(File::getAbsolutePath)
				.toList();
	}

	private static List<String> getPaths(List<File> files) {
		return files.stream().map(File::getAbsolutePath).toList();
	}

	private static @Nullable String getAbsolutePath(@Nullable FileSystemLocation location) {
		if (location == null) {
			return null;
		}

		return location.getAsFile().getAbsolutePath();
	}
}
