package com.example.vsm;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * CI gate 2 (spec §4.5, T3 step 5, T9 step 3): runs the VSM compiler twice into separate directories and compares the
 * sha256 of both outputs with each other and with the committed {@code view/generated}. Writes the hashes to the
 * report file and exits 1 on any difference.
 *
 * <p>Usage: {@code DeterminismCheck <repoRoot> <workDir> <reportFile>}.
 */
public final class DeterminismCheck {

    private DeterminismCheck() {
    }

    public static void main(String[] args) throws IOException {
        System.exit(run(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), System.out, System.err));
    }

    static int run(Path repoRoot, Path workDir, Path reportFile, PrintStream out, PrintStream err) throws IOException {
        VsmCompiler.Options committed = VsmCompiler.Options.forRepository(repoRoot);
        String[] hashes = new String[2];
        for (int i = 0; i < 2; i++) {
            Path output = workDir.resolve("run" + (i + 1));
            VsmCompiler.Options options = new VsmCompiler.Options(committed.viewsDir(), committed.schemaFile(), committed.ecoreFile(), output,
                    committed.displayRoot());
            int exitCode = VsmCompiler.run(options, out, err);
            if (exitCode != VsmCompiler.OK) {
                err.println("VSM generation failed (exit " + exitCode + ")");
                return 1;
            }
            hashes[i] = treeHash(output);
        }
        String committedHash = treeHash(committed.outputDir());
        Files.createDirectories(reportFile.getParent());
        Files.writeString(reportFile, "run1      " + hashes[0] + "\nrun2      " + hashes[1] + "\ncommitted " + committedHash + "\n", StandardCharsets.UTF_8);
        if (!hashes[0].equals(hashes[1])) {
            err.println("VSM generation is not deterministic: two runs differ (" + hashes[0] + " vs " + hashes[1] + ")");
            return 1;
        }
        if (!hashes[0].equals(committedHash)) {
            err.println("committed view/generated differs from a fresh generation; run ./gradlew :vsm-compiler:generateViews");
            return 1;
        }
        out.println("VSM generation is deterministic: sha256 " + hashes[0]);
        return 0;
    }

    /** sha256 over every file's relative path (with {@code /}) and bytes, in sorted path order. */
    static String treeHash(Path root) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile).sorted().toList();
        }
        for (Path file : files) {
            digest.update(root.relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Files.readAllBytes(file));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
