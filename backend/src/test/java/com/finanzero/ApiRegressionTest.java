package com.finanzero;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finanzero.model.*;
import com.finanzero.repository.*;
import com.finanzero.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:regression;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=validate",
        "app.mail.enabled=false", "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
class ApiRegressionTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AppUserRepository users;
    @Autowired WalletAccountRepository accounts;
    @Autowired FinanceTransactionRepository transactions;
    @Autowired DebtRepository debts;
    @MockBean EmailService email;
    @MockBean ReceiptStorageService receipts;
    AppUser owner;
    WalletAccount account;
    String token;

    @BeforeEach void setup() {
        token = UUID.randomUUID().toString();
        owner = users.save(AppUser.builder().name("Teste").email(UUID.randomUUID() + "@example.test")
                .passwordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("Senha1234"))
                .verified(true).authToken(TokenHash.of(token)).authTokenExpiresAt(LocalDateTime.now().plusHours(1)).build());
        account = accounts.save(WalletAccount.builder().owner(owner).name("Conta").balance(new BigDecimal("1000")).cardLimit(new BigDecimal("1000")).build());
    }

    ResultActions call(MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(request);
    }
    JsonNode body(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()); }
    Map<String, Object> expense(String amount, boolean reimbursable, String method) {
        return new HashMap<>(Map.of("description", "Compra", "amount", amount, "type", "VARIABLE_EXPENSE", "status", "PAID",
                "date", LocalDate.now().toString(), "paymentMethod", method, "accountId", account.getId(), "reimbursable", reimbursable));
    }
    long createExpense(String amount, boolean reimbursable, String method) throws Exception {
        return body(call(post("/api/transactions"), expense(amount, reimbursable, method)).andExpect(status().isOk())).get("id").asLong();
    }

    @Test void verifiedEmailNeverIssuesTokenWithoutPassword() throws Exception {
        mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", owner.getEmail(), "code", "arbitrary"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").doesNotExist());
        assertThat(users.findById(owner.getId()).orElseThrow().getAuthToken()).isEqualTo(TokenHash.of(token));
    }

    @Test void postWithExistingIdsIsRejectedAcrossAllEntityEndpoints() throws Exception {
        for (String path : List.of("accounts", "categories", "goals", "debts")) {
            call(post("/api/" + path), Map.of("id", account.getId(), "name", "Hijacked", "type", "VARIABLE_EXPENSE"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(accounts.findById(account.getId()).orElseThrow().getName()).isEqualTo("Conta");
    }

    @Test void cannotReferenceAccountOwnedBySomeoneElse() throws Exception {
        AppUser other = users.save(AppUser.builder().name("Outro").email(UUID.randomUUID()+"@example.test").passwordHash("unused").verified(true).build());
        WalletAccount foreign = accounts.save(WalletAccount.builder().owner(other).name("Privada").build());
        var payload = expense("10", false, "Pix"); payload.put("accountId", foreign.getId());
        call(post("/api/transactions"), payload).andExpect(status().isBadRequest());
        call(put("/api/accounts/" + foreign.getId()), Map.of("name", "changed")).andExpect(status().isBadRequest());
        call(post("/api/accounts"), Map.of("id", foreign.getId(), "name", "changed")).andExpect(status().isBadRequest());
        assertThat(accounts.findById(foreign.getId()).orElseThrow().getOwner().getId()).isEqualTo(other.getId());
    }

    @Test void receiptsRequireAuthenticationAndOwnership() throws Exception {
        long id = createExpense("10", false, "Pix");
        var transaction = transactions.findById(id).orElseThrow(); transaction.setReceiptFileName("private.pdf"); transactions.save(transaction);
        when(receipts.temporaryUrl("private.pdf")).thenReturn("https://example.test/signed");
        mvc.perform(get("/api/receipts/private.pdf")).andExpect(status().isUnauthorized());
        call(get("/api/receipts/foreign.pdf"), null).andExpect(status().isNotFound());
        call(get("/api/receipts/private.pdf"), null).andExpect(status().isOk()).andExpect(jsonPath("$.url").value("https://example.test/signed"));
        verify(receipts, times(1)).temporaryUrl("private.pdf");
    }

    @Test void negativeAmountsCannotIncreaseBalance() throws Exception {
        call(post("/api/transactions"), expense("-100", false, "Pix")).andExpect(status().isBadRequest());
        call(post("/api/investments"), Map.of("name", "Inv", "amount", -100, "accountId", account.getId())).andExpect(status().isBadRequest());
        assertThat(accounts.findById(account.getId()).orElseThrow().getBalance()).isEqualByComparingTo("1000");
    }

    @Test void duplicateCreationKeyMovesBalanceOnce() throws Exception {
        String key = UUID.randomUUID().toString();
        long first = body(call(post("/api/transactions").header("Idempotency-Key", key), expense("30", false, "Pix")).andExpect(status().isOk())).get("id").asLong();
        long second = body(call(post("/api/transactions").header("Idempotency-Key", key), expense("30", false, "Pix")).andExpect(status().isOk())).get("id").asLong();
        assertThat(second).isEqualTo(first);
        call(post("/api/transactions").header("Idempotency-Key", key), expense("40", false, "Pix")).andExpect(status().isConflict());
        assertThat(accounts.findById(account.getId()).orElseThrow().getBalance()).isEqualByComparingTo("970");
    }

    @Test void availableCardLimitMayRepresentAnOverLimitBalance() throws Exception {
        createExpense("1010", false, "Crédito");
        var after = accounts.findById(account.getId()).orElseThrow();
        assertThat(after.getCardLimit()).isEqualByComparingTo("-10");
        assertThat(after.getBalance()).isEqualByComparingTo("1000");
    }

    @Test void reimbursementCannotBeReopenedOrCreditedTwice() throws Exception {
        long id = createExpense("50", true, "Pix");
        for (int i = 0; i < 2; i++) call(post("/api/reimbursements/" + id + "/received"), Map.of("accountId", account.getId())).andExpect(status().isOk());
        call(post("/api/reimbursements/" + id + "/send"), Map.of("email", "company@example.test")).andExpect(status().isConflict());
        call(post("/api/reimbursements/" + id + "/reject"), Map.of()).andExpect(status().isConflict());
        call(put("/api/transactions/" + id), expense("50", true, "Pix")).andExpect(status().isConflict());
        var income = transactions.findByOwnerOrderByDateDesc(owner).stream().filter(t -> t.getType() == TransactionType.INCOME).toList();
        assertThat(income).hasSize(1);
        call(delete("/api/transactions/" + income.get(0).getId()), null).andExpect(status().isConflict());
        assertThat(accounts.findById(account.getId()).orElseThrow().getBalance()).isEqualByComparingTo("1000");
    }

    @Test void concurrentExpensesDoNotLoseBalanceUpdates() throws Exception {
        parallel(8, () -> call(post("/api/transactions"), expense("10", false, "Pix")).andExpect(status().isOk()));
        assertThat(accounts.findById(account.getId()).orElseThrow().getBalance()).isEqualByComparingTo("920");
        assertThat(transactions.findByOwnerOrderByDateDesc(owner)).hasSize(8);
    }

    @Test void concurrentReimbursementsCreateOnlyOneIncome() throws Exception {
        long id = createExpense("50", true, "Pix");
        parallel(4, () -> call(post("/api/reimbursements/" + id + "/received"), Map.of("accountId", account.getId())).andExpect(status().isOk()));
        assertThat(transactions.findByOwnerOrderByDateDesc(owner).stream().filter(t -> t.getType() == TransactionType.INCOME)).hasSize(1);
        assertThat(accounts.findById(account.getId()).orElseThrow().getBalance()).isEqualByComparingTo("1000");
    }

    @Test void logoutRevokesServerSession() throws Exception {
        call(post("/api/auth/logout"), Map.of()).andExpect(status().isOk());
        call(get("/api/accounts"), null).andExpect(status().isUnauthorized());
    }

    @Test void expiredTokenIsRejected() throws Exception {
        owner.setAuthTokenExpiresAt(LocalDateTime.now().minusMinutes(1)); users.save(owner);
        call(get("/api/accounts"), null).andExpect(status().isUnauthorized());
    }

    @Test void debtPartialPaymentsAdvanceDueDateOnlyWhenInstallmentIsComplete() throws Exception {
        LocalDate due = LocalDate.now().withDayOfMonth(10);
        long id = body(call(post("/api/debts"), Map.of("name", "Empréstimo", "totalAmount", 200, "paidAmount", 0,
                "monthlyPayment", 100, "totalInstallments", 2, "nextDueDate", due.toString(), "account", Map.of("id", account.getId()))).andExpect(status().isOk())).get("id").asLong();
        call(post("/api/debts/" + id + "/pay"), Map.of("accountId", account.getId(), "amount", 40)).andExpect(status().isOk()).andExpect(jsonPath("$.nextDueDate").value(due.toString()));
        call(post("/api/debts/" + id + "/pay"), Map.of("accountId", account.getId(), "amount", 60)).andExpect(status().isOk()).andExpect(jsonPath("$.nextDueDate").value(due.plusMonths(1).toString()));
        assertThat(transactions.findByOwnerOrderByDateDesc(owner)).hasSize(2);
        assertThat(accounts.findById(account.getId()).orElseThrow().getBalance()).isEqualByComparingTo("900");
        call(delete("/api/debts/" + id), null).andExpect(status().isConflict());
    }

    @Test void payingInvoiceRestoresLimitExactlyOnceWithoutDoubleCountingExpenses() throws Exception {
        long id = createExpense("50", false, "Crédito");
        var payload = Map.of("cardAccountId", account.getId(), "payingAccountId", account.getId(), "month", LocalDate.now().getMonthValue(), "year", LocalDate.now().getYear());
        for (int i = 0; i < 2; i++) call(post("/api/invoices/pay"), payload).andExpect(status().isOk());
        var after = accounts.findById(account.getId()).orElseThrow();
        assertThat(after.getBalance()).isEqualByComparingTo("950"); assertThat(after.getCardLimit()).isEqualByComparingTo("1000");
        call(get("/api/dashboard"), null).andExpect(status().isOk()).andExpect(jsonPath("$.variableExpenses").value(50));
        call(delete("/api/transactions/" + id), null).andExpect(status().isConflict());
    }

    @Test void usedAccountDeletionReturnsConflict() throws Exception {
        createExpense("10", false, "Pix");
        call(delete("/api/accounts/" + account.getId()), null).andExpect(status().isConflict());
        assertThat(accounts.existsById(account.getId())).isTrue();
    }

    @Test void typeFilterWorksWithoutDates() throws Exception {
        createExpense("10", false, "Pix");
        call(get("/api/transactions?type=INCOME"), null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    private void parallel(int count, Callable<?> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < count; i++) futures.add(pool.submit(() -> { start.await(); return action.call(); }));
            start.countDown();
            for (Future<?> future : futures) future.get(30, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }
}
