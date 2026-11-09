// Checks SysML v2 text with the OMG SysML v2 pilot implementation (P1 T8). Not part of the build; the 120 MB
// jupyter-sysml-kernel release is downloaded by hand:
//   https://github.com/Systems-Modeling/SysML-v2-Pilot-Implementation/releases (jupyter-sysml-kernel-<v>.zip)
// The pilot's loadLibrary fails on paths with spaces (it reads "Kernel%20Libraries"...), so copy sysml.library with
// every space in a directory name replaced by "_" (e.g. to lib/), then:
//   javac -cp jupyter-sysml-kernel-<v>-all.jar -d . Check.java
//   java -cp "jupyter-sysml-kernel-<v>-all.jar;." Check lib backend/build/sysml-export/*.sysml
// (the files are written by SysmlV2ExportTest). Exit code 1 if any file has a syntax or semantic error.
import java.nio.file.Files;
import java.nio.file.Path;

import org.omg.sysml.interactive.SysMLInteractive;
import org.omg.sysml.interactive.SysMLInteractiveResult;

/** Validates SysML v2 text files with the OMG pilot implementation: syntax and semantic errors, then warnings. */
public class Check {

    public static void main(String[] args) throws Exception {
        SysMLInteractive sysml = SysMLInteractive.getInstance();
        // loadLibrary appends "Kernel Libraries"... and then fails on its own %20 in paths: read the space-free copy ourselves.
        String root = args[0].endsWith("/") ? args[0] : args[0] + "/";
        org.omg.sysml.util.SysMLLibraryUtil.setModelLibraryDirectory(root);
        sysml.readAll(root + "Kernel_Libraries", false, ".kerml");
        sysml.readAll(root + "Systems_Library", false, ".sysml");
        sysml.readAll(root + "Domain_Libraries", false, ".sysml");
        int failed = 0;
        for (int i = 1; i < args.length; i++) {
            String text = Files.readString(Path.of(args[i]));
            SysMLInteractiveResult result = sysml.process(text);
            System.out.println("== " + args[i] + ": " + (result.hasErrors() ? "ERRORS" : "OK") + ", warnings " + result.getWarnings().size());
            if (result.getException() != null) {
                System.out.println(result.formatException());
            }
            result.getSyntaxErrors().forEach(e -> System.out.println("  syntax  line " + e.getLineNumber() + ": " + e.getMessage()));
            result.getSemanticErrors().forEach(e -> System.out.println("  semantic line " + e.getLineNumber() + ": " + e.getMessage()));
            result.getWarnings().forEach(e -> System.out.println("  warning line " + e.getLineNumber() + ": " + e.getMessage()));
            if (result.hasErrors()) {
                failed++;
            }
        }
        System.exit(failed == 0 ? 0 : 1);
    }
}
