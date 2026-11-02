package es.aadtex;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CsvSalesReader {
    public static final String HEADER = "StoreID,StoreName,SalesAmount,Category,TransactionDate";
    // Cota explícita por lote para rechazar entradas que agotarían la memoria.
    public static final int MAX_BYTES = 16 * 1024 * 1024;

    public SalesSummary read(InputStream input, String source) throws IOException {
        var decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        try (var reader = new BufferedReader(new InputStreamReader(new BoundedInputStream(input, MAX_BYTES), decoder))) {
            String header = reader.readLine();
            if (header != null && header.startsWith("\uFEFF")) header = header.substring(1);
            if (!HEADER.equals(header)) throw new BatchValidationException("Cabecera CSV incorrecta");
            BigDecimal total = new BigDecimal("0.00");
            Set<String> stores = new LinkedHashSet<>(), categories = new LinkedHashSet<>(), dates = new LinkedHashSet<>();
            int count = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> fields = parseLine(line);
                if (fields.size() != 5 || fields.stream().anyMatch(String::isBlank))
                    throw new BatchValidationException("Fila CSV incompleta: " + (count+2));
                try {
                    BigDecimal amount = new BigDecimal(fields.get(2)).setScale(2, java.math.RoundingMode.UNNECESSARY);
                    if (amount.signum() < 0) throw new IllegalArgumentException("Importe negativo");
                    LocalDate.parse(fields.get(4));
                    total = total.add(amount);
                } catch (RuntimeException e) { throw new BatchValidationException("Importe o fecha incorrectos en fila " + (count+2), e); }
                stores.add(fields.get(1)); categories.add(fields.get(3)); dates.add(fields.get(4)); count++;
            }
            if (count == 0) throw new BatchValidationException("CSV sin transacciones");
            return new SalesSummary(UUID.randomUUID().toString(), source, count, total,
                List.copyOf(stores), List.copyOf(categories), List.copyOf(dates), Instant.now().toString());
        } catch (CharacterCodingException e) {
            throw new BatchValidationException("Contenido UTF-8 inválido", e);
        }
    }
    // Acepta campos entre comillas, comas escapadas y comillas dobles; una transacción por línea.
    static List<String> parseLine(String line) throws IOException {
        List<String> result = new ArrayList<>(); StringBuilder field = new StringBuilder();
        boolean quoted = false, closed = false;
        for (int i=0; i<line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i+1<line.length() && line.charAt(i+1)=='"') { field.append('"'); i++; }
                    else { quoted=false; closed=true; }
                } else field.append(c);
            } else if (c == ',') { result.add(field.toString()); field.setLength(0); closed=false; }
            else if (c == '"' && field.isEmpty() && !closed) quoted=true;
            else {
                if (closed || c=='"') throw new BatchValidationException("Comillas CSV incorrectas");
                field.append(c);
            }
        }
        if (quoted) throw new BatchValidationException("Campo CSV sin cerrar");
        result.add(field.toString()); return result;
    }
    static final class BoundedInputStream extends FilterInputStream {
        private long remaining;
        BoundedInputStream(InputStream in, long limit) { super(in); remaining=limit; }
        private void consumed(int n) throws IOException {
            if (n > 0 && (remaining -= n) < 0) throw new BatchValidationException("Lote superior al límite de 16 MiB");
        }
        @Override public int read() throws IOException { int n=in.read(); consumed(n<0?0:1); return n; }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            int n=in.read(b,off,(int)Math.min(len, remaining+1)); consumed(n); return n;
        }
    }
}
