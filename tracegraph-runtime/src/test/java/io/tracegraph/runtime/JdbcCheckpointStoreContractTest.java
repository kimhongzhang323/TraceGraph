package io.tracegraph.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracegraph.core.spi.CheckpointStore;
import org.h2.jdbcx.JdbcDataSource;

import java.util.UUID;

class JdbcCheckpointStoreContractTest extends CheckpointStoreContractTest {

    @Override
    protected CheckpointStore createStore() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        JdbcCheckpointStore<String> store = JdbcCheckpointStore.of(ds, new ObjectMapper(), String.class);
        store.initSchema();
        return store;
    }
}
