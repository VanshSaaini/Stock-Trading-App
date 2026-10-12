package com.trading.portfolioservice.service;

import com.trading.portfolioservice.client.MarketDataClient;
import com.trading.portfolioservice.entity.Holding;
import com.trading.portfolioservice.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioService {

    private final HoldingRepository holdingRepository;
    private final MarketDataClient marketDataClient;

    /**
     *  Get Portfolio with current P & L Calculation
     * */
    public Map<String, Object> getPortfolio(String userId) {

        List<Holding> holdings = holdingRepository.findByUserId(userId);

        List<Map<String, Object>> portfolioItems = new ArrayList<>();

        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal currentValue = BigDecimal.ZERO;
        boolean partial = false;

        for(Holding holding : holdings){

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("symbol", holding.getSymbol());
            item.put("quantity", holding.getQuantity());
            item.put("averageBuyPrice", holding.getAverageBuyPrice());
            item.put("invested", holding.getTotalInvested());

            try{
                // Get current market price
                Map<String, Object> priceData = marketDataClient.getStockPrice(holding.getSymbol());
                BigDecimal currentPrice = new BigDecimal(
                        priceData.get("price").toString()
                );

                BigDecimal holdingValue = currentPrice.multiply(
                        BigDecimal.valueOf(holding.getQuantity())
                );

                BigDecimal invested = holding.getTotalInvested();

                BigDecimal pnl = holdingValue.subtract(invested);

                BigDecimal pnlPercent = invested.compareTo(BigDecimal.ZERO) > 0
                        ? pnl.divide(invested, 4, RoundingMode.HALF_UP)
                              .multiply(BigDecimal.valueOf(100))
                              .setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                item.put("currentPrice", currentPrice);
                item.put("currentValue", holdingValue);
                item.put("pnl", pnl);
                item.put("pnlPercent", pnlPercent);
                item.put("isProfit", pnl.compareTo(BigDecimal.ZERO) > 0);
                item.put("priceAvailable", true);

                totalInvested = totalInvested.add(invested);
                currentValue = currentValue.add(holdingValue);

            } catch(Exception e){

                // keep the holding in the response, but leave it out of the totals
                // (otherwise P&L would be wrong) and tell the client the price is missing
                log.error("Error fetching price for {}: {}",
                        holding.getSymbol(), e.getMessage());

                item.put("priceAvailable", false);
                partial = true;

            }

            portfolioItems.add(item);

        }

        BigDecimal totalPnl = currentValue.subtract(totalInvested);
        BigDecimal totalPnlPercent = totalInvested
                .compareTo(BigDecimal.ZERO) > 0
                ? totalPnl.divide(totalInvested,4,RoundingMode.HALF_UP)
                  .multiply(BigDecimal.valueOf(100))
                  .setScale(2,RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Object> portfolio = new LinkedHashMap<>();
        portfolio.put("userId", userId);
        portfolio.put("holdings", portfolioItems);
        portfolio.put("totalInvested", totalInvested);
        portfolio.put("currentValue", currentValue);
        portfolio.put("totalPnl", totalPnl);
        portfolio.put("totalPnlPercent", totalPnlPercent);
        portfolio.put("isProfit", totalPnl.compareTo(BigDecimal.ZERO) > 0);
        // true when at least one price could not be fetched - totals then cover only priced holdings
        portfolio.put("partial", partial);

        return portfolio;

    }

}
