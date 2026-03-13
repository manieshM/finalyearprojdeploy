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
const lastSeenMapElement = document.getElementById("lastSeenMap");
const lastSeenLatitudeInput = document.getElementById("lastSeenLatitude");
const lastSeenLongitudeInput = document.getElementById("lastSeenLongitude");
const mapSelectionStatus = document.getElementById("mapSelectionStatus");
const useCurrentLocationButton = document.getElementById("useCurrentLocation");
const mapModal = document.getElementById("mapModal");
const closeMapModalButton = document.getElementById("closeMapModal");
const lastSeenMapExpandedElement = document.getElementById("lastSeenMapExpanded");
const duplicateModal = document.getElementById("duplicateModal");
const duplicateMessage = document.getElementById("duplicateMessage");
const duplicateLastSeen = document.getElementById("duplicateLastSeen");
const duplicateNotes = document.getElementById("duplicateNotes");
const closeDuplicateModalButton = document.getElementById("closeDuplicateModal");
const confirmDuplicateUpdateButton = document.getElementById("confirmDuplicateUpdate");

let stream;
let modelsReady = false;
let authToken = localStorage.getItem("mpr_token") || "";
let currentUser = localStorage.getItem("mpr_user") || "";
let pendingDuplicate = null;
let lastSeenMap;
let lastSeenMarker;
let lastSeenMapExpanded;
let lastSeenMarkerExpanded;
let matchLocationMap;

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

function updateLocationInputs(lat, lng) {
  const normalizedLat = Number(lat).toFixed(6);
  const normalizedLng = Number(lng).toFixed(6);
  lastSeenLatitudeInput.value = normalizedLat;
  lastSeenLongitudeInput.value = normalizedLng;
  mapSelectionStatus.textContent = `Pinned location: ${normalizedLat}, ${normalizedLng}`;
}

function setMapPin(lat, lng, zoom = 15) {
  if (!window.L) {
    return;
  }
  const point = [lat, lng];
  if (lastSeenMap && !lastSeenMarker) {
    lastSeenMarker = window.L.marker(point, { draggable: true }).addTo(lastSeenMap);
    lastSeenMarker.on("dragend", (event) => {
      const next = event.target.getLatLng();
      updateLocationInputs(next.lat, next.lng);
      setMapPin(next.lat, next.lng, lastSeenMap.getZoom());
    });
  } else if (lastSeenMarker) {
    lastSeenMarker.setLatLng(point);
  }
  if (lastSeenMap) {
    lastSeenMap.setView(point, zoom);
  }
  if (lastSeenMapExpanded && !lastSeenMarkerExpanded) {
    lastSeenMarkerExpanded = window.L.marker(point, { draggable: true }).addTo(lastSeenMapExpanded);
    lastSeenMarkerExpanded.on("dragend", (event) => {
      const next = event.target.getLatLng();
      updateLocationInputs(next.lat, next.lng);
      setMapPin(next.lat, next.lng, lastSeenMapExpanded.getZoom());
    });
  } else if (lastSeenMarkerExpanded) {
    lastSeenMarkerExpanded.setLatLng(point);
  }
  if (lastSeenMapExpanded) {
    lastSeenMapExpanded.setView(point, zoom);
  }
  updateLocationInputs(lat, lng);
}

function initializeLastSeenMap() {
  if (!lastSeenMapElement || !window.L) {
    if (mapSelectionStatus) {
      mapSelectionStatus.textContent = "Map could not be loaded. You can still enter the location text manually.";
    }
    return;
  }
  lastSeenMap = window.L.map(lastSeenMapElement, { zoomControl: true }).setView([13.0827, 80.2707], 11);
  window.L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19,
    attribution: "&copy; OpenStreetMap contributors"
  }).addTo(lastSeenMap);
  lastSeenMap.on("click", () => {
    openMapModal();
  });
  setTimeout(() => lastSeenMap.invalidateSize(), 100);
}

function initializeExpandedLastSeenMap() {
  if (!lastSeenMapExpandedElement || !window.L || lastSeenMapExpanded) {
    return;
  }
  lastSeenMapExpanded = window.L.map(lastSeenMapExpandedElement, { zoomControl: true }).setView([13.0827, 80.2707], 11);
  window.L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19,
    attribution: "&copy; OpenStreetMap contributors"
  }).addTo(lastSeenMapExpanded);
  lastSeenMapExpanded.on("click", (event) => {
    setMapPin(event.latlng.lat, event.latlng.lng);
  });
}

function openMapModal() {
  if (!mapModal) {
    return;
  }
  mapModal.showModal();
  initializeExpandedLastSeenMap();
  const currentLat = Number(lastSeenLatitudeInput.value);
  const currentLng = Number(lastSeenLongitudeInput.value);
  if (!Number.isNaN(currentLat) && !Number.isNaN(currentLng)) {
    setMapPin(currentLat, currentLng, 16);
  }
  setTimeout(() => lastSeenMapExpanded?.invalidateSize(), 120);
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

function faceDetectionOptions() {
  return new faceapi.TinyFaceDetectorOptions({ inputSize: 320, scoreThreshold: 0.45 });
}

function createProcessedFaceCanvas(source, box) {
  const canvas = document.createElement("canvas");
  const context = canvas.getContext("2d");
  const sourceWidth = source.videoWidth || source.naturalWidth || source.width;
  const sourceHeight = source.videoHeight || source.naturalHeight || source.height;
  const paddingX = box.width * 0.22;
  const paddingY = box.height * 0.28;
  const startX = Math.max(0, box.x - paddingX);
  const startY = Math.max(0, box.y - paddingY);
  const cropWidth = Math.min(sourceWidth - startX, box.width + (paddingX * 2));
  const cropHeight = Math.min(sourceHeight - startY, box.height + (paddingY * 2));

  canvas.width = 320;
  canvas.height = 320;
  context.fillStyle = "#0a1018";
  context.fillRect(0, 0, canvas.width, canvas.height);
  context.filter = "brightness(1.08) contrast(1.08) saturate(1.02)";
  context.imageSmoothingEnabled = true;
  context.imageSmoothingQuality = "high";
  context.drawImage(source, startX, startY, cropWidth, cropHeight, 0, 0, canvas.width, canvas.height);
  context.filter = "none";
  return canvas;
}

async function extractDescriptorWithPreprocessing(source) {
  const initialDetection = await faceapi
    .detectSingleFace(source, faceDetectionOptions())
    .withFaceLandmarks();
  if (!initialDetection) {
    throw new Error("No clear face detected. Use a front-facing image with good lighting.");
  }

  const processedCanvas = createProcessedFaceCanvas(source, initialDetection.detection.box);
  const refinedDetection = await faceapi
    .detectSingleFace(processedCanvas, faceDetectionOptions())
    .withFaceLandmarks()
    .withFaceDescriptor();

  if (refinedDetection) {
    return Array.from(refinedDetection.descriptor);
  }

  const fallbackDetection = await faceapi
    .detectSingleFace(source, faceDetectionOptions())
    .withFaceLandmarks()
    .withFaceDescriptor();
  if (!fallbackDetection) {
    throw new Error("Face detected, but descriptor extraction failed. Try a clearer image.");
  }
  return Array.from(fallbackDetection.descriptor);
}

async function detectDescriptorFromImage(image) {
  return extractDescriptorWithPreprocessing(image);
}

function descriptorToString(descriptor) {
  return descriptor.join(",");
}

function descriptorSetToString(descriptors) {
  return descriptors.map((descriptor) => descriptorToString(descriptor)).join("|");
}

function showDuplicateModal(data = {}) {
  if (!duplicateModal) {
    return;
  }
  pendingDuplicate = data;
  duplicateMessage.textContent = data.message
    || "This image already exists in our database. You can update only the existing last-seen details and notes.";
  duplicateLastSeen.textContent = data.lastSeen || "Not available.";
  duplicateNotes.textContent = data.notes || "Not available.";
  if (!duplicateModal.open) {
    duplicateModal.showModal();
  }
}

function buildRegistrationFormData(files, descriptors) {
  const formData = new FormData(registerForm);
  formData.set("image", files[0]);
  formData.append("descriptor", descriptorToString(descriptors[0]));
  formData.append("descriptorSet", descriptorSetToString(descriptors));
  return formData;
}

function stopCamera() {
  if (stream) {
    stream.getTracks().forEach((track) => track.stop());
    stream = null;
  }
  video.srcObject = null;
}

function destroyMatchLocationMap() {
  if (matchLocationMap) {
    matchLocationMap.remove();
    matchLocationMap = null;
  }
}

function renderMatchLocationMap(person) {
  destroyMatchLocationMap();
  const latitude = Number(person?.latitude);
  const longitude = Number(person?.longitude);
  const mapElement = document.getElementById("matchResultMap");
  if (!mapElement || Number.isNaN(latitude) || Number.isNaN(longitude) || !window.L) {
    return;
  }
  const point = [latitude, longitude];
  matchLocationMap = window.L.map(mapElement, { zoomControl: true, dragging: true }).setView(point, 15);
  window.L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19,
    attribution: "&copy; OpenStreetMap contributors"
  }).addTo(matchLocationMap);
  window.L.marker(point).addTo(matchLocationMap);
  mapElement.addEventListener("click", () => {
    mapElement.classList.toggle("expanded");
    setTimeout(() => matchLocationMap?.invalidateSize(), 120);
  });
  setTimeout(() => matchLocationMap.invalidateSize(), 120);
}

async function runMatch(formData) {
  const response = await apiFetch("/api/match", { method: "POST", body: formData });
  const data = await response.json();
  if (!data.matched) {
    destroyMatchLocationMap();
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
    <p class="muted">Confidence: ${data.confidence != null ? `${Number(data.confidence).toFixed(2)}%` : "N/A"}</p>
    <p class="muted">Name: ${data.person.name || "Not specified"}</p>
    <p class="muted">Age: ${data.person.age || "Not specified"}</p>
    <p class="muted">Notes: ${data.person.notes || "Not specified"}</p>
    <p class="muted">Contact: ${data.person.contact || "Not specified"}</p>
    <p class="muted">Last Seen: ${data.person.lastSeen || "Not specified"}</p>
    ${data.person.latitude && data.person.longitude ? `
      <p class="muted">Pinned Location: ${Number(data.person.latitude).toFixed(6)}, ${Number(data.person.longitude).toFixed(6)}</p>
      <div id="matchResultMap" class="map-canvas mini-map" title="Click to expand the map"></div>
    ` : ""}
    ${data.person.imageUrl ? `
      <img
        src="${data.person.imageUrl}?v=${encodeURIComponent(data.person.createdAt || Date.now())}"
        alt="${data.person.name}"
        style="width:100%;max-width:260px;border-radius:14px;margin-top:12px;object-fit:cover;display:block;"
        onerror="this.replaceWith(Object.assign(document.createElement('p'), { className: 'muted', textContent: 'Profile image could not be loaded.' }))"
      >
    ` : ""}
  `;
  matchResult.className = "match-card success";
  renderMatchLocationMap(data.person);
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
    const formData = buildRegistrationFormData(files, descriptors);
    const response = await apiFetch("/api/persons", { method: "POST", body: formData });
    const data = await response.json();
    if (!response.ok) {
      if (response.status === 409 && data.duplicate) {
        showDuplicateModal(data);
        registerMessage.textContent = "Matching person found. You can update only the existing last seen and notes.";
        registerMessage.className = "message danger";
        return;
      }
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

if (closeDuplicateModalButton) {
  closeDuplicateModalButton.addEventListener("click", () => {
    pendingDuplicate = null;
    duplicateModal.close();
  });
}

if (useCurrentLocationButton) {
  useCurrentLocationButton.addEventListener("click", async () => {
    if (!navigator.geolocation) {
      mapSelectionStatus.textContent = "Geolocation is not available in this browser.";
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setMapPin(position.coords.latitude, position.coords.longitude, 16);
      },
      () => {
        mapSelectionStatus.textContent = "Current location could not be retrieved. You can place the pin manually.";
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
    );
  });
}

if (closeMapModalButton) {
  closeMapModalButton.addEventListener("click", () => {
    mapModal.close();
    setTimeout(() => lastSeenMap?.invalidateSize(), 120);
  });
}

if (confirmDuplicateUpdateButton) {
  confirmDuplicateUpdateButton.addEventListener("click", async () => {
    const personId = pendingDuplicate?.person?.id;
    const formData = new FormData(registerForm);
    const lastSeen = String(formData.get("lastSeen") || "").trim();
    const notes = String(formData.get("notes") || "").trim();
    const latitude = String(formData.get("latitude") || "").trim();
    const longitude = String(formData.get("longitude") || "").trim();
    if (!personId) {
      registerMessage.textContent = "Duplicate person details are missing.";
      registerMessage.className = "message danger";
      return;
    }
    if (!lastSeen && !notes && !(latitude && longitude)) {
      registerMessage.textContent = "Enter last seen details, notes, or select a map pin before updating.";
      registerMessage.className = "message danger";
      return;
    }
    try {
      const updateForm = new FormData();
      updateForm.append("action", "update-last-seen-notes");
      updateForm.append("personId", personId);
      updateForm.append("lastSeen", lastSeen);
      updateForm.append("notes", notes);
      updateForm.append("latitude", latitude);
      updateForm.append("longitude", longitude);
      const response = await apiFetch("/api/persons", {
        method: "POST",
        body: updateForm
      });
      const data = await response.json();
      if (!response.ok) {
        throw new Error(data.error || "Could not update person details.");
      }
      registerMessage.textContent = `Last seen and notes updated for ${data.person.name}.`;
      registerMessage.className = "message success";
      pendingDuplicate = null;
      duplicateModal.close();
      await loadDashboard();
    } catch (error) {
      registerMessage.textContent = error.message;
      registerMessage.className = "message danger";
    }
  });
}

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
initializeLastSeenMap();
loadModels();
loadDashboard().catch(console.error);
loadHistory().catch(console.error);
window.addEventListener("beforeunload", stopCamera);
