const modelStatus = document.getElementById("modelStatus");
const authState = document.getElementById("authState");
const logoutButton = document.getElementById("logoutButton");
const passwordForm = document.getElementById("passwordForm");
const passwordMessage = document.getElementById("passwordMessage");
const registerForm = document.getElementById("registerForm");
const registerMessage = document.getElementById("registerMessage");
const video = document.getElementById("video");
const canvas = document.getElementById("snapshot");
const matchResult = document.getElementById("matchResult");
const startCameraButton = document.getElementById("startCamera");
const stopCameraButton = document.getElementById("stopCamera");
const captureMatchButton = document.getElementById("captureMatch");
const uploadMatchImage = document.getElementById("uploadMatchImage");
const uploadMatchButton = document.getElementById("uploadMatchButton");
const thresholdInput = document.getElementById("threshold");
const dashboardCards = document.getElementById("dashboardCards");
const historyList = document.getElementById("historyList");
const clearHistoryButton = document.getElementById("clearHistoryButton");
const userAccessLink = document.getElementById("userAccessLink");

let stream;
let modelsReady = false;
let authToken = localStorage.getItem("mpr_token") || "";
let currentUser = localStorage.getItem("mpr_user") || "";

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

function currentRole() {
  return localStorage.getItem("mpr_role") || "";
}

function setAuthState() {
  authState.textContent = `Signed in as ${currentUser || "user"}`;
  if (userAccessLink) {
    userAccessLink.hidden = currentRole() !== "admin";
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

async function loadModels() {
  try {
    await window.loadFaceApi();
    if (!window.faceapi) {
      throw new Error("face-api.js library failed to load");
    }
    await faceapi.nets.tinyFaceDetector.loadFromUri("/models");
    await faceapi.nets.faceLandmark68Net.loadFromUri("/models");
    await faceapi.nets.faceRecognitionNet.loadFromUri("/models");
    modelsReady = true;
    if (modelStatus) {
      modelStatus.textContent = `Models loaded from ${window.faceApiSource || "unknown source"}`;
      modelStatus.className = "success";
    }
  } catch (error) {
    if (modelStatus) {
      modelStatus.textContent = String(error.message || "").includes("face-api.js library failed to load")
        ? "face-api.js did not load. Add face-api.min.js to frontend/vendor or use internet for the CDN."
        : "Model files could not be loaded from frontend/models.";
      modelStatus.className = "danger";
    }
  }
}

function renderDashboard(stats) {
  const cards = [
    { label: "Registered Persons", value: stats.registeredPersons ?? 0 },
    { label: "Total Scans", value: stats.totalScans ?? 0 },
    { label: "Successful Matches", value: stats.successfulMatches ?? 0 },
    { label: "Unmatched Scans", value: stats.unmatchedScans ?? 0 },
    { label: "Review Queue", value: stats.reviewQueue ?? 0 },
    { label: "Tracked Unknowns", value: stats.trackedUnknowns ?? 0 }
  ];
  dashboardCards.innerHTML = cards.map((card) => `
    <article class="stat-card">
      <span class="status-label">${card.label}</span>
      <strong>${card.value}</strong>
    </article>
  `).join("");
}

async function loadDashboard() {
  const response = await apiFetch("/api/dashboard");
  const data = await response.json();
  renderDashboard(data.stats || {});
}

function renderHistory(history) {
  if (!history.length) {
    historyList.innerHTML = `<div class="match-card empty">No match history yet.</div>`;
    return;
  }
  historyList.innerHTML = history.map((item) => `
    <article class="history-card">
      <div>
        <strong>${item.matched ? item.personName || "Match found" : "No match"}</strong>
        <p class="muted">Status: ${item.status || "Unknown"}</p>
      </div>
      <div>
        <p class="muted">Distance: ${item.distance ?? "N/A"}</p>
        <p class="muted">Threshold: ${item.threshold ?? "N/A"}</p>
        <p class="muted">${new Date(item.createdAt).toLocaleString()}</p>
      </div>
    </article>
  `).join("");
}

async function loadHistory() {
  const response = await apiFetch("/api/history");
  const data = await response.json();
  renderHistory(data.history || []);
}

async function detectDescriptorFromFile(file) {
  const image = await faceapi.bufferToImage(file);
  return detectDescriptorFromImage(image);
}

async function detectDescriptorFromImage(image) {
  const detection = await faceapi
    .detectSingleFace(image, new faceapi.TinyFaceDetectorOptions({ inputSize: 320, scoreThreshold: 0.45 }))
    .withFaceLandmarks()
    .withFaceDescriptor();
  if (!detection) {
    throw new Error("No clear face detected. Use a front-facing image with good lighting.");
  }
  return Array.from(detection.descriptor);
}

function descriptorToString(descriptor) {
  return descriptor.join(",");
}

function descriptorSetToString(descriptors) {
  return descriptors.map((descriptor) => descriptorToString(descriptor)).join("|");
}

function stopCamera() {
  if (stream) {
    stream.getTracks().forEach((track) => track.stop());
    stream = null;
  }
  video.srcObject = null;
}

async function runMatch(formData) {
  const response = await apiFetch("/api/match", { method: "POST", body: formData });
  const data = await response.json();
  if (!data.matched) {
    matchResult.innerHTML = `
      <strong>No confident match found.</strong>
      <p class="muted">Unknown face tracker group: ${data.unknownGroupId || "new sighting"}</p>
      <p class="muted">Scan recorded for future repeated-sighting analysis.</p>
    `;
    matchResult.className = "match-card";
    await Promise.all([loadDashboard(), loadHistory()]);
    return;
  }
  matchResult.innerHTML = `
    <strong>Match found: ${data.person.name}</strong>
    <p class="muted">Distance: ${data.distance}</p>
    <p class="muted">Confidence: ${data.confidence != null ? `${Number(data.confidence).toFixed(2)}%` : "N/A"}</p>
    <p class="muted">Cosine Similarity: ${data.cosineSimilarity != null ? Number(data.cosineSimilarity).toFixed(4) : "N/A"}</p>
    <p class="muted">Review Status: ${data.reviewStatus || "confirmed"}</p>
    <p class="muted">Last Seen: ${data.person.lastSeen || "Not specified"}</p>
    <p class="muted">Status: ${data.person.status || "Missing"}</p>
    ${data.person.imageUrl ? `<img src="${data.person.imageUrl}" alt="${data.person.name}" style="width:100%;max-width:260px;border-radius:14px;margin-top:12px;object-fit:cover;">` : ""}
  `;
  matchResult.className = "match-card success";
  await Promise.all([loadDashboard(), loadHistory()]);
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

passwordForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  passwordMessage.textContent = "";
  const formData = new FormData(passwordForm);
  try {
    const response = await apiFetch("/api/change-password", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        currentPassword: formData.get("currentPassword"),
        newPassword: formData.get("newPassword")
      })
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.error || "Password change failed");
    }
    passwordForm.reset();
    passwordMessage.textContent = "Password updated successfully.";
    passwordMessage.className = "message success";
  } catch (error) {
    passwordMessage.textContent = error.message;
    passwordMessage.className = "message danger";
  }
});

registerForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  registerMessage.textContent = "";
  if (!modelsReady) {
    registerMessage.textContent = "Load the face recognition models first.";
    return;
  }
  const files = Array.from(document.getElementById("referenceImage").files || []);
  if (!files.length) {
    registerMessage.textContent = "Select at least one reference image.";
    return;
  }
  try {
    const descriptors = [];
    for (const file of files) {
      descriptors.push(await detectDescriptorFromFile(file));
    }
    const formData = new FormData(registerForm);
    formData.set("image", files[0]);
    formData.append("descriptor", descriptorToString(descriptors[0]));
    formData.append("descriptorSet", descriptorSetToString(descriptors));
    const response = await apiFetch("/api/persons", { method: "POST", body: formData });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.error || "Registration failed.");
    }
    registerForm.reset();
    registerMessage.textContent = `Profile saved for ${data.person.name} with ${descriptors.length} reference image(s).`;
    registerMessage.className = "message success";
    await loadDashboard();
  } catch (error) {
    registerMessage.textContent = error.message;
    registerMessage.className = "message danger";
  }
});

startCameraButton.addEventListener("click", async () => {
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "user" }, audio: false });
    video.srcObject = stream;
    matchResult.textContent = "Camera active. Capture when the face is centered.";
    matchResult.className = "match-card";
  } catch (error) {
    matchResult.textContent = `Camera access failed: ${error.message}`;
    matchResult.className = "match-card danger";
  }
});

stopCameraButton.addEventListener("click", () => {
  stopCamera();
  matchResult.textContent = "Camera stopped.";
  matchResult.className = "match-card";
});

captureMatchButton.addEventListener("click", async () => {
  if (!modelsReady) {
    matchResult.textContent = "Face models are not loaded.";
    matchResult.className = "match-card danger";
    return;
  }
  if (!stream) {
    matchResult.textContent = "Start the camera first.";
    matchResult.className = "match-card danger";
    return;
  }
  try {
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const context = canvas.getContext("2d");
    context.drawImage(video, 0, 0, canvas.width, canvas.height);
    const descriptor = await detectDescriptorFromImage(canvas);
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, "image/jpeg", 0.92));
    const formData = new FormData();
    formData.append("descriptor", descriptorToString(descriptor));
    formData.append("threshold", thresholdInput.value);
    formData.append("snapshot", blob, "snapshot.jpg");
    await runMatch(formData);
  } catch (error) {
    matchResult.textContent = error.message;
    matchResult.className = "match-card danger";
  }
});

uploadMatchButton.addEventListener("click", async () => {
  if (!modelsReady) {
    matchResult.textContent = "Face models are not loaded.";
    matchResult.className = "match-card danger";
    return;
  }
  const file = uploadMatchImage.files[0];
  if (!file) {
    matchResult.textContent = "Select an image file first.";
    matchResult.className = "match-card danger";
    return;
  }
  try {
    const descriptor = await detectDescriptorFromFile(file);
    const formData = new FormData();
    formData.append("descriptor", descriptorToString(descriptor));
    formData.append("threshold", thresholdInput.value);
    formData.append("snapshot", file, file.name || "upload.jpg");
    await runMatch(formData);
  } catch (error) {
    matchResult.textContent = error.message;
    matchResult.className = "match-card danger";
  }
});

clearHistoryButton.addEventListener("click", async () => {
  try {
    const response = await apiFetch("/api/history", { method: "POST" });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.error || "Could not clear history");
    }
    historyList.innerHTML = `<div class="match-card empty">No match history yet.</div>`;
    matchResult.textContent = "Matching history cleared.";
    matchResult.className = "match-card";
    await loadDashboard();
  } catch (error) {
    matchResult.textContent = error.message;
    matchResult.className = "match-card danger";
  }
});

setAuthState();
loadModels();
loadDashboard().catch(console.error);
loadHistory().catch(console.error);
window.addEventListener("beforeunload", stopCamera);
