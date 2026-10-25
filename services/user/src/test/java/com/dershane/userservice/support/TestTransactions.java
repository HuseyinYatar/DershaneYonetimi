package com.dershane.userservice.support;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** A TransactionTemplate that runs the callback without a database, for unit tests. */
public final class TestTransactions {

  private TestTransactions() {}

  public static TransactionTemplate template() {
    return new TransactionTemplate(
        new PlatformTransactionManager() {
          @Override
          public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
          }

          @Override
          public void commit(TransactionStatus status) {
            // Nothing to commit without a database.
          }

          @Override
          public void rollback(TransactionStatus status) {
            // Nothing to roll back without a database.
          }
        });
  }
}
