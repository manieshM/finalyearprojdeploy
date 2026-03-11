const authState = document.getElementById("authState");
const logoutButton = document.getElementById("logoutButton");
const userForm = document.getElementById("userForm");
const userMessage = document.getElementById("userMessage");
const userList = document.getElementById("userList");

let authToken = localStorage.getItem("mpr_token") || "";
let currentUser = localStorage.getItem("mpr_user") || "";
let currentRole = localStorage.getItem("mpr_role") || "";

if (!authToken) {
  window.location.replace("/login.html");
} else if (currentRole !== "admin") {
  window.location.replace("/app.html");
}

function clearSession() {
  authToken = "";
  currentUser = "";
  localStorage.removeItem("mpr_token");
  localStorage.removeItem("mpr_user");
  localStorage.removeItem("mpr_role");
  localStorage.removeItem("mpr_can_case_edit");
}

function setAuthState() {
  authState.textContent = `Signed in as ${currentUser || "admin"} (Admin)`;
}

async function apiFetch(url, options = {}) {
  const headers = new Headers(options.headers || {});
  if (authToken) {
    headers.set("Authorization", `Bearer ${authToken}`);
  }
  const response = await fetch(url, { ...options, headers });
  if (response.status === 401) {
    clearSession();
    window.location.replace("/login.html");
    throw new Error("Session expired. Login again.");
  }
  return response;
}

function renderUsers(users) {
  if (!users.length) {
    userList.innerHTML = `<div class="match-card empty">No additional users created yet.</div>`;
    return;
  }
  userList.innerHTML = users.map((user) => `
    <article class="history-card">
      <div>
        <strong>${user.email}</strong>
        <p class="muted">Role: ${user.role}</p>
        <p class="muted">Case Edit Access: ${user.role === "admin" ? "Full Access" : user.canCaseEdit ? "Approved" : "Read Only"}</p>
      </div>
      <div>
        ${user.role !== "admin" ? `<button type="button" data-case-access-email="${user.email}" data-case-access="${user.canCaseEdit ? "false" : "true"}">${user.canCaseEdit ? "Revoke Case Edit" : "Approve Case Edit"}</button>` : ""}
      </div>
    </article>
  `).join("");
}

async function loadUsers() {
  const response = await apiFetch("/api/users");
  const data = await response.json();
  renderUsers(data.users || []);
}

logoutButton.addEventListener("click", async () => {
  try {
    await apiFetch("/api/logout", { method: "POST" });
  } catch (error) {
    console.error(error);
  }
  clearSession();
  window.location.replace("/login.html");
});

userForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  userMessage.textContent = "";
  const formData = new FormData(userForm);
  try {
    const response = await apiFetch("/api/users", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        email: formData.get("email"),
        password: formData.get("password"),
        role: formData.get("role")
      })
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.error || "User creation failed");
    }
    userForm.reset();
    userMessage.textContent = `User created: ${data.user.email}`;
    userMessage.className = "message success";
    await loadUsers();
  } catch (error) {
    userMessage.textContent = error.message;
    userMessage.className = "message danger";
  }
});

document.addEventListener("click", async (event) => {
  const accessButton = event.target.closest("[data-case-access-email]");
  if (!accessButton) {
    return;
  }
  try {
    const response = await apiFetch("/api/users", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        action: "set_case_access",
        email: accessButton.getAttribute("data-case-access-email"),
        canCaseEdit: accessButton.getAttribute("data-case-access")
      })
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.error || "Could not update access");
    }
    await loadUsers();
  } catch (error) {
    userMessage.textContent = error.message;
    userMessage.className = "message danger";
  }
});

setAuthState();
loadUsers().catch(console.error);
