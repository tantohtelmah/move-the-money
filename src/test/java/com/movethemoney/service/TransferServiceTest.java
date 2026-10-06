package com.movethemoney.service;

import com.movethemoney.model.Account;
import com.movethemoney.repository.AccountRepository;
import org.junit.jupiter.api.Test; //this import is used to write unit tests for the TransferService class, allowing you to verify that the transfer functionality works as expected
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.movethemoney.repository.TransferRepository;
import org.junit.jupiter.api.BeforeEach; //this import is used to set up any necessary preconditions or configurations before each test method is executed, ensuring that the tests run in a consistent environment
import java.util.concurrent.*;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest //this annotation indicates that the test class should run with the Spring Boot test support, allowing you to test the TransferService in a Spring application context
class TransferServiceTest {

    @Autowired //this annotation is used to inject the AccountService bean into the test class
    private AccountService accountService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @BeforeEach
    void cleanDatabase() {
        transferRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test //this annotation indicates that the following method is a test case that should be executed by the testing framework
    void transferMovesMoneyBetweenAccounts() {

        Account sender =
                accountService.openAccount(new BigDecimal("100.00"));

        Account receiver =
                accountService.openAccount(new BigDecimal("50.00"));

        transferService.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("30.00"),
                "test-transfer-1"
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
    }

    @Test
    void transferFailsWhenBalanceIsInsufficient() {

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
                        "insufficient-funds-test"
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
    void duplicateTransferIsAppliedOnlyOnce() {

        Account sender =
                accountService.openAccount(new BigDecimal("100.00"));

        Account receiver =
                accountService.openAccount(new BigDecimal("50.00"));

        // First request
        transferService.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("30.00"),
                "duplicate-test-key"
        );

        // Same request again
        transferService.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("30.00"),
                "duplicate-test-key"
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

        assertEquals(1, transferRepository.count());
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
                        "concurrent-1"
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
                        "concurrent-2"
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
}