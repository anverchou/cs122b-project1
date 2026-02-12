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
     *   3) return HTTP 401
     */
    // List of URI suffixes
    private final ArrayList<String> allowedURIs = new ArrayList<>();

    // Initialize filter
    @Override
    public void init(FilterConfig filterConfig) {
        // Public pages/assets
        allowedURIs.add("login.html");
        allowedURIs.add("login.js");

        // Public endpoints
        allowedURIs.add("api/login");
        allowedURIs.add("api/logout");

        // Employee dashboard public entry points
        allowedURIs.add("_dashboard");
        allowedURIs.add("dashboard-login.html");
        allowedURIs.add("dashboard-login.js");
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

        // Employee dashboard
        if (isDashboardArea(path)) {
            // Allow the dashboard entry
            if (isAllowedWithoutLogin(path)) {
                chain.doFilter(request, response);
                return;
            }

            boolean employeeLoggedIn = (req.getSession().getAttribute("employee") != null);
            if (employeeLoggedIn) {
                chain.doFilter(request, response);
                return;
            }

            boolean isApi = path.startsWith("/api/");
            if (isApi) {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.setContentType("application/json");
                res.setCharacterEncoding("UTF-8");
                res.getWriter().write("{\"status\":\"fail\",\"message\":\"not logged in\"}");
            } else {
                res.sendRedirect(contextPath + "/_dashboard");
            }
            return;
        }

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

        // Decide whether this request is an API/AJAX call:
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
            // Redirect browser to login page for normal page navigation
            res.sendRedirect(contextPath + "/login.html");
        }
    }

    // Check if a path is accessible without being logged in
    private boolean isAllowedWithoutLogin(String path) {
        // Normalize and check suffix matches
        String lower = path.toLowerCase();
        return allowedURIs.stream().anyMatch(lower::endsWith);
    }

    // Dashboard
    private boolean isDashboardArea(String path) {
        String lower = (path == null) ? "" : path.toLowerCase();
        return lower.equals("/_dashboard")
                || lower.endsWith("/dashboard.html")
                || lower.endsWith("/dashboard.js")
                || lower.endsWith("/dashboard-login.html")
                || lower.endsWith("/dashboard-login.js")
                || lower.startsWith("/api/employee")
                || lower.startsWith("/api/dashboard");
    }

    @Override
    public void destroy()  {}
}
