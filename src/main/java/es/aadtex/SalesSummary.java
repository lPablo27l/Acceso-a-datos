package es.aadtex;

import java.math.BigDecimal;
import java.util.List;

public record SalesSummary(String batchId, String sourceFile, int transactions,
                           BigDecimal totalSales, List<String> stores,
                           List<String> categories, List<String> dates, String processedAt) { }
