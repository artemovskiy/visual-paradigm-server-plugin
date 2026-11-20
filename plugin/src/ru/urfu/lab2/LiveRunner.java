package ru.urfu.lab2;

import javax.tools.*;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles a Java source sent to /run (optionally together with every .java under a src dir, so edits of
 * Seq/B/Stage* apply without reinstalling the plugin), loads it child-first in a fresh class loader and calls
 * {@code public static Object run(B b)} (or {@code run()}) on the Swing EDT.
 */
public class LiveRunner {
    static final String DEFAULT_OPENAPI = "/Applications/Visual Paradigm CE.app/Contents/Resources/app/lib/openapi.jar";

    static String run(String source, File srcDir, File pluginDir) throws Exception {
        long t0 = System.currentTimeMillis();
        Matcher pm = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;").matcher(source);
        String pkg = pm.find() ? pm.group(1) : "";
        Matcher cm = Pattern.compile("(?m)^\\s*(?:public\\s+)?(?:final\\s+)?class\\s+(\\w+)").matcher(source);
        if (!cm.find()) return "{\"ok\": false, \"error\": \"no top-level class found in source\"}";
        String cls = cm.group(1);
        String fqcn = pkg.isEmpty() ? cls : pkg + "." + cls;

        File work = Files.createTempDirectory("lab2run").toFile();
        File srcOut = new File(work, "src/" + fqcn.replace('.', '/') + ".java");
        srcOut.getParentFile().mkdirs();
        Files.write(srcOut.toPath(), source.getBytes(StandardCharsets.UTF_8));
        File classes = new File(work, "classes");
        classes.mkdirs();

        List<File> files = new ArrayList<>();
        files.add(srcOut);
        if (srcDir != null) collect(srcDir, files, "/" + fqcn.replace(".", "/") + ".java");

        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        if (jc == null) return "{\"ok\": false, \"error\": \"no system Java compiler in VP's JRE\"}";
        // VP's JRE has no jdk.zipfs, so javac cannot read jars: compile against unpacked copies
        String cp = unpacked(new File(openapiJar())) + File.pathSeparator + unpacked(new File(pluginDir, "lib/lab2.jar"));
        DiagnosticCollector<JavaFileObject> diag = new DiagnosticCollector<>();
        StandardJavaFileManager fm = jc.getStandardFileManager(diag, Locale.ROOT, StandardCharsets.UTF_8);
        List<String> opts = new ArrayList<>();
        for (String o : new String[]{"-encoding", "UTF-8", "-g", "-nowarn", "-proc:none", "-d", classes.getPath(), "-cp", cp}) opts.add(o);
        boolean ok = jc.getTask(null, fm, diag, opts, null, fm.getJavaFileObjectsFromFiles(files)).call();
        fm.close();
        long t1 = System.currentTimeMillis();
        if (!ok) {
            StringBuilder sb = new StringBuilder();
            for (Diagnostic<? extends JavaFileObject> d : diag.getDiagnostics())
                if (d.getKind() == Diagnostic.Kind.ERROR)
                    sb.append(d.getSource() == null ? "" : new File(d.getSource().toUri()).getName()).append(':').append(d.getLineNumber())
                      .append(": ").append(d.getMessage(Locale.ROOT)).append('\n');
            return "{\"ok\": false, \"stage\": \"compile\", \"error\": " + Server.q(sb.toString()) + "}";
        }

        final ClassLoader loader = new ChildFirst(new URL[]{classes.toURI().toURL()}, LiveRunner.class.getClassLoader(), classes);
        final Class<?> main = loader.loadClass(fqcn);
        final Class<?> bCls = loader.loadClass("ru.urfu.lab2.B");
        final Object[] bHolder = new Object[1];
        final ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        String result = null, error = null;
        PrintStream oldOut = System.out;
        try {
            result = Server.edt(() -> {
                Method m = null;
                for (Method x : main.getDeclaredMethods())
                    if (x.getName().equals("run") && Modifier.isStatic(x.getModifiers())
                            && (x.getParameterTypes().length == 0 || (x.getParameterTypes().length == 1 && x.getParameterTypes()[0] == bCls))) m = x;
                if (m == null) throw new IllegalStateException("need: public static Object run(B b)  (or run())");
                m.setAccessible(true);
                System.setOut(new PrintStream(outBuf, true, "UTF-8"));
                Thread.currentThread().setContextClassLoader(loader);
                Object r;
                if (m.getParameterTypes().length == 1) {
                    bHolder[0] = bCls.getConstructor().newInstance();
                    r = m.invoke(null, bHolder[0]);
                } else r = m.invoke(null);
                return r == null ? null : String.valueOf(r);
            });
        } catch (Throwable t) {
            Throwable c = t instanceof InvocationTargetException && t.getCause() != null ? t.getCause() : t;
            error = Server.stack(c);
        } finally {
            System.setOut(oldOut);
        }
        long t2 = System.currentTimeMillis();
        String log = "";
        if (bHolder[0] != null) {
            try { Field f = bCls.getDeclaredField("log"); f.setAccessible(true); log = String.valueOf(f.get(bHolder[0])); }
            catch (Throwable ignored) {}
        }
        return "{\"ok\": " + (error == null && !log.contains("FAIL ")) + ", \"compileMs\": " + (t1 - t0) + ", \"runMs\": " + (t2 - t1)
                + ", \"files\": " + files.size() + ", \"result\": " + Server.q(result)
                + ", \"log\": " + Server.q(log) + ", \"stdout\": " + Server.q(new String(outBuf.toByteArray(), StandardCharsets.UTF_8))
                + ", \"error\": " + Server.q(error) + "}";
    }

    private static void collect(File dir, List<File> out, String skipRel) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) collect(f, out, skipRel);
            else if (f.getName().endsWith(".java") && (skipRel == null || !f.getPath().endsWith(skipRel))) out.add(f);
        }
    }

    static String openapiJar() {
        if (new File(DEFAULT_OPENAPI).isFile()) return DEFAULT_OPENAPI;
        File f = new File(System.getProperty("user.dir"), "lib/openapi.jar");
        return f.isFile() ? f.getPath() : DEFAULT_OPENAPI;
    }

    /** Unpack a jar's .class files once per (path, mtime, size) into ~/Library/Caches/lab2-live. */
    static synchronized String unpacked(File jar) throws IOException {
        if (!jar.isFile()) return jar.getPath();
        File dir = new File(System.getProperty("user.home"), "Library/Caches/lab2-live/"
                + Integer.toHexString(jar.getPath().hashCode()) + "-" + jar.lastModified() + "-" + jar.length());
        File done = new File(dir, ".done");
        if (done.isFile()) return dir.getPath();
        dir.mkdirs();
        try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(new FileInputStream(jar))) {
            java.util.zip.ZipEntry e;
            byte[] buf = new byte[65536];
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory() || !e.getName().endsWith(".class")) continue;
                File out = new File(dir, e.getName());
                out.getParentFile().mkdirs();
                try (OutputStream os = new FileOutputStream(out)) { int n; while ((n = zin.read(buf)) > 0) os.write(buf, 0, n); }
            }
        }
        done.createNewFile();
        return dir.getPath();
    }

    /** Classes compiled in this run win over the installed lab2.jar ones (same names). */
    static class ChildFirst extends URLClassLoader {
        final File root;
        ChildFirst(URL[] urls, ClassLoader parent, File root) { super(urls, parent); this.root = root; }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null && new File(root, name.replace('.', '/') + ".class").isFile()) c = findClass(name);
                if (c == null) return super.loadClass(name, resolve);
                if (resolve) resolveClass(c);
                return c;
            }
        }
    }
}
