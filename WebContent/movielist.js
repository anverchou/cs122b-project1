const API_URL = "movielist";

function esc(s) {
    return String(s ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;");
}

function renderStars(starsStr) {
    if (!starsStr || starsStr.trim() === "" || starsStr === "N/A") return "N/A";

    const pairs = starsStr.split(", ").filter(Boolean);

    return pairs.map(p => {
        const [id, name] = p.split(":", 2);
        const starId = (id ?? "").trim();
        const starName = (name ?? id ?? "").trim();
        return `<a href="singlestar?id=${encodeURIComponent(starId)}">${esc(starName)}</a>`;
    }).join(", ");
}

async function loadMovies() {
    const statusEl = document.getElementById("status");
    const tbody = document.getElementById("movie_table_body");

    try {
        const res = await fetch(API_URL, { headers: { "Accept": "application/json" } });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);

        const movies = await res.json();

        statusEl.textContent = "";
        statusEl.style.display = "none";

        tbody.innerHTML = movies.map(m => `
      <tr>
        <td><a href="singlemovie?id=${encodeURIComponent(m.id)}">${esc(m.title)}</a></td>
        <td>${esc(m.year)}</td>
        <td>${esc(m.director)}</td>
        <td>${esc(m.genres || "N/A")}</td>
        <td>${renderStars(m.stars)}</td>
        <td>${esc(m.rating ?? "N/A")}</td>
      </tr>
    `).join("");

    } catch (e) {
        statusEl.textContent = "Exception in doGet: " + e.message;
        tbody.innerHTML = "";
    }
}

document.addEventListener("DOMContentLoaded", loadMovies);
