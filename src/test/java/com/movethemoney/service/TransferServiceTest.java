package com.movethemoney.service;

import com.movethemoney.PostgresIntegrationTest;
import com.movethemoney.model.Account;
import com.movethemoney.model.LedgerAccount;
import com.movethemoney.model.LedgerEntry;
import com.movethemoney.model.LedgerEntryPurpose;
import com.movethemoney.model.LedgerEntryType;
import com.movethemoney.model.Transfer;
import com.movethemoney.repository.AccountRepository;
import com.movethemoney.repository.LedgerEntryRepository;
import com.movethemoney.repository.TransferRepository;
import com.movethemoney.dto.AccountReconciliationResult;
import org.junit.jupiter.api.Test; //this import is used to write unit tests for the TransferService class, allowing you to verify that the transfer functionality works as expected
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.*;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

@SpringBootTest //this annotation indicates that the test class should run with the Spring Boot test support, allowing you to test the TransferService in a Spring application context
@AutoConfigureMockMvc
class TransferServiceTest extends PostgresIntegrationTest {

    @Autowired //this annotation is used to inject the AccountService bean into the test class
    private AccountService accountService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private ReconciliationService reconciliationService;

    @MockitoSpyBean
    private LedgerPostingService ledgerPostingService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void transferMovesMoneyBetweenAccounts() {  //this method tests the transfer functionality by creating two accounts, performing a transfer, and asserting that the balances of both accounts are updated correctly

        Account sender = accountService.openAccount(new BigDecimal("100.00"));

        Account receiver = accountService.openAccount(new BigDecimal("50.00"));

        Transfer transfer = transferService.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("30.00"),
                UUID.randomUUID().toString()
        );

        Account updatedSender =
                accountRepository.findById(sender.getId()).orElseThrow();

        Account updatedReceiver =
                accountRepository.findById(receiver.getId()).orElseThrow();

        assertEquals(
                0,
                updatedSender.getBalance().compareTo(new BigDecimal("70.00"))
        );

        assertEquals(
                0,
                updatedReceiver.getBalance().compareTo(new BigDecimal("80.00"))
        );

        List<LedgerEntry> entries =
                ledgerEntryRepository.findByTransfer_IdOrderByIdAsc(transfer.getId());
        assertEquals(2, entries.size());
        assertEquals(1, entries.stream().filter(e -> e.getEntryType() == LedgerEntryType.DEBIT).count());
        assertEquals(1, entries.stream().filter(e -> e.getEntryType() == LedgerEntryType.CREDIT).count());
        assertEquals(0, entries.get(0).getAmount().compareTo(entries.get(1).getAmount()));
        assertEquals(entries.get(0).getJournalId(), entries.get(1).getJournalId());
        assertEquals(2, entries.size());
    }

    @Test
    void transferFailsWhenBalanceIsInsufficient() { //this method tests that the transfer functionality correctly handles the case where the sender's account balance is insufficient to cover the transfer amount, expecting an IllegalArgumentException to be thrown and verifying that the account balances remain unchanged

        Account sender =
                accountService.openAccount(new BigDecimal("50.00"));

        Account receiver =
                accountService.openAccount(new BigDecimal("20.00"));

        assertThrows(
                IllegalArgumentException.class,
                () -> transferService.transfer(
                        sender.getId(),
                        receiver.getId(),
                        new BigDecimal("80.00"),
                        UUID.randomUUID().toString()
                )
        );

        Account updatedSender =
                accountRepository.findById(sender.getId()).orElseThrow();

        Account updatedReceiver =
                accountRepository.findById(receiver.getId()).orElseThrow();

        assertEquals(
                0,
                updatedSender.getBalance().compareTo(new BigDecimal("50.00"))
        );

        assertEquals(
                0,
                updatedReceiver.getBalance().compareTo(new BigDecimal("20.00"))
        );
    }

    @Test
    void duplicateTransferIsAppliedOnlyOnce() { //this method tests that duplicate transfer requests are handled correctly, ensuring that only one transfer is applied even if the same request is received multiple times

        Account sender =
                accountService.openAccount(new BigDecimal("100.00"));

        Account receiver =
                accountService.openAccount(new BigDecimal("50.00"));

        String idempotencyKey = "duplicate-test-key-" + UUID.randomUUID();
        transferService.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("30.00"),
                idempotencyKey
        );

        transferService.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("30.00"),
                idempotencyKey
        );

        Account updatedSender =
                accountRepository.findById(sender.getId()).orElseThrow();

        Account updatedReceiver =
                accountRepository.findById(receiver.getId()).orElseThrow();

        assertEquals(
                0,
                updatedSender.getBalance().compareTo(new BigDecimal("70.00"))
        );

        assertEquals(
                0,
                updatedReceiver.getBalance().compareTo(new BigDecimal("80.00"))
        );

        assertTrue(transferRepository.findByIdempotencyKey(idempotencyKey).isPresent());
    }

    @Test
    void concurrentTransfersCannotOverdrawAccount() throws Exception {

        Account sender =
                accountService.openAccount(new BigDecimal("100.00"));

        Account receiver1 =
                accountService.openAccount(new BigDecimal("0.00"));

        Account receiver2 =
                accountService.openAccount(new BigDecimal("0.00"));

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Boolean> transfer1 = () -> {
            ready.countDown();
            start.await();

            try {
                transferService.transfer(
                        sender.getId(),
                        receiver1.getId(),
                        new BigDecimal("80.00"),
                        UUID.randomUUID().toString()
                );
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        };

        Callable<Boolean> transfer2 = () -> {
            ready.countDown();
            start.await();

            try {
                transferService.transfer(
                        sender.getId(),
                        receiver2.getId(),
                        new BigDecimal("80.00"),
                        UUID.randomUUID().toString()
                );
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        };

        Future<Boolean> result1 = executor.submit(transfer1);
        Future<Boolean> result2 = executor.submit(transfer2);

        // Wait until both threads are ready
        ready.await();

        // Release both at almost exactly the same time
        start.countDown();

        boolean firstSucceeded = result1.get();
        boolean secondSucceeded = result2.get();

        executor.shutdown();

        Account updatedSender =
                accountRepository.findById(sender.getId()).orElseThrow();

        Account updatedReceiver1 =
                accountRepository.findById(receiver1.getId()).orElseThrow();

        Account updatedReceiver2 =
                accountRepository.findById(receiver2.getId()).orElseThrow();

        // Exactly ONE transfer must succeed
        assertNotEquals(firstSucceeded, secondSucceeded);

        // Sender must have $20 left
        assertEquals(
                0,
                updatedSender.getBalance().compareTo(new BigDecimal("20.00"))
        );

        // Only $80 total should reach the receivers
        BigDecimal totalReceived =
                updatedReceiver1.getBalance()
                        .add(updatedReceiver2.getBalance());

        assertEquals(
                0,
                totalReceived.compareTo(new BigDecimal("80.00"))
        );
    }

    @Test
    void concurrentRetriesWithSameIdempotencyKeyCreateOneTransferAndJournal() throws Exception {
        Account sender = accountService.openAccount(new BigDecimal("100.00"));
        Account receiver = accountService.openAccount(BigDecimal.ZERO);
        String idempotencyKey = UUID.randomUUID().toString();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Long> retry = () -> {
            ready.countDown();
            start.await();
            return transferService.transfer(
                    sender.getId(), receiver.getId(), new BigDecimal("30.00"), idempotencyKey).getId();
        };

        try {
            Future<Long> first = executor.submit(retry);
            Future<Long> second = executor.submit(retry);
            ready.await();
            start.countDown();

            assertEquals(first.get(), second.get());
        } finally {
            executor.shutdownNow();
        }

        Transfer transfer = transferRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertEquals(2, ledgerEntryRepository.findByTransfer_IdOrderByIdAsc(transfer.getId()).size());
        assertEquals(0, accountRepository.findById(sender.getId()).orElseThrow()
                .getBalance().compareTo(new BigDecimal("70.00")));
        assertEquals(0, accountRepository.findById(receiver.getId()).orElseThrow()
                .getBalance().compareTo(new BigDecimal("30.00")));
    }

    @Test
    void transferRejectsMoreThanTwoDecimalPlaces() {

        Account sender =
                accountService.openAccount(new BigDecimal("100.00"));

        Account receiver =
                accountService.openAccount(new BigDecimal("0.00"));

        assertThrows(
                IllegalArgumentException.class,
                () -> transferService.transfer(
                        sender.getId(),
                        receiver.getId(),
                        new BigDecimal("10.123"),
                        UUID.randomUUID().toString()
                )
        );
    }

    @Test
    void openingBalanceIsRecordedAsBalancedJournal() {
        Account account = accountService.openAccount(new BigDecimal("42.75"));
        List<LedgerEntry> entries =
                ledgerEntryRepository.findByAccount_IdOrderByIdAsc(account.getId());

        assertTrue(account.isLedgerInitialized());
        assertEquals(1, entries.size());
        assertEquals(LedgerEntryType.CREDIT, entries.getFirst().getEntryType());
        assertEquals(LedgerEntryPurpose.OPENING_BALANCE, entries.getFirst().getPurpose());

        List<LedgerEntry> journal =
                ledgerEntryRepository.findByJournalIdOrderByIdAsc(entries.getFirst().getJournalId());
        assertEquals(2, journal.size());
        assertEquals(0, journal.get(0).getAmount().compareTo(journal.get(1).getAmount()));
        assertTrue(journal.stream().anyMatch(e -> e.getLedgerAccount() == LedgerAccount.OPENING_EQUITY));
    }

    @Test
    void openingZeroBalanceInitializesLedgerWithoutZeroAmountEntries() {
        Account account = accountService.openAccount(BigDecimal.ZERO);

        assertTrue(account.isLedgerInitialized());
        assertTrue(ledgerEntryRepository.findByAccount_IdOrderByIdAsc(account.getId()).isEmpty());
        assertEquals(
                AccountReconciliationResult.Status.RECONCILED,
                reconciliationService.reconcile(account.getId()).status());
    }

    @Test
    void reconciliationReconstructsBalanceAndDetectsMismatch() {
        Account sender = accountService.openAccount(new BigDecimal("100.00"));
        Account receiver = accountService.openAccount(new BigDecimal("15.00"));
        transferService.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("27.25"),
                UUID.randomUUID().toString());

        AccountReconciliationResult result = reconciliationService.reconcile(sender.getId());
        assertEquals(AccountReconciliationResult.Status.RECONCILED, result.status());
        assertEquals(0, result.ledgerBalance().compareTo(new BigDecimal("72.75")));
        assertEquals(0, result.difference().compareTo(BigDecimal.ZERO));

        Account tampered = accountRepository.findById(sender.getId()).orElseThrow();
        tampered.setBalance(new BigDecimal("73.00"));
        accountRepository.saveAndFlush(tampered);

        AccountReconciliationResult mismatch = reconciliationService.reconcile(sender.getId());
        assertEquals(AccountReconciliationResult.Status.DISCREPANCY, mismatch.status());
        assertEquals(0, mismatch.ledgerBalance().compareTo(new BigDecimal("72.75")));
        assertEquals(0, mismatch.difference().compareTo(new BigDecimal("0.25")));
        assertThrows(IllegalStateException.class, () -> transferService.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("1.00"),
                UUID.randomUUID().toString()));
    }

    @Test
    void legacyAccountIsNotReportedAsReconciled() {
        Account legacyAccount = accountRepository.save(new Account(new BigDecimal("12.00")));

        AccountReconciliationResult result = reconciliationService.reconcile(legacyAccount.getId());

        assertEquals(AccountReconciliationResult.Status.LEDGER_NOT_INITIALIZED, result.status());
        assertNull(result.ledgerBalance());
        assertNull(result.difference());
    }

    @Test
    void reconciliationEndpointReturnsOnlyVerificationFields() throws Exception {
        Account account = accountService.openAccount(new BigDecimal("42.75"));

        mockMvc.perform(get("/accounts/{id}/reconciliation", account.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account.getId()))
                .andExpect(jsonPath("$.status").value("RECONCILED"))
                .andExpect(jsonPath("$.storedBalance").value(42.75))
                .andExpect(jsonPath("$.ledgerBalance").value(42.75))
                .andExpect(jsonPath("$.difference").value(0.0));
    }

    @Test
    void transferWithUninitializedLegacyAccountIsRejected() {
        Account legacyAccount = accountRepository.save(new Account(new BigDecimal("12.00")));
        Account recipient = accountService.openAccount(BigDecimal.ZERO);
        String idempotencyKey = UUID.randomUUID().toString();

        assertThrows(IllegalStateException.class, () -> transferService.transfer(
                legacyAccount.getId(),
                recipient.getId(),
                BigDecimal.ONE,
                idempotencyKey));
        assertTrue(transferRepository.findByIdempotencyKey(idempotencyKey).isEmpty());
    }

    @Test
    void unbalancedJournalIsRejected() {
        Account debitAccount = accountService.openAccount(BigDecimal.ZERO);
        Account creditAccount = accountService.openAccount(BigDecimal.ZERO);
        Transfer transfer = new Transfer(
                UUID.randomUUID().toString(),
                debitAccount,
                creditAccount,
                BigDecimal.ONE);
        UUID journalId = UUID.randomUUID();

        List<LedgerEntry> unbalanced = List.of(
                new LedgerEntry(journalId, debitAccount, transfer, LedgerAccount.CUSTOMER,
                        LedgerEntryType.DEBIT, LedgerEntryPurpose.TRANSFER, BigDecimal.ONE),
                new LedgerEntry(journalId, creditAccount, transfer, LedgerAccount.CUSTOMER,
                        LedgerEntryType.CREDIT, LedgerEntryPurpose.TRANSFER, new BigDecimal("0.99")));

        assertThrows(IllegalArgumentException.class, () -> ledgerPostingService.postJournal(unbalanced));
    }

    @Test
    void openingJournalMustContainOpeningEquityCounterEntry() {
        Account first = accountService.openAccount(BigDecimal.ZERO);
        Account second = accountService.openAccount(BigDecimal.ZERO);
        UUID journalId = UUID.randomUUID();

        List<LedgerEntry> invalidOpeningJournal = List.of(
                new LedgerEntry(journalId, first, null, LedgerAccount.CUSTOMER,
                        LedgerEntryType.CREDIT, LedgerEntryPurpose.OPENING_BALANCE, BigDecimal.ONE),
                new LedgerEntry(journalId, second, null, LedgerAccount.CUSTOMER,
                        LedgerEntryType.DEBIT, LedgerEntryPurpose.OPENING_BALANCE, BigDecimal.ONE));

        assertThrows(
                IllegalArgumentException.class,
                () -> ledgerPostingService.postJournal(invalidOpeningJournal));
    }

    @Test
    void invalidLedgerAmountsAreRejected() {
        Account account = accountService.openAccount(BigDecimal.ZERO);

        assertThrows(IllegalArgumentException.class, () -> new LedgerEntry(
                UUID.randomUUID(), account, null, LedgerAccount.CUSTOMER,
                LedgerEntryType.CREDIT, LedgerEntryPurpose.OPENING_BALANCE,
                new BigDecimal("1.001")));
        assertThrows(IllegalArgumentException.class, () -> new LedgerEntry(
                UUID.randomUUID(), account, null, LedgerAccount.CUSTOMER,
                LedgerEntryType.CREDIT, LedgerEntryPurpose.OPENING_BALANCE,
                BigDecimal.ZERO));
    }

    @Test
    void ledgerPostingFailureRollsBackTransferAndBalances() {
        Account sender = accountService.openAccount(new BigDecimal("50.00"));
        Account receiver = accountService.openAccount(BigDecimal.ZERO);
        String idempotencyKey = UUID.randomUUID().toString();

        doThrow(new IllegalStateException("simulated ledger write failure"))
                .when(ledgerPostingService).postTransfer(any(Transfer.class));

        assertThrows(IllegalStateException.class, () -> transferService.transfer(
                sender.getId(), receiver.getId(), new BigDecimal("10.00"), idempotencyKey));

        assertEquals(0, accountRepository.findById(sender.getId()).orElseThrow()
                .getBalance().compareTo(new BigDecimal("50.00")));
        assertEquals(0, accountRepository.findById(receiver.getId()).orElseThrow()
                .getBalance().compareTo(BigDecimal.ZERO));
        assertTrue(transferRepository.findByIdempotencyKey(idempotencyKey).isEmpty());
        assertTrue(ledgerEntryRepository.findByAccount_IdOrderByIdAsc(sender.getId()).stream()
                .noneMatch(entry -> entry.getPurpose() == LedgerEntryPurpose.TRANSFER));
    }

    @Test
    void openingLedgerPostingFailureRollsBackAccount() {
        long countBefore = accountRepository.count();
        BigDecimal startingBalance = new BigDecimal("12.00");

        doThrow(new IllegalStateException("simulated ledger write failure"))
                .when(ledgerPostingService).postOpeningBalance(any(Account.class), eq(startingBalance));

        assertThrows(IllegalStateException.class, () -> accountService.openAccount(startingBalance));
        assertEquals(countBefore, accountRepository.count());
    }
}