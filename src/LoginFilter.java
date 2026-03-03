import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@WebFilter(filterName = "LoginFilter", urlPatterns = "/*")
public class LoginFilter implements Filter {
    /**
     *   1) allow the request to continue,
     *   2) redirect the user to the login page (for HTML/pages), or
     *   3) return HTTP 401
     */
    // List of URI suffixes
    private final Set<String> customerAllowedSuffixes = new HashSet<>();
    private final Set<String> dashboardAllowedSuffixes = new HashSet<>();

    // Initalize Filter
    @Override
    public void init(FilterConfig filterConfig) {
        // Public pages/assets/endpoints
        customerAllowedSuffixes.add("/login.html");
        customerAllowedSuffixes.add("/login.js");
        customerAllowedSuffixes.add("/api/login");
        customerAllowedSuffixes.add("/api/logout");
        customerAllowedSuffixes.add("/movielist");
        customerAllowedSuffixes.add("/singlemovie");
        customerAllowedSuffixes.add("/singlestar");

        dashboardAllowedSuffixes.add("/_dashboard");
        dashboardAllowedSuffixes.add("/dashboard-login.html");
        dashboardAllowedSuffixes.add("/dashboard-login.js");

        dashboardAllowedSuffixes.add("/api/employee-login");
        dashboardAllowedSuffixes.add("/api/employee-logout");
        dashboardAllowedSuffixes.add("/api/employee-status");
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

        // If this is employee dashboard area
        if (isDashboardArea(path)) {

            // Allow dashboard public entry points + employee login endpoints
            if (isAllowed(path, dashboardAllowedSuffixes)) {
                chain.doFilter(request, response);
                return;
            }

            boolean employeeLoggedIn = (req.getSession().getAttribute("employee") != null);
            if (employeeLoggedIn) {
                chain.doFilter(request, response);
                return;
            }

            // Not logged in as employee
            if (isApiRequest(path)) {
                send401Json(res);
            } else {
                // Send them to the employee dashboard entry point
                res.sendRedirect(contextPath + "/_dashboard");
            }
            return;
        }

        // 2) Otherwise, this is the customer site rules
        if (isAllowed(path, customerAllowedSuffixes)) {
            chain.doFilter(request, response);
            return;
        }

        boolean userLoggedIn = (req.getSession().getAttribute("user") != null);
        if (userLoggedIn) {
            chain.doFilter(request, response);
            return;
        }

        // Not logged in as customer
        if (isApiRequest(path)) {
            send401Json(res);
        } else {
            res.sendRedirect(contextPath + "/login.html");
        }
    }

    private boolean isAllowed(String path, Set<String> allowedSuffixes) {
        String lower = (path == null) ? "" : path.toLowerCase();
        for (String suffix : allowedSuffixes) {
            if (lower.endsWith(suffix.toLowerCase())) return true;
        }
        return false;
    }

    // Employee dashboard
    private boolean isDashboardArea(String path) {
        String lower = (path == null) ? "" : path.toLowerCase();

        return lower.equals("/_dashboard")
                || lower.equals("/dashboard.html")
                || lower.equals("/dashboard.js")
                || lower.equals("/dashboard-login.html")
                || lower.equals("/dashboard-login.js")
                || lower.startsWith("/api/employee")
                || lower.startsWith("/api/dashboard");
    }

    private boolean isApiRequest(String path) {
        return path != null && (
                path.startsWith("/api/")
                        || path.equals("/movielist")
                        || path.equals("/singlemovie")
                        || path.equals("/singlestar")
        );
    }

    private void send401Json(HttpServletResponse res) throws IOException {
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"status\":\"fail\",\"message\":\"not logged in\"}");
    }

    @Override
    public void destroy() {}
}
