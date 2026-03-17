package movies;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class TimingLogger {
    private static final Object LOCK = new Object();

    private static final Path LOG_PATH = Paths.get(
            System.getProperty("catalina.base", "."),
            "logs",
            "fablix_search_timing.log"
    );

    public static void logLine(String line) {
        synchronized (LOCK) {
            try {
                Files.createDirectories(LOG_PATH.getParent());
                Files.write(
                        LOG_PATH,
                        (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            } catch (IOException e) {
                System.err.println("movies.TimingLogger failed: " + e.getMessage());
            }
        }
    }

    public static String getLogPath() {
        return LOG_PATH.toString();
    }
}