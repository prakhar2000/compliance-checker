import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Flags trades on a restricted list, or buys above the size limit.
 * Paths default to the bind paths in tool.yaml.
 */
public class ComplianceChecker {
    static final Set<String> RESTRICTED = Set.of("RST", "BAD", "XYZ");
    static final int SIZE_LIMIT = 10_000;

    public static void main(String[] args) throws IOException {
        String tradesPath = envOr("TRADES", "/inputs/trades");
        String outPath = envOr("OUTPUT_REPORT", "/outputs/report.json");
        for (int i = 0; i < args.length; i++) {
            if ("--trades".equals(args[i]) && i + 1 < args.length) {
                tradesPath = args[++i];
            } else if ("--out".equals(args[i]) && i + 1 < args.length) {
                outPath = args[++i];
            } else {
                System.err.println("Unknown argument: " + args[i]);
                System.exit(2);
            }
        }

        Path trades = Path.of(tradesPath);
        if (!Files.isRegularFile(trades)) {
            System.err.println("Missing file: " + trades);
            System.exit(1);
        }

        List<Map<String, String>> parsed = readCsv(trades);
        List<Map<String, String>> failed = new ArrayList<>();
        for (Map<String, String> row : parsed) {
            String reason = reasonFor(row);
            if (reason.isEmpty()) {
                continue;
            }
            Map<String, String> flagged = new LinkedHashMap<>();
            flagged.put("ticker", row.getOrDefault("ticker", ""));
            flagged.put("side", row.getOrDefault("side", ""));
            flagged.put("shares", row.getOrDefault("shares", ""));
            flagged.put("reason", reason);
            flagged.put("status", "failed");
            failed.add(flagged);
        }

        String json = report(parsed.size(), failed);
        Path out = Path.of(outPath);
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        Files.writeString(out, json + "\n", StandardCharsets.UTF_8);
        System.out.println(json);
        System.err.println(parsed.size() + " checked, " + failed.size() + " failed");
    }

    static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    static String reasonFor(Map<String, String> row) {
        List<String> reasons = new ArrayList<>();
        String ticker = row.getOrDefault("ticker", "").trim().toUpperCase(Locale.ROOT);
        if (RESTRICTED.contains(ticker)) {
            reasons.add("Restricted ticker");
        }
        int shares = parseShares(row.getOrDefault("shares", ""));
        String side = row.getOrDefault("side", "").trim().toLowerCase(Locale.ROOT);
        if ("buy".equals(side) && shares > SIZE_LIMIT) {
            reasons.add("Buy above " + SIZE_LIMIT + " shares");
        }
        return String.join("; ", reasons);
    }

    static int parseShares(String raw) {
        try {
            return Integer.parseInt(raw.trim().replace(",", ""));
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    static List<Map<String, String>> readCsv(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            throw new IOException(path + " is empty");
        }
        String headerLine = lines.get(0);
        if (headerLine.startsWith("\uFEFF")) {
            headerLine = headerLine.substring(1);
        }
        List<String> headers = new ArrayList<>();
        for (String header : splitCsv(headerLine)) {
            headers.add(header.trim());
        }
        if (!headers.contains("ticker") || !headers.contains("side") || !headers.contains("shares")) {
            throw new IOException(path + " needs ticker, side, and shares columns");
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            List<String> cells = splitCsv(lines.get(i));
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                row.put(headers.get(c), c < cells.size() ? cells.get(c).trim() : "");
            }
            rows.add(row);
        }
        return rows;
    }

    static List<String> splitCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    static String report(int checked, List<Map<String, String>> failed) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"summary\": { \"checked\": ").append(checked);
        json.append(", \"failed\": ").append(failed.size()).append(" },\n");
        json.append("  \"columns\": [\"ticker\", \"side\", \"shares\", \"reason\", \"status\"],\n");
        json.append("  \"rows\": [\n");
        for (int i = 0; i < failed.size(); i++) {
            Map<String, String> row = failed.get(i);
            json.append("    {");
            json.append(" \"ticker\": ").append(quote(row.get("ticker"))).append(",");
            json.append(" \"side\": ").append(quote(row.get("side"))).append(",");
            json.append(" \"shares\": ").append(quote(row.get("shares"))).append(",");
            json.append(" \"reason\": ").append(quote(row.get("reason"))).append(",");
            json.append(" \"status\": ").append(quote(row.get("status")));
            json.append(" }");
            if (i + 1 < failed.size()) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  ]\n}");
        return json.toString();
    }

    static String quote(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
