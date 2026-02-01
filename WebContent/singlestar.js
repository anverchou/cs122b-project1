const API_URL = "singlestar";

// Escape string safely so it can be insrted into HTML
function esc(s) {
    return String(s ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;");
}

// Render star id
function getStarIdFromURL() {
    const params = new URLSearchParams(window.location.search);
    return params.get("id");
}

// Render the star's movie list from container
function renderMovies(moviesStr) {
    const wrap = document.getElementById("movie_chips");
    if (!wrap) return;
    wrap.innerHTML = "";

    if (!moviesStr || moviesStr.trim() === "" || moviesStr === "N/A") {
        wrap.innerHTML = `<span class="chip">N/A</span>`;
        return;
    }

    const pairs = moviesStr.split(",").map(x => x.trim()).filter(Boolean);

    pairs.forEach(p => {
        if (p.includes(":")) {
            const [id, title] = p.split(":", 2);
            const movieId = (id ?? "").trim();
            const movieTitle = (title ?? id ?? "").trim();

            wrap.insertAdjacentHTML(
                "beforeend",
                `<a class="chip" href="singlemovie.html?id=${encodeURIComponent(movieId)}">${esc(movieTitle)}</a>`
            );
        } else {
            wrap.insertAdjacentHTML("beforeend", `<span class="chip">${esc(p)}</span>`);
        }
    });

    if (!wrap.children.length) wrap.innerHTML = `<span class="chip">N/A</span>`;
}

// Fetch selected star details
async function loadStar() {
    const statusEl = document.getElementById("status");
    const starId = getStarIdFromURL();

    if (!statusEl) return;

    if (!starId) {
        statusEl.textContent = "Missing star id in URL (expected ?id=...)";
        return;
    }

    try {
        const res = await fetch(`${API_URL}?id=${encodeURIComponent(starId)}`, {
            headers: { "Accept": "application/json" }
        });
        if (res.status === 401) {
            window.location.replace("login.html");
            return;
        }

        if (!res.ok) throw new Error(`HTTP ${res.status}`);

        const data = await res.json();

        const nameEl = document.getElementById("star_name");
        const pillEl = document.getElementById("star_info_pill");

        if (nameEl) nameEl.textContent = data.name ?? "N/A";
        if (pillEl) pillEl.textContent = `Year of Birth: ${data.birthYear ?? "N/A"}`;

        renderMovies(data.movies);

        statusEl.textContent = "";
        statusEl.style.display = "none";
        document.title = `Fabflix - ${data.name ?? "Single Star"}`;

    } catch (e) {
        statusEl.textContent = "Exception: " + e.message;
    }
}

document.addEventListener("DOMContentLoaded", loadStar);
