# Cloud-Based Missing Person Face Recognition System Using Webcam and Image Matching

## Project Abstract

This project presents a cloud-based missing person face recognition system designed to help identify missing individuals using facial recognition technology. The system allows authorized users to register missing person records with personal details and multiple reference images. It supports face matching through a live laptop webcam as well as uploaded images. The application uses a Java-based backend for secure data handling, user authentication, match history management, and profile storage. The frontend provides separate pages for login, recognition dashboard, case management, and admin-only user access, enabling secure access to recognition features. The system also includes email-based account registration, password reset in demo mode, dashboard statistics, PostgreSQL-backed structured data storage, and history tracking. This project aims to provide a practical, low-cost, and scalable approach for assisting authorities or organizations in locating missing persons using digital image analysis.

## Problem Statement

Missing person cases are difficult to manage efficiently when identification depends only on manual observation, posters, or human memory. Traditional methods are slow, less accurate, and difficult to scale when large numbers of records are involved. There is a need for a system that can digitally store missing person profiles and automatically compare faces from live camera input or uploaded images with stored records. Such a system can help improve identification speed, reduce manual effort, and support faster response in missing person investigations.

## Objectives

- To develop a secure missing person face recognition system
- To register and store missing person details with reference images
- To identify matching persons using webcam capture
- To support matching using uploaded images
- To provide user authentication with sign in, sign up, and password reset
- To maintain match history for review and auditing
- To provide a simple dashboard for system monitoring
- To create a practical final-year project with real-world relevance

## Introduction

The increasing number of missing person cases creates a strong need for systems that can assist in quick identification and recovery. In many situations, authorities and families depend on manual search methods, printed notices, or eyewitness reports. These approaches are time-consuming and do not scale well when many cases must be monitored together.

Advances in computer vision and face recognition provide a more efficient solution. A face recognition system can compare a live or uploaded image with a database of stored reference images and identify likely matches. This reduces the dependency on manual observation and helps speed up the search process.

This project implements such a system as a secure web application. Authorized users can add missing person details, capture images from a webcam, upload images for comparison, and review historical scan results. The system demonstrates how face recognition can be applied in a practical and socially useful domain.

## Existing System

In the existing approach, missing person identification is commonly handled through:

- printed posters
- newspaper announcements
- police records maintained manually
- visual checking by officers or family members
- social media sharing

### Drawbacks of the Existing System

- slow and highly manual process
- large chance of human error
- difficult to search across many missing person records
- no automated face comparison
- no centralized digital history of scan attempts
- poor scalability for real-time identification

## Proposed System

The proposed system is a cloud-based missing person face recognition web application. It stores missing person details in a secure backend and allows authorized users to identify potential matches using either a live laptop camera or an uploaded image. The system performs face detection and descriptor extraction using face recognition models, then compares the generated face descriptor against stored records to identify the closest match. PostgreSQL is used to store accounts, missing person records, descriptors, and match history, while image files remain on the local server file system.

### Features of the Proposed System

- secure user authentication
- email-based signup and sign in
- forgot password flow in demo mode
- registration of missing person details with reference image
- face matching using webcam
- face matching using uploaded images
- match history logging
- dashboard statistics
- user access management by admin on a separate page
- PostgreSQL database integration
- backend confidence scoring using distance and cosine similarity

## Scope of the Project

The scope of this project is to build a working prototype that demonstrates how missing person identification can be improved using face recognition technology. The system is intended for final-year academic demonstration and small-scale deployment.

### Included in Scope

- secure web application
- profile registration
- face comparison using webcam or uploaded image
- match history and dashboard
- local server deployment

### Excluded from Scope

- real police database integration
- live CCTV network integration
- large-scale cloud hosting
- real email delivery service
- production-level biometric compliance

## System Requirements

### Hardware Requirements

- laptop or desktop computer
- webcam
- minimum 4 GB RAM
- minimum dual-core processor
- internet browser

### Software Requirements

- Windows operating system
- Java JDK
- browser with webcam support
- face-api.js model files

## Architecture Design

The system is divided into three major parts:

1. Frontend web interface
2. Java backend server
3. PostgreSQL database and image repository

### Architecture Description

- The user opens the login page and authenticates.
- After login, the user is redirected to the recognition dashboard.
- Admin users can open a separate user-access page.
- The frontend captures a webcam image or accepts an uploaded file.
- Face recognition models generate a face descriptor from the image.
- The descriptor is sent to the backend.
- The backend compares it with stored descriptors of registered missing persons using Euclidean distance and cosine similarity.
- The best match is returned to the frontend.
- The match attempt is stored in history.

## Data Flow

### Registration Flow

1. Admin or authorized user logs in
2. Missing person details are entered
3. Reference image is uploaded
4. Face descriptor is extracted
5. Data is stored by the backend in PostgreSQL and local image folders

### Matching Flow

1. User starts webcam or uploads an image
2. Face descriptor is extracted
3. Descriptor is sent to backend
4. Backend compares it with stored records using confidence scoring
5. Best match is returned
6. Match history is updated

## Modules Description

### 1. Authentication Module

Handles user sign in, sign up, logout, password change, and forgot password functionality. It ensures that only authorized users can access protected features.

### 2. Missing Person Registration Module

Allows a user to enter person details such as name, age, last seen location, contact, notes, status, and reference image. This information is securely stored in the backend.

### 3. Face Recognition Module

Uses face-api.js to detect faces and generate numerical facial descriptors. These descriptors are compared against stored descriptors to find the nearest match using backend scoring based on Euclidean distance and cosine similarity.

### 4. Webcam Matching Module

Captures a live image from the laptop webcam and sends it for face matching.

### 5. Uploaded Image Matching Module

Allows the user to upload an external image file and use it for face matching.

### 6. Match History Module

Stores every scan attempt along with time, match result, status, and snapshot path. This helps maintain an audit trail.

### 7. Dashboard Module

Displays system statistics such as number of registered persons, total scans, matched scans, and unmatched scans.

### 8. User Management Module

Allows an admin to create new user accounts and approve or revoke case-edit access from a separate user-access page.

## Technologies Used

### Frontend

- HTML
- CSS
- JavaScript

### Backend

- Java
- built-in Java HTTP server

### Face Recognition

- face-api.js

### Storage

- PostgreSQL
- image file storage

## Methodology

The project follows a prototype-based development methodology. The system was designed, implemented, tested, and refined in multiple stages.

### Development Steps

1. Problem identification
2. Requirement analysis
3. Interface design
4. Backend implementation
5. Face recognition integration
6. Authentication and security improvements
7. Testing and validation

## Implementation Details

### Frontend Implementation

The frontend consists of separate pages for login, recognition dashboard, case management, and admin-only user access. The UI allows account creation, separate admin and user sign in, webcam capture, uploaded image matching, match history viewing, lifecycle updates, and permission-based user management.

### Backend Implementation

The Java backend serves static frontend files and exposes REST-style API endpoints for:

- separate admin and user login
- signup
- logout
- password change
- forgot password
- reset password
- missing person registration
- match history
- dashboard
- matching requests
- user creation

### Recognition Implementation

Face-api.js is used to detect a face and extract a face descriptor from a reference or probe image. The backend compares descriptors using Euclidean distance together with cosine similarity and returns the most suitable match if it is within a threshold. The system also calculates a confidence score to support match interpretation.

## Testing and Results

The system was tested using registered person images, live webcam input, and uploaded photos.

### Test Cases

- valid user sign in
- new account creation
- password reset
- person registration with image
- webcam-based matching
- uploaded image matching
- match history clearing
- stop camera functionality
- admin user creation and approval of case-edit access

### Observed Results

- users can authenticate successfully
- missing person profiles can be created and stored
- successful matches display person details and image
- unmatched scans are recorded in history
- uploaded image matching works as expected
- dashboard counts update correctly
- PostgreSQL stores users, person records, descriptors, and match history successfully

## Advantages

- reduces manual effort in missing person identification
- provides quick face-based comparison
- supports both webcam and uploaded images
- includes secure login and user management
- maintains a match history for review
- simple to run on a laptop for demonstration
- uses structured PostgreSQL storage for better persistence and scalability
- provides confidence-based backend matching output

## Limitations

- accuracy depends on image quality and lighting
- current deployment is local and not large-scale cloud hosted
- forgot password uses demo reset code instead of real email service
- image files are still stored on the local file system
- performance may reduce with a very large number of records

## Future Enhancements

- deploy on a real cloud server
- add real email service for password reset
- support multiple reference images per person
- integrate live CCTV feeds
- add SMS or email alerts for positive matches
- add advanced reporting and PDF export
- improve recognition performance with server-side deep learning services

## Conclusion

The Cloud-Based Missing Person Face Recognition System successfully demonstrates how face recognition technology can assist in identifying missing persons through digital records and image comparison. The project provides a secure and practical web-based solution that supports authentication, person registration, webcam recognition, uploaded image matching, and history tracking. It is suitable as a final-year academic project because it addresses a real-world problem, uses modern technology, and demonstrates both software engineering and AI-based problem solving.

## References

1. face-api.js documentation
2. Java official documentation
3. Computer vision and face recognition research references
4. Web development documentation for HTML, CSS, and JavaScript

---

# PPT Content

## Slide 1: Title

Cloud-Based Missing Person Face Recognition System Using Webcam and Image Matching

Developed by Maniesh, Sree Harish, Pragas Pathy, and Suwin

## Slide 2: Problem Statement

- missing person identification is mostly manual
- traditional methods are slow and inefficient
- large datasets are difficult to manage manually
- face recognition can improve speed and accuracy

## Slide 3: Objectives

- secure missing person identification system
- webcam and uploaded image matching
- protected user access
- dashboard and history tracking

## Slide 4: Existing System

- posters and newspapers
- social media sharing
- manual police records
- no automated matching

## Slide 5: Proposed System

- digital missing person profile storage
- face recognition matching
- separate admin and user login system
- dashboard and history

## Slide 6: Architecture

- frontend
- Java backend
- PostgreSQL database
- face recognition model

## Slide 7: Modules

- authentication
- registration
- webcam matching
- image upload matching
- history
- user management

## Slide 8: Technology Stack

- HTML, CSS, JavaScript
- Java backend
- PostgreSQL
- face-api.js
- local image storage

## Slide 9: Working Flow

- login
- register person
- capture or upload image
- match result
- history update

## Slide 10: Results

- successful matching
- dashboard statistics
- image display after match
- history records

## Slide 11: Limitations

- image quality dependent
- no real email sending
- local hosting

## Slide 12: Future Enhancements

- real email service
- cloud deployment
- CCTV integration
- stronger server-side AI services

## Slide 13: Conclusion

The system demonstrates a practical and secure approach for missing person identification using face recognition technology.

---

# Viva Questions and Answers

## 1. What is the aim of your project?

The aim of the project is to identify missing persons using face recognition through webcam input or uploaded images and compare them with stored profiles in a secure system.

## 2. Why did you choose this topic?

I chose this topic because missing person identification is a socially important problem and face recognition offers a modern technical solution with real-world relevance.

## 3. Why is the project called cloud-based if it runs on a laptop?

It is called cloud-based because the system uses a centralized server-based architecture where data and matching logic are handled through a backend. For demonstration, the server is hosted on a laptop, but the same design can be deployed to the cloud.

## 4. What technologies are used in the project?

The project uses HTML, CSS, JavaScript for the frontend, Java for the backend, PostgreSQL for structured data storage, and face-api.js for face recognition.

## 5. Why did you use Java for the backend?

Java is stable, secure, widely used in backend development, and suitable for handling API requests, storage logic, and authentication.

## 6. How does face recognition work in your system?

The system extracts a face descriptor from an image and compares it with stored face descriptors using Euclidean distance and cosine similarity. If the result is within the threshold and produces a strong confidence score, the system reports a match.

## 7. What is a face descriptor?

A face descriptor is a numerical representation of facial features used by the system to compare one face with another.

## 8. What input sources are supported?

The system supports live webcam capture and uploaded image files.

## 9. How is user authentication handled?

The system supports sign up, sign in, password change, logout, and a forgot-password reset flow in demo mode.

## 10. Why did you add match history?

Match history helps maintain an audit record of all scan attempts and is useful for review, analysis, and tracking.

## 11. What are the limitations of the project?

The project depends on image quality, runs locally, uses demo-mode password reset, and still stores images on the local file system instead of object storage or cloud storage.

## 12. What are the future improvements?

Real email reset, cloud hosting, multi-image enrollment, CCTV integration, and stronger server-side AI services.

---

# Demo Script

## Demo Flow

1. Open the login page
2. Show sign up and sign in
3. Log in using an account
4. Explain dashboard statistics
5. Register a missing person with image
6. Use webcam matching
7. Use uploaded image matching
8. Show matched result with image
9. Show match history
10. Show clear history
11. Show stop camera
12. Show user creation and access approval on the separate admin page
13. Show account security option

## Short Demo Explanation

This system is designed to help identify missing persons using face recognition. Users can securely log in, register missing person records, and then check a live or uploaded image against stored profiles. If a match is found, the system shows the identified person and stores the event in history.

---

# Suggested Screenshots for Report

- login page
- sign up page or signup card
- forgot password card
- dashboard page
- registration form
- webcam matching
- uploaded image matching
- successful match result
- match history
- user access panel

---

# Final Submission Checklist

- project title finalized
- report written
- PPT prepared
- screenshots collected
- demo tested
- viva questions practiced
- references added
