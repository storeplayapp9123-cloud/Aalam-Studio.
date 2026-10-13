package com.aalamstudio.app;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class CloudSync {

    private static FirebaseFirestore db;
    private static FirebaseAuth auth;

    public interface SaveCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface LoadCallback {
        void onLoaded(String content);
    }

    public static void init() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public static void saveFile(String projectName, String fileKey, String content, SaveCallback callback) {
        if (auth.getCurrentUser() == null) {
            callback.onError("Not signed in");
            return;
        }

        String uid = auth.getCurrentUser().getUid();
        String docId = fileKey.replace("/", "__").replace("::", "__");

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("content", content);
        data.put("updatedAt", System.currentTimeMillis());

        db.collection("users").document(uid)
                .collection("projects").document(projectName)
                .collection("files").document(docId)
                .set(data)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public static void loadFile(String projectName, String fileKey, LoadCallback callback) {
        if (auth.getCurrentUser() == null) {
            callback.onLoaded(null);
            return;
        }

        String uid = auth.getCurrentUser().getUid();
        String docId = fileKey.replace("/", "__").replace("::", "__");

        db.collection("users").document(uid)
                .collection("projects").document(projectName)
                .collection("files").document(docId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        callback.onLoaded(doc.getString("content"));
                    } else {
                        callback.onLoaded(null);
                    }
                })
                .addOnFailureListener(e -> callback.onLoaded(null));
    }

    public static boolean isSignedIn() {
        return auth != null && auth.getCurrentUser() != null;
    }

    public static void signOut() {
        if (auth != null) auth.signOut();
    }
}
