package es.aadtex;

import java.math.BigDecimal;
import java.util.List;

public record SalesSummary(String batchId, String sourceFile, int transactions,
                           BigDecimal totalSales, List<String> stores,
                           List<String> categories, List<String> dates, String processedAt) {
    public SalesSummary identifiedBy(String id) {
        return new SalesSummary(id, sourceFile, transactions, totalSales, stores, categories, dates, processedAt);
    }
}
