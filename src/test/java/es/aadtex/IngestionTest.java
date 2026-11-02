package es.aadtex;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class IngestionTest {
    @TempDir Path root;
    StoragePaths paths;
    CryptoService crypto;
    CsvSalesReader csv;
    StoreIngestionService engine;
    @BeforeEach void setup() throws Exception {
        paths=new StoragePaths(root.toString()); paths.initialize();
        crypto=new CryptoService(); csv=new CsvSalesReader();
        engine=new StoreIngestionService(paths,crypto,csv,new SignatureService(),new CloudBucketService(paths));
    }
    List<Path> list(Path dir) throws IOException { try (var s=Files.list(dir)) { return s.toList(); } }
    void seed() throws Exception { new MultiLanguageDataGenerator(paths,crypto,true).run(); }
    void put(String name, byte[] bytes) throws IOException { Files.write(paths.input().resolve(name),bytes); }
    @Test void demoProducesFourSummariesAndQuarantinesEmptyFile() throws Exception {
        seed(); byte[] original=Files.readAllBytes(paths.input().resolve("signature_spain.jpg"));
        engine.processPending();
        assertTrue(list(paths.input()).isEmpty());
        assertEquals(4,list(paths.bucket()).size());
        assertEquals(4,list(paths.signatures()).size());
        assertEquals(0,Files.size(paths.error().resolve("daily_sales_broken_store.csv")));
        assertArrayEquals(original,Files.readAllBytes(paths.signatures().resolve("signature_spain.jpg")));
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        BigDecimal total=BigDecimal.ZERO; Set<String> actual=new HashSet<>();
        for (Path file:list(paths.bucket())) {
            var json=mapper.readTree(Files.readString(file));
            total=total.add(json.get("totalSales").decimalValue()); actual.add(json.get("totalSales").asText());
        }
        assertEquals(0,total.compareTo(new BigDecimal("224852.35")));
        assertEquals(Set.of("20100.8","41300.8","95000.0","68450.75"),actual);
        assertTrue(list(paths.bucket()).stream().anyMatch(f->{try { return Files.readString(f).contains("上海南京东路店"); } catch(IOException e) { throw new UncheckedIOException(e); }}));
        assertFalse(Files.exists(paths.processed().resolve("daily_sales_financial.csv")));
        assertTrue(list(paths.staging()).isEmpty());
    }
    @Test void secondCycleAndRestartDoNotDuplicateBatches() throws Exception {
        seed(); engine.processPending(); Set<Path> first=new HashSet<>(list(paths.bucket()));
        engine.processPending(); new MultiLanguageDataGenerator(paths,crypto,true).run(); engine.processPending();
        assertEquals(first,new HashSet<>(list(paths.bucket()))); assertTrue(list(paths.input()).isEmpty());
    }
    @Test void cryptoRoundTripAndCorruptCiphertext() throws Exception {
        byte[] clear=MultiLanguageDataGenerator.financial().getBytes(StandardCharsets.UTF_8);
        byte[] encrypted=crypto.encrypt(clear);
        assertFalse(Arrays.equals(clear,encrypted)); assertArrayEquals(clear,crypto.decrypt(encrypted));
        assertThrows(java.security.GeneralSecurityException.class,()->crypto.decrypt(new byte[]{1,2,3}));
    }
    @Test void csvPreservesSpanishAndCjkAndUsesExactDecimalArithmetic() throws Exception {
        var es=csv.read(new ByteArrayInputStream(MultiLanguageDataGenerator.spain().getBytes(StandardCharsets.UTF_8)),"es");
        assertTrue(es.stores().contains("Zara Gran Vía Madrid")); assertEquals(new BigDecimal("20100.80"),es.totalSales());
        var cn=csv.read(new ByteArrayInputStream(MultiLanguageDataGenerator.china().getBytes(StandardCharsets.UTF_8)),"cn");
        assertEquals(List.of("Zara 上海南京东路店"),cn.stores());
    }
    @Test void quotedCsvFieldsAreAccepted() throws Exception {
        var fields=CsvSalesReader.parseLine("ES,\"Zara, \"\"Centro\"\"\",10.20,MEN,2026-09-13");
        assertEquals("Zara, \"Centro\"",fields.get(1));
    }
    @Test void malformedUtf8AndBadAmountsAreRejected() throws Exception {
        assertThrows(IOException.class,()->csv.read(new ByteArrayInputStream(new byte[]{(byte)0xc3,0x28}),"bad"));
        for (String amount:List.of("abc","-1.00","0.001")) {
            String text=CsvSalesReader.HEADER+"\nES,Tienda,"+amount+",MEN,2026-09-13\n";
            assertThrows(IOException.class,()->csv.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),"bad"));
        }
    }
    @Test void corruptSalesAndEncryptedFileAreIsolatedWithoutSummary() throws Exception {
        put("signature_broken.jpg",MultiLanguageDataGenerator.jpeg("broken"));
        put("signature_financial.jpg",MultiLanguageDataGenerator.jpeg("financial"));
        put("daily_sales_broken.csv","wrong header".getBytes(StandardCharsets.UTF_8));
        put("daily_sales_financial.enc",new byte[]{1,2,3}); engine.processPending();
        assertEquals(2,list(paths.error()).size()); assertTrue(list(paths.bucket()).isEmpty());
    }
    @Test void missingSignatureWaitsUntilItArrives() throws Exception {
        put("daily_sales_spain.csv",MultiLanguageDataGenerator.spain().getBytes(StandardCharsets.UTF_8)); engine.processPending();
        assertTrue(Files.exists(paths.input().resolve("daily_sales_spain.csv"))); assertTrue(list(paths.bucket()).isEmpty());
        put("signature_spain.jpg",MultiLanguageDataGenerator.jpeg("spain")); engine.processPending();
        assertEquals(1,list(paths.bucket()).size());
    }
    void zip(String name,Map<String,byte[]> entries) throws Exception {
        try(var out=new ZipOutputStream(Files.newOutputStream(paths.input().resolve(name)))) {
            for(var e:entries.entrySet()) { out.putNextEntry(new ZipEntry(e.getKey())); out.write(e.getValue());out.closeEntry(); }
        }
    }
    @Test void zipTraversalAndMissingPartnerAreRejected() throws Exception {
        zip("traversal.zip",Map.of("../escape.csv",MultiLanguageDataGenerator.usa().getBytes(StandardCharsets.UTF_8)));
        zip("missing.zip",Map.of("daily_sales_usa.csv",MultiLanguageDataGenerator.usa().getBytes(StandardCharsets.UTF_8)));
        engine.processPending(); assertEquals(2,list(paths.error()).size());
        assertTrue(list(paths.bucket()).isEmpty()); assertTrue(list(paths.signatures()).isEmpty()); assertFalse(Files.exists(root.resolve("escape.csv")));
    }
    @Test void zipSignatureFirstWorksAndTruncatedArchiveIsRejected() throws Exception {
        var entries=new LinkedHashMap<String,byte[]>();
        entries.put("signature_usa.jpg",MultiLanguageDataGenerator.jpeg("usa"));
        entries.put("daily_sales_usa.csv",MultiLanguageDataGenerator.usa().getBytes(StandardCharsets.UTF_8));
        zip("good.zip",entries); put("truncated.zip",new byte[]{0x50,0x4b,0x03,0x04});
        engine.processPending(); assertEquals(1,list(paths.bucket()).size()); assertEquals(1,list(paths.error()).size());
    }
    @Test void badJpegIsQuarantined() throws Exception {
        put("signature_fake.jpg",new byte[]{1,2,3,4}); engine.processPending();
        assertEquals(1,list(paths.error()).size()); assertTrue(list(paths.signatures()).isEmpty()); assertTrue(list(paths.staging()).isEmpty());
    }
    @Test void signatureLargerThanBufferIsCopiedExactly() throws Exception {
        byte[] original=MultiLanguageDataGenerator.jpeg("buffer"); assertTrue(original.length>4096);
        Path target=root.resolve("copied.jpg"); new SignatureService().copy(new ByteArrayInputStream(original),target);
        assertArrayEquals(original,Files.readAllBytes(target));
    }

    StoreIngestionService withBucket(CloudBucketService custom) {
        return new StoreIngestionService(paths, crypto, csv, new SignatureService(), custom);
    }
    void spainBatch() throws Exception {
        put("signature_spain.jpg", MultiLanguageDataGenerator.jpeg("spain"));
        put("daily_sales_spain.csv", MultiLanguageDataGenerator.spain().getBytes(StandardCharsets.UTF_8));
    }
    @Test void identicalRedeliveryDoesNotCreateAnotherSummary() throws Exception {
        spainBatch(); engine.processPending();
        Path report = list(paths.bucket()).getFirst(); byte[] original = Files.readAllBytes(report);
        spainBatch(); engine.processPending();
        assertEquals(List.of(report), list(paths.bucket()));
        assertArrayEquals(original, Files.readAllBytes(report));
        assertTrue(list(paths.input()).isEmpty());
    }
    @Test void changedContentsWithSameNameCreateANewBatch() throws Exception {
        spainBatch(); engine.processPending();
        put("daily_sales_spain.csv", MultiLanguageDataGenerator.spain().replace("2000.00", "2001.00").getBytes(StandardCharsets.UTF_8));
        engine.processPending(); assertEquals(2, list(paths.bucket()).size());
    }
    @Test void restartAfterPublishingBeforeArchivingKeepsTheOriginalJson() throws Exception {
        spainBatch();
        var interrupted = new CloudBucketService(paths) {
            @Override public Path publish(SalesSummary summary) throws IOException {
                super.publish(summary);
                throw new IOException("Interrupción simulada tras publicar el JSON");
            }
        };
        withBucket(interrupted).processPending();
        assertTrue(Files.exists(paths.input().resolve("daily_sales_spain.csv")));
        assertTrue(list(paths.error()).isEmpty());
        Path report = list(paths.bucket()).getFirst(); byte[] original = Files.readAllBytes(report);
        // Una instancia nueva representa el reinicio, sin memoria del ciclo anterior.
        withBucket(new CloudBucketService(paths)).processPending();
        assertEquals(1, list(paths.bucket()).size()); assertArrayEquals(original, Files.readAllBytes(report));
        assertTrue(list(paths.input()).isEmpty());
    }
    @Test void zipRecoversAfterSignatureWasWrittenButPublicationFailed() throws Exception {
        var entries = new LinkedHashMap<String,byte[]>();
        entries.put("daily_sales_usa.csv", MultiLanguageDataGenerator.usa().getBytes(StandardCharsets.UTF_8));
        entries.put("signature_usa.jpg", MultiLanguageDataGenerator.jpeg("usa"));
        zip("batch_usa.zip", entries);
        var unavailable = new CloudBucketService(paths) {
            @Override public Path publish(SalesSummary summary) throws IOException {
                throw new IOException("Disco no disponible temporalmente");
            }
        };
        withBucket(unavailable).processPending();
        assertEquals(1, list(paths.signatures()).size()); assertTrue(list(paths.bucket()).isEmpty());
        assertTrue(Files.exists(paths.input().resolve("batch_usa.zip"))); assertTrue(list(paths.error()).isEmpty());
        withBucket(new CloudBucketService(paths)).processPending();
        assertEquals(1, list(paths.bucket()).size()); assertTrue(list(paths.input()).isEmpty());
    }
    @Test void transientPublicationFailureDoesNotQuarantineValidSales() throws Exception {
        spainBatch();
        var unavailable = new CloudBucketService(paths) {
            @Override public Path publish(SalesSummary summary) throws IOException { throw new IOException("Espacio no disponible"); }
        };
        withBucket(unavailable).processPending();
        assertTrue(Files.exists(paths.input().resolve("daily_sales_spain.csv")));
        assertTrue(list(paths.error()).isEmpty()); assertTrue(list(paths.bucket()).isEmpty());
        engine.processPending(); assertEquals(1, list(paths.bucket()).size());
    }
    @Test void invalidUtf8IsQuarantinedByTheEngine() throws Exception {
        put("signature_spain.jpg", MultiLanguageDataGenerator.jpeg("spain"));
        put("daily_sales_spain.csv", new byte[]{(byte)0xc3, 0x28});
        engine.processPending(); assertEquals(1, list(paths.error()).size());
        assertTrue(list(paths.bucket()).isEmpty());
    }
    @Test void inputModifiedDuringProcessingIsRetriedBeforePublishing() throws Exception {
        spainBatch();
        var changingReader = new CsvSalesReader() {
            @Override public SalesSummary read(InputStream input, String source) throws IOException {
                SalesSummary result = super.read(input, source);
                Files.writeString(paths.input().resolve("daily_sales_spain.csv"), MultiLanguageDataGenerator.spain().replace("2000.00", "2002.00"));
                return result;
            }
        };
        new StoreIngestionService(paths,crypto,changingReader,new SignatureService(),new CloudBucketService(paths)).processPending();
        assertTrue(list(paths.bucket()).isEmpty()); assertTrue(list(paths.error()).isEmpty());
        assertTrue(Files.exists(paths.input().resolve("daily_sales_spain.csv")));
        engine.processPending(); assertEquals(1, list(paths.bucket()).size());
        assertTrue(Files.readString(list(paths.bucket()).getFirst()).contains("20102.80"));
    }
    @Test void oversizedSignatureIsRejectedWithoutLoadingItIntoMemory() throws Exception {
        // Flujo sintético: genera más de 32 MiB sin reservar un array de ese tamaño.
        InputStream large = new InputStream() {
            private long position;
            @Override public int read() {
                long current = position++;
                if (current >= SignatureService.MAX_BYTES + 1) return -1;
                return current == 0 || current == 2 ? 255 : current == 1 ? 216 : 0;
            }
            @Override public int read(byte[] b, int off, int length) {
                int i = 0;
                for (; i < length; i++) { int value = read(); if (value < 0) break; b[off+i]=(byte)value; }
                return i == 0 ? -1 : i;
            }
        };
        assertThrows(BatchValidationException.class, () -> new SignatureService().copy(large, root.resolve("oversize.jpg")));
    }

    @Test void newZipWithChangedSalesAndSignatureCanReuseItsOriginalFilename() throws Exception {
        zip("batch_usa.zip", Map.of(
            "daily_sales_usa.csv", MultiLanguageDataGenerator.usa().getBytes(StandardCharsets.UTF_8),
            "signature_usa.jpg", MultiLanguageDataGenerator.jpeg("usa")));
        engine.processPending();
        byte[] newSignature = MultiLanguageDataGenerator.jpeg("usa siguiente cierre");
        zip("batch_usa.zip", Map.of(
            "daily_sales_usa.csv", MultiLanguageDataGenerator.usa().replace("7600.00", "7700.00").getBytes(StandardCharsets.UTF_8),
            "signature_usa.jpg", newSignature));
        engine.processPending();
        assertEquals(2, list(paths.bucket()).size()); assertTrue(list(paths.error()).isEmpty());
        assertArrayEquals(newSignature, Files.readAllBytes(paths.signatures().resolve("signature_usa.jpg")));
    }
}
