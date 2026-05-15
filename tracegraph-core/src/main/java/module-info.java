module io.tracegraph.core {
    requires transitive org.slf4j;

    exports io.tracegraph.core;
    exports io.tracegraph.core.spi;
    exports io.tracegraph.core.analysis;
    exports io.tracegraph.core.viz;
    exports io.tracegraph.core.exec;
}
