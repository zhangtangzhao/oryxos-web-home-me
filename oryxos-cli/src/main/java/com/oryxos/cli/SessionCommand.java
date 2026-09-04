package com.oryxos.cli;

import com.oryxos.core.workspace.WorkspaceInitializer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * oryxos session list — light command (no Spring): plain JDBC over
 * <workspace>/oryxos.db, so it stays sub-second (FR-022).
 */
@Command(name = "session", description = "Manage sessions",
         subcommands = SessionCommand.ListCommand.class)
public class SessionCommand {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Command(name = "list", description = "List conversation sessions")
    public static class ListCommand implements Callable<Integer> {

        @Option(names = {"--profile", "-p"}, description = "只列出指定 Agent 的会话")
        private String profile;

        @Option(names = {"--limit", "-l"}, defaultValue = "50", description = "最多显示条数（默认 50）")
        private int limit;

        @Override
        public Integer call() {
            Path root = WorkspaceInitializer.resolveRoot();
            Path db = root.resolve("oryxos.db");
            if (!Files.exists(db)) {
                System.out.println("（尚无会话数据库 " + db + " —— 首次 chat/serve 后生成）");
                return 0;
            }

            String sql = "SELECT session_id, profile_name, channel, user_id, status, last_active_at "
                    + "FROM sessions"
                    + (profile == null || profile.isBlank() ? "" : " WHERE profile_name = ?")
                    + " ORDER BY last_active_at DESC LIMIT " + Math.max(1, limit);
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + db);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                if (profile != null && !profile.isBlank()) {
                    ps.setString(1, profile.trim());
                }
                try (ResultSet rs = ps.executeQuery()) {
                    System.out.println("Sessions" + (profile == null || profile.isBlank()
                            ? "" : " (profile=" + profile.trim() + ")") + ":");
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        String status = rs.getString("status");
                        String lastActive = formatLastActive(rs.getObject("last_active_at"));
                        System.out.printf("  [%s] %s%s%n",
                                "ARCHIVED".equalsIgnoreCase(status) ? "归档" : "活跃",
                                rs.getString("session_id"),
                                lastActive == null ? "" : "  最后活跃 " + lastActive);
                        System.out.printf("        channel=%s user=%s profile=%s%n",
                                rs.getString("channel"), rs.getString("user_id"), rs.getString("profile_name"));
                    }
                    if (count == 0) {
                        System.out.println("  - （无会话记录）");
                    } else {
                        System.out.println("  共 " + count + " 条");
                    }
                }
            } catch (Exception e) {
                System.err.println("读取会话失败: " + e.getMessage());
                return 1;
            }
            return 0;
        }

        /** SQLite stores Instants in driver/Hibernate-dependent formats; parse defensively. */
        private static String formatLastActive(Object raw) {
            Instant instant = null;
            if (raw instanceof Timestamp ts) {
                instant = ts.toInstant();
            } else if (raw instanceof Number n) {
                long v = n.longValue();
                instant = v > 100_000_000_000L ? Instant.ofEpochMilli(v) : Instant.ofEpochSecond(v);
            } else if (raw instanceof String s && !s.isBlank()) {
                String text = s.trim();
                for (DateTimeFormatter fmt : List.of(DateTimeFormatter.ISO_INSTANT,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))) {
                    try {
                        if (fmt == DateTimeFormatter.ISO_INSTANT) {
                            instant = Instant.parse(text);
                        } else {
                            instant = LocalDateTime.parse(text, fmt).atZone(ZoneId.systemDefault()).toInstant();
                        }
                        break;
                    } catch (DateTimeParseException ignored) {
                        // try next format
                    }
                }
                if (instant == null) {
                    return text;
                }
            }
            return instant == null ? null : TS.format(instant.atZone(ZoneId.systemDefault()));
        }
    }
}
