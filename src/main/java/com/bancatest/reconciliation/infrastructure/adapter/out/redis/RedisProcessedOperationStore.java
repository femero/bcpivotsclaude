package com.bancatest.reconciliation.infrastructure.adapter.out.redis;

import com.bancatest.reconciliation.domain.model.vo.OperationReference;
import com.bancatest.reconciliation.domain.port.out.ProcessedOperationStore;
import com.bancatest.reconciliation.infrastructure.config.ReconciliationConfig;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Redis pre-filter for duplicates: {@code SET key value NX EX ttl GET}. A null previous value means
 * this call marked the reference. If Redis fails the filter is skipped (the database unique index
 * remains the guarantee).
 */
@ApplicationScoped
public class RedisProcessedOperationStore implements ProcessedOperationStore {

  private static final Logger LOG = Logger.getLogger(RedisProcessedOperationStore.class);
  private static final String KEY_PREFIX = "dedup:";

  private final ValueCommands<String, String> values;
  private final SetArgs setArgs;

  public RedisProcessedOperationStore(RedisDataSource dataSource, ReconciliationConfig config) {
    this.values = dataSource.value(String.class);
    this.setArgs = new SetArgs().nx().ex(config.idempotency().ttl());
  }

  @Override
  public boolean markAsProcessed(OperationReference reference) {
    try {
      return values.setGet(KEY_PREFIX + reference.value(), "1", setArgs) == null;
    } catch (RuntimeException e) {
      LOG.warnf("Redis unavailable, duplicate filter skipped for %s", reference.value());
      return true;
    }
  }
}
