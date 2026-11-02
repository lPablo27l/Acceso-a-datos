package es.aadtex;

import java.io.InputStream;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class MultiLanguageDataGenerator implements CommandLineRunner {
    private final Path root;

    public MultiLanguageDataGenerator(@Value("${aadtex.data-dir}") String folder) {
        root = Path.of(folder);
    }

    @Override
    public void run(String... args) throws Exception {
        Path input = Files.createDirectories(root.resolve("input"));
        Path keys = Files.createDirectories(root.resolve("keys"));
        Path marker = root.resolve(".datos-generados");
        if (Files.exists(marker)) return;

        // Los ejemplos UTF-8 y binarios se incluyen como recursos del proyecto.
        String[] files = {"daily_sales_spain.csv", "daily_sales_china.csv",
            "daily_sales_financial.enc", "signature_spain.jpg", "signature_china.jpg",
            "signature_financial.jpg", "batch_usa.zip", "daily_sales_broken_store.csv"};
        for (String name : files) {
            copyResource(name, input.resolve(name));
        }
        copyResource("AadTexSecret.key", keys.resolve("AadTexSecret.key"));
        Files.createFile(marker);
        System.out.println("[DATOS] Ejemplos preparados en " + input.toAbsolutePath());
    }

    private void copyResource(String name, Path destination) throws Exception {
        if (Files.exists(destination)) return;
        try (InputStream input = new ClassPathResource("datos/" + name).getInputStream()) {
            Files.copy(input, destination);
        }
    }
}
