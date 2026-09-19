package com.jwplayer.rnjwplayer;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public final class ForegroundReconfigureRegressionTest {
    public static void main(String[] args) throws Exception {
        Path productionSource = Paths.get(args[0]);
        Path harnessSource = Paths.get(args[1]);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Run with a JDK, not a JRE");
        }
        Map<String, String> methods = new LinkedHashMap<>();
        for (String name : Arrays.asList("reconfigurePlayer", "reuseForegroundPlayerForConfig",
            "resolveForegroundRebuildStartOverrideSec",
                "isForegroundRebuildSnapshotFresh", "clearForegroundRebuildSnapshot")) {
            methods.put(name, null);
        }
        try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(null, files, null,
                    Collections.singletonList("-proc:none"), null,
                    files.getJavaFileObjects(productionSource.toFile()));
            for (CompilationUnitTree unit : task.parse()) {
                for (Tree declaration : unit.getTypeDecls()) {
                    if (!(declaration instanceof ClassTree)) {
                        continue;
                    }
                    for (Tree member : ((ClassTree) declaration).getMembers()) {
                        if (member instanceof MethodTree) {
                            MethodTree method = (MethodTree) member;
                            String name = method.getName().toString();
                            if (methods.containsKey(name)) {
                                methods.put(name, method.toString());
                            }
                        }
                    }
                }
            }
        }
        for (Map.Entry<String, String> entry : methods.entrySet()) {
            if (entry.getValue() == null) {
                throw new AssertionError("Production method missing: " + entry.getKey());
            }
        }
        String harness = new String(Files.readAllBytes(harnessSource), StandardCharsets.UTF_8);
        String marker = "/* PRODUCTION_METHODS */";
        if (harness.indexOf(marker) < 0 || harness.indexOf(marker) != harness.lastIndexOf(marker)) {
            throw new AssertionError("Harness must contain one production-method insertion point");
        }
        Path output = Files.createTempDirectory("foreground-reconfigure-test-");
        Path generated = output.resolve("ForegroundReconfigureHarness.java");
        Files.write(generated, harness.replace(marker, String.join("\n", methods.values()))
                .getBytes(StandardCharsets.UTF_8));
        int result = compiler.run(null, null, null, "-d", output.toString(), generated.toString());
        if (result != 0) {
            throw new AssertionError("Production-method harness compilation failed: " + result);
        }
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[] {output.toUri().toURL()})) {
            Class<?> test = loader.loadClass("ForegroundReconfigureHarness");
            test.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        }
    }
}