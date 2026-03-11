# Viva Questions and Answers

## 1. What is the aim of your project?

The aim of the project is to identify missing persons using face recognition through webcam input or uploaded images and compare them with stored profiles in a secure system.

## 2. Why did you choose this topic?

I chose this topic because missing person identification is a socially important problem and face recognition offers a modern technical solution with real-world relevance.

## 3. Why is the project called cloud-based if it runs on a laptop?

It is called cloud-based because the system uses a centralized server-based architecture where data and matching logic are handled through a backend. For demonstration, the server is hosted on a laptop, but the same design can be deployed to the cloud.

## 4. What technologies are used in the project?

The project uses HTML, CSS, JavaScript for the frontend, Java for the backend, PostgreSQL for structured storage, and face-api.js for face recognition.

## 5. Why did you use Java for the backend?

Java is stable, secure, widely used in backend development, and suitable for handling API requests, storage logic, and authentication.

## 6. How does face recognition work in your system?

The system extracts a face descriptor from an image and compares it with stored face descriptors using multiple reference images, Euclidean distance, cosine similarity, and confidence scoring. If the result is within the threshold, the system reports a match.

## 7. What is a face descriptor?

A face descriptor is a numerical representation of facial features used by the system to compare one face with another.

## 8. What input sources are supported?

The system supports live webcam capture and uploaded image files.

## 9. How is user authentication handled?

The system supports separate admin and user login, sign up, password change, logout, and a forgot-password reset flow in demo mode. Admin also controls user case-edit approval.

## 10. Why did you add match history?

Match history helps maintain an audit record of all scan attempts and is useful for review, analysis, and tracking.

## 11. What are the limitations of the project?

The project depends on image quality, runs locally, uses demo-mode password reset, and still stores images on the local file system even though structured data is stored in PostgreSQL.

## 12. What are the future improvements?

Real email reset, cloud hosting, CCTV integration, object storage for images, and stronger server-side AI services.
