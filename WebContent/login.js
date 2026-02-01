let login_form = $("#login_form");
/*
    * - Intercepts the login form submit event and performs an AJAX POST
    * - If login succeeds, redirects the user to main.html.
   */
function submitLoginForm(e) {
    // Prevent default form submission
    e.preventDefault();

    // POST to /api/login (relative to your app context)
    const url = "api/login";

    // Send AJAX query
    $.ajax(url, {
        method: "POST",
        data: login_form.serialize(),
        dataType: "json",
        // Success when server responds with HTTP 2xx
        success: (res) => {
            if (res.status === "success") {
                window.location.replace("main.html");
            } else {
                $("#login_error_message").text(res.message || "Login failed");
            }
        },
        // Not success when HTTP is not 2xx
        error: (xhr) => {
            console.log("Login error:", xhr.status, xhr.responseText);
            $("#login_error_message").text(`Login request failed (HTTP ${xhr.status})`);
        }
    });
}
login_form.off("submit").on("submit", submitLoginForm);
