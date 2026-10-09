package com.movethemoney.service;

import com.movethemoney.model.Account;
import com.movethemoney.model.LedgerAccount;
import com.movethemoney.model.LedgerEntry;
import com.movethemoney.model.LedgerEntryPurpose;
import com.movethemoney.model.LedgerEntryType;
import com.movethemoney.model.Transfer;
import com.movethemoney.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class LedgerPostingService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerPostingService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    void postOpeningBalance(Account account, BigDecimal amount) {
        if (amount.signum() == 0) {
            return;
        }
        UUID journalId = UUID.randomUUID();
        postJournal(List.of(
                new LedgerEntry(
                        journalId,
                        account,
                        null,
                        LedgerAccount.CUSTOMER,
                        LedgerEntryType.CREDIT,
                        LedgerEntryPurpose.OPENING_BALANCE,
                        amount),
                new LedgerEntry(
                        journalId,
                        null,
                        null,
                        LedgerAccount.OPENING_EQUITY,
                        LedgerEntryType.DEBIT,
                        LedgerEntryPurpose.OPENING_BALANCE,
                        amount)));
    }

    void postTransfer(Transfer transfer) {
        UUID journalId = UUID.randomUUID();
        postJournal(List.of(
                new LedgerEntry(
                        journalId,
                        transfer.getFromAccount(),
                        transfer,
                        LedgerAccount.CUSTOMER,
                        LedgerEntryType.DEBIT,
                        LedgerEntryPurpose.TRANSFER,
                        transfer.getAmount()),
                new LedgerEntry(
                        journalId,
                        transfer.getToAccount(),
                        transfer,
                        LedgerAccount.CUSTOMER,
                        LedgerEntryType.CREDIT,
                        LedgerEntryPurpose.TRANSFER,
                        transfer.getAmount())));
    }

    List<LedgerEntry> postJournal(List<LedgerEntry> entries) {
        validateBalanced(entries);
        return ledgerEntryRepository.saveAll(entries);
    }

    private void validateBalanced(List<LedgerEntry> entries) {
        if (entries == null || entries.size() != 2) {
            throw new IllegalArgumentException("A journal must contain exactly two entries");
        }

        LedgerEntry first = entries.get(0);
        LedgerEntry second = entries.get(1);
        if (!first.getJournalId().equals(second.getJournalId())
                || first.getPurpose() != second.getPurpose()
                || first.getEntryType() == second.getEntryType()
                || first.getAmount().compareTo(second.getAmount()) != 0) {
            throw new IllegalArgumentException("Journal entries must balance as one equal debit and credit");
        }

        if (first.getPurpose() == LedgerEntryPurpose.TRANSFER) {
            if (first.getTransfer() != second.getTransfer()
                    || first.getAccount().getId().equals(second.getAccount().getId())) {
                throw new IllegalArgumentException("Transfer journal entries must reference distinct accounts and one transfer");
            }
            LedgerEntry debit = first.getEntryType() == LedgerEntryType.DEBIT ? first : second;
            LedgerEntry credit = first.getEntryType() == LedgerEntryType.CREDIT ? first : second;
            if (!debit.getAccount().getId().equals(debit.getTransfer().getFromAccount().getId())
                    || !credit.getAccount().getId().equals(credit.getTransfer().getToAccount().getId())) {
                throw new IllegalArgumentException("Transfer debits and credits must match the transfer accounts");
            }
        } else {
            LedgerEntry customerEntry = first.getLedgerAccount() == LedgerAccount.CUSTOMER ? first : second;
            LedgerEntry equityEntry = first.getLedgerAccount() == LedgerAccount.OPENING_EQUITY ? first : second;
            if (customerEntry.getLedgerAccount() != LedgerAccount.CUSTOMER
                    || equityEntry.getLedgerAccount() != LedgerAccount.OPENING_EQUITY
                    || customerEntry.getEntryType() != LedgerEntryType.CREDIT
                    || equityEntry.getEntryType() != LedgerEntryType.DEBIT) {
                throw new IllegalArgumentException("Opening balance must credit the customer and debit opening equity");
            }
        }
    }
}
