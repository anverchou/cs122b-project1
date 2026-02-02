// Always call the servlet with the current query string
// To read URL and clear
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

// Format rating to one decimal digit
function formatRating(r) {
    if (r == null) return "N/A";
    const n = Number(r);
    return Number.isFinite(n) ? n.toFixed(1) : "N/A";
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

    // string case
    const str = String(stars).trim();
    if (!str || str === "N/A") return "N/A";

    return str
        .split(",")
        .map(x => x.trim())
        .filter(Boolean)
        .map(p => {
            if (!p.includes(":")) return esc(p);
            const [id, name] = p.split(":", 2);
            const sid = (id ?? "").trim();
            const sname = (name ?? id ?? "").trim();
            return `<a href="singlestar.html?id=${encodeURIComponent(sid)}">${esc(sname)}</a>`;
        })
        .join(", ");
}

// URL and state helpers
function currentParams() {
    return new URLSearchParams(window.location.search);
}

// Fetch the reloaded movies
function setParamAndReload(updates) {
    const params = currentParams();
    for (const [k, v] of Object.entries(updates)) {
        if (v == null || v === "") params.delete(k);
        else params.set(k, String(v));
    }
    const qs = params.toString();
    const nextUrl = qs ? `${window.location.pathname}?${qs}` : window.location.pathname;
    history.pushState({}, "", nextUrl);
    loadMovies();
}

function apiUrl() {
    return `${getContextPath()}/movielist${window.location.search}`;
}

function applyStateToToolbar(state) {
    const sortSel = document.getElementById("sort_select");
    const dirSel = document.getElementById("dir_select");
    const sizeSel = document.getElementById("page_size_select");
    const pageLabel = document.getElementById("page_label");

    if (sortSel && state?.sortPrimary) {
        sortSel.value = (state.sortPrimary === "title") ? "title_rating" : "rating_title";
    }

    if (dirSel) {
        dirSel.value = state?.ratingDir === "asc" ? "asc" : "desc";
    }

    if (sizeSel && state?.pageSize) {
        sizeSel.value = String(state.pageSize);
    }

    if (pageLabel) {
        pageLabel.textContent = `Page ${state?.page ?? 1}`;
    }
}

// Toolbar for sorting/filters
function setupToolbar(state, hasPrev, hasNext) {
    const sortSel = document.getElementById("sort_select");
    const dirSel = document.getElementById("dir_select");
    const sizeSel = document.getElementById("page_size_select");
    const prevBtn = document.getElementById("prev_btn");
    const nextBtn = document.getElementById("next_btn");

    if (prevBtn) prevBtn.disabled = !hasPrev;
    if (nextBtn) nextBtn.disabled = !hasNext;

    if (sortSel && !sortSel._bound) {
        sortSel._bound = true;
        sortSel.addEventListener("change", () => {
            const v = sortSel.value;
            const sortPrimary = (v === "title_rating") ? "title" : "rating";
            setParamAndReload({ sortPrimary, page: 1 });
        });
    }

    if (dirSel && !dirSel._bound) {
        dirSel._bound = true;
        dirSel.addEventListener("change", () => {
            const dir = dirSel.value === "asc" ? "asc" : "desc";
            // map to both
            setParamAndReload({ titleDir: dir, ratingDir: dir, page: 1 });
        });
    }

    if (sizeSel && !sizeSel._bound) {
        sizeSel._bound = true;
        sizeSel.addEventListener("change", () => {
            const ps = Number(sizeSel.value);
            setParamAndReload({ pageSize: ps, page: 1 });
        });
    }

    if (prevBtn && !prevBtn._bound) {
        prevBtn._bound = true;
        prevBtn.addEventListener("click", () => {
            const p = Number(state?.page ?? 1);
            if (p > 1) setParamAndReload({ page: p - 1 });
        });
    }

    if (nextBtn && !nextBtn._bound) {
        nextBtn._bound = true;
        nextBtn.addEventListener("click", () => {
            const p = Number(state?.page ?? 1);
            setParamAndReload({ page: p + 1 });
        });
    }
}

// Load the movielist
async function loadMovies() {
    const statusEl = document.getElementById("status");
    const tbody = document.getElementById("movie_table_body");

    statusEl.style.display = "";
    statusEl.textContent = "Loading…";
    tbody.innerHTML = "";

    try {
        const res = await fetch(apiUrl(), { headers: { Accept: "application/json" } });

        if (res.status === 401) {
            window.location.replace("login.html");
            return;
        }

        const text = await res.text();
        let data;
        try {
            data = JSON.parse(text);
        } catch {
            throw new Error(`Non-JSON response (first 120): ${text.slice(0, 120)}`);
        }

        const movies = Array.isArray(data) ? data : data.movies;

        // Debug
        if (!Array.isArray(movies)) {
            throw new Error(`Expected movies array, got: ${JSON.stringify(data).slice(0, 200)}`);
        }

        const state = Array.isArray(data) ? {} : (data.state ?? {});
        const hasPrev = !!data.hasPrev;
        const hasNext = !!data.hasNext;

        applyStateToToolbar(state);
        setupToolbar(state, hasPrev, hasNext);

        if (movies.length === 0) {
            statusEl.textContent = "No results.";
            return;
        }

        statusEl.textContent = "";
        statusEl.style.display = "none";

        tbody.innerHTML = movies
            .map(m => `
        <tr>
          <td><a href="singlemovie.html?id=${encodeURIComponent(m.id)}">${esc(m.title)}</a></td>
          <td>${esc(m.year)}</td>
          <td>${esc(m.director)}</td>
          <td>${renderGenres(m.genres)}</td>
          <td>${renderStars(m.stars)}</td>
          <td>${formatRating(m.rating)}</td>
        </tr>
      `)
            .join("");

        // Debug
    } catch (e) {
        statusEl.textContent = "Error loading movies: " + e.message;
        console.log("API URL:", apiUrl());
        console.log("Error:", e);
    }
}

window.addEventListener("popstate", loadMovies);
document.addEventListener("DOMContentLoaded", loadMovies);
