package es.aadtex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class CloudBucketService {
    private final StoragePaths paths;
    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    public CloudBucketService(StoragePaths paths) { this.paths = paths; }
    private Path reportPath(String batchId) {
        return paths.bucket().resolve("summary_batch_" + batchId + ".json");
    }
    public Optional<SalesSummary> published(String batchId) throws IOException {
        Path report = reportPath(batchId);
        if (!Files.exists(report)) return Optional.empty();
        SalesSummary result = json.readValue(Files.readString(report, StandardCharsets.UTF_8), SalesSummary.class);
        if (!batchId.equals(result.batchId())) throw new IOException("Resumen publicado incoherente: " + report);
        return Optional.of(result);
    }
    public Path publish(SalesSummary summary) throws IOException {
        Path target = reportPath(summary.batchId());
        // No sustituir un resumen confirmado: conserva fecha y contenido al reintentar.
        if (published(summary.batchId()).isPresent()) return target;
        Path temp = paths.temporary(".json.tmp");
        try {
            Files.writeString(temp, json.writeValueAsString(summary), StandardCharsets.UTF_8);
            StoragePaths.move(temp, target);
            return target;
        } finally { Files.deleteIfExists(temp); }
    }
}
