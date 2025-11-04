package com.crystaelix.loom.legacyforge;

import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARRAYLENGTH;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.F_APPEND;
import static org.objectweb.asm.Opcodes.F_CHOP;
import static org.objectweb.asm.Opcodes.F_FULL;
import static org.objectweb.asm.Opcodes.F_SAME;
import static org.objectweb.asm.Opcodes.F_SAME1;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.IFNONNULL;
import static org.objectweb.asm.Opcodes.IFNULL;
import static org.objectweb.asm.Opcodes.IF_ACMPEQ;
import static org.objectweb.asm.Opcodes.IF_ICMPLT;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INTEGER;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.TOP;

import dev.architectury.loom.forge.ForgeVersion;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AdviceAdapter;

/**
 * Transforms Forge's FMLTweaker class to expand the classpath.
 * For motivation, see comments at usage site.
 */
public class FMLTweakerTransformer extends ClassVisitor {
	private static final String FORGE_PACKAGE = "net/minecraftforge/";
	private static final String FORGE_CLASS = FORGE_PACKAGE + "fml/common/launcher/FMLTweaker";
	public static final String FORGE_FILE = FORGE_CLASS + ".class";
	private static final String CPW_PACKAGE = "cpw/mods/";
	private static final String CPW_CLASS = CPW_PACKAGE + "fml/common/launcher/FMLTweaker";
	public static final String CPW_FILE = CPW_CLASS + ".class";

	private static final String TARGET_METHOD = "<init>";
	private static final String OUR_METHOD_NAME = "loom$expandClasspath";
	private static final String OUR_METHOD_DESCRIPTOR = "()V";

	private final String clazz;

	public FMLTweakerTransformer(ClassVisitor classVisitor, ForgeVersion forgeVersion) {
		super(Opcodes.ASM9, classVisitor);
		clazz = forgeVersion.cpwFml() ? CPW_CLASS : FORGE_CLASS;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);

		// We inject a call to our method, which will expand the classpath, at the start of the constructor.
		if (name.equals(TARGET_METHOD)) {
			methodVisitor = new InjectCallAtHead(methodVisitor, access, name, descriptor);
		}

		return methodVisitor;
	}

	@Override
	public void visitEnd() {
		// We add the following method, which will expand the classpath.
		//
		//  private static void loom$expandClasspath() {
		//		String cp = System.getProperty("java.class.path");
		//		if (cp == null || cp.contains(File.pathSeparator)) return;
		//		File file = new File(cp);
		//		if (!file.isFile()) return;
		//		try (JarFile jar = new JarFile(file)) {
		//			Manifest mf = jar.getManifest();
		//			if (mf == null) return;
		//			Attributes attrs = mf.getMainAttributes();
		//			String cpAttr = attrs.getValue(Attributes.Name.CLASS_PATH);
		//			if (cpAttr == null || cpAttr.isEmpty()) return;
		//			URL url = file.toURI().toURL();
		//			String[] entries = cpAttr.split(" ");
		//			List<URL> urls = new ArrayList<>();
		//			List<String> paths = new ArrayList<>();
		//			paths.add(cp);
		//			for (String entry : entries) {
		//				URL u = new URL(url, entry);
		//				if(u.getProtocol().equals("file")) {
		//					urls.add(u);
		//					paths.add(new File(u.toURI()).getAbsolutePath());
		//				}
		//			}
		//			String newCp = String.join(File.pathSeparator, paths);
		//			System.setProperty("java.class.path", newCp);
		//			for (URL u : urls) Launch.classLoader.addURL(u);
		//		} catch (Exception e) {
		//			throw new RuntimeException(e);
		//		}
		//	}
		//
		// Converted to ASM via the "ASM Bytecode Viewer" plugin:
		{
			MethodVisitor methodVisitor = super.visitMethod(ACC_PRIVATE | ACC_STATIC, OUR_METHOD_NAME, OUR_METHOD_DESCRIPTOR, null, null);
			methodVisitor.visitCode();
			Label label0 = new Label();
			Label label1 = new Label();
			Label label2 = new Label();
			methodVisitor.visitTryCatchBlock(label0, label1, label2, null);
			Label label3 = new Label();
			Label label4 = new Label();
			methodVisitor.visitTryCatchBlock(label3, label4, label2, null);
			Label label5 = new Label();
			Label label6 = new Label();
			methodVisitor.visitTryCatchBlock(label5, label6, label2, null);
			Label label7 = new Label();
			Label label8 = new Label();
			Label label9 = new Label();
			methodVisitor.visitTryCatchBlock(label7, label8, label9, null);
			Label label10 = new Label();
			methodVisitor.visitTryCatchBlock(label3, label10, label9, null);
			methodVisitor.visitTryCatchBlock(label5, label9, label9, null);
			Label label11 = new Label();
			Label label12 = new Label();
			methodVisitor.visitTryCatchBlock(label11, label8, label12, "java/lang/Exception");
			methodVisitor.visitTryCatchBlock(label3, label10, label12, "java/lang/Exception");
			methodVisitor.visitTryCatchBlock(label5, label12, label12, "java/lang/Exception");
			Label label13 = new Label();
			methodVisitor.visitLabel(label13);
			methodVisitor.visitLineNumber(202, label13);
			methodVisitor.visitLdcInsn("java.class.path");
			methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/System", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;", false);
			methodVisitor.visitVarInsn(ASTORE, 0);
			Label label14 = new Label();
			methodVisitor.visitLabel(label14);
			methodVisitor.visitLineNumber(203, label14);
			methodVisitor.visitVarInsn(ALOAD, 0);
			Label label15 = new Label();
			methodVisitor.visitJumpInsn(IFNULL, label15);
			methodVisitor.visitVarInsn(ALOAD, 0);
			methodVisitor.visitFieldInsn(GETSTATIC, "java/io/File", "pathSeparator", "Ljava/lang/String;");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false);
			Label label16 = new Label();
			methodVisitor.visitJumpInsn(IFEQ, label16);
			methodVisitor.visitLabel(label15);
			methodVisitor.visitFrame(F_APPEND, 1, new Object[] {"java/lang/String"}, 0, null);
			methodVisitor.visitInsn(RETURN);
			methodVisitor.visitLabel(label16);
			methodVisitor.visitLineNumber(204, label16);
			methodVisitor.visitFrame(F_SAME, 0, null, 0, null);
			methodVisitor.visitTypeInsn(NEW, "java/io/File");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ALOAD, 0);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false);
			methodVisitor.visitVarInsn(ASTORE, 1);
			Label label17 = new Label();
			methodVisitor.visitLabel(label17);
			methodVisitor.visitLineNumber(205, label17);
			methodVisitor.visitVarInsn(ALOAD, 1);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/File", "isFile", "()Z", false);
			methodVisitor.visitJumpInsn(IFNE, label11);
			methodVisitor.visitInsn(RETURN);
			methodVisitor.visitLabel(label11);
			methodVisitor.visitLineNumber(206, label11);
			methodVisitor.visitFrame(F_APPEND, 1, new Object[] {"java/io/File"}, 0, null);
			methodVisitor.visitInsn(ACONST_NULL);
			methodVisitor.visitVarInsn(ASTORE, 2);
			methodVisitor.visitInsn(ACONST_NULL);
			methodVisitor.visitVarInsn(ASTORE, 3);
			methodVisitor.visitLabel(label7);
			methodVisitor.visitTypeInsn(NEW, "java/util/jar/JarFile");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ALOAD, 1);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/jar/JarFile", "<init>", "(Ljava/io/File;)V", false);
			methodVisitor.visitVarInsn(ASTORE, 4);
			methodVisitor.visitLabel(label0);
			methodVisitor.visitLineNumber(207, label0);
			methodVisitor.visitVarInsn(ALOAD, 4);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/JarFile", "getManifest", "()Ljava/util/jar/Manifest;", false);
			methodVisitor.visitVarInsn(ASTORE, 5);
			Label label18 = new Label();
			methodVisitor.visitLabel(label18);
			methodVisitor.visitLineNumber(208, label18);
			methodVisitor.visitVarInsn(ALOAD, 5);
			methodVisitor.visitJumpInsn(IFNONNULL, label3);
			methodVisitor.visitLabel(label1);
			methodVisitor.visitLineNumber(227, label1);
			methodVisitor.visitVarInsn(ALOAD, 4);
			methodVisitor.visitJumpInsn(IFNULL, label8);
			methodVisitor.visitVarInsn(ALOAD, 4);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/JarFile", "close", "()V", false);
			methodVisitor.visitLabel(label8);
			methodVisitor.visitLineNumber(208, label8);
			methodVisitor.visitFrame(F_FULL, 6, new Object[] {"java/lang/String", "java/io/File", "java/lang/Throwable", "java/lang/Throwable", "java/util/jar/JarFile", "java/util/jar/Manifest"}, 0, new Object[] {});
			methodVisitor.visitInsn(RETURN);
			methodVisitor.visitLabel(label3);
			methodVisitor.visitLineNumber(209, label3);
			methodVisitor.visitFrame(F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 5);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/Manifest", "getMainAttributes", "()Ljava/util/jar/Attributes;", false);
			methodVisitor.visitVarInsn(ASTORE, 6);
			Label label19 = new Label();
			methodVisitor.visitLabel(label19);
			methodVisitor.visitLineNumber(210, label19);
			methodVisitor.visitVarInsn(ALOAD, 6);
			methodVisitor.visitFieldInsn(GETSTATIC, "java/util/jar/Attributes$Name", "CLASS_PATH", "Ljava/util/jar/Attributes$Name;");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/Attributes", "getValue", "(Ljava/util/jar/Attributes$Name;)Ljava/lang/String;", false);
			methodVisitor.visitVarInsn(ASTORE, 7);
			Label label20 = new Label();
			methodVisitor.visitLabel(label20);
			methodVisitor.visitLineNumber(211, label20);
			methodVisitor.visitVarInsn(ALOAD, 7);
			methodVisitor.visitJumpInsn(IFNULL, label4);
			methodVisitor.visitVarInsn(ALOAD, 7);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "isEmpty", "()Z", false);
			methodVisitor.visitJumpInsn(IFEQ, label5);
			methodVisitor.visitLabel(label4);
			methodVisitor.visitLineNumber(227, label4);
			methodVisitor.visitFrame(F_APPEND, 2, new Object[] {"java/util/jar/Attributes", "java/lang/String"}, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 4);
			methodVisitor.visitJumpInsn(IFNULL, label10);
			methodVisitor.visitVarInsn(ALOAD, 4);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/JarFile", "close", "()V", false);
			methodVisitor.visitLabel(label10);
			methodVisitor.visitLineNumber(211, label10);
			methodVisitor.visitFrame(F_SAME, 0, null, 0, null);
			methodVisitor.visitInsn(RETURN);
			methodVisitor.visitLabel(label5);
			methodVisitor.visitLineNumber(212, label5);
			methodVisitor.visitFrame(F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 1);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/File", "toURI", "()Ljava/net/URI;", false);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/net/URI", "toURL", "()Ljava/net/URL;", false);
			methodVisitor.visitVarInsn(ASTORE, 8);
			Label label21 = new Label();
			methodVisitor.visitLabel(label21);
			methodVisitor.visitLineNumber(213, label21);
			methodVisitor.visitVarInsn(ALOAD, 7);
			methodVisitor.visitLdcInsn(" ");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false);
			methodVisitor.visitVarInsn(ASTORE, 9);
			Label label22 = new Label();
			methodVisitor.visitLabel(label22);
			methodVisitor.visitLineNumber(214, label22);
			methodVisitor.visitTypeInsn(NEW, "java/util/ArrayList");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
			methodVisitor.visitVarInsn(ASTORE, 10);
			Label label23 = new Label();
			methodVisitor.visitLabel(label23);
			methodVisitor.visitLineNumber(215, label23);
			methodVisitor.visitTypeInsn(NEW, "java/util/ArrayList");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
			methodVisitor.visitVarInsn(ASTORE, 11);
			Label label24 = new Label();
			methodVisitor.visitLabel(label24);
			methodVisitor.visitLineNumber(216, label24);
			methodVisitor.visitVarInsn(ALOAD, 11);
			methodVisitor.visitVarInsn(ALOAD, 0);
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
			methodVisitor.visitInsn(POP);
			Label label25 = new Label();
			methodVisitor.visitLabel(label25);
			methodVisitor.visitLineNumber(217, label25);
			methodVisitor.visitVarInsn(ALOAD, 9);
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ASTORE, 15);
			methodVisitor.visitInsn(ARRAYLENGTH);
			methodVisitor.visitVarInsn(ISTORE, 14);
			methodVisitor.visitInsn(ICONST_0);
			methodVisitor.visitVarInsn(ISTORE, 13);
			Label label26 = new Label();
			methodVisitor.visitJumpInsn(GOTO, label26);
			Label label27 = new Label();
			methodVisitor.visitLabel(label27);
			methodVisitor.visitFrame(Opcodes.F_FULL, 16, new Object[] {"java/lang/String", "java/io/File", "java/lang/Throwable", "java/lang/Throwable", "java/util/jar/JarFile", "java/util/jar/Manifest", "java/util/jar/Attributes", "java/lang/String", "java/net/URL", "[Ljava/lang/String;", "java/util/List", "java/util/List", TOP, INTEGER, INTEGER, "[Ljava/lang/String;"}, 0, new Object[] {});
			methodVisitor.visitVarInsn(ALOAD, 15);
			methodVisitor.visitVarInsn(ILOAD, 13);
			methodVisitor.visitInsn(AALOAD);
			methodVisitor.visitVarInsn(ASTORE, 12);
			Label label28 = new Label();
			methodVisitor.visitLabel(label28);
			methodVisitor.visitLineNumber(218, label28);
			methodVisitor.visitTypeInsn(NEW, "java/net/URL");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ALOAD, 8);
			methodVisitor.visitVarInsn(ALOAD, 12);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/net/URL", "<init>", "(Ljava/net/URL;Ljava/lang/String;)V", false);
			methodVisitor.visitVarInsn(ASTORE, 16);
			Label label29 = new Label();
			methodVisitor.visitLabel(label29);
			methodVisitor.visitLineNumber(219, label29);
			methodVisitor.visitVarInsn(ALOAD, 16);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/net/URL", "getProtocol", "()Ljava/lang/String;", false);
			methodVisitor.visitLdcInsn("file");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
			Label label30 = new Label();
			methodVisitor.visitJumpInsn(IFEQ, label30);
			Label label31 = new Label();
			methodVisitor.visitLabel(label31);
			methodVisitor.visitLineNumber(220, label31);
			methodVisitor.visitVarInsn(ALOAD, 10);
			methodVisitor.visitVarInsn(ALOAD, 16);
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
			methodVisitor.visitInsn(POP);
			Label label32 = new Label();
			methodVisitor.visitLabel(label32);
			methodVisitor.visitLineNumber(221, label32);
			methodVisitor.visitVarInsn(ALOAD, 11);
			methodVisitor.visitTypeInsn(NEW, "java/io/File");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ALOAD, 16);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/net/URL", "toURI", "()Ljava/net/URI;", false);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/net/URI;)V", false);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/File", "getAbsolutePath", "()Ljava/lang/String;", false);
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
			methodVisitor.visitInsn(POP);
			methodVisitor.visitLabel(label30);
			methodVisitor.visitLineNumber(217, label30);
			methodVisitor.visitFrame(F_SAME, 0, null, 0, null);
			methodVisitor.visitIincInsn(13, 1);
			methodVisitor.visitLabel(label26);
			methodVisitor.visitFrame(F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ILOAD, 13);
			methodVisitor.visitVarInsn(ILOAD, 14);
			methodVisitor.visitJumpInsn(IF_ICMPLT, label27);
			Label label33 = new Label();
			methodVisitor.visitLabel(label33);
			methodVisitor.visitLineNumber(224, label33);
			methodVisitor.visitFieldInsn(GETSTATIC, "java/io/File", "pathSeparator", "Ljava/lang/String;");
			methodVisitor.visitVarInsn(ALOAD, 11);
			methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/String", "join", "(Ljava/lang/CharSequence;Ljava/lang/Iterable;)Ljava/lang/String;", false);
			methodVisitor.visitVarInsn(ASTORE, 12);
			Label label34 = new Label();
			methodVisitor.visitLabel(label34);
			methodVisitor.visitLineNumber(225, label34);
			methodVisitor.visitLdcInsn("java.class.path");
			methodVisitor.visitVarInsn(ALOAD, 12);
			methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/System", "setProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
			methodVisitor.visitInsn(POP);
			Label label35 = new Label();
			methodVisitor.visitLabel(label35);
			methodVisitor.visitLineNumber(226, label35);
			methodVisitor.visitVarInsn(ALOAD, 10);
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
			methodVisitor.visitVarInsn(ASTORE, 14);
			Label label36 = new Label();
			methodVisitor.visitJumpInsn(GOTO, label36);
			Label label37 = new Label();
			methodVisitor.visitLabel(label37);
			methodVisitor.visitFrame(F_FULL, 15, new Object[] {"java/lang/String", "java/io/File", "java/lang/Throwable", "java/lang/Throwable", "java/util/jar/JarFile", "java/util/jar/Manifest", "java/util/jar/Attributes", "java/lang/String", "java/net/URL", "[Ljava/lang/String;", "java/util/List", "java/util/List", "java/lang/String", TOP, "java/util/Iterator"}, 0, new Object[] {});
			methodVisitor.visitVarInsn(ALOAD, 14);
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
			methodVisitor.visitTypeInsn(CHECKCAST, "java/net/URL");
			methodVisitor.visitVarInsn(ASTORE, 13);
			Label label38 = new Label();
			methodVisitor.visitLabel(label38);
			methodVisitor.visitFieldInsn(GETSTATIC, "net/minecraft/launchwrapper/Launch", "classLoader", "Lnet/minecraft/launchwrapper/LaunchClassLoader;");
			methodVisitor.visitVarInsn(ALOAD, 13);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "net/minecraft/launchwrapper/LaunchClassLoader", "addURL", "(Ljava/net/URL;)V", false);
			methodVisitor.visitLabel(label36);
			methodVisitor.visitFrame(F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 14);
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
			methodVisitor.visitJumpInsn(IFNE, label37);
			methodVisitor.visitLabel(label6);
			methodVisitor.visitLineNumber(227, label6);
			methodVisitor.visitVarInsn(ALOAD, 4);
			Label label39 = new Label();
			methodVisitor.visitJumpInsn(IFNULL, label39);
			methodVisitor.visitVarInsn(ALOAD, 4);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/JarFile", "close", "()V", false);
			methodVisitor.visitJumpInsn(GOTO, label39);
			methodVisitor.visitLabel(label2);
			methodVisitor.visitFrame(F_FULL, 5, new Object[] {"java/lang/String", "java/io/File", "java/lang/Throwable", "java/lang/Throwable", "java/util/jar/JarFile"}, 1, new Object[] {"java/lang/Throwable"});
			methodVisitor.visitVarInsn(ASTORE, 2);
			methodVisitor.visitVarInsn(ALOAD, 4);
			Label label40 = new Label();
			methodVisitor.visitJumpInsn(IFNULL, label40);
			methodVisitor.visitVarInsn(ALOAD, 4);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/JarFile", "close", "()V", false);
			methodVisitor.visitLabel(label40);
			methodVisitor.visitFrame(F_CHOP, 1, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 2);
			methodVisitor.visitInsn(ATHROW);
			methodVisitor.visitLabel(label9);
			methodVisitor.visitFrame(F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
			methodVisitor.visitVarInsn(ASTORE, 3);
			methodVisitor.visitVarInsn(ALOAD, 2);
			Label label41 = new Label();
			methodVisitor.visitJumpInsn(IFNONNULL, label41);
			methodVisitor.visitVarInsn(ALOAD, 3);
			methodVisitor.visitVarInsn(ASTORE, 2);
			Label label42 = new Label();
			methodVisitor.visitJumpInsn(GOTO, label42);
			methodVisitor.visitLabel(label41);
			methodVisitor.visitFrame(F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 2);
			methodVisitor.visitVarInsn(ALOAD, 3);
			methodVisitor.visitJumpInsn(IF_ACMPEQ, label42);
			methodVisitor.visitVarInsn(ALOAD, 2);
			methodVisitor.visitVarInsn(ALOAD, 3);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Throwable", "addSuppressed", "(Ljava/lang/Throwable;)V", false);
			methodVisitor.visitLabel(label42);
			methodVisitor.visitFrame(F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 2);
			methodVisitor.visitInsn(ATHROW);
			methodVisitor.visitLabel(label12);
			methodVisitor.visitFrame(F_FULL, 2, new Object[] {"java/lang/String", "java/io/File"}, 1, new Object[] {"java/lang/Exception"});
			methodVisitor.visitVarInsn(ASTORE, 2);
			Label label43 = new Label();
			methodVisitor.visitLabel(label43);
			methodVisitor.visitLineNumber(228, label43);
			methodVisitor.visitTypeInsn(NEW, "java/lang/RuntimeException");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ALOAD, 2);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/Throwable;)V", false);
			methodVisitor.visitInsn(ATHROW);
			methodVisitor.visitLabel(label39);
			methodVisitor.visitLineNumber(230, label39);
			methodVisitor.visitFrame(F_SAME, 0, null, 0, null);
			methodVisitor.visitInsn(RETURN);
			Label label44 = new Label();
			methodVisitor.visitLabel(label44);
			methodVisitor.visitLocalVariable("cp", "Ljava/lang/String;", null, label14, label44, 0);
			methodVisitor.visitLocalVariable("file", "Ljava/io/File;", null, label17, label44, 1);
			methodVisitor.visitLocalVariable("jar", "Ljava/util/jar/JarFile;", null, label0, label40, 4);
			methodVisitor.visitLocalVariable("mf", "Ljava/util/jar/Manifest;", null, label18, label6, 5);
			methodVisitor.visitLocalVariable("attrs", "Ljava/util/jar/Attributes;", null, label19, label6, 6);
			methodVisitor.visitLocalVariable("cpAttr", "Ljava/lang/String;", null, label20, label6, 7);
			methodVisitor.visitLocalVariable("url", "Ljava/net/URL;", null, label21, label6, 8);
			methodVisitor.visitLocalVariable("entries", "[Ljava/lang/String;", null, label22, label6, 9);
			methodVisitor.visitLocalVariable("urls", "Ljava/util/List;", "Ljava/util/List<Ljava/net/URL;>;", label23, label6, 10);
			methodVisitor.visitLocalVariable("paths", "Ljava/util/List;", "Ljava/util/List<Ljava/lang/String;>;", label24, label6, 11);
			methodVisitor.visitLocalVariable("entry", "Ljava/lang/String;", null, label28, label30, 12);
			methodVisitor.visitLocalVariable("u", "Ljava/net/URL;", null, label29, label30, 16);
			methodVisitor.visitLocalVariable("newCp", "Ljava/lang/String;", null, label34, label6, 12);
			methodVisitor.visitLocalVariable("u", "Ljava/net/URL;", null, label38, label36, 13);
			methodVisitor.visitLocalVariable("e", "Ljava/lang/Exception;", null, label43, label39, 2);
			methodVisitor.visitMaxs(4, 17);
			methodVisitor.visitEnd();
		}
	}

	private class InjectCallAtHead extends AdviceAdapter {
		private InjectCallAtHead(MethodVisitor methodVisitor, int access, String name, String descriptor) {
			super(ASM9, methodVisitor, access, name, descriptor);
		}

		@Override
		protected void onMethodEnter() {
			super.onMethodEnter();
			super.visitMethodInsn(INVOKESTATIC, clazz, OUR_METHOD_NAME, OUR_METHOD_DESCRIPTOR, false);
		}
	}
}
