package io.miragon.common.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * JavaParser half of the combined suite: the <em>source-structure</em> rules that ArchUnit cannot see,
 * because the compiler erases them from the bytecode.
 *
 * <p>The source files are read from every module of the project (main and test), found by walking up from
 * the working directory to the project root (the nearest {@code .mvn}, else the outermost {@code pom.xml}); build output, {@code node_modules} and hidden
 * directories are skipped. The generated {@code adapter.process} sources are
 * excluded — they are machine-written from the BPMN models by the bpmn-to-code plugin and are not
 * hand-maintained code.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class JavaSourceGuidelinesTest {

    private static final String GENERATED_PACKAGE = ".adapter.process";

    /** Build output and tooling directories that never hold hand-written sources (hidden ones are skipped too). */
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of("target", "node_modules");

    private final String pathFromRoot;

    private final Map<Path, CompilationUnit> sources;

    protected JavaSourceGuidelinesTest(String pathFromRoot) {
        this.pathFromRoot = pathFromRoot;
        this.sources = parseProjectSources();
    }

    @Test
    @DisplayName("no wildcard imports")
    void noWildcardImports() {
        List<String> violations =
            handWrittenSources()
                .flatMap(entry -> entry.getValue().getImports().stream()
                    .filter(ImportDeclaration::isAsterisk)
                    .filter(it -> !it.getNameAsString().startsWith("java.util"))
                    .map(it -> entry.getKey() + ": import " + it.getNameAsString() + ".*"))
                .toList();

        assertTrue(violations.isEmpty(), () -> "Wildcard imports found:\n" + String.join("\n", violations));
    }

    /**
     * javac only insists on one <em>public</em> type per file, so further package-private top-level types
     * compile without complaint. In bytecode each of them is a class file of its own and only an optional
     * {@code SourceFile} attribute still links it to its file; JavaParser reads the source and counts the
     * declarations directly. Together with the wildcard-import rule — imports do not survive compilation at
     * all — this is why the suite reads source next to bytecode.
     */
    @Test
    @DisplayName("files define at most one top-level class, interface, enum or record")
    void filesDefineAtMostOneTopLevelType() {
        List<String> violations =
            handWrittenSources()
                .filter(entry -> packageOf(entry.getValue()).startsWith(pathFromRoot))
                .filter(entry -> entry.getValue().getTypes().size() > 1)
                .map(entry -> entry.getKey() + " declares " + entry.getValue().getTypes().size() + " top-level types")
                .toList();

        assertTrue(violations.isEmpty(), () -> "Files with more than one top-level type:\n" + String.join("\n", violations));
    }

    private Stream<Map.Entry<Path, CompilationUnit>> handWrittenSources() {
        return sources.entrySet().stream()
            .filter(entry -> !packageOf(entry.getValue()).contains(GENERATED_PACKAGE));
    }

    private static String packageOf(CompilationUnit unit) {
        return unit.getPackageDeclaration().map(PackageDeclaration::getNameAsString).orElse("");
    }

    private static Map<Path, CompilationUnit> parseProjectSources() {
        JavaParser parser = new JavaParser(new ParserConfiguration().setLanguageLevel(LanguageLevel.JAVA_21));
        List<Path> files = new ArrayList<>();
        try {
            Files.walkFileTree(projectRoot(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                    String name = dir.getFileName().toString();
                    boolean skipped = SKIPPED_DIRECTORIES.contains(name) || name.startsWith(".");
                    return skipped ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (isSourceFile(file)) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return files.stream().collect(Collectors.toMap(Function.identity(), path -> parse(parser, path)));
    }

    private static boolean isSourceFile(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return normalized.endsWith(".java")
            && (normalized.contains("/src/main/java/") || normalized.contains("/src/test/java/"));
    }

    private static CompilationUnit parse(JavaParser parser, Path path) {
        try {
            ParseResult<CompilationUnit> result = parser.parse(path);
            return result.getResult()
                .filter(unit -> result.isSuccessful())
                .orElseThrow(() -> new IllegalStateException("Cannot parse " + path + ": " + result.getProblems()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The project root: the nearest directory above the module's working directory that holds {@code .mvn} (the
     * marker Maven itself uses for the multi-module root), falling back to the outermost {@code pom.xml}.
     */
    private static Path projectRoot() {
        Path start = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path outermostPom = null;
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve(".mvn"))) {
                return dir;
            }
            if (Files.exists(dir.resolve("pom.xml"))) {
                outermostPom = dir;
            }
        }
        if (outermostPom == null) {
            throw new IllegalStateException("could not locate the project root (no .mvn or pom.xml found above " + start + ")");
        }
        return outermostPom;
    }
}
