package movies;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

// Measure TS for /movielist requests and logs
@WebFilter(filterName = "movies.SearchTimingFilter", urlPatterns = {"/movielist"})
public class SearchTimingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        long start = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            long ts = System.nanoTime() - start;

            long tj = -1L;
            Object attr = req.getAttribute("TJ_NANOS");
            if (attr instanceof Long) tj = (Long) attr;

            HttpServletRequest r = (req instanceof HttpServletRequest) ? (HttpServletRequest) req : null;
            String uri = (r != null) ? r.getRequestURI() : "";
            String title = (r != null) ? r.getParameter("title") : "";
            if (title == null) title = "";
            title = title.replaceAll("\\s+", " ").trim();

            // One line per request, nanoseconds
            TimingLogger.logLine("TS=" + ts + " TJ=" + tj + " uri=" + uri + " title=" + title);
        }
    }
}