package com.finanzero.service;

import com.finanzero.dto.BatchReimbursementSendRequest;
import com.finanzero.dto.ReimbursementReceiveRequest;
import com.finanzero.dto.SendReimbursementRequest;
import com.finanzero.dto.TransactionRequest;
import com.finanzero.model.*;
import com.finanzero.repository.FinanceTransactionRepository;
import com.finanzero.repository.WalletAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReimbursementService {
    private final FinanceTransactionRepository transactions;
    private final WalletAccountRepository accounts;
    private final CurrentUserService currentUserService;
    private final EmailService emailService;
    private final TransactionService transactionService;
    private final ReceiptStorageService receiptStorageService;

    public List<FinanceTransaction> list(ReimbursementStatus status) {
        AppUser owner = currentUserService.requiredUser();
        return status == null
                ? transactions.findByOwnerAndReimbursableTrueOrderByDateDesc(owner)
                : transactions.findByOwnerAndReimbursableTrueAndReimbursementStatusOrderByDateDesc(owner, status);
    }

    @Transactional
    public FinanceTransaction send(Long transactionId, SendReimbursementRequest request) {
        AppUser owner = currentUserService.requiredUser();
        FinanceTransaction expense = transactions.findByIdAndOwner(transactionId, owner)
                .orElseThrow(() -> new IllegalArgumentException("Gasto reembolsável não encontrado."));
        if (!expense.isReimbursable()) throw new IllegalArgumentException("Este lançamento não está marcado como reembolsável.");

        String destination = firstNonBlank(request == null ? null : request.email(), expense.getReimbursementEmail());
        if (isBlank(destination)) throw new IllegalArgumentException("Informe o e-mail de destino do reembolso.");

        String company = firstNonBlank(request == null ? null : request.company(), expense.getReimbursementCompany(), "Empresa");
        String subject = "Solicitação de reembolso - " + owner.getName() + " - " + expense.getDate();
        String body = buildEmailBody(owner, expense, company);
        System.out.println("Enviando reembolso para " + destination + " referente ao lançamento " + expense.getId());
        emailService.send(destination, subject, body);
        System.out.println("Reembolso enviado para " + destination + ".");

        expense.setReimbursementEmail(destination);
        expense.setReimbursementCompany(company);
        expense.setReimbursementStatus(ReimbursementStatus.SENT);
        expense.setReimbursementSentAt(LocalDate.now());
        return transactions.save(expense);
    }


    @Transactional
    public List<FinanceTransaction> sendBatch(BatchReimbursementSendRequest request) {
        AppUser owner = currentUserService.requiredUser();
        if (request == null || request.transactionIds() == null || request.transactionIds().isEmpty()) {
            throw new IllegalArgumentException("Selecione ao menos um reembolso para enviar.");
        }

        String destination = request.email();
        if (isBlank(destination)) throw new IllegalArgumentException("Informe o e-mail de destino do reembolso.");

        List<FinanceTransaction> expenses = new ArrayList<>();
        for (Long id : request.transactionIds()) {
            FinanceTransaction expense = transactions.findByIdAndOwner(id, owner)
                    .orElseThrow(() -> new IllegalArgumentException("Gasto reembolsável não encontrado: " + id));
            if (!expense.isReimbursable()) throw new IllegalArgumentException("O lançamento " + id + " não está marcado como reembolsável.");
            if (expense.getReimbursementStatus() == ReimbursementStatus.REIMBURSED || expense.getReimbursementStatus() == ReimbursementStatus.REJECTED) {
                throw new IllegalArgumentException("O lançamento " + id + " já está finalizado.");
            }
            expenses.add(expense);
        }

        String company = firstNonBlank(request.company(), commonCompany(expenses), "Empresa");
        String subject = "Solicitação de reembolso - " + owner.getName() + " - " + expenses.size() + " gasto(s)";
        String body = buildBatchEmailBody(owner, expenses, company);

        System.out.println("Enviando " + expenses.size() + " reembolso(s) para " + destination + ".");
        emailService.send(destination, subject, body);
        System.out.println("Reembolsos enviados para " + destination + ".");

        LocalDate sentAt = LocalDate.now();
        for (FinanceTransaction expense : expenses) {
            expense.setReimbursementEmail(destination);
            expense.setReimbursementCompany(company);
            expense.setReimbursementStatus(ReimbursementStatus.SENT);
            expense.setReimbursementSentAt(sentAt);
        }
        return transactions.saveAll(expenses);
    }

    @Transactional
    public FinanceTransaction markReceived(Long transactionId, ReimbursementReceiveRequest request) {
        AppUser owner = currentUserService.requiredUser();
        FinanceTransaction expense = transactions.findByIdAndOwner(transactionId, owner)
                .orElseThrow(() -> new IllegalArgumentException("Gasto reembolsável não encontrado."));
        if (!expense.isReimbursable()) throw new IllegalArgumentException("Este lançamento não está marcado como reembolsável.");
        if (expense.getReimbursementStatus() == ReimbursementStatus.REIMBURSED) return expense;

        WalletAccount account = request != null && request.accountId() != null
                ? accounts.findByIdAndOwner(request.accountId(), owner).orElseThrow(() -> new IllegalArgumentException("Conta não encontrada."))
                : expense.getAccount();
        if (account == null) throw new IllegalArgumentException("Selecione a conta em que o reembolso entrou.");

        LocalDate receivedAt = request != null && request.receivedAt() != null ? request.receivedAt() : LocalDate.now();
        transactionService.createForUser(owner, new TransactionRequest(
                "Reembolso - " + expense.getDescription(),
                TransactionType.INCOME,
                expense.getAmount(),
                receivedAt,
                null,
                receivedAt,
                "Pix",
                "Reembolso referente ao gasto ID " + expense.getId(),
                false,
                null,
                null,
                ReimbursementStatus.NOT_REIMBURSABLE,
                PaymentStatus.RECEIVED,
                null,
                account.getId()
        ));
        expense.setReimbursementStatus(ReimbursementStatus.REIMBURSED);
        expense.setReimbursementReceivedAt(receivedAt);
        return transactions.save(expense);
    }

    @Transactional
    public FinanceTransaction reject(Long transactionId) {
        AppUser owner = currentUserService.requiredUser();
        FinanceTransaction expense = transactions.findByIdAndOwner(transactionId, owner)
                .orElseThrow(() -> new IllegalArgumentException("Gasto reembolsável não encontrado."));
        if (!expense.isReimbursable()) throw new IllegalArgumentException("Este lançamento não está marcado como reembolsável.");
        expense.setReimbursementStatus(ReimbursementStatus.REJECTED);
        return transactions.save(expense);
    }

    private String buildEmailBody(AppUser owner, FinanceTransaction expense, String company) {
        return "Olá,<br><br>" +
                "Segue solicitação de reembolso referente ao gasto abaixo.<br><br>" +
                "Solicitante: " + html(owner.getName()) + "<br>" +
                "Empresa: " + html(company) + "<br>" +
                "Data: " + html(String.valueOf(expense.getDate())) + "<br>" +
                "Descrição: " + html(expense.getDescription()) + "<br>" +
                "Categoria: " + html(expense.getCategory() == null ? "-" : expense.getCategory().getName()) + "<br>" +
                "Forma de pagamento: " + html(expense.getPaymentMethod()) + "<br>" +
                "Valor: R$ " + html(String.valueOf(expense.getAmount())) + "<br>" +
                receiptLine(expense) +
                "<br>" +
                "Total solicitado: R$ " + html(String.valueOf(expense.getAmount())) + "<br><br>" +
                "Atenciosamente,<br>" + html(owner.getName());
    }


    private String buildBatchEmailBody(AppUser owner, List<FinanceTransaction> expenses, String company) {
        BigDecimal total = BigDecimal.ZERO;
        StringBuilder lines = new StringBuilder();

        for (FinanceTransaction expense : expenses) {
            BigDecimal amount = expense.getAmount() == null ? BigDecimal.ZERO : expense.getAmount();
            total = total.add(amount);
            lines.append("- ")
                    .append(html(String.valueOf(expense.getDate()))).append(" | ")
                    .append(html(expense.getDescription())).append(" | ")
                    .append(html(expense.getCategory() == null ? "-" : expense.getCategory().getName())).append(" | ")
                    .append(html(expense.getPaymentMethod())).append(" | R$ ")
                    .append(html(String.valueOf(amount)))
                    .append("<br>");
            if (!isBlank(expense.getReceiptFileName())) {
                lines.append("&nbsp;&nbsp;Comprovante: ").append(receiptLink(expense)).append("<br>");
            }
        }

        return "Olá,<br><br>" +
                "Segue solicitação de reembolso referente aos gastos abaixo.<br><br>" +
                "Solicitante: " + html(owner.getName()) + "<br>" +
                "Empresa: " + html(company) + "<br><br>" +
                lines + "<br>" +
                "Total solicitado: R$ " + html(String.valueOf(total)) + "<br><br>" +
                "Atenciosamente,<br>" + html(owner.getName());
    }

    private String receiptLine(FinanceTransaction expense) {
        if (expense == null || isBlank(expense.getReceiptFileName())) return "";
        return "Comprovante: " + receiptLink(expense) + "<br>";
    }

    private String receiptLink(FinanceTransaction expense) {
        String url = receiptUrl(expense);
        return "<a href=\"" + html(url) + "\" target=\"_blank\" rel=\"noopener noreferrer\">Abrir comprovante</a>";
    }

    private String receiptUrl(FinanceTransaction expense) {
        return receiptStorageService.temporaryUrl(expense.getReceiptFileName());
    }

    private String html(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String commonCompany(List<FinanceTransaction> expenses) {
        String company = null;
        for (FinanceTransaction expense : expenses) {
            if (isBlank(expense.getReimbursementCompany())) continue;
            String current = expense.getReimbursementCompany().trim();
            if (company == null) company = current;
            else if (!company.equalsIgnoreCase(current)) return null;
        }
        return company;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (!isBlank(value)) return value.trim();
        return null;
    }

    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
}
