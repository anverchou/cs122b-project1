import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Entry point for the employee dashboard.
 *
 * - Forces HTTPS
 * - Redirects to dashboard-login.html if employee is not logged in
 * - Redirects to dashboard.html if employee is logged in
 */
@WebServlet(name = "DashboardEntryServlet", urlPatterns = "/_dashboard")
public class DashboardEntryServlet extends HttpServlet {
    /**
     *
     *  1) If the incoming request is not secure, redirect toHTTPS
     *  2) Otherwise, check whether the employee is logged in
     *  3) Redirect to the appropriate dashboard page:
     *      - Logged in  -> dashboard.html
     *      - Not logged -> dashboard-login.html
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Force HTTPS
        if (!request.isSecure()) {
            String host = request.getServerName();
            String uri = request.getRequestURI();
            String qs = request.getQueryString();
            String target = "https://" + host + ":8443" + uri + (qs != null ? ("?" + qs) : "");
            response.sendRedirect(target);
            return;
        }

        // If already HTTPS
        String ctx = request.getContextPath();
        boolean employeeLoggedIn = request.getSession().getAttribute("employee") != null;
        if (employeeLoggedIn) {
            // Logged in employee
            response.sendRedirect(ctx + "/dashboard.html");
        } else {
            // Not logged in employee redirect
            response.sendRedirect(ctx + "/dashboard-login.html");
        }
    }
}
