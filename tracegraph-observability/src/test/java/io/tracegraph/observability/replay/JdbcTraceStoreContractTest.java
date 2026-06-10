package io.tracegraph.observability.replay;

import org.h2.jdbcx.JdbcDataSource;

import java.util.UUID;

class JdbcTraceStoreContractTest extends TraceStoreContractTest {

    @Override
    protected TraceStore createStore() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        JdbcTraceStore<String> store = JdbcTraceStore.of(ds, String.class);
        store.initSchema();
        return store;
    }
}
