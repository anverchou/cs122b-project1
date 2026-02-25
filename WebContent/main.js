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

// Sign out button initialization
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

    let selectedMovieId = null;

    // Autocomplete handler
    window.__setSelectedMovieId = (movieId) => {
        selectedMovieId = movieId || null;
    };

    // Clear selection if the user edits the title after selecting something
    const titleInput = document.getElementById("autocomplete");
    if (titleInput) {
        titleInput.addEventListener("input", () => {
            selectedMovieId = null;
        });
    }

    form.addEventListener("submit", (e) => {
        e.preventDefault();

        // Go to single movie if an auto completed option was selected
        if (selectedMovieId) {
            window.location.href = `singlemovie.html?id=${encodeURIComponent(selectedMovieId)}`;
            return;
        }

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

        // Clear old session filters
        params.set("reset", "1");
        params.set("page", "1");

        // If user submits with nothing filled, go to Top 20
        const qs = params.toString();
        window.location.href = qs ? `movielist.html?${qs}` : `movielist.html`;
    });
}

// Autocomplete on main title search box
function setupAutocomplete() {
    // Autocomplete library requires jQuery
    if (typeof window.jQuery === "undefined") return;

    const $input = window.jQuery("#autocomplete");
    if ($input.length === 0) return;

    const CACHE_PREFIX = "ac_suggestions_";

    function cacheKey(q) {
        return CACHE_PREFIX + String(q || "").trim().toLowerCase();
    }

    function readCache(q) {
        try {
            const raw = sessionStorage.getItem(cacheKey(q));
            if (!raw) return null;
            const parsed = JSON.parse(raw);
            return Array.isArray(parsed) ? parsed : null;
        } catch (_) {
            return null;
        }
    }

    function writeCache(q, list) {
        try {
            sessionStorage.setItem(cacheKey(q), JSON.stringify(list || []));
        } catch (_) {
            // Ignore quota errors
        }
    }

    $input.autocomplete({
        minChars: 3,
        deferRequestBy: 300,
        lookup: function (query, doneCallback) {
            // Autocomplete search is completed
            console.log("Autocomplete search initiated");

            const q = String(query || "").trim();
            if (q.length < 3) {
                doneCallback({ suggestions: [] });
                return;
            }

            const cached = readCache(q);
            if (cached) {
                // Using cached results
                console.log("Autocomplete using cached results");
                console.log(cached);
                doneCallback({ suggestions: cached });
                return;
            }

            // Sending AJAX request to the server
            console.log("Autocomplete sending AJAX request");

            window.jQuery.ajax({
                method: "GET",
                url: `${getContextPath()}/api/autocomplete?query=${encodeURIComponent(q)}`,
                dataType: "json",
                success: function (data) {
                    const list = Array.isArray(data) ? data.slice(0, 10) : [];
                    writeCache(q, list);
                    console.log(list);
                    doneCallback({ suggestions: list });
                },
                error: function (xhr) {
                    // If session expired, kick to login.
                    if (xhr && xhr.status === 401) {
                        window.location.replace("login.html");
                        return;
                    }
                    doneCallback({ suggestions: [] });
                }
            });
        },
        onSelect: function (suggestion) {
            const movieId = suggestion?.data?.movieId;
            if (!movieId) return;

            if (typeof window.__setSelectedMovieId === "function") {
                window.__setSelectedMovieId(movieId);
            }
            // jump directly to the single movie page
            window.location.href = `singlemovie.html?id=${encodeURIComponent(movieId)}`;
        }
    });
}

// Fetch the list of all genres
async function loadGenres() {
    const wrap = document.getElementById("genres");
    const hint = document.getElementById("genres_hint");
    if (!wrap) return;

    wrap.innerHTML = `<span class="chip">Loading…</span>`;
    if (hint) hint.textContent = "";

    // Check if user is logged in
    try {
        const url = `${getContextPath()}/api/genres`;

        const res = await fetch(url, { headers: { "Accept": "application/json" } });

        // If not logged in
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
            `<a class="chip" href="movielist.html?reset=1&genreId=${encodeURIComponent(g.id)}">${esc(g.name)}</a>`
        ).join("");

    } catch (e) {
        wrap.innerHTML = `<span class="chip">Failed to load genres</span>`;
        if (hint) hint.textContent = "Check Console for details.";
        console.log("Genres fetch exception:", e);
    }
}

// Clear Search of previous filters
function clearSearchUI() {
    const form = document.getElementById("search_form");
    if (form) form.reset();
}

async function clearMovielistStateIfRequested() {
    const params = new URLSearchParams(window.location.search);
    if (params.get("reset") !== "1") return;

    // 1) Clear the visible inputs
    clearSearchUI();

    // 2) Clear server-side movielist session
    try {
        await fetch(`${getContextPath()}/movielist?reset=1`, {
            headers: { "Accept": "application/json" }
        });
        // Ignore errors; state will still be cleared on next load path
    } catch (_) {
    }

    params.delete("reset");
    const qs = params.toString();
    history.replaceState({}, "", qs ? `${window.location.pathname}?${qs}` : window.location.pathname);
}

// Browse by movie title requirement
function renderTitleBrowse() {
    const wrap = document.getElementById("title_browse");
    if (!wrap) return;

    const chars = [];
    for (let i = 0; i <= 9; i++) chars.push(String(i));
    for (let c = 65; c <= 90; c++) chars.push(String.fromCharCode(c));
    chars.push("*");

    wrap.innerHTML = chars
        .map(ch => `<a class="chip" href="movielist.html?startsWith=${encodeURIComponent(ch)}&reset=1">${esc(ch)}</a>`)
        .join("");
}

// Initialize Content
document.addEventListener("DOMContentLoaded", () => {
    clearMovielistStateIfRequested();
    setupLogout();
    setupSearchForm();
    setupAutocomplete();
    renderTitleBrowse();
    loadGenres();
});