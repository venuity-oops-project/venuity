package com.venuity.client.ui;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class Async {

    private Async() {
    }

    public static <T> void run(Supplier<T> work, Consumer<T> onOk, Consumer<Throwable> onErr) {
        SwingUtilities.invokeLater(() -> new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() {
                return work.get();
            }

            @Override
            protected void done() {
                try {
                    onOk.accept(get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    onErr.accept(exception);
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    onErr.accept(cause == null ? exception : cause);
                }
            }
        }.execute());
    }

    public static void runVoid(Runnable work, Runnable onOk, Consumer<Throwable> onErr) {
        run(() -> {
            work.run();
            return Boolean.TRUE;
        }, ignored -> onOk.run(), onErr);
    }
}
