package io.tracegraph.memory;

import io.tracegraph.core.spi.MemoryStore;
import org.h2.jdbcx.JdbcDataSource;

import java.util.UUID;

class JdbcMemoryStoreContractTest extends MemoryStoreContractTest {

    @Override
    protected MemoryStore createStore() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        JdbcMemoryStore store = JdbcMemoryStore.of(ds);
        store.initSchema();
        return store;
    }
}
