# Project Synopsis

## Title

Cloud-Based Missing Person Face Recognition System Using Webcam and Image Matching

## Abstract

This project proposes a secure web-based missing person face recognition system that helps identify missing individuals using webcam capture and uploaded images. The system stores missing person details and multiple reference images, extracts facial descriptors, and compares them with live or uploaded image inputs to find possible matches. It includes role-based authentication, PostgreSQL-backed storage, match history, dashboard statistics, case lifecycle management, and admin-controlled user access.

## Problem Statement

Traditional missing person identification methods are slow, manual, and difficult to manage at scale. There is a need for a digital system that can store missing person data and automatically compare faces from images with registered records.

## Objectives

- develop a secure missing person face recognition application
- support webcam-based and uploaded-image matching
- provide protected login and account management
- maintain match history, dashboard statistics, and case lifecycle tracking

## Proposed Work

The system uses a Java backend and a browser-based frontend. Authorized users can register missing person records, perform matching through webcam or image upload, and review results. Admin users can manage user access from a separate page, while case management supports lifecycle updates and manual review. Face recognition is implemented using face-api.js models and descriptor comparison with multiple references.

## Technologies Used

- HTML
- CSS
- JavaScript
- Java
- PostgreSQL
- face-api.js

## Expected Outcome

The expected outcome is a working prototype that demonstrates how face recognition can improve the speed and organization of missing person identification.

## Future Scope

- cloud deployment
- real email integration
- CCTV support
- alert system
- stronger server-side AI services
