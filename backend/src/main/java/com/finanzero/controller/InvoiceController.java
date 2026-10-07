package com.finanzero.controller;

import com.finanzero.dto.TransactionRequest;
import com.finanzero.model.*;
import com.finanzero.repository.*;
import com.finanzero.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {
    private final CurrentUserService currentUser;
    private final FinanceTransactionRepository transactions;
    private final WalletAccountRepository accounts;
    private final TransactionService transactionService;

    public record Payment(@NotNull Long cardAccountId, @NotNull Long payingAccountId,
                          @Min(1) @Max(12) int month, @Min(1900) @Max(9999) int year) {}

    /** Settles paid credit purchases in the selected calendar month, once per purchase. */
    @PostMapping("/pay")
    @Transactional
    public java.util.Map<String, Object> pay(@RequestBody @Valid Payment request) {
        AppUser owner = currentUser.lockUser();
        WalletAccount card = accounts.findByIdAndOwner(request.cardAccountId(), owner).orElseThrow(() -> new IllegalArgumentException("Cartão não encontrado."));
        WalletAccount paying = accounts.findByIdAndOwner(request.payingAccountId(), owner).orElseThrow(() -> new IllegalArgumentException("Conta não encontrada."));
        LocalDate start = LocalDate.of(request.year(), request.month(), 1);
        var purchases = transactions.findByOwnerAndDateBetweenOrderByDateDesc(owner, start, start.withDayOfMonth(start.lengthOfMonth())).stream()
                .filter(t -> t.getType() != TransactionType.INCOME && t.getStatus() == PaymentStatus.PAID && "Crédito".equals(t.getPaymentMethod()))
                .filter(t -> t.getAccount() != null && t.getAccount().getId().equals(card.getId()) && !t.isCardSettled()).toList();
        BigDecimal total = purchases.stream().map(FinanceTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() == 0) return java.util.Map.of("amount", BigDecimal.ZERO, "message", "Nenhuma compra pendente de liquidação.");
        var payment = transactionService.createForUser(owner, new TransactionRequest("Pagamento de fatura - " + card.getName(),
                TransactionType.VARIABLE_EXPENSE, total, LocalDate.now(), null, null, "Débito",
                "Liquidação das compras de " + start.getMonthValue() + "/" + start.getYear(), false,
                null, null, ReimbursementStatus.NOT_REIMBURSABLE, PaymentStatus.PAID, null, paying.getId()));
        payment.setInvoicePaymentKey(UUID.randomUUID().toString());
        transactions.save(payment);
        card.setCardLimit(card.getCardLimit().add(total));
        accounts.save(card);
        purchases.forEach(t -> t.setCardSettled(true));
        transactions.saveAll(purchases);
        return java.util.Map.of("amount", total, "message", "Fatura paga e limite recomposto.");
    }
}
