const API_URL = "movielist" + window.location.search;

// Escape string safely so it can be inserted into HTML
function esc(s) {
    return String(s ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;");
}

// Convert stars string into clickable link
function renderStars(starsStr) {
    if (!starsStr || starsStr.trim() === "" || starsStr === "N/A") return "N/A";

    const pairs = starsStr.split(", ").filter(Boolean);

    return pairs.map(p => {
        const [id, name] = p.split(":", 2);
        const starId = (id ?? "").trim();
        const starName = (name ?? id ?? "").trim();
        return `<a href="singlestar.html?id=${encodeURIComponent(starId)}">${esc(starName)}</a>`;
    }).join(", ");
}

// Normalize rating values for display in the table
function formatRating(r) {
    if (r == null) return "N/A";
    if (typeof r === "string" && r.trim() === "N/A") return "N/A";

    const n = Number(r);
    return Number.isFinite(n) ? n.toFixed(1) : "N/A";
}

// Fetch movies list JSON and render into the table
async function loadMovies() {
    const statusEl = document.getElementById("status");
    const tbody = document.getElementById("movie_table_body");

    try {
        const res = await fetch(API_URL, { headers: { "Accept": "application/json" } });

        // If user is not authetnciated
        if (res.status === 401) {
            window.location.replace("login.html");
            return;
        }

        if (!res.ok) throw new Error(`HTTP ${res.status}`);

        const movies = await res.json();

        // Hide status once loaded
        statusEl.textContent = "";
        statusEl.style.display = "none";

        // Render movie roles
        tbody.innerHTML = movies.map(m => `
      <tr>
        <td><a href="singlemovie.html?id=${encodeURIComponent(m.id)}">${esc(m.title)}</a></td>
        <td>${esc(m.year)}</td>
        <td>${esc(m.director)}</td>
        <td>${esc(m.genres || "N/A")}</td>
        <td>${renderStars(m.stars)}</td>
        <td>${formatRating(m.rating)}</td>
      </tr>
    `).join("");

        // Error handling
    } catch (e) {
        statusEl.textContent = "Exception in doGet: " + e.message;
        tbody.innerHTML = "";
    }
}

document.addEventListener("DOMContentLoaded", loadMovies);
