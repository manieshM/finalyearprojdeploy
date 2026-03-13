const authState = document.getElementById("authState");
const logoutButton = document.getElementById("logoutButton");
const caseList = document.getElementById("caseList");
const reviewQueueList = document.getElementById("reviewQueueList");
const unknownSightingsList = document.getElementById("unknownSightingsList");
const userAccessLink = document.getElementById("userAccessLink");

let authToken = localStorage.getItem("mpr_token") || "";
let currentUser = localStorage.getItem("mpr_user") || "";
let canCaseEdit = localStorage.getItem("mpr_can_case_edit") === "1";
let currentRole = localStorage.getItem("mpr_role") || "";

if (!authToken) {
  window.location.replace("/login.html");
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
  authState.textContent = `Signed in as ${currentUser || "user"}${currentRole === "admin" ? " (Admin)" : canCaseEdit ? " (Approved Editor)" : " (Read Only)"}`;
  if (userAccessLink) {
    userAccessLink.hidden = currentRole !== "admin";
  }
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

function caseStatusOptions(selected) {
  return ["Missing", "Under Investigation", "Sighted", "Found", "Closed"]
    .map((status) => `<option value="${status}" ${status === selected ? "selected" : ""}>${status}</option>`)
    .join("");
}

function renderCases(cases) {
  if (!cases.length) {
    caseList.innerHTML = `<div class="match-card empty">No registered cases available yet.</div>`;
    return;
  }

  caseList.innerHTML = cases.map((entry) => `
    <article class="panel case-card">
      <div class="case-head">
        <div>
          <strong>${entry.person.name}</strong>
          <p class="muted">Last seen: ${entry.person.lastSeen || "Not specified"}</p>
        </div>
        <span class="priority-pill">Priority ${entry.priority}</span>
      </div>
      <div class="case-meta">
        <span class="badge">Lifecycle: ${entry.person.status || "Missing"}</span>
        <span class="badge">References: ${entry.person.referenceCount || 1}</span>
        <span class="badge">Sightings: ${entry.sightingCount || 0}</span>
        <span class="badge">Pending Reviews: ${entry.pendingReviews || 0}</span>
      </div>
      <div class="case-actions">
        <img class="insight-thumb" src="${entry.person.imageUrl}" alt="${entry.person.name}">
        <div>
          <p class="muted">Contact: ${entry.person.contact || "Not provided"}</p>
          <p class="muted">Latest sighting: ${entry.latestSighting ? new Date(entry.latestSighting).toLocaleString() : "No confirmed sighting yet"}</p>
        </div>
        ${canCaseEdit || currentRole === "admin" ? `
          <div class="action-cluster">
            <select data-person-id="${entry.person.id}">
              ${caseStatusOptions(entry.person.status || "Missing")}
            </select>
            <button type="button" data-save-status="${entry.person.id}">Update Lifecycle</button>
            <button type="button" class="button-danger" data-delete-person="${entry.person.id}" data-delete-person-name="${entry.person.name}">Delete Person</button>
          </div>
        ` : `<span class="badge">Read Only Access</span>`}
      </div>
    </article>
  `).join("");
}

function renderInsights(data) {
  const reviewQueue = data.reviewQueue || [];
  const unknownSightings = data.unknownSightings || [];

  reviewQueueList.innerHTML = reviewQueue.length
    ? reviewQueue.map((item) => `
      <article class="history-card">
        <div>
          <strong>${item.personName || "Borderline match"}</strong>
          <p class="muted">Confidence: ${item.confidence ?? "N/A"}</p>
          <p class="muted">Review Status: ${item.reviewStatus || "review_required"}</p>
          <p class="muted">${new Date(item.createdAt).toLocaleString()}</p>
        </div>
        <div class="review-actions">
          <img class="insight-thumb" src="${item.snapshotUrl}" alt="Review snapshot">
          ${canCaseEdit || currentRole === "admin"
            ? `<button type="button" data-review-id="${item.id}" data-review-status="verified">Verify</button>
               <button type="button" data-review-id="${item.id}" data-review-status="rejected">Reject</button>`
            : `<span class="badge">Read Only</span>`}
        </div>
      </article>
    `).join("")
    : `<div class="match-card empty">No cases waiting for verification.</div>`;

  unknownSightingsList.innerHTML = unknownSightings.length
    ? unknownSightings.map((item) => `
      <article class="history-card">
        <div>
          <strong>${item.groupId}</strong>
          <p class="muted">Repeated sightings: ${item.sightings}</p>
          <p class="muted">Latest seen: ${new Date(item.latestSeenAt).toLocaleString()}</p>
        </div>
        <div>
          <img class="insight-thumb" src="${item.latestSnapshotUrl}" alt="Unknown face snapshot">
        </div>
      </article>
    `).join("")
    : `<div class="match-card empty">No repeated unknown sightings yet.</div>`;
}

async function loadCasePage() {
  const [caseResponse, insightResponse] = await Promise.all([
    apiFetch("/api/cases"),
    apiFetch("/api/review-insights")
  ]);
  const caseData = await caseResponse.json();
  const insightData = await insightResponse.json();
  renderCases(caseData.cases || []);
  renderInsights(insightData);
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

document.addEventListener("click", async (event) => {
  const saveButton = event.target.closest("[data-save-status]");
  if (saveButton) {
    if (!(canCaseEdit || currentRole === "admin")) {
      alert("Admin approval is required to edit case management.");
      return;
    }
    const personId = saveButton.getAttribute("data-save-status");
    const select = document.querySelector(`select[data-person-id="${personId}"]`);
    try {
      const response = await apiFetch("/api/case-status", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ personId, status: select.value })
      });
      const data = await response.json();
      if (!response.ok) {
        throw new Error(data.error || "Could not update lifecycle");
      }
      await loadCasePage();
    } catch (error) {
      alert(error.message);
    }
    return;
  }

  const deletePersonButton = event.target.closest("[data-delete-person]");
  if (deletePersonButton) {
    if (!(canCaseEdit || currentRole === "admin")) {
      alert("Admin approval is required to edit case management.");
      return;
    }
    const personId = deletePersonButton.getAttribute("data-delete-person");
    const personName = deletePersonButton.getAttribute("data-delete-person-name") || "this person";
    if (!window.confirm(`Delete ${personName}? This removes the registered person record and related match history.`)) {
      return;
    }
    try {
      const response = await apiFetch("/api/cases", {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ personId })
      });
      const data = await response.json();
      if (!response.ok) {
        throw new Error(data.error || "Could not delete person");
      }
      await loadCasePage();
    } catch (error) {
      alert(error.message);
    }
    return;
  }

  const reviewButton = event.target.closest("[data-review-id]");
  if (reviewButton) {
    if (!(canCaseEdit || currentRole === "admin")) {
      alert("Admin approval is required to review cases.");
      return;
    }
    const historyId = reviewButton.getAttribute("data-review-id");
    const reviewStatus = reviewButton.getAttribute("data-review-status");
    try {
      const response = await apiFetch("/api/reviews", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ historyId, reviewStatus })
      });
      const data = await response.json();
      if (!response.ok) {
        throw new Error(data.error || "Could not update review");
      }
      await loadCasePage();
    } catch (error) {
      alert(error.message);
    }
  }
});

setAuthState();
loadCasePage().catch(console.error);
