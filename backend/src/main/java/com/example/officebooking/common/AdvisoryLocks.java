package com.example.officebooking.common;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Транзакционные advisory-блокировки PostgreSQL (снимаются автоматически при commit/rollback). */
@Component
public class AdvisoryLocks {
    @PersistenceContext
    private EntityManager em;

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(long key) {
        // подзапрос нужен, чтобы не читать результат типа void
        em.createNativeQuery("select 1 from (select pg_advisory_xact_lock(:k)) as t")
                .setParameter("k", key).getSingleResult();
    }
}
