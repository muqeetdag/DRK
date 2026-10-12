package com.drkmuqeet.wifi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Every privileged action in the app goes through here.
 *
 * <p>Command strings are fixed constants assembled in {@link WifiRepo}; no value that
 * originated from the environment (an SSID, a BSSID, a file) is ever concatenated into a
 * shell line. The only variable that reaches a command is a network id already parsed into
 * an {@code int}.
 */
final class RootShell {

    static final class Result {
        final int exit;
        final String out;
        final String err;

        Result(int exit, String out, String err) {
            this.exit = exit;
            this.out = out;
            this.err = err;
        }

        boolean ok() {
            return exit == 0;
        }

        boolean blank() {
            return out.trim().isEmpty();
        }
    }

    private RootShell() {
    }

    /** Runs {@code cmd} as root. Returns exit -1 when su is absent, denied or too slow. */
    static Result root(String cmd, long timeoutMs) {
        return exec(new String[]{"su", "-c", cmd}, timeoutMs);
    }

    /** Root, falling back to a plain shell for the reads some ROMs allow unprivileged. */
    static Result rootThenShell(String cmd, long timeoutMs) {
        Result rooted = root(cmd, timeoutMs);
        if (!rooted.blank()) {
            return rooted;
        }
        Result plain = exec(new String[]{"sh", "-c", cmd}, timeoutMs);
        return plain.blank() ? rooted : plain;
    }

    private static Result exec(String[] argv, long timeoutMs) {
        Process p = null;
        try {
            p = new ProcessBuilder(argv).start();
            Drain out = new Drain(p.getInputStream());
            Drain err = new Drain(p.getErrorStream());
            try {
                p.getOutputStream().close();
            } catch (IOException ignored) {
                // A su that does not read stdin closing quietly is not a failure.
            }
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                return new Result(-1, out.text(), "timed out");
            }
            return new Result(p.exitValue(), out.text(), err.text());
        } catch (IOException e) {
            String message = e.getMessage() == null ? "io error" : e.getMessage();
            return new Result(-1, "", message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(-1, "", "interrupted");
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }

    /** Streams a pipe on its own thread so a chatty child can never stall the wait. */
    private static final class Drain {
        private final StringBuilder sb = new StringBuilder();
        private final Thread thread;

        Drain(InputStream in) {
            thread = new Thread(() -> {
                try (BufferedReader reader =
                             new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                } catch (IOException ignored) {
                    // Pipe closed with the process; whatever arrived is already buffered.
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        String text() {
            try {
                thread.join(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return sb.toString();
        }
    }
}
