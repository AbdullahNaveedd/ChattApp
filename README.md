Chat App

1. Authentication
Signup / Login using Firebase Authentication.

On signup, the user’s name and email are saved to Firestore under a Users collection.

2. Contacts (Users List)
In the Contacts Fragment, all users from the Users collection (except the current user) are shown.

You can start a chat with any of these users.

3. Chat System (Firestore)
Chats are stored in a Chats collection.

Each chat document contains:

Sender ID and Receiver ID.

Message type: "text", "image", or "voice".

Timestamp, message content (or image URL / voice file path).

Each chat may have a Messages subcollection for organizing individual messages between two users.

4. Image Messages
Users can send images via:

Gallery (Intent with ACTION_PICK)

Camera (Intent with ACTION_IMAGE_CAPTURE)

Images are uploaded to Firebase Storage, and the download URL is saved in Firestore.

5. Voice Messages
Microphone permission is requested before recording.

Audio is recorded using MediaRecorder, stored as a temporary cache file.

MediaPlayer is used to play/pause the audio.

6. Calls (Voice/Video)
Uses WebRTC via LiveKit for real-time audio/video calling.

To secure the call:

A JWT (JSON Web Token) is generated using Firebase functions or custom backend.

The token is sent to LiveKit Server, which validates it and creates a session.

Calls are established using sender ID and receiver ID, ensuring unique 1-to-1 connections.

8. For Images And Voices Used Cloudinary 
 Store images and vocies in CLoudinary and than show in Chat 
