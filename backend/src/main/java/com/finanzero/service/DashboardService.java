package com.finanzero.service;

import com.finanzero.dto.CategoryUsage;
import com.finanzero.dto.DashboardSummary;
import com.finanzero.model.*;
import com.finanzero.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final FinanceTransactionRepository transactions;
    private final CategoryRepository categories;
    private final DebtRepository debts;
    private final InvestmentRepository investments;
    private final WalletAccountRepository accounts;
    private final CurrentUserService currentUserService;

    public DashboardSummary summary(int month, int year) {
        AppUser owner = currentUserService.requiredUser();
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        List<FinanceTransaction> monthTransactions = transactions.findByOwnerAndDateBetweenOrderByDateDesc(owner, start, end);

        BigDecimal income = sum(monthTransactions, TransactionType.INCOME);
        BigDecimal fixed = sumPaidFixed(monthTransactions);
        BigDecimal variable = sum(monthTransactions, TransactionType.VARIABLE_EXPENSE);
        BigDecimal debtsOpen = debts.findByOwnerOrderByIdDesc(owner).stream()
                .map(d -> nvl(d.getTotalAmount()).subtract(nvl(d.getPaidAmount())).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal invested = investments.findByOwnerOrderByIdDesc(owner).stream().map(i -> nvl(i.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal available = accounts.findByOwnerOrderByName(owner).stream().map(a -> nvl(a.getBalance())).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, BigDecimal> spentByCategoryId = monthTransactions.stream()
                .filter(this::countsAsExpense)
                .filter(t -> t.getCategory() != null)
                .collect(Collectors.groupingBy(t -> t.getCategory().getId(), Collectors.reducing(BigDecimal.ZERO, FinanceTransaction::getAmount, BigDecimal::add)));

        List<CategoryUsage> usage = categories.findByOwnerOrderByName(owner).stream()
                .filter(c -> c.getType() != TransactionType.INCOME)
                .map(c -> {
                    BigDecimal spent = spentByCategoryId.getOrDefault(c.getId(), BigDecimal.ZERO);
                    BigDecimal limit = c.getMonthlyLimit() == null ? BigDecimal.ZERO : c.getMonthlyLimit();
                    double percent = limit.compareTo(BigDecimal.ZERO) > 0
                            ? spent.multiply(BigDecimal.valueOf(100)).divide(limit, 2, RoundingMode.HALF_UP).doubleValue()
                            : 0;
                    return new CategoryUsage(c.getName(), c.getColor(), spent, limit, percent);
                })
                .sorted(Comparator.comparing(CategoryUsage::spent).reversed())
                .toList();

        return new DashboardSummary(income, fixed, variable, debtsOpen, invested, available, usage, monthlyByType(owner, year, TransactionType.INCOME), monthlyExpenses(owner, year));
    }

    private BigDecimal sum(List<FinanceTransaction> list, TransactionType type) {
        return list.stream().filter(t -> t.getType() == type).map(t -> nvl(t.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumPaidFixed(List<FinanceTransaction> list) {
        return list.stream().filter(t -> t.getType() == TransactionType.FIXED_EXPENSE && t.getStatus() == PaymentStatus.PAID).map(t -> nvl(t.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean countsAsExpense(FinanceTransaction t) {
        if (t.getType() == TransactionType.VARIABLE_EXPENSE) return true;
        return t.getType() == TransactionType.FIXED_EXPENSE && t.getStatus() == PaymentStatus.PAID;
    }

    private Map<String, BigDecimal> monthlyByType(AppUser owner, int year, TransactionType type) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            LocalDate start = LocalDate.of(year, m, 1);
            LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
            BigDecimal total = transactions.findByOwnerAndTypeAndDateBetweenOrderByDateDesc(owner, type, start, end)
                    .stream().map(t -> nvl(t.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
            map.put(Month.of(m).getDisplayName(TextStyle.SHORT, new Locale("pt", "BR")), total);
        }
        return map;
    }

    private Map<String, BigDecimal> monthlyExpenses(AppUser owner, int year) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            LocalDate start = LocalDate.of(year, m, 1);
            LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
            BigDecimal total = transactions.findByOwnerAndDateBetweenOrderByDateDesc(owner, start, end).stream().filter(this::countsAsExpense).map(t -> nvl(t.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
            map.put(Month.of(m).getDisplayName(TextStyle.SHORT, new Locale("pt", "BR")), total);
        }
        return map;
    }

    private BigDecimal nvl(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
