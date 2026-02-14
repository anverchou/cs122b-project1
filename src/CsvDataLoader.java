import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

// Import CSV files
public class CsvDataLoader {

    private static final String DEFAULT_DB_URL = "jdbc:mysql://localhost:3306/moviedb";
    private static final String DEFAULT_DB_USER = "mytestuser";
    private static final String DEFAULT_DB_PASS = "password";

    private enum Mode { NAIVE, OPTIMIZED }

    // Key -> DB
    private final Map<String, String> movieKeyToId = new HashMap<>();
    private final Map<String, String> starKeyToId = new HashMap<>();
    private final Map<String, Integer> genreNameToId = new HashMap<>();

    // Source-ID
    private final Map<String, String> srcMovieIdToDbId = new HashMap<>();
    private final Map<String, String> srcStarIdToDbId = new HashMap<>();
    private final Map<String, Integer> srcGenreIdToDbId = new HashMap<>();
    private final Map<String, String> srcGenreIdToName = new HashMap<>();

    private long nextMovieNum;
    private long nextStarNum;
    private long badRows = 0;
    private long warnings = 0;

    public static void main(String[] args) throws Exception {
        Map<String, String> a = parseArgs(args);

        // Validate required args
        if (a.containsKey("help") || !a.containsKey("movies") || !a.containsKey("stars") ||
                !a.containsKey("genres") || !a.containsKey("sim") || !a.containsKey("gim") ||
                !a.containsKey("ratings")) {
            return;
        }

        // Decide mode and batch size
        Mode mode = "naive".equalsIgnoreCase(a.getOrDefault("mode", "optimized")) ? Mode.NAIVE : Mode.OPTIMIZED;
        int batchSize = parseIntOrDefault(a.get("batchSize"), 1000);

        String url = envOrDefault("DB_URL", DEFAULT_DB_URL);
        url = ensureJdbcParam(url, "rewriteBatchedStatements", "true");
        String user = envOrDefault("DB_USER", DEFAULT_DB_USER);
        String pass = envOrDefault("DB_PASSWORD", DEFAULT_DB_PASS);

        Class.forName("com.mysql.cj.jdbc.Driver");

        long t0 = System.nanoTime();
        CsvDataLoader loader = new CsvDataLoader();

        // Import overhead
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            conn.setAutoCommit(false);
            // Initialize internal ID generators
            loader.initIdGenerators(conn);

            // Preload caches from DB
            if (mode == Mode.OPTIMIZED) {
                loader.loadCaches(conn);
            }
            conn.commit();
            //  1) genres (so genre IDs exist)
            //  2) movies (so movie IDs exist)
            //  3) stars (so star IDs exist)
            //  4) relationship tables (need mappings)
            //  5) ratings (needs movie mapping)
            long m0 = System.nanoTime();
            loader.importGenres(conn, a.get("genres"), mode);
            conn.commit();
            long m1 = System.nanoTime();

            long m2 = System.nanoTime();
            loader.importMovies(conn, a.get("movies"), mode, batchSize);
            conn.commit();

            long m3 = System.nanoTime();

            long m4 = System.nanoTime();
            loader.importStars(conn, a.get("stars"), mode, batchSize);
            conn.commit();

            long m5 = System.nanoTime();

            long m6 = System.nanoTime();
            loader.importStarsInMovies(conn, a.get("sim"), mode, batchSize);
            conn.commit();

            long m7 = System.nanoTime();

            long m8 = System.nanoTime();
            loader.importGenresInMovies(conn, a.get("gim"), mode, batchSize);
            conn.commit();
            long m9 = System.nanoTime();

            long m10 = System.nanoTime();
            loader.importRatings(conn, a.get("ratings"), mode, batchSize);
            conn.commit();
            long m11 = System.nanoTime();
        }

        long t1 = System.nanoTime();
    }

    // Load existing DB records into in-memory maps
    private void loadCaches(Connection conn) throws SQLException {
        // Cache movies
        try (PreparedStatement ps = conn.prepareStatement("SELECT id, title, year, director FROM movies");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String id = rs.getString("id");
                String title = rs.getString("title");
                int year = rs.getInt("year");
                String director = rs.getString("director");
                movieKeyToId.put(movieKey(title, year, director), id);
            }
        }

        // Cache stars
        try (PreparedStatement ps = conn.prepareStatement("SELECT id, name, birthYear FROM stars");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String id = rs.getString("id");
                String name = rs.getString("name");
                Integer by = (Integer) rs.getObject("birthYear"); // getObject preserves NULL
                starKeyToId.putIfAbsent(starKey(name, by), id);
            }
        }

        // Cache genres
        try (PreparedStatement ps = conn.prepareStatement("SELECT id, name FROM genres");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                genreNameToId.putIfAbsent(norm(name), id);
            }
        }
    }

    // Generate the next tt/nm numeric IDs
    private void initIdGenerators(Connection conn) throws SQLException {
        // Find the largest numeric part after "tt" in movies.id, then start from +1.
        nextMovieNum = 1 + maxNumericSuffix(conn,
                "SELECT MAX(CAST(SUBSTRING(id,3) AS UNSIGNED)) FROM movies WHERE id REGEXP '^tt[0-9]+$'");

        // Find the largest numeric part after "nm" in stars.id, then start from +1.
        nextStarNum = 1 + maxNumericSuffix(conn,
                "SELECT MAX(CAST(SUBSTRING(id,3) AS UNSIGNED)) FROM stars WHERE id REGEXP '^nm[0-9]+$'");
    }

    // Return largest max numeric suffix
    private static long maxNumericSuffix(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                long v = rs.getLong(1);
                if (rs.wasNull()) return 0;
                return v;
            }
        }
        return 0;
    }

    // Import genres
    private void importGenres(Connection conn, String genresCsv, Mode mode) throws IOException, SQLException {
        long inserted = 0, dup = 0, read = 0;

        try (CsvReader r = new CsvReader(genresCsv)) {
            List<String> row;
            r.readHeader();
            while ((row = r.readRow()) != null) {
                read++;

                String srcId = safeGet(row, 0);
                String name = safeGet(row, 1);

                if (name == null || name.isBlank()) {
                    bad(r, "genres.csv missing name", row);
                    continue;
                }
                srcGenreIdToName.putIfAbsent(srcId, name);
                String k = norm(name);
                Integer existing = (mode == Mode.OPTIMIZED) ? genreNameToId.get(k) : findGenreIdByName(conn, name);

                if (existing != null) {
                    srcGenreIdToDbId.put(srcId, existing);
                    dup++;
                    continue;
                }

                // Insert new genre and record
                int newId = insertGenre(conn, name);
                srcGenreIdToDbId.put(srcId, newId);
                if (mode == Mode.OPTIMIZED) genreNameToId.put(k, newId);
                inserted++;
            }
        }

    }

    // Find an existing genre id by name
    private static Integer findGenreIdByName(Connection conn, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM genres WHERE name = ? LIMIT 1")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return null;
    }

    // Insert a genre and its ID
    private static int insertGenre(Connection conn, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO genres(name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }

        // Find by name if retrieval fais
        Integer id = findGenreIdByName(conn, name);
        return (id == null) ? -1 : id;
    }

    // Import movies
    private void importMovies(Connection conn, String moviesCsv, Mode mode, int batchSize) throws IOException, SQLException {

        long inserted = 0, reused = 0, read = 0;

        String insertSql = "INSERT INTO movies(id, title, year, director) VALUES (?,?,?,?)";
        try (PreparedStatement ins = conn.prepareStatement(insertSql);
             CsvReader r = new CsvReader(moviesCsv)) {

            r.readHeader();
            List<String> row;
            int batchCount = 0;

            while ((row = r.readRow()) != null) {
                read++;

                String srcId = safeGet(row, 0);
                String title = safeGet(row, 1);
                String yearStr = safeGet(row, 2);
                String director = safeGet(row, 3);

                // Validate required fields.
                if (title == null || title.isBlank() || director == null || director.isBlank()) {
                    bad(r, "movies.csv missing required title/director", row);
                    continue;
                }
                Integer year = parseIntOrNull(yearStr);
                if (year == null) {
                    bad(r, "movies.csv invalid year: '" + yearStr + "'", row);
                    continue;
                }
                String key = movieKey(title, year, director);
                String existingId = (mode == Mode.OPTIMIZED) ? movieKeyToId.get(key) : findMovieId(conn, title, year, director);
                // Check if movie exists
                if (existingId != null) {
                    srcMovieIdToDbId.put(srcId, existingId);
                    reused++;
                    continue;
                }

                // Movie is new then generate new Fablix
                String newId = nextMovieId();
                srcMovieIdToDbId.put(srcId, newId);
                if (mode == Mode.OPTIMIZED) movieKeyToId.put(key, newId);

                ins.setString(1, newId);
                ins.setString(2, title);
                ins.setInt(3, year);
                ins.setString(4, director);

                if (mode == Mode.OPTIMIZED) {
                    ins.addBatch();
                    batchCount++;
                    if (batchCount >= batchSize) {
                        inserted += execBatch(ins);
                        batchCount = 0;
                    }
                } else {
                    ins.executeUpdate();
                    inserted++;
                }
            }
            if (mode == Mode.OPTIMIZED && batchCount > 0) {
                inserted += execBatch(ins);
            }
        }
    }

    // Find an existing movie id
    private static String findMovieId(Connection conn, String title, int year, String director) throws SQLException {
        String sql = "SELECT id FROM movies WHERE title = ? AND year = ? AND director = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setInt(2, year);
            ps.setString(3, director);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        return null;
    }

    // Import stars
    private void importStars(Connection conn, String starsCsv, Mode mode, int batchSize) throws IOException, SQLException {
        long inserted = 0, reused = 0, read = 0;

        String insertSql = "INSERT INTO stars(id, name, birthYear) VALUES (?,?,?)";
        try (PreparedStatement ins = conn.prepareStatement(insertSql);
             CsvReader r = new CsvReader(starsCsv)) {

            r.readHeader();
            List<String> row;
            int batchCount = 0;

            while ((row = r.readRow()) != null) {
                read++;

                String srcId = safeGet(row, 0);
                String name = safeGet(row, 1);
                String byStr = safeGet(row, 2);

                if (name == null || name.isBlank()) {
                    bad(r, "stars.csv missing required name", row);
                    continue;
                }
                Integer by = parseIntOrNull(byStr);
                String key = starKey(name, by);
                String existingId = (mode == Mode.OPTIMIZED)
                        ? starKeyToId.get(key)
                        : findStarId(conn, name, by);

                if (existingId != null) {
                    srcStarIdToDbId.put(srcId, existingId);
                    reused++;
                    continue;
                }

                String newId = nextStarId();
                srcStarIdToDbId.put(srcId, newId);
                if (mode == Mode.OPTIMIZED) starKeyToId.put(key, newId);

                ins.setString(1, newId);
                ins.setString(2, name);
                if (by == null) ins.setNull(3, Types.INTEGER);
                else ins.setInt(3, by);

                if (mode == Mode.OPTIMIZED) {
                    ins.addBatch();
                    batchCount++;
                    if (batchCount >= batchSize) {
                        inserted += execBatch(ins);
                        batchCount = 0;
                    }
                } else {
                    ins.executeUpdate();
                    inserted++;
                }
            }

            if (mode == Mode.OPTIMIZED && batchCount > 0) {
                inserted += execBatch(ins);
            }
        }
    }

    // Find an existing star
    private static String findStarId(Connection conn, String name, Integer by) throws SQLException {
        String sql = (by == null)
                ? "SELECT id FROM stars WHERE name = ? AND birthYear IS NULL LIMIT 1"
                : "SELECT id FROM stars WHERE name = ? AND birthYear = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            if (by != null) ps.setInt(2, by);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        return null;
    }

    private void importStarsInMovies(Connection conn, String simCsv, Mode mode, int batchSize) throws IOException, SQLException {
        long inserted = 0, skipped = 0, read = 0;

        String sql = "INSERT IGNORE INTO stars_in_movies(starId, movieId) VALUES (?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             CsvReader r = new CsvReader(simCsv)) {

            r.readHeader();
            List<String> row;
            int batchCount = 0;

            while ((row = r.readRow()) != null) {
                read++;
                String srcStar = safeGet(row, 0);
                String srcMovie = safeGet(row, 1);
                String starId = srcStarIdToDbId.get(srcStar);
                String movieId = srcMovieIdToDbId.get(srcMovie);

                if (starId == null || movieId == null) {
                    warn(r, "stars_in_movies references missing star/movie: starId=" + srcStar + ", movieId=" + srcMovie, row);
                    skipped++;
                    continue;
                }

                ps.setString(1, starId);
                ps.setString(2, movieId);

                if (mode == Mode.OPTIMIZED) {
                    ps.addBatch();
                    batchCount++;
                    if (batchCount >= batchSize) {
                        inserted += execBatch(ps);
                        batchCount = 0;
                    }
                } else {
                    int c = ps.executeUpdate();
                    if (c == 1) inserted++;
                }
            }

            if (mode == Mode.OPTIMIZED && batchCount > 0) {
                inserted += execBatch(ps);
            }
        }
    }

    // Genres relationships import
    private void importGenresInMovies(Connection conn, String gimCsv, Mode mode, int batchSize) throws IOException, SQLException {
        long inserted = 0, skipped = 0, read = 0;

        String sql = "INSERT IGNORE INTO genres_in_movies(genreId, movieId) VALUES (?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             CsvReader r = new CsvReader(gimCsv)) {

            r.readHeader();
            List<String> row;
            int batchCount = 0;

            while ((row = r.readRow()) != null) {
                read++;
                String srcGenre = safeGet(row, 0);
                String srcMovie = safeGet(row, 1);

                Integer genreId = srcGenreIdToDbId.get(srcGenre);
                String movieId = srcMovieIdToDbId.get(srcMovie);

                if (genreId == null || movieId == null) {
                    warn(r, "genres_in_movies references missing genre/movie: genreId=" + srcGenre + ", movieId=" + srcMovie, row);
                    skipped++;
                    continue;
                }

                ps.setInt(1, genreId);
                ps.setString(2, movieId);

                if (mode == Mode.OPTIMIZED) {
                    ps.addBatch();
                    batchCount++;
                    if (batchCount >= batchSize) {
                        inserted += execBatch(ps);
                        batchCount = 0;
                    }
                } else {
                    int c = ps.executeUpdate();
                    if (c == 1) inserted++;
                }
            }

            if (mode == Mode.OPTIMIZED && batchCount > 0) {
                inserted += execBatch(ps);
            }
        }
    }

    // Import ratings
    private void importRatings(Connection conn, String ratingsCsv, Mode mode, int batchSize) throws IOException, SQLException {
        long inserted = 0, skipped = 0, read = 0;

        String sql = "INSERT IGNORE INTO ratings(movieId, rating, numVotes) VALUES (?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             CsvReader r = new CsvReader(ratingsCsv)) {

            r.readHeader();
            List<String> row;
            int batchCount = 0;

            while ((row = r.readRow()) != null) {
                read++;

                String srcMovie = safeGet(row, 0);
                String ratingStr = safeGet(row, 1);
                String votesStr = safeGet(row, 2);

                String movieId = srcMovieIdToDbId.get(srcMovie);
                if (movieId == null) {
                    skipped++;
                    continue;
                }

                Float rating = parseFloatOrNull(ratingStr);
                Integer votes = parseIntOrNull(votesStr);
                if (rating == null || votes == null) {
                    skipped++;
                    continue;
                }

                ps.setString(1, movieId);
                ps.setFloat(2, rating);
                ps.setInt(3, votes);

                if (mode == Mode.OPTIMIZED) {
                    ps.addBatch();
                    batchCount++;
                    if (batchCount >= batchSize) {
                        inserted += execBatch(ps);
                        batchCount = 0;
                    }
                } else {
                    int c = ps.executeUpdate();
                    if (c == 1) inserted++;
                }
            }

            if (mode == Mode.OPTIMIZED && batchCount > 0) {
                inserted += execBatch(ps);
            }
        }
    }
    // Rturn next movieid
    private String nextMovieId() {
        long n = nextMovieNum++;
        return "tt" + String.format("%07d", n);
    }

    // Return next star id
    private String nextStarId() {
        long n = nextStarNum++;
        return "nm" + String.format("%07d", n);
    }

    private static String ensureJdbcParam(String url, String key, String value) {
        if (url == null) return null;
        String lower = url.toLowerCase(Locale.ROOT);
        String kLower = key.toLowerCase(Locale.ROOT) + "=";
        if (lower.contains(kLower)) return url;

        char join = url.contains("?") ? '&' : '?';
        return url + join + key + "=" + value;
    }

    private static long execBatch(PreparedStatement ps) throws SQLException {
        long inserted = 0;
        int[] res = ps.executeBatch();
        ps.clearBatch();
        for (int r : res) {
            if (r == Statement.SUCCESS_NO_INFO) inserted++;
            else if (r > 0) inserted += r;
        }
        return inserted;
    }

    // Unique movie
    private static String movieKey(String title, int year, String director) {
        return norm(title) + "\u0001" + year + "\u0001" + norm(director);
    }

    // Star identity
    private static String starKey(String name, Integer birthYear) {
        return norm(name) + "\u0001" + (birthYear == null ? "null" : birthYear);
    }

    private static String norm(String s) {
        return (s == null) ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private void bad(CsvReader r, String reason, List<String> row) {
        badRows++;
        System.out.println("[BAD] " + r.fileName + " line " + r.lineNum + ": " + reason + " :: " + row);
    }

    private void warn(CsvReader r, String reason, List<String> row) {
        warnings++;
        System.out.println("[WARN] " + r.fileName + " line " + r.lineNum + ": " + reason + " :: " + row);
    }

    private static String safeGet(List<String> row, int idx) {
        if (idx < 0 || idx >= row.size()) return null;
        String v = row.get(idx);
        if (v == null) return null;

        if (idx == 0 && !v.isEmpty() && v.charAt(0) == '\uFEFF') {
            v = v.substring(1);
        }
        return v;
    }

    // Parse ints
    private static Integer parseIntOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            return Integer.parseInt(t);
        } catch (Exception e) {
            return null;
        }
    }

    // Parse floats
    private static Float parseFloatOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            return Float.parseFloat(t);
        } catch (Exception e) {
            return null;
        }
    }

    // Parse ints or use default values
    private static int parseIntOrDefault(String s, int def) {
        Integer v = parseIntOrNull(s);
        return (v == null) ? def : v;
    }

    // Read env keys
    private static String envOrDefault(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    private static long ms(long nanos) {
        return nanos / 1_000_000L;
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a == null) continue;

            if (a.equals("--help") || a.equals("-h")) {
                m.put("help", "1");
                continue;
            }

            if (a.startsWith("--")) {
                String k = a.substring(2);
                String v = (i + 1 < args.length && !args[i + 1].startsWith("--")) ? args[++i] : "true";
                m.put(k, v);
            }
        }
        return m;
    }

    // CSV reader
    private static class CsvReader implements AutoCloseable {
        private final BufferedReader br;
        private final String fileName;
        private String headerLine;
        private long lineNum = 0;

        CsvReader(String path) throws IOException {
            this.fileName = path;
            this.br = new BufferedReader(new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8));
        }

        void readHeader() throws IOException {
            headerLine = br.readLine();
            lineNum++;
        }

        List<String> readRow() throws IOException {
            String line = br.readLine();
            if (line == null) return null;
            lineNum++;
            return parseCsvLine(line);
        }

        @Override
        public void close() throws IOException {
            br.close();
        }

        private static List<String> parseCsvLine(String line) {
            if (line.indexOf('"') < 0) {
                return Arrays.asList(line.split(",", -1));
            }

            List<String> out = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            boolean inQuotes = false;

            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);

                if (c == '"') {
                    if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = !inQuotes;
                    }
                } else if (c == ',' && !inQuotes) {
                    out.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }

            out.add(cur.toString());
            return out;
        }
    }
}
