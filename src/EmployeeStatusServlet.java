import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

// Provide endpoint for dhasboard UI to check whether employee is logged in
@WebServlet(name = "EmployeeStatusServlet", urlPatterns = "/api/employee-status")
public class EmployeeStatusServlet extends HttpServlet {
    /**
     *  1) Set JSON response headers.
     *  2) Check for session attribute "employee".
     *      - If missing: return HTTP 401 with a JSON fail message.
     *  3) Read optional session attribute "employee_name".
     *  4) Return JSON containing email + fullname.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Determine login state
        Object email = request.getSession().getAttribute("employee");
        if (email == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"status\":\"fail\",\"message\":\"not logged in\"}");
            return;
        }

        // Return session info
        String fullname = (String) request.getSession().getAttribute("employee_name");
        if (fullname == null) fullname = "";

        response.getWriter().write(
                "{\"status\":\"success\",\"email\":\"" + escapeJson(String.valueOf(email)) + "\",\"fullname\":\"" + escapeJson(fullname) + "\"}"
        );
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
