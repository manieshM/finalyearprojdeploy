# Missing Person Face Recognition System

Final-year project starter for a cloud-based missing person recognition system.

## Stack

- Backend: Java 25 using the built-in `HttpServer`
- Frontend: HTML, CSS, vanilla JavaScript
- Recognition: `face-api.js` in the browser
- Storage: local TSV file plus image files under `storage/`

## Why this design

- Java stays in the backend, as requested.
- Webcam access works directly from the browser on your laptop.
- Face recognition does not need Python, OpenCV, or Maven to get the first version running.
- The backend still acts like the cloud system by storing registered profiles centrally and serving match APIs.

## Features implemented

- Register a missing person with details and a reference photo
- Extract a face descriptor from the reference image in the browser
- Store profiles in the Java backend
- Start webcam and capture a live face
- Match captured descriptor against registered profiles using Euclidean distance
- Show best match with stored profile details

## Run

1. Download the required `face-api.js` model weights into `frontend/models/`.
2. From `d:\project2`, run `run.bat`.
3. Open `http://localhost:8080`

## Suggested next upgrades

- Move storage from TSV to PostgreSQL or MySQL
- Add admin login and police-station roles
- Add case status, FIR or case ID, and report history
- Add multi-image enrollment per person
- Add email or SMS alerts on match
- Move recognition to a dedicated backend inference service if your faculty expects server-side AI
