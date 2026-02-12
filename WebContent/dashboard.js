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

function setMsg($el, kind, text) {
    $el.removeClass("ok bad");
    if (kind) $el.addClass(kind);
    $el.text(text || "");
}

$(function () {
    const ctx = getContextPath();

    // Show who is logged in (optional)
    $.ajax(ctx + "/api/employee-status", {
        method: "GET",
        dataType: "json",
        success: function (res) {
            if (res.status === "success") {
                const label = (res.fullname && res.fullname.trim()) ? res.fullname : res.email;
                $("#employee_name").text("Logged in as " + label);
            }
        }
    });

    // Logout
    $("#logout_btn").click(function () {
        $.ajax(ctx + "/api/employee-logout", {
            method: "POST",
            dataType: "json",
            complete: function () {
                window.location.replace(ctx + "/_dashboard");
            }
        });
    });

    // Add Star
    const $starMsg = $("#add_star_msg");
    $("#add_star_form").submit(function (e) {
        e.preventDefault();
        setMsg($starMsg, "", "");

        $.ajax(ctx + "/api/dashboard/add-star", {
            method: "POST",
            dataType: "json",
            data: $(this).serialize(),
            success: function (res) {
                if (res.status === "success") {
                    const text = (res.messages && Array.isArray(res.messages) && res.messages.length)
                        ? res.messages.join("\n")
                        : (res.message || "Star added.");
                    setMsg($starMsg, "ok", text);
                    $("#add_star_form")[0].reset();
                } else {
                    setMsg($starMsg, "bad", res.message || "Failed to add star.");
                }
            },
            error: function (xhr) {
                setMsg($starMsg, "bad", "Server error: " + (xhr.responseText || xhr.status));
            }
        });
    });

    // Add Movie
    const $movieMsg = $("#add_movie_msg");
    $("#add_movie_form").submit(function (e) {
        e.preventDefault();
        setMsg($movieMsg, "", "");

        $.ajax(ctx + "/api/dashboard/add-movie", {
            method: "POST",
            dataType: "json",
            data: $(this).serialize(),
            success: function (res) {
                if (res.status === "success") {
                    const text = (res.messages && Array.isArray(res.messages) && res.messages.length)
                        ? res.messages.join("\n")
                        : (res.message || "Movie added.");
                    setMsg($movieMsg, "ok", text);
                    $("#add_movie_form")[0].reset();
                } else {
                    const text = (res.messages && Array.isArray(res.messages) && res.messages.length)
                        ? res.messages.join("\n")
                        : (res.message || "Failed to add movie.");
                    setMsg($movieMsg, "bad", text);
                }
            },
            error: function (xhr) {
                setMsg($movieMsg, "bad", "Server error: " + (xhr.responseText || xhr.status));
            }
        });
    });

    // Metadata
    const $metaMsg = $("#meta_msg");
    const $metaBox = $("#meta_box");

    function renderMetadata(tables) {
        let html = "";
        for (const t of tables) {
            html += `<div class="tableTitle">${esc(t.name)}</div>`;
            html += `<table><thead><tr><th>Column</th><th>Type</th></tr></thead><tbody>`;
            for (const c of (t.columns || [])) {
                html += `<tr><td>${esc(c.name)}</td><td>${esc(c.type)}</td></tr>`;
            }
            html += `</tbody></table>`;
        }
        $metaBox.html(html || "<div class='muted'>No metadata returned.</div>");
    }

    function loadMetadata() {
        setMsg($metaMsg, "", "");
        $metaBox.html("Loading...");

        $.ajax(ctx + "/api/dashboard/metadata", {
            method: "GET",
            dataType: "json",
            success: function (res) {
                if (res.status === "success") {
                    renderMetadata(res.tables || []);
                    setMsg($metaMsg, "ok", "Loaded metadata.");
                } else {
                    $metaBox.html("");
                    setMsg($metaMsg, "bad", res.message || "Failed to load metadata.");
                }
            },
            error: function (xhr) {
                $metaBox.html("");
                setMsg($metaMsg, "bad", "Server error: " + (xhr.responseText || xhr.status));
            }
        });
    }

    $("#refresh_meta").click(loadMetadata);
    loadMetadata();
});
