package dev.architectury.loom.legacyforge;

import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ARRAYLENGTH;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DUP;
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
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.POP;

import dev.architectury.loom.forge.ForgeVersion;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Transforms Forge's ModDiscoverer class to expand the classpath when discovering mods.
 * For motivation, see comments at usage site.
 */
public class ModDiscovererTransformer extends ClassVisitor {
	private static final String FORGE_PACKAGE = "net/minecraftforge/";
	private static final String FORGE_CLASS = FORGE_PACKAGE + "fml/common/discovery/ModDiscoverer";
	public static final String FORGE_FILE = FORGE_CLASS + ".class";
	private static final String CPW_PACKAGE = "cpw/mods/";
	private static final String CPW_CLASS = CPW_PACKAGE + "fml/common/discovery/ModDiscoverer";
	public static final String CPW_FILE = CPW_CLASS + ".class";

	private static final String TARGET_METHOD = "findClasspathMods";
	private static final String TARGET_INVOKE = "getParentSources";
	private static final String OUR_METHOD_NAME = "loom$expandClasspath";
	private static final String OUR_METHOD_DESCRIPTOR = "([Ljava/io/File;)[Ljava/io/File;";

	private final ForgeVersion forgeVersion;
	private final String pakkage;
	private final String clazz;

	public ModDiscovererTransformer(ClassVisitor classVisitor, ForgeVersion forgeVersion) {
		super(Opcodes.ASM9, classVisitor);
		this.forgeVersion = forgeVersion;
		pakkage = forgeVersion.cpwFml() ? CPW_PACKAGE : FORGE_PACKAGE;
		clazz = forgeVersion.cpwFml() ? CPW_CLASS : FORGE_CLASS;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);

		// We inject a call to our method, which expand the classpath, at the call site of the original method.
		if (name.equals(TARGET_METHOD)) {
			methodVisitor = new InjectCallAtInvoke(methodVisitor);
		}

		return methodVisitor;
	}

	@Override
	public void visitEnd() {
		// We add the following method, which will expand the classpath.
		//
		//	private static File[] loom$expandClasspath(File[] files) {
		//		Deque<File> toProcess = new LinkedList<>();
		//		Collections.addAll(toProcess, files);
		//		Set<File> expanded = new LinkedHashSet<>();
		//		while(!toProcess.isEmpty()) {
		//			File file = toProcess.poll();
		//			expanded.add(file);
		//			if (!file.exists()) continue;
		//				Manifest manifest = null;
		//				if (file.isDirectory()) {
		//					File manifestFile = new File(file, "META-INF/MANIFEST.MF");
		//					if (manifestFile.exists()) try (FileInputStream stream = new FileInputStream(manifestFile)) {
		//						manifest = new Manifest(stream);
		//					} catch(Exception e) {}
		//				} else if (file.getName().endsWith("jar")) try (JarFile jar = new JarFile(file)) {
		//					manifest = jar.getManifest();
		//				} catch(Exception e) {}
		//				if (manifest != null) {
		//					String cp = manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
		//					if (cp != null) {
		//						for (String path : cp.split(" ")) {
		//							try {
		//								URL url = new URL(file.toURI().toURL(), path);
		//								if(url.getProtocol().equals("file")) toProcess.add(new File(url.toURI().getPath()));
		//							}
		//							catch(Exception e) {}
		//						}
		//					}
		//				}
		//		}
		//		return expanded.toArray(new File[0]);
		//	}
		//
		// Converted to ASM via the "ASM Bytecode Viewer" plugin:
		{
			MethodVisitor methodVisitor = super.visitMethod(ACC_PRIVATE | ACC_STATIC, "loom$expandClasspath", "([Ljava/io/File;)[Ljava/io/File;", null, null);
			methodVisitor.visitCode();
			Label label0 = new Label();
			Label label1 = new Label();
			Label label2 = new Label();
			methodVisitor.visitTryCatchBlock(label0, label1, label2, null);
			Label label3 = new Label();
			Label label4 = new Label();
			methodVisitor.visitTryCatchBlock(label3, label4, label4, null);
			Label label5 = new Label();
			Label label6 = new Label();
			methodVisitor.visitTryCatchBlock(label5, label6, label6, "java/lang/Exception");
			Label label7 = new Label();
			Label label8 = new Label();
			Label label9 = new Label();
			methodVisitor.visitTryCatchBlock(label7, label8, label9, null);
			Label label10 = new Label();
			Label label11 = new Label();
			methodVisitor.visitTryCatchBlock(label10, label11, label11, null);
			Label label12 = new Label();
			Label label13 = new Label();
			methodVisitor.visitTryCatchBlock(label12, label13, label13, "java/lang/Exception");
			Label label14 = new Label();
			Label label15 = new Label();
			Label label16 = new Label();
			methodVisitor.visitTryCatchBlock(label14, label15, label16, "java/lang/Exception");
			Label label17 = new Label();
			methodVisitor.visitLabel(label17);
			methodVisitor.visitLineNumber(201, label17);
			methodVisitor.visitTypeInsn(NEW, "java/util/LinkedList");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/LinkedList", "<init>", "()V", false);
			methodVisitor.visitVarInsn(ASTORE, 1);
			Label label18 = new Label();
			methodVisitor.visitLabel(label18);
			methodVisitor.visitLineNumber(202, label18);
			methodVisitor.visitVarInsn(ALOAD, 1);
			methodVisitor.visitVarInsn(ALOAD, 0);
			methodVisitor.visitMethodInsn(INVOKESTATIC, "java/util/Collections", "addAll", "(Ljava/util/Collection;[Ljava/lang/Object;)Z", false);
			methodVisitor.visitInsn(POP);
			Label label19 = new Label();
			methodVisitor.visitLabel(label19);
			methodVisitor.visitLineNumber(203, label19);
			methodVisitor.visitTypeInsn(NEW, "java/util/LinkedHashSet");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/LinkedHashSet", "<init>", "()V", false);
			methodVisitor.visitVarInsn(ASTORE, 2);
			Label label20 = new Label();
			methodVisitor.visitLabel(label20);
			methodVisitor.visitLineNumber(204, label20);
			Label label21 = new Label();
			methodVisitor.visitJumpInsn(GOTO, label21);
			Label label22 = new Label();
			methodVisitor.visitLabel(label22);
			methodVisitor.visitLineNumber(205, label22);
			methodVisitor.visitFrame(Opcodes.F_APPEND, 2, new Object[] {"java/util/Deque", "java/util/Set"}, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 1);
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/Deque", "poll", "()Ljava/lang/Object;", true);
			methodVisitor.visitTypeInsn(CHECKCAST, "java/io/File");
			methodVisitor.visitVarInsn(ASTORE, 3);
			Label label23 = new Label();
			methodVisitor.visitLabel(label23);
			methodVisitor.visitLineNumber(206, label23);
			methodVisitor.visitVarInsn(ALOAD, 2);
			methodVisitor.visitVarInsn(ALOAD, 3);
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/Set", "add", "(Ljava/lang/Object;)Z", true);
			methodVisitor.visitInsn(POP);
			Label label24 = new Label();
			methodVisitor.visitLabel(label24);
			methodVisitor.visitLineNumber(207, label24);
			methodVisitor.visitVarInsn(ALOAD, 3);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/File", "exists", "()Z", false);
			Label label25 = new Label();
			methodVisitor.visitJumpInsn(IFNE, label25);
			methodVisitor.visitJumpInsn(GOTO, label21);
			methodVisitor.visitLabel(label25);
			methodVisitor.visitLineNumber(208, label25);
			methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[] {"java/io/File"}, 0, null);
			methodVisitor.visitInsn(ACONST_NULL);
			methodVisitor.visitVarInsn(ASTORE, 4);
			Label label26 = new Label();
			methodVisitor.visitLabel(label26);
			methodVisitor.visitLineNumber(209, label26);
			methodVisitor.visitVarInsn(ALOAD, 3);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/File", "isDirectory", "()Z", false);
			Label label27 = new Label();
			methodVisitor.visitJumpInsn(IFEQ, label27);
			Label label28 = new Label();
			methodVisitor.visitLabel(label28);
			methodVisitor.visitLineNumber(210, label28);
			methodVisitor.visitTypeInsn(NEW, "java/io/File");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ALOAD, 3);
			methodVisitor.visitLdcInsn("META-INF/MANIFEST.MF");
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/io/File;Ljava/lang/String;)V", false);
			methodVisitor.visitVarInsn(ASTORE, 5);
			Label label29 = new Label();
			methodVisitor.visitLabel(label29);
			methodVisitor.visitLineNumber(211, label29);
			methodVisitor.visitVarInsn(ALOAD, 5);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/File", "exists", "()Z", false);
			Label label30 = new Label();
			methodVisitor.visitJumpInsn(IFEQ, label30);
			methodVisitor.visitLabel(label5);
			methodVisitor.visitInsn(ACONST_NULL);
			methodVisitor.visitVarInsn(ASTORE, 6);
			methodVisitor.visitInsn(ACONST_NULL);
			methodVisitor.visitVarInsn(ASTORE, 7);
			methodVisitor.visitLabel(label3);
			methodVisitor.visitTypeInsn(NEW, "java/io/FileInputStream");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ALOAD, 5);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/io/FileInputStream", "<init>", "(Ljava/io/File;)V", false);
			methodVisitor.visitVarInsn(ASTORE, 8);
			methodVisitor.visitLabel(label0);
			methodVisitor.visitLineNumber(212, label0);
			methodVisitor.visitTypeInsn(NEW, "java/util/jar/Manifest");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ALOAD, 8);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/jar/Manifest", "<init>", "(Ljava/io/InputStream;)V", false);
			methodVisitor.visitVarInsn(ASTORE, 4);
			methodVisitor.visitLabel(label1);
			methodVisitor.visitLineNumber(213, label1);
			methodVisitor.visitVarInsn(ALOAD, 8);
			methodVisitor.visitJumpInsn(IFNULL, label30);
			methodVisitor.visitVarInsn(ALOAD, 8);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/FileInputStream", "close", "()V", false);
			methodVisitor.visitJumpInsn(GOTO, label30);
			methodVisitor.visitLabel(label2);
			methodVisitor.visitFrame(Opcodes.F_FULL, 9, new Object[] {"[Ljava/io/File;", "java/util/Deque", "java/util/Set", "java/io/File", "java/util/jar/Manifest", "java/io/File", "java/lang/Throwable", "java/lang/Throwable", "java/io/FileInputStream"}, 1, new Object[] {"java/lang/Throwable"});
			methodVisitor.visitVarInsn(ASTORE, 6);
			methodVisitor.visitVarInsn(ALOAD, 8);
			Label label31 = new Label();
			methodVisitor.visitJumpInsn(IFNULL, label31);
			methodVisitor.visitVarInsn(ALOAD, 8);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/FileInputStream", "close", "()V", false);
			methodVisitor.visitLabel(label31);
			methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 6);
			methodVisitor.visitInsn(ATHROW);
			methodVisitor.visitLabel(label4);
			methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
			methodVisitor.visitVarInsn(ASTORE, 7);
			methodVisitor.visitVarInsn(ALOAD, 6);
			Label label32 = new Label();
			methodVisitor.visitJumpInsn(IFNONNULL, label32);
			methodVisitor.visitVarInsn(ALOAD, 7);
			methodVisitor.visitVarInsn(ASTORE, 6);
			Label label33 = new Label();
			methodVisitor.visitJumpInsn(GOTO, label33);
			methodVisitor.visitLabel(label32);
			methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 6);
			methodVisitor.visitVarInsn(ALOAD, 7);
			methodVisitor.visitJumpInsn(IF_ACMPEQ, label33);
			methodVisitor.visitVarInsn(ALOAD, 6);
			methodVisitor.visitVarInsn(ALOAD, 7);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Throwable", "addSuppressed", "(Ljava/lang/Throwable;)V", false);
			methodVisitor.visitLabel(label33);
			methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 6);
			methodVisitor.visitInsn(ATHROW);
			methodVisitor.visitLabel(label6);
			methodVisitor.visitFrame(Opcodes.F_FULL, 6, new Object[] {"[Ljava/io/File;", "java/util/Deque", "java/util/Set", "java/io/File", "java/util/jar/Manifest", "java/io/File"}, 1, new Object[] {"java/lang/Exception"});
			methodVisitor.visitVarInsn(ASTORE, 6);
			Label label34 = new Label();
			methodVisitor.visitLabel(label34);
			methodVisitor.visitLineNumber(214, label34);
			methodVisitor.visitJumpInsn(GOTO, label30);
			methodVisitor.visitLabel(label27);
			methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 3);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/File", "getName", "()Ljava/lang/String;", false);
			methodVisitor.visitLdcInsn("jar");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "endsWith", "(Ljava/lang/String;)Z", false);
			methodVisitor.visitJumpInsn(IFEQ, label30);
			methodVisitor.visitLabel(label12);
			methodVisitor.visitInsn(ACONST_NULL);
			methodVisitor.visitVarInsn(ASTORE, 5);
			methodVisitor.visitInsn(ACONST_NULL);
			methodVisitor.visitVarInsn(ASTORE, 6);
			methodVisitor.visitLabel(label10);
			methodVisitor.visitTypeInsn(NEW, "java/util/jar/JarFile");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ALOAD, 3);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/jar/JarFile", "<init>", "(Ljava/io/File;)V", false);
			methodVisitor.visitVarInsn(ASTORE, 7);
			methodVisitor.visitLabel(label7);
			methodVisitor.visitLineNumber(215, label7);
			methodVisitor.visitVarInsn(ALOAD, 7);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/JarFile", "getManifest", "()Ljava/util/jar/Manifest;", false);
			methodVisitor.visitVarInsn(ASTORE, 4);
			methodVisitor.visitLabel(label8);
			methodVisitor.visitLineNumber(216, label8);
			methodVisitor.visitVarInsn(ALOAD, 7);
			methodVisitor.visitJumpInsn(IFNULL, label30);
			methodVisitor.visitVarInsn(ALOAD, 7);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/JarFile", "close", "()V", false);
			methodVisitor.visitJumpInsn(GOTO, label30);
			methodVisitor.visitLabel(label9);
			methodVisitor.visitFrame(Opcodes.F_FULL, 8, new Object[] {"[Ljava/io/File;", "java/util/Deque", "java/util/Set", "java/io/File", "java/util/jar/Manifest", "java/lang/Throwable", "java/lang/Throwable", "java/util/jar/JarFile"}, 1, new Object[] {"java/lang/Throwable"});
			methodVisitor.visitVarInsn(ASTORE, 5);
			methodVisitor.visitVarInsn(ALOAD, 7);
			Label label35 = new Label();
			methodVisitor.visitJumpInsn(IFNULL, label35);
			methodVisitor.visitVarInsn(ALOAD, 7);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/JarFile", "close", "()V", false);
			methodVisitor.visitLabel(label35);
			methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 5);
			methodVisitor.visitInsn(ATHROW);
			methodVisitor.visitLabel(label11);
			methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
			methodVisitor.visitVarInsn(ASTORE, 6);
			methodVisitor.visitVarInsn(ALOAD, 5);
			Label label36 = new Label();
			methodVisitor.visitJumpInsn(IFNONNULL, label36);
			methodVisitor.visitVarInsn(ALOAD, 6);
			methodVisitor.visitVarInsn(ASTORE, 5);
			Label label37 = new Label();
			methodVisitor.visitJumpInsn(GOTO, label37);
			methodVisitor.visitLabel(label36);
			methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 5);
			methodVisitor.visitVarInsn(ALOAD, 6);
			methodVisitor.visitJumpInsn(IF_ACMPEQ, label37);
			methodVisitor.visitVarInsn(ALOAD, 5);
			methodVisitor.visitVarInsn(ALOAD, 6);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Throwable", "addSuppressed", "(Ljava/lang/Throwable;)V", false);
			methodVisitor.visitLabel(label37);
			methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 5);
			methodVisitor.visitInsn(ATHROW);
			methodVisitor.visitLabel(label13);
			methodVisitor.visitFrame(Opcodes.F_FULL, 5, new Object[] {"[Ljava/io/File;", "java/util/Deque", "java/util/Set", "java/io/File", "java/util/jar/Manifest"}, 1, new Object[] {"java/lang/Exception"});
			methodVisitor.visitVarInsn(ASTORE, 5);
			methodVisitor.visitLabel(label30);
			methodVisitor.visitLineNumber(217, label30);
			methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 4);
			methodVisitor.visitJumpInsn(IFNULL, label21);
			Label label38 = new Label();
			methodVisitor.visitLabel(label38);
			methodVisitor.visitLineNumber(218, label38);
			methodVisitor.visitVarInsn(ALOAD, 4);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/Manifest", "getMainAttributes", "()Ljava/util/jar/Attributes;", false);
			methodVisitor.visitFieldInsn(GETSTATIC, "java/util/jar/Attributes$Name", "CLASS_PATH", "Ljava/util/jar/Attributes$Name;");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/Attributes", "getValue", "(Ljava/util/jar/Attributes$Name;)Ljava/lang/String;", false);
			methodVisitor.visitVarInsn(ASTORE, 5);
			Label label39 = new Label();
			methodVisitor.visitLabel(label39);
			methodVisitor.visitLineNumber(219, label39);
			methodVisitor.visitVarInsn(ALOAD, 5);
			methodVisitor.visitJumpInsn(IFNULL, label21);
			Label label40 = new Label();
			methodVisitor.visitLabel(label40);
			methodVisitor.visitLineNumber(220, label40);
			methodVisitor.visitVarInsn(ALOAD, 5);
			methodVisitor.visitLdcInsn(" ");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false);
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ASTORE, 9);
			methodVisitor.visitInsn(ARRAYLENGTH);
			methodVisitor.visitVarInsn(ISTORE, 8);
			methodVisitor.visitInsn(ICONST_0);
			methodVisitor.visitVarInsn(ISTORE, 7);
			Label label41 = new Label();
			methodVisitor.visitJumpInsn(GOTO, label41);
			Label label42 = new Label();
			methodVisitor.visitLabel(label42);
			methodVisitor.visitFrame(Opcodes.F_FULL, 10, new Object[] {"[Ljava/io/File;", "java/util/Deque", "java/util/Set", "java/io/File", "java/util/jar/Manifest", "java/lang/String", Opcodes.TOP, Opcodes.INTEGER, Opcodes.INTEGER, "[Ljava/lang/String;"}, 0, new Object[] {});
			methodVisitor.visitVarInsn(ALOAD, 9);
			methodVisitor.visitVarInsn(ILOAD, 7);
			methodVisitor.visitInsn(AALOAD);
			methodVisitor.visitVarInsn(ASTORE, 6);
			methodVisitor.visitLabel(label14);
			methodVisitor.visitLineNumber(222, label14);
			methodVisitor.visitTypeInsn(NEW, "java/net/URL");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ALOAD, 3);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/File", "toURI", "()Ljava/net/URI;", false);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/net/URI", "toURL", "()Ljava/net/URL;", false);
			methodVisitor.visitVarInsn(ALOAD, 6);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/net/URL", "<init>", "(Ljava/net/URL;Ljava/lang/String;)V", false);
			methodVisitor.visitVarInsn(ASTORE, 10);
			Label label43 = new Label();
			methodVisitor.visitLabel(label43);
			methodVisitor.visitLineNumber(223, label43);
			methodVisitor.visitVarInsn(ALOAD, 10);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/net/URL", "getProtocol", "()Ljava/lang/String;", false);
			methodVisitor.visitLdcInsn("file");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
			Label label44 = new Label();
			methodVisitor.visitJumpInsn(IFEQ, label44);
			methodVisitor.visitVarInsn(ALOAD, 1);
			methodVisitor.visitTypeInsn(NEW, "java/io/File");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ALOAD, 10);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/net/URL", "toURI", "()Ljava/net/URI;", false);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/net/URI", "getPath", "()Ljava/lang/String;", false);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false);
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/Deque", "add", "(Ljava/lang/Object;)Z", true);
			methodVisitor.visitInsn(POP);
			methodVisitor.visitLabel(label15);
			methodVisitor.visitLineNumber(224, label15);
			methodVisitor.visitJumpInsn(GOTO, label44);
			methodVisitor.visitLabel(label16);
			methodVisitor.visitLineNumber(225, label16);
			methodVisitor.visitFrame(Opcodes.F_FULL, 10, new Object[] {"[Ljava/io/File;", "java/util/Deque", "java/util/Set", "java/io/File", "java/util/jar/Manifest", "java/lang/String", "java/lang/String", Opcodes.INTEGER, Opcodes.INTEGER, "[Ljava/lang/String;"}, 1, new Object[] {"java/lang/Exception"});
			methodVisitor.visitVarInsn(ASTORE, 10);
			methodVisitor.visitLabel(label44);
			methodVisitor.visitLineNumber(220, label44);
			methodVisitor.visitFrame(Opcodes.F_FULL, 10, new Object[] {"[Ljava/io/File;", "java/util/Deque", "java/util/Set", "java/io/File", "java/util/jar/Manifest", "java/lang/String", Opcodes.TOP, Opcodes.INTEGER, Opcodes.INTEGER, "[Ljava/lang/String;"}, 0, new Object[] {});
			methodVisitor.visitIincInsn(7, 1);
			methodVisitor.visitLabel(label41);
			methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ILOAD, 7);
			methodVisitor.visitVarInsn(ILOAD, 8);
			methodVisitor.visitJumpInsn(IF_ICMPLT, label42);
			methodVisitor.visitLabel(label21);
			methodVisitor.visitLineNumber(204, label21);
			methodVisitor.visitFrame(Opcodes.F_FULL, 3, new Object[] {"[Ljava/io/File;", "java/util/Deque", "java/util/Set"}, 0, new Object[] {});
			methodVisitor.visitVarInsn(ALOAD, 1);
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/Deque", "isEmpty", "()Z", true);
			methodVisitor.visitJumpInsn(IFEQ, label22);
			Label label45 = new Label();
			methodVisitor.visitLabel(label45);
			methodVisitor.visitLineNumber(230, label45);
			methodVisitor.visitVarInsn(ALOAD, 2);
			methodVisitor.visitInsn(ICONST_0);
			methodVisitor.visitTypeInsn(ANEWARRAY, "java/io/File");
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/Set", "toArray", "([Ljava/lang/Object;)[Ljava/lang/Object;", true);
			methodVisitor.visitTypeInsn(CHECKCAST, "[Ljava/io/File;");
			methodVisitor.visitInsn(ARETURN);
			Label label46 = new Label();
			methodVisitor.visitLabel(label46);
			methodVisitor.visitLocalVariable("files", "[Ljava/io/File;", null, label17, label46, 0);
			methodVisitor.visitLocalVariable("toProcess", "Ljava/util/Deque;", "Ljava/util/Deque<Ljava/io/File;>;", label18, label46, 1);
			methodVisitor.visitLocalVariable("expanded", "Ljava/util/Set;", "Ljava/util/Set<Ljava/io/File;>;", label20, label46, 2);
			methodVisitor.visitLocalVariable("file", "Ljava/io/File;", null, label23, label21, 3);
			methodVisitor.visitLocalVariable("manifest", "Ljava/util/jar/Manifest;", null, label26, label21, 4);
			methodVisitor.visitLocalVariable("manifestFile", "Ljava/io/File;", null, label29, label34, 5);
			methodVisitor.visitLocalVariable("stream", "Ljava/io/FileInputStream;", null, label0, label31, 8);
			methodVisitor.visitLocalVariable("jar", "Ljava/util/jar/JarFile;", null, label7, label35, 7);
			methodVisitor.visitLocalVariable("cp", "Ljava/lang/String;", null, label39, label21, 5);
			methodVisitor.visitLocalVariable("path", "Ljava/lang/String;", null, label14, label44, 6);
			methodVisitor.visitLocalVariable("url", "Ljava/net/URL;", null, label43, label15, 10);
			methodVisitor.visitMaxs(4, 11);
			methodVisitor.visitEnd();
		}

		super.visitEnd();
	}

	private class InjectCallAtInvoke extends MethodVisitor {
		private boolean injected = false;

		private InjectCallAtInvoke(MethodVisitor methodVisitor) {
			super(Opcodes.ASM9, methodVisitor);
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
			super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);

			if (!injected && name.equals(TARGET_INVOKE)) {
				super.visitMethodInsn(INVOKESTATIC, clazz, OUR_METHOD_NAME, OUR_METHOD_DESCRIPTOR, false);
				injected = true;
			}
		}
	}
}
