import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@WebFilter(filterName = "LoginFilter", urlPatterns = "/*")
public class LoginFilter implements Filter {
    private final Set<String> allowedSuffixes = new HashSet<>();

    @Override
    public void init(FilterConfig filterConfig) {
        allowedSuffixes.add("/login.html");
        allowedSuffixes.add("/login.js");
        allowedSuffixes.add("/api/login");
        allowedSuffixes.add("/api/logout");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String contextPath = req.getContextPath();
        String uri = req.getRequestURI();
        String path = uri.substring(contextPath.length());

        if (isAllowed(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Check Redis for user session
        String sessionId = req.getSession().getId();
        String user = RedisUtil.getSessionAttribute(sessionId, "user");

        if (user != null) {
            chain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/api/")) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType("application/json");
            res.setCharacterEncoding("UTF-8");
            res.getWriter().write("{\"status\":\"fail\",\"message\":\"not logged in\"}");
        } else {
            res.sendRedirect(contextPath + "/login.html");
        }
    }

    private boolean isAllowed(String path) {
        String lower = (path == null) ? "" : path.toLowerCase();
        for (String suffix : allowedSuffixes) {
            if (lower.endsWith(suffix.toLowerCase())) return true;
        }
        return false;
    }

    @Override
    public void destroy() {}
}