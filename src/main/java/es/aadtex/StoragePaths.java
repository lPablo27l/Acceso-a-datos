package es.aadtex;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StoragePaths {
    private final Path root;
    public StoragePaths(@Value("${aadtex.data-dir:./store_data}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }
    public Path root() { return root; }
    public Path input() { return root.resolve("input"); }
    public Path processed() { return root.resolve("output/processed"); }
    public Path signatures() { return processed().resolve("signatures"); }
    public Path error() { return root.resolve("output/error"); }
    public Path bucket() { return root.resolve("output/bucket/aadtex-daily-reports/2026/09"); }
    public Path staging() { return root.resolve("output/.staging"); }
    public void initialize() throws IOException {
        for (Path p : new Path[]{input(), processed(), signatures(), error(), bucket(), staging(), root.resolve("keys")}) {
            if (!Files.exists(p)) Files.createDirectories(p);
        }
    }
    public Path temporary(String suffix) throws IOException {
        return Files.createTempFile(staging(), "batch-", suffix);
    }
    // Los temporales y el destino están en el mismo sistema de ficheros.
    public static void move(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
    public static Path uniqueDestination(Path directory, String name) {
        Path candidate = directory.resolve(name);
        if (Files.exists(candidate)) candidate = directory.resolve(UUID.randomUUID()+"_"+name);
        return candidate;
    }
}
