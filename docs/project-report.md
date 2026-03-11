# Cloud-Based Missing Person Face Recognition System Using Webcam and Image Matching

## Abstract

This project presents a cloud-based missing person face recognition system designed to help identify missing individuals using facial recognition technology. The system allows authorized users to register missing person records with personal details and multiple reference images. It supports face matching through a live laptop webcam as well as uploaded images. The application uses a Java-based backend for secure data handling, user authentication, PostgreSQL-backed structured storage, match history management, and profile storage. The frontend provides separate pages for login, recognition operations, case management, and admin-only user access. The system also includes email-based account registration, password reset in demo mode, dashboard statistics, history tracking, unknown face grouping, and manual review support.

## Introduction

The increasing number of missing person cases creates a strong need for systems that can assist in quick identification and recovery. In many situations, authorities and families depend on manual search methods, printed notices, or eyewitness reports. These approaches are time-consuming and do not scale well when many cases must be monitored together.

Advances in computer vision and face recognition provide a more efficient solution. A face recognition system can compare a live or uploaded image with a database of stored reference images and identify likely matches. This reduces the dependency on manual observation and helps speed up the search process.

This project implements such a system as a secure web application. Authorized users can add missing person details, capture images from a webcam, upload images for comparison, and review historical scan results. The system demonstrates how face recognition can be applied in a practical and socially useful domain.

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

## Existing System

In the existing approach, missing person identification is commonly handled through printed posters, newspaper announcements, police records maintained manually, visual checking by officers or family members, and social media sharing.

### Drawbacks

- slow and highly manual process
- large chance of human error
- difficult to search across many missing person records
- no automated face comparison
- no centralized digital history of scan attempts
- poor scalability for real-time identification

## Proposed System

The proposed system is a cloud-based missing person face recognition web application. It stores missing person details in a secure backend and allows authorized users to identify potential matches using either a live laptop camera or an uploaded image. The system performs face detection and descriptor extraction using face recognition models, then compares the generated face descriptor against stored records to identify the closest match. PostgreSQL stores users, person records, descriptors, and match history, while images remain in local storage folders.

### Features

- secure user authentication
- email-based signup and sign in
- forgot password flow in demo mode
- registration of missing person details with reference image
- face matching using webcam
- face matching using uploaded images
- match history logging
- dashboard statistics
- user access management by admin
- separate admin-only user access page
- case lifecycle management
- manual review queue for borderline matches
- unknown face tracking
- multi-image enrollment for better matching

## Scope of the Project

The scope of this project is to build a working prototype that demonstrates how missing person identification can be improved using face recognition technology. The system is intended for final-year academic demonstration and small-scale deployment.

### Included

- secure web application
- profile registration
- face comparison using webcam or uploaded image
- match history and dashboard
- local server deployment

### Excluded

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
- Admin users can additionally open the user-access page.
- The frontend captures a webcam image or accepts an uploaded file.
- Face recognition models generate a face descriptor from the image.
- The descriptor is sent to the backend.
- The backend compares it with stored descriptors of registered missing persons using multiple reference descriptors, Euclidean distance, cosine similarity, and confidence scoring.
- The best match is returned to the frontend.
- The match attempt is stored in history.

## Modules Description

### Authentication Module

Handles user sign in, sign up, logout, password change, and forgot password functionality. It ensures that only authorized users can access protected features.

### Missing Person Registration Module

Allows a user to enter person details such as name, age, last seen location, contact, notes, status, and multiple reference images. This information is securely stored in the backend.

### Face Recognition Module

Uses face-api.js to detect faces and generate numerical facial descriptors. These descriptors are compared against stored descriptor sets to find the nearest match.

### Webcam Matching Module

Captures a live image from the laptop webcam and sends it for face matching.

### Uploaded Image Matching Module

Allows the user to upload an external image file and use it for face matching.

### Match History Module

Stores every scan attempt along with time, match result, status, and snapshot path. This helps maintain an audit trail.

### Dashboard Module

Displays system statistics such as number of registered persons, total scans, matched scans, and unmatched scans.

### User Management Module

Allows an admin to create new user accounts, approve case-edit access, and manage user permissions from a separate admin-only page.

### Case Lifecycle Module

Allows investigation-oriented status changes such as Missing, Under Investigation, Sighted, Found, and Closed.

### Review and Unknown Tracking Module

Creates a review queue for borderline AI matches and groups repeated unmatched sightings as tracked unknown faces.

## Technologies Used

- HTML
- CSS
- JavaScript
- Java
- face-api.js
- PostgreSQL
- local image storage

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

The frontend consists of separate pages for login, recognition operations, case management, and admin-only user access. The UI allows account creation, separate admin and user sign-in, webcam capture, uploaded image matching, match history viewing, lifecycle management, and permission-based user management.

The Java backend serves static frontend files and exposes API endpoints for login, signup, logout, password change, forgot password, reset password, missing person registration, match history, dashboard, matching requests, case lifecycle updates, review actions, and user-access management.

Face-api.js is used to detect a face and extract a face descriptor from a reference or probe image. The backend compares descriptors using multiple stored references, Euclidean distance, cosine similarity, and confidence scoring to return the most suitable match.

## Testing and Results

The system was tested using registered person images, live webcam input, and uploaded photos.

### Test Cases

- valid user sign in
- new account creation
- password reset
- person registration with image
- person registration with multiple reference images
- webcam-based matching
- uploaded image matching
- match history clearing
- stop camera functionality
- admin user creation
- case lifecycle update
- admin approval of case-edit access
- review queue verification and rejection

### Observed Results

- users can authenticate successfully
- missing person profiles can be created and stored
- successful matches display person details and image
- unmatched scans are recorded in history
- uploaded image matching works as expected
- dashboard counts update correctly
- PostgreSQL stores users, cases, and match history successfully
- case lifecycle and review workflow operate correctly

## Advantages

- reduces manual effort in missing person identification
- provides quick face-based comparison
- supports both webcam and uploaded images
- includes secure login and user management
- maintains a match history for review
- simple to run on a laptop for demonstration

## Limitations

- accuracy depends on image quality and lighting
- current deployment is local and not large-scale cloud hosted
- forgot password uses demo reset code instead of real email service
- images are still stored on the local file system
- performance may reduce with a very large number of records

## Future Enhancements

- deploy on a real cloud server
- add real email service for password reset
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
