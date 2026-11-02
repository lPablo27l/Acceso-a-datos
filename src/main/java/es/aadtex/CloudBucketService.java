package es.aadtex;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CloudBucketService {
    private final Path bucket;
    private final ObjectMapper mapper = new ObjectMapper();

    public CloudBucketService(@Value("${aadtex.data-dir}") String root) {
        bucket = Path.of(root, "output", "bucket", "aadtex-daily-reports", "2026", "09");
    }

    public void publish(SalesSummary summary) throws IOException {
        Files.createDirectories(bucket);
        Path destination = bucket.resolve("summary_batch_" + summary.id() + ".json");
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary);
        Files.writeString(destination, json, StandardCharsets.UTF_8);
        System.out.println("[JSON] " + destination.getFileName());
    }
}
