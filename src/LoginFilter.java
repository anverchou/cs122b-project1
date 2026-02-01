import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;

@WebFilter(filterName = "LoginFilter", urlPatterns = "/*")
public class LoginFilter implements Filter {
    /**
     *   1) allow the request to continue,
     *   2) redirect the user to the login page (for HTML/pages), or
     *   3) return HTTP 401 (for API/AJAX requests).
    */
    // List of URI suffixes
    private final ArrayList<String> allowedURIs = new ArrayList<>();

    // Initialize filter to popular allowedURIs
    @Override
    public void init(FilterConfig filterConfig) {
        allowedURIs.add("login.html");
        allowedURIs.add("login.js");
        allowedURIs.add("api/login");
        allowedURIs.add("api/logout");
    }

    // Filter for incoming requests
    /**
     * 1) Check if the user is logged in via session attribute "user".
     * 2) If logged in, allow it through.
     * 3) If not logged in:
     *    - If it's an API request, return 401 JSON
     *    - Else redirect to login.html
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        // Paths
        String contextPath = req.getContextPath();
        String uri = req.getRequestURI();
        String path = uri.substring(contextPath.length());

        // Allow login assets/endpoint
        if (isAllowedWithoutLogin(path)) {
            chain.doFilter(request, response);
            return;
        }

        // If user is not logged in
        boolean loggedIn = (req.getSession().getAttribute("user") != null);
        if (loggedIn) {
            chain.doFilter(request, response);
            return;
        }

        boolean isApi =
                path.equals("/movielist") ||
                        path.equals("/singlemovie") ||
                        path.equals("/singlestar") ||
                        path.startsWith("/api/");

        // Return JSON error for unauthorized API access
        if (isApi) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType("application/json");
            res.setCharacterEncoding("UTF-8");
            res.getWriter().write("{\"status\":\"fail\",\"message\":\"not logged in\"}");
        } else {
            // Redirect browser to login page
            res.sendRedirect("login.html");
        }
    }

    // Check if a path is accessible without being logged in
    private boolean isAllowedWithoutLogin(String path) {
        String lower = path.toLowerCase();
        return allowedURIs.stream().anyMatch(lower::endsWith);
    }
}
