package es.aadtex;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class IngestionTest {
    @TempDir Path root;
    StoreIngestionService ingestion;

    @BeforeEach
    void prepare() {
        ingestion = new StoreIngestionService(root.toString(), new CsvSalesReader(),
                new CryptoService(), new CloudBucketService(root.toString()));
    }

    private List<Path> files(Path directory) throws IOException {
        try (var stream = Files.list(directory)) { return stream.toList(); }
    }

    private Path bucket() {
        return root.resolve("output/bucket/aadtex-daily-reports/2026/09");
    }

    private void example(String name) throws IOException {
        Files.createDirectories(root.resolve("input"));
        try (InputStream in = getClass().getResourceAsStream("/datos/" + name)) {
            assertNotNull(in);
            Files.copy(in, root.resolve("input").resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test
    void processAllExamplesWithCorrectTotalsAndUtf8() throws Exception {
        new MultiLanguageDataGenerator(root.toString()).run();
        byte[] original = Files.readAllBytes(root.resolve("input/signature_spain.jpg"));
        assertTrue(original.length > 4096);
        ingestion.processPending();

        assertTrue(files(root.resolve("input")).isEmpty());
        assertEquals(4, files(bucket()).size());
        assertEquals(4, files(root.resolve("output/processed/signatures")).size());
        assertEquals(0, Files.size(root.resolve("output/error/daily_sales_broken_store.csv")));
        assertArrayEquals(original, Files.readAllBytes(root.resolve("output/processed/signatures/signature_spain.jpg")));
        assertFalse(Files.exists(root.resolve("output/processed/daily_sales_financial.csv")));
        assertFalse(Files.exists(root.resolve("output/processed/daily_sales_usa.csv")));

        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String,BigDecimal> totals = new HashMap<>();
        StringBuilder text = new StringBuilder();
        for (Path file : files(bucket())) {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            text.append(json);
            var summary = mapper.readTree(json);
            totals.put(summary.get("sourceFile").asText(), summary.get("totalSales").decimalValue());
        }
        assertEquals(0, totals.get("daily_sales_spain.csv").compareTo(new BigDecimal("20100.80")));
        assertEquals(0, totals.get("daily_sales_china.csv").compareTo(new BigDecimal("41300.80")));
        assertEquals(0, totals.get("daily_sales_financial.enc").compareTo(new BigDecimal("95000.00")));
        assertEquals(0, totals.get("daily_sales_usa.csv").compareTo(new BigDecimal("68450.75")));
        assertTrue(text.toString().contains("Gran Vía"));
        assertTrue(text.toString().contains("上海南京东路店"));
    }

    @Test
    void isolateEmptyAndCorruptFiles() throws Exception {
        example("signature_spain.jpg");
        example("signature_financial.jpg");
        Files.writeString(root.resolve("input/daily_sales_spain.csv"), "incorrect header");
        Files.write(root.resolve("input/daily_sales_financial.enc"), new byte[]{1,2,3});
        Files.write(root.resolve("input/broken.zip"), new byte[]{0x50,0x4b,0x03,0x04});
        Files.write(root.resolve("input/empty.csv"), new byte[0]);
        Files.write(root.resolve("input/signature_broken.jpg"), new byte[]{1,2,3});
        ingestion.processPending();
        assertEquals(5, files(root.resolve("output/error")).size());
        assertFalse(Files.exists(root.resolve("output/processed/signatures/signature_broken.jpg")));
        assertFalse(Files.exists(bucket()));
    }

    @Test
    void repeatedCyclesAndRestartDoNotRegenerateTheExamples() throws Exception {
        new MultiLanguageDataGenerator(root.toString()).run();
        ingestion.processPending();
        Set<Path> first = new HashSet<>(files(bucket()));
        ingestion.processPending();
        new MultiLanguageDataGenerator(root.toString()).run();
        prepare();
        ingestion.processPending();
        assertEquals(first, new HashSet<>(files(bucket())));
        assertTrue(files(root.resolve("input")).isEmpty());
    }

    @Test
    void waitForTheSalesSignature() throws Exception {
        example("daily_sales_spain.csv");
        ingestion.processPending();
        assertTrue(Files.exists(root.resolve("input/daily_sales_spain.csv")));
        assertTrue(files(root.resolve("output/error")).isEmpty());
        example("signature_spain.jpg");
        ingestion.processPending();
        assertEquals(1, files(bucket()).size());
        assertTrue(files(root.resolve("input")).isEmpty());
    }
}
