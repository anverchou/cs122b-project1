import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/*
 * Logs the current user out by destroying HTTP session.
 * After invalidation, any session-based login marker is removed
 * Protected endpoints/pages will be blocked by LoginFilter until another valid login
 */
@WebServlet(name = "LogoutServlet", urlPatterns = "/api/logout")
public class LogoutServlet extends HttpServlet {
    /*
       1) Invalidate the session
       2) Return a small JSON response indicating success.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Destroy session
        request.getSession().invalidate();

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":\"success\"}");
    }
}
