package es.aadtex;

import java.math.BigDecimal;
import java.util.List;

public record SalesSummary(String id, String sourceFile, int transactions,
                           BigDecimal totalSales, List<String> stores) { }
