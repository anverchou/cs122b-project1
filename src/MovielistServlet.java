import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.*;

@WebServlet("/movielist")
public class MovielistServlet extends HttpServlet {

    // Session key used to store the last Movie List
    private static final String STATE_KEY = "movielist_state";

    // Only allow these page sizes
    private static final Set<Integer> ALLOWED_PAGE_SIZES =
            new HashSet<>(Arrays.asList(10, 25, 50, 100));

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String loginUser = "admin";
        String loginPasswd = "password";
        String loginUrl = "jdbc:mysql://localhost:3306/moviedb";

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            // 1) Resolve effective state
            Map<String, String> effective = resolveState(request);

            String title = trimOrNull(effective.get("title"));
            String yearStr = trimOrNull(effective.get("year"));
            String director = trimOrNull(effective.get("director"));
            String star = trimOrNull(effective.get("star"));
            String genreIdStr = trimOrNull(effective.get("genreId"));
            String startsWith = trimOrNull(effective.get("startsWith"));

            String sortPrimary = normSortPrimary(effective.get("sortPrimary"));
            String titleDir = normDir(effective.get("titleDir"));
            String ratingDir = normDir(effective.get("ratingDir"));

            int page = parsePositiveInt(effective.get("page"), 1);
            int pageSize = parseAllowedPageSize(effective.get("pageSize"), 25);

            // 2) Build parameters
            List<String> where = new ArrayList<>();
            List<Object> params = new ArrayList<>();

            if (title != null) {
                where.add("LOWER(m.title) LIKE CONCAT('%', LOWER(?), '%')");
                params.add(title);
            }

            if (director != null) {
                where.add("LOWER(m.director) LIKE CONCAT('%', LOWER(?), '%')");
                params.add(director);
            }

            if (yearStr != null) {
                try {
                    int y = Integer.parseInt(yearStr);
                    where.add("m.year = ?");
                    params.add(y);
                } catch (NumberFormatException nfe) {
                    // invalid year gives empty result
                    writeEmpty(out, effective, page, pageSize, sortPrimary, titleDir, ratingDir);
                    return;
                }
            }

            if (star != null) {
                where.add(
                        "EXISTS (" +
                                "  SELECT 1 " +
                                "  FROM stars_in_movies sim " +
                                "  JOIN stars s ON s.id = sim.starId " +
                                "  WHERE sim.movieId = m.id " +
                                "    AND LOWER(s.name) LIKE CONCAT('%', LOWER(?), '%')" +
                                ")"
                );
                params.add(star);
            }

            if (genreIdStr != null) {
                try {
                    int gid = Integer.parseInt(genreIdStr);
                    where.add(
                            "EXISTS (" +
                                    "  SELECT 1 " +
                                    "  FROM genres_in_movies gim " +
                                    "  WHERE gim.movieId = m.id AND gim.genreId = ?" +
                                    ")"
                    );
                    // Debug
                    params.add(gid);
                } catch (NumberFormatException nfe) {
                    writeEmpty(out, effective, page, pageSize, sortPrimary, titleDir, ratingDir);
                    return;
                }
            }

            if (startsWith != null) {
                if ("*".equals(startsWith)) {
                    where.add("m.title REGEXP '^[^0-9A-Za-z]'");
                } else {
                    String ch = startsWith.substring(0, 1);
                    where.add("LOWER(m.title) LIKE CONCAT(LOWER(?), '%')");
                    params.add(ch);
                }
            }

            String whereSql = where.isEmpty() ? "" : (" WHERE " + String.join(" AND ", where));

            // 3) Query total count
            int total;
            try (Connection conn = DriverManager.getConnection(loginUrl, loginUser, loginPasswd)) {
                String countSql =
                        "SELECT COUNT(DISTINCT m.id) AS cnt " +
                                "FROM movies m " +
                                "LEFT JOIN ratings r ON r.movieId = m.id " +
                                whereSql;

                try (PreparedStatement cps = conn.prepareStatement(countSql)) {
                    bindParams(cps, params);
                    try (ResultSet crs = cps.executeQuery()) {
                        crs.next();
                        total = crs.getInt("cnt");
                    }
                }

                // Build main SQL with first-3 genres + first-3 stars
                StringBuilder sql = new StringBuilder();
                sql.append("SELECT m.id, m.title, m.year, m.director, r.rating AS rating, ");

                // First three genres:
                sql.append(" ( ");
                sql.append("   SELECT GROUP_CONCAT(CONCAT(t.id, ':', t.name) ORDER BY t.name SEPARATOR ',') ");
                sql.append("   FROM ( ");
                sql.append("     SELECT g.id, g.name ");
                sql.append("     FROM genres_in_movies gim ");
                sql.append("     JOIN genres g ON g.id = gim.genreId ");
                sql.append("     WHERE gim.movieId = m.id ");
                sql.append("     ORDER BY g.name ");
                sql.append("     LIMIT 3 ");
                sql.append("   ) AS t ");
                sql.append(" ) AS genrePairs, ");

                // First three stars:
                sql.append(" ( ");
                sql.append("   SELECT GROUP_CONCAT(CONCAT(t2.id, ':', t2.name) ");
                sql.append("                      ORDER BY t2.movieCount DESC, t2.name ASC SEPARATOR ',') ");
                sql.append("   FROM ( ");
                sql.append("     SELECT s.id, s.name, ");
                sql.append("            (SELECT COUNT(DISTINCT sim2.movieId) ");
                sql.append("             FROM stars_in_movies sim2 ");
                sql.append("             WHERE sim2.starId = s.id) AS movieCount ");
                sql.append("     FROM stars_in_movies sim ");
                sql.append("     JOIN stars s ON s.id = sim.starId ");
                sql.append("     WHERE sim.movieId = m.id ");
                sql.append("     GROUP BY s.id, s.name ");
                sql.append("     ORDER BY movieCount DESC, s.name ASC ");
                sql.append("     LIMIT 3 ");
                sql.append("   ) AS t2 ");
                sql.append(" ) AS starPairs ");

                sql.append("FROM movies m ");
                sql.append("LEFT JOIN ratings r ON r.movieId = m.id ");
                sql.append(whereSql);

                // Sort by title/rating or rating/title
                // ascending or descending order.
                sql.append(" ORDER BY ");
                if ("title".equals(sortPrimary)) {
                    sql.append("LOWER(m.title) ").append(titleDir).append(", ");
                    sql.append("(r.rating IS NULL) ASC, ");
                    sql.append("r.rating ").append(ratingDir).append(" ");
                } else {
                    sql.append("(r.rating IS NULL) ASC, ");
                    sql.append("r.rating ").append(ratingDir).append(", ");
                    sql.append("LOWER(m.title) ").append(titleDir).append(" ");
                }

                // Pagination
                sql.append(" LIMIT ? OFFSET ? ");

                int offset = (page - 1) * pageSize;

                // 5) Execute and stream JSON
                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    int idx = bindParams(ps, params);
                    ps.setInt(idx++, pageSize);
                    ps.setInt(idx, offset);

                    boolean hasPrev = page > 1;
                    boolean hasNext = (page * pageSize) < total;

                    out.print("{");

                    out.print("\"state\":");
                    writeStateJson(out, title, yearStr, director, star, genreIdStr, startsWith,
                            sortPrimary, titleDir, ratingDir, page, pageSize);
                    out.print(",");

                    out.print("\"total\":");
                    out.print(total);
                    out.print(",");

                    out.print("\"hasPrev\":");
                    out.print(hasPrev);
                    out.print(",");

                    out.print("\"hasNext\":");
                    out.print(hasNext);
                    out.print(",");

                    out.print("\"movies\":[");

                    boolean firstMovie = true;
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            if (!firstMovie) out.print(",");
                            firstMovie = false;

                            String movieId = rs.getString("id");
                            String t = rs.getString("title");
                            int y = rs.getInt("year");
                            String dir = rs.getString("director");

                            Number r = (Number) rs.getObject("rating");
                            Double rating = (r == null) ? null : r.doubleValue();

                            String genrePairs = rs.getString("genrePairs");
                            String starPairs = rs.getString("starPairs");

                            out.print("{");

                            out.print("\"id\":\"");
                            out.print(escapeJson(movieId));
                            out.print("\",");

                            out.print("\"title\":\"");
                            out.print(escapeJson(t));
                            out.print("\",");

                            out.print("\"year\":");
                            out.print(y);
                            out.print(",");

                            out.print("\"director\":\"");
                            out.print(escapeJson(dir));
                            out.print("\",");

                            out.print("\"rating\":");
                            if (rating == null) out.print("null");
                            else out.print(rating);
                            out.print(",");

                            // genres/stars are returned as arrays of {id, name}
                            out.print("\"genres\":");
                            writePairsArray(out, genrePairs, true);
                            out.print(",");

                            out.print("\"stars\":");
                            writePairsArray(out, starPairs, false);

                            out.print("}");
                        }
                    }

                    out.print("]}");
                }
            }

        } catch (Exception e) {
            request.getServletContext().log("MovielistServlet error:", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\":\"");
            out.print(escapeJson(e.getMessage()));
            out.print("\"}");
        } finally {
            out.close();
        }
    }

    // Hlp function
    private Map<String, String> resolveState(HttpServletRequest request) {
        // Clear session state
        String reset = request.getParameter("reset");
        if ("1".equals(reset) || "true".equalsIgnoreCase(reset)) {
            request.getSession().removeAttribute(STATE_KEY);
        }

        @SuppressWarnings("unchecked")
        Map<String, String> sessionState =
                (Map<String, String>) request.getSession().getAttribute(STATE_KEY);

        // Detect if the request includes ANY state parameter at all
        boolean hasAny =
                request.getParameter("title") != null ||
                        request.getParameter("year") != null ||
                        request.getParameter("director") != null ||
                        request.getParameter("star") != null ||
                        request.getParameter("genreId") != null ||
                        request.getParameter("startsWith") != null ||
                        request.getParameter("sortPrimary") != null ||
                        request.getParameter("titleDir") != null ||
                        request.getParameter("ratingDir") != null ||
                        request.getParameter("page") != null ||
                        request.getParameter("pageSize") != null;

        // If no params and session exists, use session state
        if (!hasAny && sessionState != null) return sessionState;

        // Otherwise build a new/updated state and store it
        Map<String, String> next = new HashMap<>();
        List<String> keys = Arrays.asList(
                "title", "year", "director", "star",
                "genreId", "startsWith",
                "sortPrimary", "titleDir", "ratingDir",
                "page", "pageSize"
        );

        // Track whether the user changed
        boolean stateChangedExcludingPage =
                request.getParameter("title") != null ||
                        request.getParameter("year") != null ||
                        request.getParameter("director") != null ||
                        request.getParameter("star") != null ||
                        request.getParameter("genreId") != null ||
                        request.getParameter("startsWith") != null ||
                        request.getParameter("sortPrimary") != null ||
                        request.getParameter("titleDir") != null ||
                        request.getParameter("ratingDir") != null ||
                        request.getParameter("pageSize") != null;

        for (String k : keys) {
            String v = request.getParameter(k);

            // If not present on request, fall back to session state (to preserve state)
            if (v == null && sessionState != null) v = sessionState.get(k);

            if (v != null) next.put(k, v);
        }

        // Defaults
        next.putIfAbsent("sortPrimary", "rating");
        next.putIfAbsent("titleDir", "asc");
        next.putIfAbsent("ratingDir", "desc");
        next.putIfAbsent("pageSize", "25");
        next.putIfAbsent("page", "1");

        // If user changed anything except page, and did NOT explicitly send page, reset page to 1
        if (stateChangedExcludingPage && request.getParameter("page") == null) {
            next.put("page", "1");
        }

        request.getSession().setAttribute(STATE_KEY, next);
        return next;
    }

    private static void writeStateJson(PrintWriter out, String title, String year, String director, String star, String genreId, String startsWith,
                                       String sortPrimary, String titleDir, String ratingDir,
                                       int page, int pageSize) {
        out.print("{");

        out.print("\"title\":");
        out.print(jsonOrNull(title));
        out.print(",");

        out.print("\"year\":");
        out.print(jsonOrNull(year));
        out.print(",");

        out.print("\"director\":");
        out.print(jsonOrNull(director));
        out.print(",");

        out.print("\"star\":");
        out.print(jsonOrNull(star));
        out.print(",");

        out.print("\"genreId\":");
        out.print(jsonOrNull(genreId));
        out.print(",");

        out.print("\"startsWith\":");
        out.print(jsonOrNull(startsWith));
        out.print(",");

        out.print("\"sortPrimary\":\"");
        out.print(escapeJson(sortPrimary));
        out.print("\",");

        out.print("\"titleDir\":\"");
        out.print(escapeJson(titleDir));
        out.print("\",");

        out.print("\"ratingDir\":\"");
        out.print(escapeJson(ratingDir));
        out.print("\",");

        out.print("\"page\":");
        out.print(page);
        out.print(",");

        out.print("\"pageSize\":");
        out.print(pageSize);

        out.print("}");
    }

    private static void writePairsArray(PrintWriter out, String pairs, boolean numericId) {
        // pairs format: "1:Action,2:Comedy" or "nm123:Star One,nm999:Star Two"
        out.print("[");
        if (pairs != null && !pairs.trim().isEmpty()) {
            String[] items = pairs.split("\\s*,\\s*");
            boolean first = true;
            for (String item : items) {
                if (item == null || item.isEmpty()) continue;

                String[] parts = item.split(":", 2);
                String id = parts.length > 0 ? parts[0].trim() : "";
                String name = parts.length > 1 ? parts[1].trim() : id;

                if (id.isEmpty()) continue;

                if (!first) out.print(",");
                first = false;

                out.print("{");
                if (numericId) {
                    // genres are numeric ids in moviedb (safe to return as JSON number)
                    String numeric = id.replaceAll("[^0-9]", "");
                    if (numeric.isEmpty()) numeric = "0";
                    out.print("\"id\":");
                    out.print(numeric);
                } else {
                    // stars are strings like "nm123..."
                    out.print("\"id\":\"");
                    out.print(escapeJson(id));
                    out.print("\"");
                }

                out.print(",\"name\":\"");
                out.print(escapeJson(name));
                out.print("\"");

                out.print("}");
            }
        }
        out.print("]");
    }

    private static void writeEmpty(PrintWriter out, Map<String, String> effective,
                                   int page, int pageSize, String sortPrimary, String titleDir, String ratingDir) {
        out.print("{\"state\":");
        writeStateJson(out,
                trimOrNull(effective.get("title")),
                trimOrNull(effective.get("year")),
                trimOrNull(effective.get("director")),
                trimOrNull(effective.get("star")),
                trimOrNull(effective.get("genreId")),
                trimOrNull(effective.get("startsWith")),
                sortPrimary, titleDir, ratingDir, page, pageSize);
        out.print(",\"total\":0,\"hasPrev\":false,\"hasNext\":false,\"movies\":[]}");
    }

    private static int bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        int idx = 1;
        for (Object p : params) {
            if (p instanceof Integer) ps.setInt(idx++, (Integer) p);
            else ps.setString(idx++, String.valueOf(p));
        }
        return idx;
    }

    private static int parsePositiveInt(String s, int def) {
        try {
            int v = Integer.parseInt(String.valueOf(s));
            return v < 1 ? def : v;
        } catch (Exception e) {
            return def;
        }
    }

    private static int parseAllowedPageSize(String s, int def) {
        int v = parsePositiveInt(s, def);
        return ALLOWED_PAGE_SIZES.contains(v) ? v : def;
    }

    private static String normDir(String s) {
        String v = (s == null) ? "" : s.trim().toLowerCase();
        return ("desc".equals(v)) ? "desc" : "asc";
    }

    private static String normSortPrimary(String s) {
        String v = (s == null) ? "" : s.trim().toLowerCase();
        return ("title".equals(v)) ? "title" : "rating";
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private static String jsonOrNull(String s) {
        if (s == null || s.trim().isEmpty()) return "null";
        return "\"" + escapeJson(s) + "\"";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }
}
