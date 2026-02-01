/*
    * - Escapes user/data strings before inserting them into HTML via innerHTML/template strings.
    * - Prevents HTML injection by converting special characters into HTML entities.
*/
function esc(s) {
    return String(s ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;");
}

// Return the context path for calls
function getContextPath() {
    const path = window.location.pathname;
    const i = path.indexOf("/", 1);
    return i === -1 ? "" : path.substring(0, i);
}

// Sign out button initalization
function setupLogout() {
    const btn = document.getElementById("logout_btn");
    if (!btn) return;

    btn.addEventListener("click", async () => {
        try {
            const url = `${getContextPath()}/api/logout`;
            const res = await fetch(url, { method: "POST" });

            // If something blocks it, still redirect
            if (!res.ok) console.log("Logout failed HTTP", res.status, await res.text());
        } catch (e) {
            console.log("Logout exception:", e);
        }
        // Return back to home page
        window.location.replace("login.html");
    });
}

// Search form submission to navigate user to movielist with filters
function setupSearchForm() {
    const form = document.getElementById("search_form");
    if (!form) return;

    form.addEventListener("submit", (e) => {
        e.preventDefault();

        const fd = new FormData(form);
        const params = new URLSearchParams();

        for (const [k, v] of fd.entries()) {
            const val = String(v ?? "").trim();
            if (!val) continue;

            if (k === "year") {
                // exact year only
                if (!/^\d+$/.test(val)) {
                    alert("Year must be a number (e.g., 1999).");
                    return;
                }
                params.set("year", val);
            } else {
                params.set(k, val);
            }
        }

        // If user submits with nothing filled, go to Top 20
        const qs = params.toString();
        window.location.href = qs ? `movielist.html?${qs}` : `movielist.html`;
    });
}

// Fetch the list of all genres
async function loadGenres() {
    const wrap = document.getElementById("genres");
    const hint = document.getElementById("genres_hint");
    if (!wrap) return;

    wrap.innerHTML = `<span class="chip">Loading…</span>`;
    if (hint) hint.textContent = "";

    // Check if user is loggd in
    try {
        const url = `${getContextPath()}/api/genres`;

        const res = await fetch(url, { headers: { "Accept": "application/json" } });

        // If not loggd in
        if (res.status === 401) {
            window.location.replace("login.html");
            return;
        }

        // Show error for bad responses
        if (!res.ok) {
            const t = await res.text();
            wrap.innerHTML = `<span class="chip">Failed (HTTP ${res.status})</span>`;
            if (hint) hint.textContent = "Open DevTools → Network → api/genres";
            console.log("Genres HTTP error:", res.status, t);
            return;
        }

        const genres = await res.json();

        // Handle empty lists
        if (!Array.isArray(genres) || genres.length === 0) {
            wrap.innerHTML = `<span class="chip">No genres</span>`;
            return;
        }

        genres.sort((a, b) => String(a.name).localeCompare(String(b.name)));

        wrap.innerHTML = genres.map(g =>
            `<a class="chip" href="movielist.html?genreId=${encodeURIComponent(g.id)}">${esc(g.name)}</a>`
        ).join("");

    } catch (e) {
        wrap.innerHTML = `<span class="chip">Failed to load genres</span>`;
        if (hint) hint.textContent = "Check Console for details.";
        console.log("Genres fetch exception:", e);
    }
}

// Initalize Content
document.addEventListener("DOMContentLoaded", () => {
    setupLogout();
    setupSearchForm();
    loadGenres();
});
