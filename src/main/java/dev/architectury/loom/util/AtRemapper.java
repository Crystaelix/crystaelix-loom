/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020-2021 FabricMC
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

package dev.architectury.loom.util;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import org.cadixdev.at.AccessTransformSet;
import org.cadixdev.at.io.AccessTransformFormats;
import org.cadixdev.lorenz.MappingSet;

import net.fabricmc.lorenztiny.TinyMappingsReader;
import net.fabricmc.mappingio.tree.MappingTree;

public final class AtRemapper {
	public static byte[] remap(byte[] ats, MappingTree mappingTree, String from, String to) throws IOException {
		AccessTransformSet accessTransformSet = AccessTransformSet.create();
		AccessTransformFormats.FML.read(new InputStreamReader(new ByteArrayInputStream(ats)), accessTransformSet);

		MappingSet mappingSet = new TinyMappingsReader(mappingTree, from, to).read();
		accessTransformSet = AccessTransformSetMapper.remap(accessTransformSet, mappingSet);

		ByteArrayOutputStream remappedOut = new ByteArrayOutputStream();
		// TODO the extra BufferedWriter wrapper and closing can be removed once https://github.com/CadixDev/at/issues/6 is fixed
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(remappedOut));
		AccessTransformFormats.FML.write(writer, accessTransformSet);
		writer.close();
		return remappedOut.toByteArray();
	}
}
