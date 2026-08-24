package com.finanzero.config;

import com.finanzero.dto.TransactionRequest;
import com.finanzero.model.*;
import com.finanzero.repository.*;
import com.finanzero.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {
    private final AppUserRepository users;
    private final CategoryRepository categories;
    private final WalletAccountRepository accounts;
    private final DebtRepository debts;
    private final GoalRepository goals;
    private final InvestmentRepository investments;
    private final TransactionService transactions;
    private final BCryptPasswordEncoder encoder;

    @Override
    public void run(String... args) {
        if (users.count() > 0) return;

        AppUser owner = users.save(AppUser.builder()
                .name("Diogo Silva")
                .email("demo@finanzero.local")
                .passwordHash(encoder.encode("123456"))
                .verified(true)
                .role("USER")
                .authToken(UUID.randomUUID().toString())
                .build());

        Category salario = saveCategory(owner, "Salário", "💼", "#a3e635", TransactionType.INCOME, "0");
        Category renda = saveCategory(owner, "Renda extra", "🚀", "#c084fc", TransactionType.INCOME, "0");
        Category casa = saveCategory(owner, "Casa", "🏠", "#38bdf8", TransactionType.FIXED_EXPENSE, "5000");
        Category saude = saveCategory(owner, "Saúde", "💊", "#fb7185", TransactionType.FIXED_EXPENSE, "1200");
        Category mercado = saveCategory(owner, "Mercado", "🛒", "#f97316", TransactionType.VARIABLE_EXPENSE, "3000");
        Category lazer = saveCategory(owner, "Lazer", "🎮", "#e879f9", TransactionType.VARIABLE_EXPENSE, "1500");
        Category transporte = saveCategory(owner, "Transporte", "🚗", "#60a5fa", TransactionType.VARIABLE_EXPENSE, "2000");

        WalletAccount conta = saveAccount(owner, "Conta Digital", "1000.00", "0");
        accounts.save(WalletAccount.builder().name("Nubank").balance(new BigDecimal("500.00")).cardLimit(BigDecimal.ZERO).owner(owner).build());
        accounts.save(WalletAccount.builder().name("VeraCard").balance(BigDecimal.ZERO).cardLimit(new BigDecimal("5000.00")).owner(owner).build());

        LocalDate today = LocalDate.now();
        createTransaction(owner, "Salário - Empresa ABC", TransactionType.INCOME, "8900", today.withDayOfMonth(5), null, PaymentStatus.RECEIVED, salario.getId(), conta.getId(), "Pix", false, null, null);
        createTransaction(owner, "Freelance - Projeto X", TransactionType.INCOME, "1800", today.withDayOfMonth(15), null, PaymentStatus.RECEIVED, renda.getId(), conta.getId(), "Pix", false, null, null);
        createTransaction(owner, "Aluguel", TransactionType.FIXED_EXPENSE, "2500", today.withDayOfMonth(5), today.withDayOfMonth(5), PaymentStatus.PAID, casa.getId(), conta.getId(), "Débito", false, null, null);
        createTransaction(owner, "Plano de Saúde", TransactionType.FIXED_EXPENSE, "780", today.withDayOfMonth(10), today.withDayOfMonth(10), PaymentStatus.PAID, saude.getId(), conta.getId(), "Débito", false, null, null);
        createTransaction(owner, "Supermercado", TransactionType.VARIABLE_EXPENSE, "560", today.withDayOfMonth(21), null, PaymentStatus.PAID, mercado.getId(), conta.getId(), "Débito", false, null, null);
        createTransaction(owner, "Uber trabalho", TransactionType.VARIABLE_EXPENSE, "48.90", today.withDayOfMonth(20), null, PaymentStatus.PAID, transporte.getId(), conta.getId(), "Crédito", true, "Empresa ABC", "financeiro@empresa.com");
        createTransaction(owner, "Cinema", TransactionType.VARIABLE_EXPENSE, "45", today.withDayOfMonth(16), null, PaymentStatus.PAID, lazer.getId(), conta.getId(), "Crédito", false, null, null);

        debts.save(Debt.builder().name("Cartão de Crédito").creditor("VeraCard").totalAmount(new BigDecimal("4500")).paidAmount(new BigDecimal("3060")).totalInstallments(10).monthlyPayment(new BigDecimal("450")).nextDueDate(today.withDayOfMonth(5)).status(PaymentStatus.PENDING).owner(owner).build());
        debts.save(Debt.builder().name("Empréstimo Pessoal").creditor("CreditoBom").totalAmount(new BigDecimal("3000")).paidAmount(new BigDecimal("1200")).totalInstallments(10).monthlyPayment(new BigDecimal("300")).nextDueDate(today.withDayOfMonth(10)).status(PaymentStatus.PENDING).owner(owner).build());

        goals.save(Goal.builder().name("Reserva de Emergência").icon("🛡️").targetAmount(new BigDecimal("10000")).currentAmount(new BigDecimal("5000")).deadline(today.plusMonths(8)).owner(owner).build());
        goals.save(Goal.builder().name("Viagem em Família").icon("🌎").targetAmount(new BigDecimal("8000")).currentAmount(new BigDecimal("2450")).deadline(today.plusMonths(12)).owner(owner).build());

        investments.save(Investment.builder().name("Renda Fixa").investmentType("CDB/Tesouro").amount(new BigDecimal("2800")).profitabilityPercent(new BigDecimal("1.04")).account(conta).owner(owner).build());
    }

    private Category saveCategory(AppUser owner, String name, String icon, String color, TransactionType type, String limit) {
        return categories.save(Category.builder().name(name).icon(icon).color(color).type(type).monthlyLimit(new BigDecimal(limit)).owner(owner).build());
    }

    private WalletAccount saveAccount(AppUser owner, String name, String balance, String cardLimit) {
        return accounts.save(WalletAccount.builder().name(name).balance(new BigDecimal(balance)).cardLimit(new BigDecimal(cardLimit)).owner(owner).build());
    }

    private void createTransaction(AppUser owner, String description, TransactionType type, String amount, LocalDate date, LocalDate dueDate, PaymentStatus status, Long categoryId, Long accountId, String paymentMethod, boolean reimbursable, String reimbursementCompany, String reimbursementEmail) {
        transactions.createForUser(owner, new TransactionRequest(
                description,
                type,
                new BigDecimal(amount),
                date,
                dueDate,
                type == TransactionType.INCOME ? date : null,
                paymentMethod,
                null,
                reimbursable,
                reimbursementCompany,
                reimbursementEmail,
                reimbursable ? ReimbursementStatus.TO_SEND : ReimbursementStatus.NOT_REIMBURSABLE,
                status,
                categoryId,
                accountId
        ));
    }
}
