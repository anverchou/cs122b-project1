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

// Build API endpoint
const CART_API = () => `${getContextPath()}/api/cart`;

// Format to 2 decimal places
function formatMoney(n) {
    const num = Number(n);
    return Number.isFinite(num) ? `$${num.toFixed(2)}` : "N/A";
}

// Cart API/servlet
async function cartPost(fields) {
    // fields: { action: 'inc'|'dec'|'set'|'delete'|'clear', movieId, quantity }
    const body = new URLSearchParams();
    for (const [k, v] of Object.entries(fields)) {
        if (v === undefined || v === null) continue;
        body.set(k, String(v));
    }

    const res = await fetch(CART_API(), {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded", "Accept": "application/json" },
        body,
    });

    if (res.status === 401) {
        window.location.replace("login.html");
        return null;
    }

    if (!res.ok) {
        const t = await res.text();
        throw new Error(`HTTP ${res.status}: ${t.slice(0, 200)}`);
    }

    return await res.json();
}

// Retrieve current cart state
async function cartGet() {
    const res = await fetch(CART_API(), { headers: { "Accept": "application/json" } });

    if (res.status === 401) {
        window.location.replace("login.html");
        return null;
    }

    if (!res.ok) {
        const t = await res.text();
        throw new Error(`HTTP ${res.status}: ${t.slice(0, 200)}`);
    }

    return await res.json();
}

// Update status on cart page
function setStatus(msg, isError = false) {
    const statusEl = document.getElementById("status");
    if (!statusEl) return;
    statusEl.textContent = msg;
    statusEl.style.display = msg ? "" : "none";
    statusEl.style.color = isError ? "#b42318" : "";
}

// Render the cart and total
function renderCart(data) {
    const tbody = document.getElementById("cart_table_body");
    const totalEl = document.getElementById("cart_total");
    const payBtn = document.getElementById("pay_btn");

    // Debugging
    if (!tbody || !totalEl) {
        throw new Error(
            `Cart page is missing required elements. Expected #cart_table_body and #cart_total.`
        );
    }

    const items = Array.isArray(data?.items) ? data.items : [];

    // Build table rows for movies
    tbody.innerHTML = items
        .map((it) => {
            const id = it.movieId ?? it.id;
            const title = it.title ?? "(untitled)";
            const qty = Number(it.quantity ?? 0);
            const price = it.price;
            const lineTotal = it.lineTotal ?? it.subtotal ?? (Number(price) * qty);
            return `
        <tr>
          <td>${esc(title)}</td>
          <td class="nowrap">
            <button class="qty" data-action="dec" data-id="${esc(id)}" type="button">−</button>
            <span class="qty-num">${esc(qty)}</span>
            <button class="qty" data-action="inc" data-id="${esc(id)}" type="button">+</button>
          </td>
          <td class="nowrap">${formatMoney(price)}</td>
          <td class="nowrap">${formatMoney(lineTotal)}</td>
          <td class="nowrap">
            <button class="remove" data-action="remove" data-id="${esc(id)}" type="button">Delete</button>
          </td>
        </tr>
      `;
        })
        .join("");

    // Render the total cart price
    const total = Number(data?.total ?? 0);
    totalEl.textContent = formatMoney(total);

    // Disable pay if cart is empty
    if (payBtn) payBtn.disabled = items.length === 0;

    // Display empty cart message
    if (items.length === 0) {
        setStatus("Your cart is empty.");
    } else {
        setStatus("");
    }
}

// Fetch cart data and render
async function loadCart() {
    setStatus("Loading…");
    const data = await cartGet();
    if (!data) return;
    renderCart(data);
}

// Cart actions (Add/Subtraction/Delete)
function wireCartActions() {
    const tbody = document.getElementById("cart_table_body");
    if (!tbody) return;

    // Cart buttons
    tbody.addEventListener("click", async (e) => {
        const target = e.target;
        if (!(target instanceof HTMLElement)) return;

        const id = target.getAttribute("data-id");
        const action = target.getAttribute("data-action");
        if (!id || !action) return;

        try {
            if (action === "inc") {
                await cartPost({ action: "inc", movieId: id });
            } else if (action === "dec") {
                await cartPost({ action: "dec", movieId: id });
            } else if (action === "remove") {
                await cartPost({ action: "delete", movieId: id });
            }
            await loadCart();
        } catch (err) {
            setStatus("Error updating cart: " + (err?.message ?? String(err)), true);
            console.log(err);
        }
    });
}

// Pay/Clear buttons
function wireButtons() {
    const payBtn = document.getElementById("pay_btn");
    const clearBtn = document.getElementById("clear_btn");

    if (payBtn) {
        payBtn.addEventListener("click", () => {
            // Navigate using context path so we don't accidentally end up under /api/...
            window.location.href = `${getContextPath()}/payment.html`;
        });
    }

    if (clearBtn) {
        clearBtn.addEventListener("click", async () => {
            try {
                await cartPost({ action: "clear" });
                await loadCart();
            } catch (err) {
                setStatus("Error clearing cart: " + (err?.message ?? String(err)), true);
                console.log(err);
            }
        });
    }
}

document.addEventListener("DOMContentLoaded", () => {
    try {
        wireCartActions();
        wireButtons();
        loadCart();
    } catch (err) {
        setStatus("Error loading cart: " + (err?.message ?? String(err)), true);
        console.log(err);
    }
});
