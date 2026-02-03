// Get current URL
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

function escAttr(s) {
    // Escape HTML and single quotes for safe
    return esc(s).replaceAll("'", "&#39;");
}

// Always call the servlet with the current query string
function buildApiUrl() {
    return `${getContextPath()}/movielist${window.location.search}`;
}

function flashStatus(msg, isError = false) {
    const statusEl = document.getElementById("status");
    if (!statusEl) return;

    statusEl.style.display = "";
    statusEl.style.color = isError ? "crimson" : "";
    statusEl.textContent = msg;

    // Auto-clear only for non-error messages
    if (!isError) {
        window.setTimeout(() => {
            if (statusEl.textContent === msg) {
                statusEl.textContent = "";
                statusEl.style.display = "none";
                statusEl.style.color = "";
            }
        }, 1200);
    }
}

// Format rating to one decimal digit
function formatRating(r) {
    if (r === null || r === undefined) return "N/A";
    const n = Number(r);
    return Number.isFinite(n) ? n.toFixed(1) : esc(r);
}

// Render the Genres
function renderGenres(genres) {
    if (!genres) return "N/A";

    // array-of-objects case
    if (Array.isArray(genres)) {
        if (genres.length === 0) return "N/A";
        return genres
            .map(g => {
                const id = g?.id;
                const name = g?.name ?? "N/A";
                // filter by genre
                if (id != null) {
                    return `<a href="movielist.html?genreId=${encodeURIComponent(id)}&page=1">${esc(name)}</a>`;
                }
                return esc(name);
            })
            .join(", ");
    }

    const s = String(genres).trim();
    return s ? esc(s) : "N/A";
}

// Render the stars
function renderStars(stars) {
    if (!stars) return "N/A";

    // array-of-objects case
    if (Array.isArray(stars)) {
        if (stars.length === 0) return "N/A";
        return stars
            .map(s => {
                const id = s?.id ?? "";
                const name = s?.name ?? s?.id ?? "N/A";
                if (!id) return esc(name);
                return `<a href="singlestar.html?id=${encodeURIComponent(id)}">${esc(name)}</a>`;
            })
            .join(", ");
    }

    if (typeof stars === "string" && stars.trim() && stars !== "N/A") {
        return stars
            .split(",")
            .map(x => x.trim())
            .filter(Boolean)
            .map(p => {
                if (!p.includes(":")) return esc(p);
                const [id, name] = p.split(":", 2);
                const sid = (id ?? "").trim();
                const sname = ((name ?? id) ?? "").trim();
                if (!sid) return esc(sname);
                return `<a href="singlestar.html?id=${encodeURIComponent(sid)}">${esc(sname)}</a>`;
            })
            .join(", ");
    }

    return "N/A";
}

async function addToCart(movieId) {
    const url = `${getContextPath()}/api/cart`;
    const body = new URLSearchParams({ action: "add", movieId });

    const res = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body
    });

    if (res.status === 401) {
        window.location.replace("login.html");
        return;
    }

    if (!res.ok) {
        const t = await res.text();
        throw new Error(`HTTP ${res.status}: ${t.slice(0, 200)}`);
    }

    const data = await res.json().catch(() => ({}));
    if (data && data.status === "success") {
        flashStatus("Added to cart!");
    } else {
        flashStatus("Add to cart failed.", true);
    }
}

let currentState = { page: 1, pageSize: 25, sortPrimary: "rating", titleDir: "asc", ratingDir: "desc" };

function getPageFromUrl() {
    const p = parseInt(new URLSearchParams(window.location.search).get("page") || "1", 10);
    return Number.isFinite(p) && p > 0 ? p : 1;
}

function updateLocation(changes) {
    const params = new URLSearchParams(window.location.search);

    // Keep these in the URL so paging/sorting remains stable across reloads.
    if (!params.has("pageSize")) params.set("pageSize", String(currentState.pageSize || 25));
    if (!params.has("sortPrimary")) params.set("sortPrimary", String(currentState.sortPrimary || "rating"));
    if (!params.has("titleDir")) params.set("titleDir", String(currentState.titleDir || "asc"));
    if (!params.has("ratingDir")) params.set("ratingDir", String(currentState.ratingDir || "desc"));

    // Apply requested changes
    for (const [k, v] of Object.entries(changes)) {
        if (v === null || v === undefined || String(v) === "") params.delete(k);
        else params.set(k, String(v));
    }

    const qs = params.toString();
    window.location.href = qs ? `movielist.html?${qs}` : `movielist.html`;
}

// Toolbar for sorting/filters
function setupControls() {
    const sortSel = document.getElementById("sort_select");
    const dirSel = document.getElementById("dir_select");
    const pageSizeSel = document.getElementById("page_size_select");
    const prevBtn = document.getElementById("prev_btn");
    const nextBtn = document.getElementById("next_btn");

    if (sortSel) {
        sortSel.addEventListener("change", () => {
            const dir = dirSel ? dirSel.value : "desc";
            if (sortSel.value === "title_rating") {
                updateLocation({ sortPrimary: "title", titleDir: dir, ratingDir: "desc", page: 1 });
            } else {
                updateLocation({ sortPrimary: "rating", ratingDir: dir, titleDir: "asc", page: 1 });
            }
        });
    }

    if (dirSel) {
        dirSel.addEventListener("change", () => {
            const dir = dirSel.value;
            const sort = sortSel ? sortSel.value : "rating_title";
            if (sort === "title_rating") {
                updateLocation({ sortPrimary: "title", titleDir: dir, page: 1 });
            } else {
                updateLocation({ sortPrimary: "rating", ratingDir: dir, page: 1 });
            }
        });
    }

    if (pageSizeSel) {
        pageSizeSel.addEventListener("change", () => {
            updateLocation({ pageSize: pageSizeSel.value, page: 1 });
        });
    }

    if (prevBtn) {
        prevBtn.addEventListener("click", () => {
            const p = Math.max(1, getPageFromUrl() - 1);
            updateLocation({ page: p });
        });
    }

    if (nextBtn) {
        nextBtn.addEventListener("click", () => {
            const p = getPageFromUrl() + 1;
            updateLocation({ page: p });
        });
    }
}

function syncControls(state, hasPrev, hasNext) {
    const sortSel = document.getElementById("sort_select");
    const dirSel = document.getElementById("dir_select");
    const pageSizeSel = document.getElementById("page_size_select");
    const prevBtn = document.getElementById("prev_btn");
    const nextBtn = document.getElementById("next_btn");
    const pageLabel = document.getElementById("page_label");

    if (sortSel) sortSel.value = (state.sortPrimary === "title") ? "title_rating" : "rating_title";

    const shownDir = (state.sortPrimary === "title") ? state.titleDir : state.ratingDir;
    if (dirSel) dirSel.value = (shownDir === "desc") ? "desc" : "asc";

    if (pageSizeSel) pageSizeSel.value = String(state.pageSize ?? 25);

    if (pageLabel) pageLabel.textContent = `Page ${state.page ?? 1}`;
    if (prevBtn) prevBtn.disabled = !hasPrev;
    if (nextBtn) nextBtn.disabled = !hasNext;
}

// Load the movielist
async function loadMovies() {
    const statusEl = document.getElementById("status");
    const tbody = document.getElementById("movie_table_body");

    if (!statusEl || !tbody) return;

    statusEl.style.display = "";
    statusEl.style.color = "";
    statusEl.textContent = "Loading…";
    tbody.innerHTML = "";

    try {
        const API_URL = buildApiUrl();
        const res = await fetch(API_URL, { headers: { "Accept": "application/json" } });

        if (res.status === 401) {
            window.location.replace("login.html");
            return;
        }

        if (!res.ok) {
            const t = await res.text();
            throw new Error(`HTTP ${res.status}: ${t.slice(0, 200)}`);
        }

        const data = await res.json();

        // Accept either an array or an object
        let movies = null;
        let state = null;
        let hasPrev = false;
        let hasNext = false;

        if (Array.isArray(data)) {
            movies = data;
        } else if (data && typeof data === "object") {
            state = data.state || null;
            hasPrev = !!data.hasPrev;
            hasNext = !!data.hasNext;
            movies = data.movies || data.results || data.data || data.movieList;

            if (!Array.isArray(movies)) {
                for (const k of Object.keys(data)) {
                    if (Array.isArray(data[k])) {
                        movies = data[k];
                        break;
                    }
                }
            }
        }

        // Debug
        if (!Array.isArray(movies)) {
            throw new Error(`Expected movie array but got: ${JSON.stringify(data).slice(0, 200)}`);
        }

        // Sync toolbar state
        if (state && typeof state === "object") {
            currentState = {
                page: Number(state.page || 1),
                pageSize: Number(state.pageSize || 25),
                sortPrimary: state.sortPrimary || "rating",
                titleDir: state.titleDir || "asc",
                ratingDir: state.ratingDir || "desc",
            };
            syncControls(currentState, hasPrev, hasNext);
        }

        if (movies.length === 0) {
            statusEl.textContent = "No results.";
            return;
        }

        statusEl.textContent = "";
        statusEl.style.display = "none";

        tbody.innerHTML = movies.map(m => {
            const mid = m.id;
            return `
                <tr>
                    <td><a href="singlemovie.html?id=${encodeURIComponent(mid)}">${esc(m.title)}</a></td>
                    <td>${esc(m.year)}</td>
                    <td>${esc(m.director)}</td>
                    <td>${renderGenres(m.genres)}</td>
                    <td>${renderStars(m.stars)}</td>
                    <td>${formatRating(m.rating)}</td>
                    <td class="nowrap">
                        <button class="btn mini js-add-cart" type="button" data-movie-id="${escAttr(mid)}">+ Cart</button>
                    </td>
                </tr>
            `;
        }).join("");

        // Wire cart buttons
        tbody.querySelectorAll(".js-add-cart").forEach(btn => {
            btn.addEventListener("click", async () => {
                const movieId = btn.getAttribute("data-movie-id");
                if (!movieId) return;
                btn.disabled = true;
                try {
                    await addToCart(movieId);
                } catch (e) {
                    flashStatus("Add to cart failed: " + e.message, true);
                } finally {
                    btn.disabled = false;
                }
            });
        });

        // Debug
    } catch (e) {
        statusEl.style.display = "";
        statusEl.textContent = "Error loading movies: " + e.message;
        tbody.innerHTML = "";
        console.log("API_URL was:", buildApiUrl());
        console.log("Error:", e);
    }
}

document.addEventListener("DOMContentLoaded", () => {
    setupControls();
    loadMovies();
});
