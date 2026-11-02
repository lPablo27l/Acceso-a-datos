package es.aadtex;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CsvSalesReader {
    public SalesSummary read(InputStream input, String source) throws IOException {
        BigDecimal total = BigDecimal.ZERO;
        Set<String> stores = new LinkedHashSet<>();
        int transactions = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (!"StoreID,StoreName,SalesAmount,Category,TransactionDate".equals(header)) {
                throw new IOException("Cabecera CSV incorrecta");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] fields = line.split(",", -1);
                if (fields.length != 5 || Arrays.stream(fields).anyMatch(String::isBlank)) {
                    throw new IOException("Fila CSV incompleta");
                }
                try {
                    total = total.add(new BigDecimal(fields[2]));
                } catch (NumberFormatException e) {
                    throw new IOException("Importe de venta incorrecto", e);
                }
                stores.add(fields[1]);
                transactions++;
            }
        }
        if (transactions == 0) throw new IOException("CSV sin ventas");
        return new SalesSummary(UUID.randomUUID().toString(), source, transactions,
                total, List.copyOf(stores));
    }
}
