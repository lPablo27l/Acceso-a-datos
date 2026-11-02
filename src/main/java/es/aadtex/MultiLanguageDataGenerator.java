package es.aadtex;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class MultiLanguageDataGenerator implements CommandLineRunner {
    private static final Logger log=LoggerFactory.getLogger(MultiLanguageDataGenerator.class);
    private final StoragePaths paths; private final CryptoService crypto; private final boolean enabled;
    public MultiLanguageDataGenerator(StoragePaths paths,CryptoService crypto,
        @Value("${aadtex.generate-demo:true}") boolean enabled) {
        this.paths=paths; this.crypto=crypto; this.enabled=enabled;
    }
    private static String table(String... rows) { return CsvSalesReader.HEADER+"\n"+String.join("\n",rows)+"\n"; }
    public static String spain() { return table(
        "ZARA_ES_001,Zara Puerta del Sol Madrid,14500.50,WOMEN,2026-09-13",
        "ZARA_ES_001,Zara Puerta del Sol Madrid,3600.30,MEN,2026-09-13",
        "ZARA_ES_001,Zara Gran Vía Madrid,2000.00,KIDS,2026-09-13"); }
    public static String china() { return table(
        "ZARA_CH_001,Zara 上海南京东路店,28900.00,WOMEN,2026-09-13",
        "ZARA_CH_001,Zara 上海南京东路店,12400.80,MEN,2026-09-13"); }
    public static String financial() { return table("ZARA_CENTRAL_FIN,Central Financial Office,95000.00,FINANCIAL_REPORT,2026-09-13"); }
    public static String usa() { return table(
        "ZARA_US_001,Zara Times Square New York,42000.00,WOMEN,2026-09-13",
        "ZARA_US_001,Zara Times Square New York,18850.75,MEN,2026-09-13",
        "ZARA_US_001,Zara 5th Avenue New York,7600.00,KIDS,2026-09-13"); }
    @Override public void run(String... args) throws Exception {
        paths.initialize();
        Path marker=paths.root().resolve(".demo-generated");
        if (!enabled || Files.exists(marker)) return;
        Files.writeString(paths.root().resolve("keys/AadTexSecret.key"),CryptoService.SECRET,StandardCharsets.UTF_8);
        write("daily_sales_spain.csv",spain().getBytes(StandardCharsets.UTF_8));
        write("daily_sales_china.csv",china().getBytes(StandardCharsets.UTF_8));
        write("daily_sales_financial.enc",crypto.encrypt(financial().getBytes(StandardCharsets.UTF_8)));
        for (String region:new String[]{"spain","china","financial"}) write("signature_"+region+".jpg",jpeg(region));
        if (!Files.exists(paths.input().resolve("batch_usa.zip"))) {
            try (var out=new ZipOutputStream(Files.newOutputStream(paths.input().resolve("batch_usa.zip")))) {
                out.putNextEntry(new ZipEntry("daily_sales_usa.csv")); out.write(usa().getBytes(StandardCharsets.UTF_8)); out.closeEntry();
                out.putNextEntry(new ZipEntry("signature_usa.jpg")); out.write(jpeg("usa")); out.closeEntry();
            }
        }
        write("daily_sales_broken_store.csv",new byte[0]);
        Files.writeString(marker,"Datos de demostración generados una sola vez.\n",StandardCharsets.UTF_8);
        log.info("[DEMO] Datos UTF-8, AES-128, ZIP, firmas JPEG y fichero vacío preparados en {}",paths.input());
    }
    private void write(String name,byte[] bytes) throws IOException {
        Path path=paths.input().resolve(name);
        if (!Files.exists(path)) Files.write(path,bytes,StandardOpenOption.CREATE_NEW);
    }
    // Firmas sintéticas de prueba: JPEG reales, no imágenes de una persona.
    public static byte[] jpeg(String region) throws IOException {
        var image=new BufferedImage(640,180,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();
        try {
            g.setColor(Color.WHITE);g.fillRect(0,0,640,180);
            g.setColor(new Color(20,45,90));g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,20));
            g.drawString("AADTEX / COMPROBANTE DE PRUEBA / "+region.toUpperCase(),25,35);
            g.setStroke(new BasicStroke(3));
            g.drawPolyline(new int[]{35,80,100,130,115,170,210,235,290,350,430,490,590},
                new int[]{125,65,138,78,116,90,125,75,120,95,110,85,105},13);
            g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,13));g.drawString("Firma ficticia generada para la actividad RA1. 13/09/2026",25,165);
        } finally { g.dispose(); }
        try (var out=new ByteArrayOutputStream()) {
            if (!ImageIO.write(image,"jpg",out)) throw new IOException("Codificador JPEG no disponible");
            return out.toByteArray();
        }
    }
}
