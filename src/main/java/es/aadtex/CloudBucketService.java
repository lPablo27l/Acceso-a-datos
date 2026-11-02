package es.aadtex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.springframework.stereotype.Service;

@Service
public class CloudBucketService {
    private final StoragePaths paths;
    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    public CloudBucketService(StoragePaths paths) { this.paths=paths; }
    public Path publish(SalesSummary summary) throws IOException {
        Path target=paths.bucket().resolve("summary_batch_"+summary.batchId()+".json");
        Path temp=paths.temporary(".json.tmp");
        try {
            Files.writeString(temp,json.writeValueAsString(summary),StandardCharsets.UTF_8);
            StoragePaths.move(temp,target); return target;
        } finally { Files.deleteIfExists(temp); }
    }
}
