const API_URL = "singlemovie";

// Escape string safely so it can be insrted into HTML
function esc(s) {
    return String(s ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;");
}

// Read movieid from page url query
function getMovieIdFromURL() {
    const params = new URLSearchParams(window.location.search);
    return params.get("id");
}

// Cnvert comma-seperated strings into an array of trimmed strings
function toArrayFromCommaString(s) {
    if (!s || s.trim() === "" || s === "N/A") return [];
    return s.split(",").map(x => x.trim()).filter(Boolean);
}

// Noramlize rating format
function formatRating(r) {
    if (r == null) return "N/A";
    if (typeof r === "string" && r.trim() === "N/A") return "N/A";
    const n = Number(r);
    return Number.isFinite(n) ? n.toFixed(1) : "N/A";
}

// Render genres into containers
function renderGenres(genresField) {
    const wrap = document.getElementById("genre_chips");
    if (!wrap) return; // prevents null crash
    wrap.innerHTML = "";

    const arr = Array.isArray(genresField)
        ? genresField.map(g => (typeof g === "string" ? g : (g?.name ?? ""))).filter(Boolean)
        : toArrayFromCommaString(genresField);

    if (!arr.length) {
        wrap.innerHTML = `<span class="chip">N/A</span>`;
        return;
    }

    arr.forEach(g => wrap.insertAdjacentHTML("beforeend", `<span class="chip">${esc(g)}</span>`));
}

// Render stars into containers
function renderStars(starsField) {
    const wrap = document.getElementById("star_chips");
    // Prevent null crashes
    if (!wrap) return;
    wrap.innerHTML = "";

    if (!starsField || starsField === "N/A") {
        wrap.innerHTML = `<span class="chip">N/A</span>`;
        return;
    }

    const raw = Array.isArray(starsField) ? starsField : String(starsField);

    const pieces = Array.isArray(raw) ? raw : raw.split(",").map(x => x.trim()).filter(Boolean);

    pieces.forEach(item => {
        if (typeof item === "string") {
            if (item.includes(":")) {
                const [id, name] = item.split(":", 2);
                wrap.insertAdjacentHTML(
                    "beforeend",
                    `<a class="chip" href="singlestar.html?id=${encodeURIComponent(id.trim())}">${esc((name ?? id).trim())}</a>`
                );
            } else {
                wrap.insertAdjacentHTML("beforeend", `<span class="chip">${esc(item)}</span>`);
            }
        } else if (item && typeof item === "object") {
            const id = item.id ?? "";
            const name = item.name ?? item.id ?? "N/A";
            if (id) {
                wrap.insertAdjacentHTML(
                    "beforeend",
                    `<a class="chip" href="singlestar.html?id=${encodeURIComponent(id)}">${esc(name)}</a>`
                );
            } else {
                wrap.insertAdjacentHTML("beforeend", `<span class="chip">${esc(name)}</span>`);
            }
        }
    });

    if (!wrap.children.length) wrap.innerHTML = `<span class="chip">N/A</span>`;
}

// Ftch the selected movie details
async function loadSingleMovie() {
    const statusEl = document.getElementById("status");
    const movieId = getMovieIdFromURL();

    // Prevent null crash
    if (!statusEl) return;

    if (!movieId) {
        statusEl.textContent = "Missing movie id in URL,";
        return;
    }

    try {
        const res = await fetch(`${API_URL}?id=${encodeURIComponent(movieId)}`, {
            headers: { "Accept": "application/json" }
        });
        if (res.status === 401) {
            window.location.replace("login.html");
            return;
        }

        if (!res.ok) throw new Error(`HTTP ${res.status}`);

        const data = await res.json();
        const m = Array.isArray(data) ? (data[0] ?? {}) : data;

        const titleEl = document.getElementById("movie_title");
        const yearEl = document.getElementById("movie_year_pill");
        const ratingEl = document.getElementById("movie_rating_pill");
        const directorEl = document.getElementById("movie_director_pill");

        if (titleEl) titleEl.textContent = m.title ?? "N/A";
        if (yearEl) yearEl.textContent = `Year: ${m.year ?? "N/A"}`;
        if (ratingEl) ratingEl.textContent = `Rating: ${formatRating(m.rating)}`;
        if (directorEl) directorEl.textContent = `Director: ${m.director ?? "N/A"}`;

        renderGenres(m.genres);
        renderStars(m.stars);

        statusEl.textContent = "";
        statusEl.style.display = "none";
        document.title = `Fabflix - ${m.title ?? "Single Movie"}`;

    } catch (e) {
        statusEl.textContent = "Exception: " + e.message;
    }
}

document.addEventListener("DOMContentLoaded", loadSingleMovie);
