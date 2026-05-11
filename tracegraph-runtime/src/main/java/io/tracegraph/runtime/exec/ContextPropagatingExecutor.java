package io.tracegraph.runtime.exec;

import io.tracegraph.core.Context;
import org.slf4j.MDC;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ContextPropagatingExecutor {

    private ContextPropagatingExecutor() {}

    public static Executor wrap(Executor delegate, Context current) {
        Map<String, String> mdcSnapshot = MDC.getCopyOfContextMap();
        return runnable -> delegate.execute(propagating(runnable, mdcSnapshot));
    }

    public static ExecutorService wrap(ExecutorService delegate, Context current) {
        Map<String, String> mdcSnapshot = MDC.getCopyOfContextMap();
        return new PropagatingExecutorService(delegate, mdcSnapshot);
    }

    private static Runnable propagating(Runnable runnable, Map<String, String> mdcSnapshot) {
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (mdcSnapshot == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(mdcSnapshot);
                }
                runnable.run();
            } finally {
                if (previous == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previous);
                }
            }
        };
    }

    private static <V> Callable<V> propagating(Callable<V> callable, Map<String, String> mdcSnapshot) {
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (mdcSnapshot == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(mdcSnapshot);
                }
                return callable.call();
            } finally {
                if (previous == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previous);
                }
            }
        };
    }

    private static final class PropagatingExecutorService implements ExecutorService {

        private final ExecutorService delegate;
        private final Map<String, String> mdcSnapshot;

        PropagatingExecutorService(ExecutorService delegate, Map<String, String> mdcSnapshot) {
            this.delegate = delegate;
            this.mdcSnapshot = mdcSnapshot;
        }

        @Override
        public void execute(Runnable command) {
            delegate.execute(propagating(command, mdcSnapshot));
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            return delegate.submit(propagating(task, mdcSnapshot));
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            return delegate.submit(propagating(task, mdcSnapshot), result);
        }

        @Override
        public Future<?> submit(Runnable task) {
            return delegate.submit(propagating(task, mdcSnapshot));
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
                throws InterruptedException {
            return delegate.invokeAll(tasks.stream().map(t -> propagating(t, mdcSnapshot)).toList());
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.invokeAll(
                    tasks.stream().map(t -> propagating(t, mdcSnapshot)).toList(), timeout, unit);
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
                throws InterruptedException, ExecutionException {
            return delegate.invokeAny(tasks.stream().map(t -> propagating(t, mdcSnapshot)).toList());
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.invokeAny(
                    tasks.stream().map(t -> propagating(t, mdcSnapshot)).toList(), timeout, unit);
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }
    }
}
