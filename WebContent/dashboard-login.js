function getContextPath() {
    const path = window.location.pathname;
    const i = path.indexOf("/", 1);
    return i === -1 ? "" : path.substring(0, i);
}

function esc(s) {
    return String(s ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;");
}

$(function () {
    const ctx = getContextPath();
    const err = $("#employee_login_error");

    $("#employee_login_form").submit(function (e) {
        e.preventDefault();
        err.text("");

        const form = $(this);
        const btn = form.find("button[type=submit]");
        btn.prop("disabled", true);

        $.ajax(ctx + "/api/employee-login", {
            method: "POST",
            dataType: "json",
            data: form.serialize(),
            success: function (res) {
                if (res.status === "success") {
                    window.location.replace(ctx + "/_dashboard");
                } else {
                    err.text(res.message || "Login failed.");
                }
            },
            error: function (xhr) {
                err.text("Server error: " + (xhr.responseText || xhr.status));
            },
            complete: function () {
                btn.prop("disabled", false);
            }
        });
    });
});
