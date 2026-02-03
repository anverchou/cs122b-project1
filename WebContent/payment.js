// Get current URL
function getContextPath() {
    const path = window.location.pathname;
    const i = path.indexOf("/", 1);
    return i === -1 ? "" : path.substring(0, i);
}

// Load cart total and render to page
async function loadTotal() {
    const statusEl = document.getElementById("status");
    const totalEl = document.getElementById("total_price");

    try {
        const res = await fetch(`${getContextPath()}/api/cart`, {
            headers: { "Accept": "application/json" }
        });

        // Not logged in
        if (res.status === 401) {
            window.location.replace("login.html");
            return;
        }

        // HTTP error
        if (!res.ok) {
            const t = await res.text();
            throw new Error(`HTTP ${res.status}: ${t.slice(0, 200)}`);
        }

        // Parse cart data
        const data = await res.json();
        const total = Number(data?.total ?? 0);
        if (totalEl) totalEl.textContent = total.toFixed(2);

        // Clear status message on sucess
        if (statusEl) {
            statusEl.textContent = "";
            statusEl.style.display = "none";
        }
        // Display errors
    } catch (e) {
        if (statusEl) {
            statusEl.style.display = "";
            statusEl.textContent = "Error loading cart total: " + e.message;
        }
    }
}

// Payment form
function setupPaymentForm() {
    const form = document.getElementById("payment_form");
    const errEl = document.getElementById("error");

    if (!form) return;

    form.addEventListener("submit", async (e) => {
        e.preventDefault();

        if (errEl) {
            errEl.style.display = "none";
            errEl.textContent = "";
        }

        // Read form values
        const fd = new FormData(form);
        const firstName = String(fd.get("firstName") ?? "").trim();
        const lastName = String(fd.get("lastName") ?? "").trim();
        const cardNumber = String(fd.get("cardNumber") ?? "").trim();
        const expiration = String(fd.get("expiration") ?? "").trim();

        // Validate input fields
        if (!firstName || !lastName || !cardNumber || !expiration) {
            if (errEl) {
                errEl.textContent = "Please fill out all fields.";
                errEl.style.display = "";
            }
            return;
        }

        // Allow for digits only
        if (!/^\d+$/.test(cardNumber)) {
            if (errEl) {
                errEl.textContent = "Credit card number must be digits only.";
                errEl.style.display = "";
            }
            return;
        }

        // Send infor to backend
        try {
            const res = await fetch(`${getContextPath()}/api/place-order`, {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body: new URLSearchParams({ firstName, lastName, cardNumber, expiration })
            });

            // Repeat process from above
            if (res.status === 401) {
                window.location.replace("login.html");
                return;
            }

            if (!res.ok) {
                const t = await res.text();
                throw new Error(`HTTP ${res.status}: ${t.slice(0, 200)}`);
            }

            const data = await res.json().catch(() => ({}));

            if (data.status === "success") {
                window.location.replace(`${getContextPath()}/confirmation.html`);
                return;
            }

            // fail path
            const msg = data.message || "Payment info did not match a credit card record.";
            if (errEl) {
                errEl.textContent = msg;
                errEl.style.display = "";
            }

        } catch (e2) {
            if (errEl) {
                errEl.textContent = "Error placing order: " + e2.message;
                errEl.style.display = "";
            }
        }
    });
}

document.addEventListener("DOMContentLoaded", () => {
    loadTotal();
    setupPaymentForm();
});
