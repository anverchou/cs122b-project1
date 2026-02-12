import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(name = "EmployeeLogoutServlet", urlPatterns = "/api/employee-logout")
public class EmployeeLogoutServlet extends HttpServlet {
    /**  - Logs an employee out of dashboard by clearing employee-related session attributes.
    *  - After logout, dashboard endpoints that require authentication
    */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Clear authetnication state
        request.getSession().removeAttribute("employee");
        request.getSession().removeAttribute("employee_name");

        // Success
        response.getWriter().write("{\"status\":\"success\",\"message\":\"logged out\"}");
    }
}
