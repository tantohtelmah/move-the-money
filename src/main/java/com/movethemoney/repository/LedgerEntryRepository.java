package com.movethemoney.repository;

import com.movethemoney.model.LedgerEntry;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface LedgerEntryRepository extends Repository<LedgerEntry, Long> {
    <S extends LedgerEntry> List<S> saveAll(Iterable<S> entries);

    List<LedgerEntry> findByAccount_IdOrderByIdAsc(Long accountId);

    List<LedgerEntry> findByJournalIdOrderByIdAsc(java.util.UUID journalId);

    List<LedgerEntry> findByTransfer_IdOrderByIdAsc(Long transferId);

    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE -amount END), 0)
            FROM ledger_entries
            WHERE account_id = :accountId
            """, nativeQuery = true)
    BigDecimal calculateBalanceForAccount(@Param("accountId") Long accountId);
}
