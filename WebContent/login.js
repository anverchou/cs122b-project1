let login_form = $("#login_form");
/*
    * - Intercepts the login form submit event and performs an AJAX POST
    * - If login succeeds, redirects the user to main.html.
   */
function submitLoginForm(e) {
    // Prevent default form submission
    e.preventDefault();

    // Completed Captcha
    if (typeof grecaptcha !== "undefined") {
        const token = grecaptcha.getResponse();
        if (!token) {
            $("#login_error_message").text("Please complete the reCAPTCHA.");
            return;
        }
    }

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
                return;
            }

            $("#login_error_message").text(res.message || "Login failed");

            if (typeof grecaptcha !== "undefined") {
                grecaptcha.reset();
            }
        },
        // Not success when HTTP is not 2xx
        error: (xhr) => {
            console.log("Login error:", xhr.status, xhr.responseText);

            let msg = `Login request failed (HTTP ${xhr.status})`;
            try {
                const j = JSON.parse(xhr.responseText);
                if (j && j.message) msg = j.message;
            } catch (e) {}

            $("#login_error_message").text(msg);

            // Reset reCAPTCHA
            if (typeof grecaptcha !== "undefined") {
                grecaptcha.reset();
            }
        }
    });
}
login_form.off("submit").on("submit", submitLoginForm);
