package dev.architectury.loom.util;

import java.io.File;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.ModSettings;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.util.gradle.SourceSetReference;

public class ForgeSourceRootHelper {
	public static String getForgeSourceRoots(Project project, RunConfigSettings settings, BiFunction<SourceSetReference, Project, List<File>> classpathFunc) {
		if (classpathFunc == null) {
			return null;
		}

		LoomGradleExtension extension = LoomGradleExtension.get(project);

		if (!extension.isModernForgeLike()) {
			return null;
		}

		Multimap<String, String> modClasses = MultimapBuilder.linkedHashKeys().arrayListValues().build();
		NamedDomainObjectContainer<ModSettings> mods = extension.getMods();

		if (!settings.getMods().isEmpty()) {
			mods = settings.getMods();
		}

		for (ModSettings mod : mods) {
			for (SourceSetReference modSourceSet : mod.getModSourceSets().get()) {
				for (File file : classpathFunc.apply(modSourceSet, project)) {
					modClasses.put(mod.getName(), file.getAbsolutePath());
				}
			}
		}

		String value = modClasses.entries().stream()
				.map(entry -> entry.getKey() + "%%" + entry.getValue())
				.collect(Collectors.joining(File.pathSeparator));
		return value.isEmpty() ? null : value;
	}
}
