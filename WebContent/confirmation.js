// Escape unsafe strings
function esc(s) {
    return String(s ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;");
}

// Get current URL
function getContextPath() {
    const path = window.location.pathname;
    const i = path.indexOf("/", 1);
    return i === -1 ? "" : path.substring(0, i);
}

// Format to 2 decimal places
function money(v) {
    const n = Number(v);
    if (!Number.isFinite(n)) return "$0.00";
    return `$${n.toFixed(2)}`;
}

// Fetch order confirmation data
async function loadConfirmation() {
    const statusEl = document.getElementById("status");
    const metaEl = document.getElementById("meta");
    const totalEl = document.getElementById("total_value");
    const tbody = document.getElementById("items_body");

    try {
        const res = await fetch(`${getContextPath()}/api/order-confirmation`, {
            headers: { "Accept": "application/json" }
        });

        // Check for user login
        if (res.status === 401) {
            window.location.replace("login.html");
            return;
        }

        const raw = await res.text();
        const ct = (res.headers.get("content-type") || "").toLowerCase();
        let data = null;
        if (ct.includes("application/json")) {
            data = raw ? JSON.parse(raw) : null;
        }

        // HTTP error
        if (!res.ok) {
            const snippet = (raw || "").slice(0, 120);
            throw new Error(`HTTP ${res.status}: ${snippet}`);
        }

        // Application error
        if (!data || data.status !== "success") {
            const msg = (data && data.message) ? data.message : "Unexpected server response";
            throw new Error(msg);
        }

        // Extra order payload
        const order = data.order || {};
        const items = Array.isArray(order.items) ? order.items : [];

        // Render order date
        if (metaEl) {
            const od = order.orderDate ? String(order.orderDate) : "(unknown)";
            metaEl.textContent = `Order placed successfully. Order Date: ${od}`;
        }

        // Render total amount
        if (totalEl) totalEl.textContent = money(order.total);

        // Render line itmes for table
        if (tbody) {
            tbody.innerHTML = items.map(it => {
                const title = it.title ?? "N/A";
                const qty = Number(it.quantity ?? 0);
                const price = it.price ?? 0;
                const subtotal = it.subtotal ?? (Number(price) * qty);

                return `
          <tr>
            <td><a href="singlemovie.html?id=${encodeURIComponent(it.movieId ?? "")}">${esc(title)}</a></td>
            <td class="right">${esc(qty)}</td>
            <td class="right">${money(price)}</td>
            <td class="right">${money(subtotal)}</td>
          </tr>
        `;
            }).join("");
        }

        if (statusEl) {
            statusEl.textContent = "";
            statusEl.style.display = "none";
        }

    } catch (e) {
        if (statusEl) {
            statusEl.style.display = "";
            statusEl.textContent = `Error loading confirmation: ${e.message}`;
        }
        if (tbody) tbody.innerHTML = "";
    }
}

document.addEventListener("DOMContentLoaded", loadConfirmation);
