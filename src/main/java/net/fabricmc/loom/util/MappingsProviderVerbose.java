/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import net.fabricmc.mappingio.adapter.RegularAsFlatMappingVisitor;
import net.fabricmc.mappingio.format.tiny.Tiny2FileWriter;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.TinyRemapper;

public class MappingsProviderVerbose {
	public static void saveFile(TinyRemapper providers) throws IOException {
		try {
			Field field = TinyRemapper.class.getDeclaredField("mappingProviders");
			field.setAccessible(true);
			Set<IMappingProvider> mappingProviders = (Set<IMappingProvider>) field.get(providers);
			saveFile(mappingProviders);
		} catch (IllegalAccessException | NoSuchFieldException e) {
			e.printStackTrace();
		}
	}

	public static void saveFile(Iterable<IMappingProvider> providers) throws IOException {
		MemoryMappingTree tree = new MemoryMappingTree();
		tree.setSrcNamespace("from");
		tree.setDstNamespaces(new ArrayList<>(Collections.singletonList("to")));
		RegularAsFlatMappingVisitor flatVisitor = new RegularAsFlatMappingVisitor(tree);

		for (IMappingProvider provider : providers) {
			provider.load(new IMappingProvider.MappingAcceptor() {
				@Override
				public void acceptClass(String from, String to) {
					try {
						flatVisitor.visitClass(from, to);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				}

				@Override
				public void acceptMethod(IMappingProvider.Member from, String to) {
					try {
						flatVisitor.visitMethod(from.owner, from.name, from.desc, to);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				}

				@Override
				public void acceptMethodArg(IMappingProvider.Member from, int lvIndex, String to) {
					try {
						flatVisitor.visitMethodArg(from.owner, from.name, from.desc, lvIndex, lvIndex, "", to);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				}

				@Override
				public void acceptMethodVar(IMappingProvider.Member from, int i, int i1, int i2, String s) {
					// NO-OP
				}

				@Override
				public void acceptField(IMappingProvider.Member from, String to) {
					try {
						flatVisitor.visitField(from.owner, from.name, from.desc, to);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				}
			});
		}

		Path check = Files.createTempFile("CHECK", null);
		StringWriter stringWriter = new StringWriter();
		Tiny2FileWriter tiny2Writer = new Tiny2FileWriter(stringWriter, false);
		tree.accept(tiny2Writer);
		Files.writeString(check, stringWriter.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		System.out.println("Saved debug check mappings to " + check);
	}
}
