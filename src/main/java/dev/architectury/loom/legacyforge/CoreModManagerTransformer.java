package dev.architectury.loom.legacyforge;

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
import static org.objectweb.asm.Opcodes.GETFIELD;
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
import static org.objectweb.asm.Opcodes.RETURN;

import dev.architectury.loom.forge.ForgeVersion;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Transforms Forge's CoreModManager class to search the classpath for coremods.
 * For motivation, see comments at usage site.
 */
public class CoreModManagerTransformer extends ClassVisitor {
	private static final String FORGE_PACKAGE = "net/minecraftforge/";
	private static final String FORGE_CLASS = FORGE_PACKAGE + "fml/relauncher/CoreModManager";
	public static final String FORGE_FILE = FORGE_CLASS + ".class";
	private static final String CPW_PACKAGE = "cpw/mods/";
	private static final String CPW_CLASS = CPW_PACKAGE + "fml/relauncher/CoreModManager";
	public static final String CPW_FILE = CPW_CLASS + ".class";

	private static final String TARGET_METHOD = "discoverCoreMods";
	private static final String OUR_METHOD_NAME = "loom$injectCoremodsFromClasspath";
	private static final String OUR_METHOD_DESCRIPTOR = "(Lnet/minecraft/launchwrapper/LaunchClassLoader;)V";

	private final ForgeVersion forgeVersion;
	private final String pakkage;
	private final String clazz;

	public CoreModManagerTransformer(ClassVisitor classVisitor, ForgeVersion forgeVersion) {
		super(Opcodes.ASM9, classVisitor);
		this.forgeVersion = forgeVersion;
		pakkage = forgeVersion.cpwFml() ? CPW_PACKAGE : FORGE_PACKAGE;
		clazz = forgeVersion.cpwFml() ? CPW_CLASS : FORGE_CLASS;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);

		// We inject a call to our method, which will discover and load coremods from the classpath, at the very start of the
		// regular discovery method.
		if (name.equals(TARGET_METHOD)) {
			methodVisitor = new InjectCallAtHead(methodVisitor);
		}

		return methodVisitor;
	}

	@Override
	public void visitEnd() {
		// We add the following method, which will find all coremods on the classpath, and load them.
		//
		//	private static void loom$injectCoremodsFromClasspath(LaunchClassLoader classLoader) {
		//		Set<String> coreMods = new HashSet<>();
		//		for (FMLPluginWrapper a : loadPlugins) coreMods.add(a.coreModInstance.getClass().getName());
		//		List<String> tweaks = (List<String>) Launch.blackboard.get("TweakClasses");
		//		URL forgeUrl = CoreModManager.class.getProtectionDomain().getCodeSource().getLocation();
		//		Deque<URL> toProcess = new LinkedList<>();
		//		Collections.addAll(toProcess, classLoader.getURLs());
		//		loop: while (!toProcess.isEmpty()) {
		//			URL url = toProcess.poll();
		//			if (url.equals(forgeUrl) || !url.getProtocol().equals("file")) continue;
		//			try {
		//				File file = new File(url.toURI().getPath());
		//				if (!file.exists()) continue;
		//				Manifest manifest = null;
		//				if (file.isDirectory()) {
		//					File manifestFile = new File(file, "META-INF/MANIFEST.MF");
		//					if (manifestFile.exists()) try (FileInputStream stream = new FileInputStream(manifestFile)) {
		//						manifest = new Manifest(stream);
		//					} catch (Exception e) {}
		//				} else if (file.getName().endsWith("jar")) try (JarFile jar = new JarFile(file)) {
		//					manifest = jar.getManifest();
		//					if (manifest != null) {
		//						String ats = manifest.getMainAttributes().getValue("FMLAT");
		//						if (ats != null && !ats.isEmpty()) ModAccessTransformer.addJar(jar, ats);
		//					}
		//				} catch (Exception e) {}
		//				if (manifest != null) {
		//					String cp = manifest.getMainAttributes().getValue("Class-Path");
		//					if (cp != null) {
		//						for (String path : cp.split(" ")) {
		//							try {
		//								URL cpurl = new URL(url, path);
		//								if(cpurl.getProtocol().equals("file")) toProcess.add(cpurl);
		//							} catch(Exception e) {}
		//						}
		//					}
		//					String tweak = manifest.getMainAttributes().getValue("TweakClass");
		//					if (tweak != null) {
		//						if (!tweaks.contains(tweak)) {
		//							Integer sortOrder = Ints.tryParse(Strings.nullToEmpty(manifest.getMainAttributes().getValue("TweakOrder")));
		//							tweaks.add(tweak);
		//							if (sortOrder != null) tweakSorting.put(tweak, sortOrder);
		//						}
		//						continue;
		//					}
		//					String coreMod = manifest.getMainAttributes().getValue("FMLCorePlugin");
		//					if (coreMod != null) {
		//						for (FMLPluginWrapper plugin : loadPlugins) if (plugin.coreModInstance.getClass().getName().equals(coreMod)) continue loop;
		//						loadCoreMod(classLoader, coreMod, file);
		//					}
		//				}
		//			} catch(Exception e) {}
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
			Label label18 = new Label();
			Label label19 = new Label();
			methodVisitor.visitTryCatchBlock(label17, label18, label19, "java/lang/Exception");
			Label label20 = new Label();
			Label label21 = new Label();
			methodVisitor.visitTryCatchBlock(label20, label21, label19, "java/lang/Exception");
			Label label22 = new Label();
			Label label23 = new Label();
			methodVisitor.visitTryCatchBlock(label22, label23, label19, "java/lang/Exception");
			Label label24 = new Label();
			Label label25 = new Label();
			methodVisitor.visitTryCatchBlock(label24, label25, label19, "java/lang/Exception");
			Label label26 = new Label();
			methodVisitor.visitLabel(label26);
			methodVisitor.visitLineNumber(752, label26);
			methodVisitor.visitTypeInsn(NEW, "java/util/HashSet");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/HashSet", "<init>", "()V", false);
			methodVisitor.visitVarInsn(ASTORE, 1);
			Label label27 = new Label();
			methodVisitor.visitLabel(label27);
			methodVisitor.visitLineNumber(753, label27);
			methodVisitor.visitFieldInsn(GETSTATIC, pakkage + "fml/relauncher/CoreModManager", "loadPlugins", "Ljava/util/List;");
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
			methodVisitor.visitVarInsn(ASTORE, 3);
			Label label28 = new Label();
			methodVisitor.visitJumpInsn(GOTO, label28);
			Label label29 = new Label();
			methodVisitor.visitLabel(label29);
			methodVisitor.visitFrame(Opcodes.F_FULL, 4, new Object[] {"net/minecraft/launchwrapper/LaunchClassLoader", "java/util/Set", Opcodes.TOP, "java/util/Iterator"}, 0, new Object[] {});
			methodVisitor.visitVarInsn(ALOAD, 3);
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
			methodVisitor.visitTypeInsn(CHECKCAST, pakkage + "fml/relauncher/CoreModManager$FMLPluginWrapper");
			methodVisitor.visitVarInsn(ASTORE, 2);
			Label label30 = new Label();
			methodVisitor.visitLabel(label30);
			methodVisitor.visitVarInsn(ALOAD, 1);
			methodVisitor.visitVarInsn(ALOAD, 2);
			methodVisitor.visitFieldInsn(GETFIELD, pakkage + "fml/relauncher/CoreModManager$FMLPluginWrapper", "coreModInstance", "L" + pakkage + "fml/relauncher/IFMLLoadingPlugin;");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/Set", "add", "(Ljava/lang/Object;)Z", true);
			methodVisitor.visitInsn(POP);
			methodVisitor.visitLabel(label28);
			methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 3);
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
			methodVisitor.visitJumpInsn(IFNE, label29);
			Label label31 = new Label();
			methodVisitor.visitLabel(label31);
			methodVisitor.visitLineNumber(754, label31);
			methodVisitor.visitFieldInsn(GETSTATIC, "net/minecraft/launchwrapper/Launch", "blackboard", "Ljava/util/Map;");
			methodVisitor.visitLdcInsn("TweakClasses");
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
			methodVisitor.visitTypeInsn(CHECKCAST, "java/util/List");
			methodVisitor.visitVarInsn(ASTORE, 2);
			Label label32 = new Label();
			methodVisitor.visitLabel(label32);
			methodVisitor.visitLineNumber(755, label32);
			methodVisitor.visitLdcInsn(Type.getType("L" + pakkage + "fml/relauncher/CoreModManager;"));
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getProtectionDomain", "()Ljava/security/ProtectionDomain;", false);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/security/ProtectionDomain", "getCodeSource", "()Ljava/security/CodeSource;", false);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/security/CodeSource", "getLocation", "()Ljava/net/URL;", false);
			methodVisitor.visitVarInsn(ASTORE, 3);
			Label label33 = new Label();
			methodVisitor.visitLabel(label33);
			methodVisitor.visitLineNumber(756, label33);
			methodVisitor.visitTypeInsn(NEW, "java/util/LinkedList");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/LinkedList", "<init>", "()V", false);
			methodVisitor.visitVarInsn(ASTORE, 4);
			Label label34 = new Label();
			methodVisitor.visitLabel(label34);
			methodVisitor.visitLineNumber(757, label34);
			methodVisitor.visitVarInsn(ALOAD, 4);
			methodVisitor.visitVarInsn(ALOAD, 0);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "net/minecraft/launchwrapper/LaunchClassLoader", "getURLs", "()[Ljava/net/URL;", false);
			methodVisitor.visitMethodInsn(INVOKESTATIC, "java/util/Collections", "addAll", "(Ljava/util/Collection;[Ljava/lang/Object;)Z", false);
			methodVisitor.visitInsn(POP);
			Label label35 = new Label();
			methodVisitor.visitLabel(label35);
			methodVisitor.visitLineNumber(758, label35);
			Label label36 = new Label();
			methodVisitor.visitJumpInsn(GOTO, label36);
			Label label37 = new Label();
			methodVisitor.visitLabel(label37);
			methodVisitor.visitLineNumber(759, label37);
			methodVisitor.visitFrame(Opcodes.F_FULL, 5, new Object[] {"net/minecraft/launchwrapper/LaunchClassLoader", "java/util/Set", "java/util/List", "java/net/URL", "java/util/Deque"}, 0, new Object[] {});
			methodVisitor.visitVarInsn(ALOAD, 4);
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/Deque", "poll", "()Ljava/lang/Object;", true);
			methodVisitor.visitTypeInsn(CHECKCAST, "java/net/URL");
			methodVisitor.visitVarInsn(ASTORE, 5);
			Label label38 = new Label();
			methodVisitor.visitLabel(label38);
			methodVisitor.visitLineNumber(760, label38);
			methodVisitor.visitVarInsn(ALOAD, 5);
			methodVisitor.visitVarInsn(ALOAD, 3);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/net/URL", "equals", "(Ljava/lang/Object;)Z", false);
			methodVisitor.visitJumpInsn(IFNE, label36);
			methodVisitor.visitVarInsn(ALOAD, 5);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/net/URL", "getProtocol", "()Ljava/lang/String;", false);
			methodVisitor.visitLdcInsn("file");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
			methodVisitor.visitJumpInsn(IFNE, label17);
			methodVisitor.visitJumpInsn(GOTO, label36);
			methodVisitor.visitLabel(label17);
			methodVisitor.visitLineNumber(762, label17);
			methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[] {"java/net/URL"}, 0, null);
			methodVisitor.visitTypeInsn(NEW, "java/io/File");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ALOAD, 5);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/net/URL", "toURI", "()Ljava/net/URI;", false);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/net/URI", "getPath", "()Ljava/lang/String;", false);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false);
			methodVisitor.visitVarInsn(ASTORE, 6);
			Label label39 = new Label();
			methodVisitor.visitLabel(label39);
			methodVisitor.visitLineNumber(763, label39);
			methodVisitor.visitVarInsn(ALOAD, 6);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/File", "exists", "()Z", false);
			methodVisitor.visitJumpInsn(IFNE, label20);
			methodVisitor.visitLabel(label18);
			methodVisitor.visitJumpInsn(GOTO, label36);
			methodVisitor.visitLabel(label20);
			methodVisitor.visitLineNumber(764, label20);
			methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[] {"java/io/File"}, 0, null);
			methodVisitor.visitInsn(ACONST_NULL);
			methodVisitor.visitVarInsn(ASTORE, 7);
			Label label40 = new Label();
			methodVisitor.visitLabel(label40);
			methodVisitor.visitLineNumber(765, label40);
			methodVisitor.visitVarInsn(ALOAD, 6);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/File", "isDirectory", "()Z", false);
			Label label41 = new Label();
			methodVisitor.visitJumpInsn(IFEQ, label41);
			Label label42 = new Label();
			methodVisitor.visitLabel(label42);
			methodVisitor.visitLineNumber(766, label42);
			methodVisitor.visitTypeInsn(NEW, "java/io/File");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ALOAD, 6);
			methodVisitor.visitLdcInsn("META-INF/MANIFEST.MF");
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/io/File;Ljava/lang/String;)V", false);
			methodVisitor.visitVarInsn(ASTORE, 8);
			Label label43 = new Label();
			methodVisitor.visitLabel(label43);
			methodVisitor.visitLineNumber(767, label43);
			methodVisitor.visitVarInsn(ALOAD, 8);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/File", "exists", "()Z", false);
			Label label44 = new Label();
			methodVisitor.visitJumpInsn(IFEQ, label44);
			methodVisitor.visitLabel(label5);
			methodVisitor.visitInsn(ACONST_NULL);
			methodVisitor.visitVarInsn(ASTORE, 9);
			methodVisitor.visitInsn(ACONST_NULL);
			methodVisitor.visitVarInsn(ASTORE, 10);
			methodVisitor.visitLabel(label3);
			methodVisitor.visitTypeInsn(NEW, "java/io/FileInputStream");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ALOAD, 8);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/io/FileInputStream", "<init>", "(Ljava/io/File;)V", false);
			methodVisitor.visitVarInsn(ASTORE, 11);
			methodVisitor.visitLabel(label0);
			methodVisitor.visitLineNumber(768, label0);
			methodVisitor.visitTypeInsn(NEW, "java/util/jar/Manifest");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ALOAD, 11);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/jar/Manifest", "<init>", "(Ljava/io/InputStream;)V", false);
			methodVisitor.visitVarInsn(ASTORE, 7);
			methodVisitor.visitLabel(label1);
			methodVisitor.visitLineNumber(769, label1);
			methodVisitor.visitVarInsn(ALOAD, 11);
			methodVisitor.visitJumpInsn(IFNULL, label44);
			methodVisitor.visitVarInsn(ALOAD, 11);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/FileInputStream", "close", "()V", false);
			methodVisitor.visitJumpInsn(GOTO, label44);
			methodVisitor.visitLabel(label2);
			methodVisitor.visitFrame(Opcodes.F_FULL, 12, new Object[] {"net/minecraft/launchwrapper/LaunchClassLoader", "java/util/Set", "java/util/List", "java/net/URL", "java/util/Deque", "java/net/URL", "java/io/File", "java/util/jar/Manifest", "java/io/File", "java/lang/Throwable", "java/lang/Throwable", "java/io/FileInputStream"}, 1, new Object[] {"java/lang/Throwable"});
			methodVisitor.visitVarInsn(ASTORE, 9);
			methodVisitor.visitVarInsn(ALOAD, 11);
			Label label45 = new Label();
			methodVisitor.visitJumpInsn(IFNULL, label45);
			methodVisitor.visitVarInsn(ALOAD, 11);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/FileInputStream", "close", "()V", false);
			methodVisitor.visitLabel(label45);
			methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 9);
			methodVisitor.visitInsn(ATHROW);
			methodVisitor.visitLabel(label4);
			methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
			methodVisitor.visitVarInsn(ASTORE, 10);
			methodVisitor.visitVarInsn(ALOAD, 9);
			Label label46 = new Label();
			methodVisitor.visitJumpInsn(IFNONNULL, label46);
			methodVisitor.visitVarInsn(ALOAD, 10);
			methodVisitor.visitVarInsn(ASTORE, 9);
			Label label47 = new Label();
			methodVisitor.visitJumpInsn(GOTO, label47);
			methodVisitor.visitLabel(label46);
			methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 9);
			methodVisitor.visitVarInsn(ALOAD, 10);
			methodVisitor.visitJumpInsn(IF_ACMPEQ, label47);
			methodVisitor.visitVarInsn(ALOAD, 9);
			methodVisitor.visitVarInsn(ALOAD, 10);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Throwable", "addSuppressed", "(Ljava/lang/Throwable;)V", false);
			methodVisitor.visitLabel(label47);
			methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 9);
			methodVisitor.visitInsn(ATHROW);
			methodVisitor.visitLabel(label6);
			methodVisitor.visitFrame(Opcodes.F_FULL, 9, new Object[] {"net/minecraft/launchwrapper/LaunchClassLoader", "java/util/Set", "java/util/List", "java/net/URL", "java/util/Deque", "java/net/URL", "java/io/File", "java/util/jar/Manifest", "java/io/File"}, 1, new Object[] {"java/lang/Exception"});
			methodVisitor.visitVarInsn(ASTORE, 9);
			Label label48 = new Label();
			methodVisitor.visitLabel(label48);
			methodVisitor.visitLineNumber(770, label48);
			methodVisitor.visitJumpInsn(GOTO, label44);
			methodVisitor.visitLabel(label41);
			methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 6);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/File", "getName", "()Ljava/lang/String;", false);
			methodVisitor.visitLdcInsn("jar");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "endsWith", "(Ljava/lang/String;)Z", false);
			methodVisitor.visitJumpInsn(IFEQ, label44);
			methodVisitor.visitLabel(label12);
			methodVisitor.visitInsn(ACONST_NULL);
			methodVisitor.visitVarInsn(ASTORE, 8);
			methodVisitor.visitInsn(ACONST_NULL);
			methodVisitor.visitVarInsn(ASTORE, 9);
			methodVisitor.visitLabel(label10);
			methodVisitor.visitTypeInsn(NEW, "java/util/jar/JarFile");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ALOAD, 6);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/jar/JarFile", "<init>", "(Ljava/io/File;)V", false);
			methodVisitor.visitVarInsn(ASTORE, 10);
			methodVisitor.visitLabel(label7);
			methodVisitor.visitLineNumber(771, label7);
			methodVisitor.visitVarInsn(ALOAD, 10);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/JarFile", "getManifest", "()Ljava/util/jar/Manifest;", false);
			methodVisitor.visitVarInsn(ASTORE, 7);
			Label label49 = new Label();
			methodVisitor.visitLabel(label49);
			methodVisitor.visitLineNumber(772, label49);
			methodVisitor.visitVarInsn(ALOAD, 7);
			methodVisitor.visitJumpInsn(IFNULL, label8);
			Label label50 = new Label();
			methodVisitor.visitLabel(label50);
			methodVisitor.visitLineNumber(773, label50);
			methodVisitor.visitVarInsn(ALOAD, 7);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/Manifest", "getMainAttributes", "()Ljava/util/jar/Attributes;", false);
			methodVisitor.visitLdcInsn("FMLAT");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/Attributes", "getValue", "(Ljava/lang/String;)Ljava/lang/String;", false);
			methodVisitor.visitVarInsn(ASTORE, 11);
			Label label51 = new Label();
			methodVisitor.visitLabel(label51);
			methodVisitor.visitLineNumber(774, label51);
			methodVisitor.visitVarInsn(ALOAD, 11);
			methodVisitor.visitJumpInsn(IFNULL, label8);
			methodVisitor.visitVarInsn(ALOAD, 11);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "isEmpty", "()Z", false);
			methodVisitor.visitJumpInsn(IFNE, label8);
			methodVisitor.visitVarInsn(ALOAD, 10);

			if (!forgeVersion.versionModDirs()) {
				methodVisitor.visitVarInsn(ALOAD, 11);
				methodVisitor.visitMethodInsn(INVOKESTATIC, pakkage + "fml/common/asm/transformers/ModAccessTransformer", "addJar", "(Ljava/util/jar/JarFile;Ljava/lang/String;)V", false);
			} else {
				methodVisitor.visitMethodInsn(INVOKESTATIC, pakkage + "fml/common/asm/transformers/ModAccessTransformer", "addJar", "(Ljava/util/jar/JarFile;)V", false);
			}

			methodVisitor.visitLabel(label8);
			methodVisitor.visitLineNumber(776, label8);
			methodVisitor.visitFrame(Opcodes.F_APPEND, 3, new Object[] {"java/lang/Throwable", "java/lang/Throwable", "java/util/jar/JarFile"}, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 10);
			methodVisitor.visitJumpInsn(IFNULL, label44);
			methodVisitor.visitVarInsn(ALOAD, 10);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/JarFile", "close", "()V", false);
			methodVisitor.visitJumpInsn(GOTO, label44);
			methodVisitor.visitLabel(label9);
			methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
			methodVisitor.visitVarInsn(ASTORE, 8);
			methodVisitor.visitVarInsn(ALOAD, 10);
			Label label52 = new Label();
			methodVisitor.visitJumpInsn(IFNULL, label52);
			methodVisitor.visitVarInsn(ALOAD, 10);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/JarFile", "close", "()V", false);
			methodVisitor.visitLabel(label52);
			methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 8);
			methodVisitor.visitInsn(ATHROW);
			methodVisitor.visitLabel(label11);
			methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
			methodVisitor.visitVarInsn(ASTORE, 9);
			methodVisitor.visitVarInsn(ALOAD, 8);
			Label label53 = new Label();
			methodVisitor.visitJumpInsn(IFNONNULL, label53);
			methodVisitor.visitVarInsn(ALOAD, 9);
			methodVisitor.visitVarInsn(ASTORE, 8);
			Label label54 = new Label();
			methodVisitor.visitJumpInsn(GOTO, label54);
			methodVisitor.visitLabel(label53);
			methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 8);
			methodVisitor.visitVarInsn(ALOAD, 9);
			methodVisitor.visitJumpInsn(IF_ACMPEQ, label54);
			methodVisitor.visitVarInsn(ALOAD, 8);
			methodVisitor.visitVarInsn(ALOAD, 9);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Throwable", "addSuppressed", "(Ljava/lang/Throwable;)V", false);
			methodVisitor.visitLabel(label54);
			methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 8);
			methodVisitor.visitInsn(ATHROW);
			methodVisitor.visitLabel(label13);
			methodVisitor.visitFrame(Opcodes.F_FULL, 8, new Object[] {"net/minecraft/launchwrapper/LaunchClassLoader", "java/util/Set", "java/util/List", "java/net/URL", "java/util/Deque", "java/net/URL", "java/io/File", "java/util/jar/Manifest"}, 1, new Object[] {"java/lang/Exception"});
			methodVisitor.visitVarInsn(ASTORE, 8);
			methodVisitor.visitLabel(label44);
			methodVisitor.visitLineNumber(777, label44);
			methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 7);
			methodVisitor.visitJumpInsn(IFNULL, label36);
			Label label55 = new Label();
			methodVisitor.visitLabel(label55);
			methodVisitor.visitLineNumber(778, label55);
			methodVisitor.visitVarInsn(ALOAD, 7);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/Manifest", "getMainAttributes", "()Ljava/util/jar/Attributes;", false);
			methodVisitor.visitLdcInsn("Class-Path");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/Attributes", "getValue", "(Ljava/lang/String;)Ljava/lang/String;", false);
			methodVisitor.visitVarInsn(ASTORE, 8);
			Label label56 = new Label();
			methodVisitor.visitLabel(label56);
			methodVisitor.visitLineNumber(779, label56);
			methodVisitor.visitVarInsn(ALOAD, 8);
			Label label57 = new Label();
			methodVisitor.visitJumpInsn(IFNULL, label57);
			Label label58 = new Label();
			methodVisitor.visitLabel(label58);
			methodVisitor.visitLineNumber(780, label58);
			methodVisitor.visitVarInsn(ALOAD, 8);
			methodVisitor.visitLdcInsn(" ");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false);
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ASTORE, 12);
			methodVisitor.visitInsn(ARRAYLENGTH);
			methodVisitor.visitVarInsn(ISTORE, 11);
			methodVisitor.visitInsn(ICONST_0);
			methodVisitor.visitVarInsn(ISTORE, 10);
			Label label59 = new Label();
			methodVisitor.visitJumpInsn(GOTO, label59);
			Label label60 = new Label();
			methodVisitor.visitLabel(label60);
			methodVisitor.visitFrame(Opcodes.F_FULL, 13, new Object[] {"net/minecraft/launchwrapper/LaunchClassLoader", "java/util/Set", "java/util/List", "java/net/URL", "java/util/Deque", "java/net/URL", "java/io/File", "java/util/jar/Manifest", "java/lang/String", Opcodes.TOP, Opcodes.INTEGER, Opcodes.INTEGER, "[Ljava/lang/String;"}, 0, new Object[] {});
			methodVisitor.visitVarInsn(ALOAD, 12);
			methodVisitor.visitVarInsn(ILOAD, 10);
			methodVisitor.visitInsn(AALOAD);
			methodVisitor.visitVarInsn(ASTORE, 9);
			methodVisitor.visitLabel(label14);
			methodVisitor.visitLineNumber(782, label14);
			methodVisitor.visitTypeInsn(NEW, "java/net/URL");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ALOAD, 5);
			methodVisitor.visitVarInsn(ALOAD, 9);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/net/URL", "<init>", "(Ljava/net/URL;Ljava/lang/String;)V", false);
			methodVisitor.visitVarInsn(ASTORE, 13);
			Label label61 = new Label();
			methodVisitor.visitLabel(label61);
			methodVisitor.visitLineNumber(783, label61);
			methodVisitor.visitVarInsn(ALOAD, 13);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/net/URL", "getProtocol", "()Ljava/lang/String;", false);
			methodVisitor.visitLdcInsn("file");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
			Label label62 = new Label();
			methodVisitor.visitJumpInsn(IFEQ, label62);
			methodVisitor.visitVarInsn(ALOAD, 4);
			methodVisitor.visitVarInsn(ALOAD, 13);
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/Deque", "add", "(Ljava/lang/Object;)Z", true);
			methodVisitor.visitInsn(POP);
			methodVisitor.visitLabel(label15);
			methodVisitor.visitLineNumber(784, label15);
			methodVisitor.visitJumpInsn(GOTO, label62);
			methodVisitor.visitLabel(label16);
			methodVisitor.visitFrame(Opcodes.F_FULL, 13, new Object[] {"net/minecraft/launchwrapper/LaunchClassLoader", "java/util/Set", "java/util/List", "java/net/URL", "java/util/Deque", "java/net/URL", "java/io/File", "java/util/jar/Manifest", "java/lang/String", "java/lang/String", Opcodes.INTEGER, Opcodes.INTEGER, "[Ljava/lang/String;"}, 1, new Object[] {"java/lang/Exception"});
			methodVisitor.visitVarInsn(ASTORE, 13);
			methodVisitor.visitLabel(label62);
			methodVisitor.visitLineNumber(780, label62);
			methodVisitor.visitFrame(Opcodes.F_FULL, 13, new Object[] {"net/minecraft/launchwrapper/LaunchClassLoader", "java/util/Set", "java/util/List", "java/net/URL", "java/util/Deque", "java/net/URL", "java/io/File", "java/util/jar/Manifest", "java/lang/String", Opcodes.TOP, Opcodes.INTEGER, Opcodes.INTEGER, "[Ljava/lang/String;"}, 0, new Object[] {});
			methodVisitor.visitIincInsn(10, 1);
			methodVisitor.visitLabel(label59);
			methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ILOAD, 10);
			methodVisitor.visitVarInsn(ILOAD, 11);
			methodVisitor.visitJumpInsn(IF_ICMPLT, label60);
			methodVisitor.visitLabel(label57);
			methodVisitor.visitLineNumber(787, label57);
			methodVisitor.visitFrame(Opcodes.F_FULL, 9, new Object[] {"net/minecraft/launchwrapper/LaunchClassLoader", "java/util/Set", "java/util/List", "java/net/URL", "java/util/Deque", "java/net/URL", "java/io/File", "java/util/jar/Manifest", "java/lang/String"}, 0, new Object[] {});
			methodVisitor.visitVarInsn(ALOAD, 7);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/Manifest", "getMainAttributes", "()Ljava/util/jar/Attributes;", false);
			methodVisitor.visitLdcInsn("TweakClass");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/Attributes", "getValue", "(Ljava/lang/String;)Ljava/lang/String;", false);
			methodVisitor.visitVarInsn(ASTORE, 9);
			Label label63 = new Label();
			methodVisitor.visitLabel(label63);
			methodVisitor.visitLineNumber(788, label63);
			methodVisitor.visitVarInsn(ALOAD, 9);
			methodVisitor.visitJumpInsn(IFNULL, label22);
			Label label64 = new Label();
			methodVisitor.visitLabel(label64);
			methodVisitor.visitLineNumber(789, label64);
			methodVisitor.visitVarInsn(ALOAD, 2);
			methodVisitor.visitVarInsn(ALOAD, 9);
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "contains", "(Ljava/lang/Object;)Z", true);
			methodVisitor.visitJumpInsn(IFNE, label36);
			Label label65 = new Label();
			methodVisitor.visitLabel(label65);
			methodVisitor.visitLineNumber(790, label65);
			methodVisitor.visitVarInsn(ALOAD, 7);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/Manifest", "getMainAttributes", "()Ljava/util/jar/Attributes;", false);
			methodVisitor.visitLdcInsn("TweakOrder");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/Attributes", "getValue", "(Ljava/lang/String;)Ljava/lang/String;", false);
			methodVisitor.visitMethodInsn(INVOKESTATIC, "com/google/common/base/Strings", "nullToEmpty", "(Ljava/lang/String;)Ljava/lang/String;", false);
			methodVisitor.visitMethodInsn(INVOKESTATIC, "com/google/common/primitives/Ints", "tryParse", "(Ljava/lang/String;)Ljava/lang/Integer;", false);
			methodVisitor.visitVarInsn(ASTORE, 10);
			Label label66 = new Label();
			methodVisitor.visitLabel(label66);
			methodVisitor.visitLineNumber(791, label66);
			methodVisitor.visitVarInsn(ALOAD, 2);
			methodVisitor.visitVarInsn(ALOAD, 9);
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
			methodVisitor.visitInsn(POP);
			Label label67 = new Label();
			methodVisitor.visitLabel(label67);
			methodVisitor.visitLineNumber(792, label67);
			methodVisitor.visitVarInsn(ALOAD, 10);
			methodVisitor.visitJumpInsn(IFNULL, label36);
			methodVisitor.visitFieldInsn(GETSTATIC, pakkage + "fml/relauncher/CoreModManager", "tweakSorting", "Ljava/util/Map;");
			methodVisitor.visitVarInsn(ALOAD, 9);
			methodVisitor.visitVarInsn(ALOAD, 10);
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
			methodVisitor.visitInsn(POP);
			methodVisitor.visitLabel(label21);
			methodVisitor.visitLineNumber(794, label21);
			methodVisitor.visitJumpInsn(GOTO, label36);
			methodVisitor.visitLabel(label22);
			methodVisitor.visitLineNumber(796, label22);
			methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[] {"java/lang/String"}, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 7);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/Manifest", "getMainAttributes", "()Ljava/util/jar/Attributes;", false);
			methodVisitor.visitLdcInsn("FMLCorePlugin");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/Attributes", "getValue", "(Ljava/lang/String;)Ljava/lang/String;", false);
			methodVisitor.visitVarInsn(ASTORE, 10);
			Label label68 = new Label();
			methodVisitor.visitLabel(label68);
			methodVisitor.visitLineNumber(797, label68);
			methodVisitor.visitVarInsn(ALOAD, 10);
			methodVisitor.visitJumpInsn(IFNULL, label36);
			Label label69 = new Label();
			methodVisitor.visitLabel(label69);
			methodVisitor.visitLineNumber(798, label69);
			methodVisitor.visitFieldInsn(GETSTATIC, pakkage + "fml/relauncher/CoreModManager", "loadPlugins", "Ljava/util/List;");
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
			methodVisitor.visitVarInsn(ASTORE, 12);
			methodVisitor.visitJumpInsn(GOTO, label24);
			Label label70 = new Label();
			methodVisitor.visitLabel(label70);
			methodVisitor.visitFrame(Opcodes.F_FULL, 13, new Object[] {"net/minecraft/launchwrapper/LaunchClassLoader", "java/util/Set", "java/util/List", "java/net/URL", "java/util/Deque", "java/net/URL", "java/io/File", "java/util/jar/Manifest", "java/lang/String", "java/lang/String", "java/lang/String", Opcodes.TOP, "java/util/Iterator"}, 0, new Object[] {});
			methodVisitor.visitVarInsn(ALOAD, 12);
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
			methodVisitor.visitTypeInsn(CHECKCAST, pakkage + "fml/relauncher/CoreModManager$FMLPluginWrapper");
			methodVisitor.visitVarInsn(ASTORE, 11);
			Label label71 = new Label();
			methodVisitor.visitLabel(label71);
			methodVisitor.visitVarInsn(ALOAD, 11);
			methodVisitor.visitFieldInsn(GETFIELD, pakkage + "fml/relauncher/CoreModManager$FMLPluginWrapper", "coreModInstance", "L" + pakkage + "fml/relauncher/IFMLLoadingPlugin;");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
			methodVisitor.visitVarInsn(ALOAD, 10);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
			methodVisitor.visitJumpInsn(IFEQ, label24);
			methodVisitor.visitLabel(label23);
			methodVisitor.visitJumpInsn(GOTO, label36);
			methodVisitor.visitLabel(label24);
			methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 12);
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
			methodVisitor.visitJumpInsn(IFNE, label70);
			Label label72 = new Label();
			methodVisitor.visitLabel(label72);
			methodVisitor.visitLineNumber(799, label72);
			methodVisitor.visitVarInsn(ALOAD, 0);
			methodVisitor.visitVarInsn(ALOAD, 10);
			methodVisitor.visitVarInsn(ALOAD, 6);
			methodVisitor.visitMethodInsn(INVOKESTATIC, pakkage + "fml/relauncher/CoreModManager", "loadCoreMod", "(Lnet/minecraft/launchwrapper/LaunchClassLoader;Ljava/lang/String;Ljava/io/File;)L" + pakkage + "fml/relauncher/CoreModManager$FMLPluginWrapper;", false);
			methodVisitor.visitInsn(POP);
			methodVisitor.visitLabel(label25);
			methodVisitor.visitLineNumber(802, label25);
			methodVisitor.visitJumpInsn(GOTO, label36);
			methodVisitor.visitLabel(label19);
			methodVisitor.visitFrame(Opcodes.F_FULL, 6, new Object[] {"net/minecraft/launchwrapper/LaunchClassLoader", "java/util/Set", "java/util/List", "java/net/URL", "java/util/Deque", "java/net/URL"}, 1, new Object[] {"java/lang/Exception"});
			methodVisitor.visitVarInsn(ASTORE, 6);
			methodVisitor.visitLabel(label36);
			methodVisitor.visitLineNumber(758, label36);
			methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 4);
			methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/util/Deque", "isEmpty", "()Z", true);
			methodVisitor.visitJumpInsn(IFEQ, label37);
			Label label73 = new Label();
			methodVisitor.visitLabel(label73);
			methodVisitor.visitLineNumber(804, label73);
			methodVisitor.visitInsn(RETURN);
			Label label74 = new Label();
			methodVisitor.visitLabel(label74);
			methodVisitor.visitLocalVariable("classLoader", "Lnet/minecraft/launchwrapper/LaunchClassLoader;", null, label26, label74, 0);
			methodVisitor.visitLocalVariable("coreMods", "Ljava/util/Set;", "Ljava/util/Set<Ljava/lang/String;>;", label27, label74, 1);
			methodVisitor.visitLocalVariable("a", "L" + pakkage + "fml/relauncher/CoreModManager$FMLPluginWrapper;", null, label30, label28, 2);
			methodVisitor.visitLocalVariable("tweaks", "Ljava/util/List;", "Ljava/util/List<Ljava/lang/String;>;", label32, label74, 2);
			methodVisitor.visitLocalVariable("forgeUrl", "Ljava/net/URL;", null, label33, label74, 3);
			methodVisitor.visitLocalVariable("toProcess", "Ljava/util/Deque;", "Ljava/util/Deque<Ljava/net/URL;>;", label34, label74, 4);
			methodVisitor.visitLocalVariable("url", "Ljava/net/URL;", null, label38, label36, 5);
			methodVisitor.visitLocalVariable("file", "Ljava/io/File;", null, label39, label25, 6);
			methodVisitor.visitLocalVariable("manifest", "Ljava/util/jar/Manifest;", null, label40, label25, 7);
			methodVisitor.visitLocalVariable("manifestFile", "Ljava/io/File;", null, label43, label48, 8);
			methodVisitor.visitLocalVariable("stream", "Ljava/io/FileInputStream;", null, label0, label45, 11);
			methodVisitor.visitLocalVariable("jar", "Ljava/util/jar/JarFile;", null, label7, label52, 10);
			methodVisitor.visitLocalVariable("ats", "Ljava/lang/String;", null, label51, label8, 11);
			methodVisitor.visitLocalVariable("cp", "Ljava/lang/String;", null, label56, label25, 8);
			methodVisitor.visitLocalVariable("path", "Ljava/lang/String;", null, label14, label62, 9);
			methodVisitor.visitLocalVariable("cpurl", "Ljava/net/URL;", null, label61, label15, 13);
			methodVisitor.visitLocalVariable("tweak", "Ljava/lang/String;", null, label63, label25, 9);
			methodVisitor.visitLocalVariable("sortOrder", "Ljava/lang/Integer;", null, label66, label21, 10);
			methodVisitor.visitLocalVariable("coreMod", "Ljava/lang/String;", null, label68, label25, 10);
			methodVisitor.visitLocalVariable("plugin", "L" + pakkage + "fml/relauncher/CoreModManager$FMLPluginWrapper;", null, label71, label24, 11);
			methodVisitor.visitMaxs(4, 14);
			methodVisitor.visitEnd();
		}

		super.visitEnd();
	}

	private class InjectCallAtHead extends MethodVisitor {
		private InjectCallAtHead(MethodVisitor methodVisitor) {
			super(Opcodes.ASM9, methodVisitor);
		}

		@Override
		public void visitCode() {
			super.visitCode();

			super.visitVarInsn(Opcodes.ALOAD, 1);
			super.visitMethodInsn(Opcodes.INVOKESTATIC, clazz, OUR_METHOD_NAME, OUR_METHOD_DESCRIPTOR, false);
		}
	}
}
