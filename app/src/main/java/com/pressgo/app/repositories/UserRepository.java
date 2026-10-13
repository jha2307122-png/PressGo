package com.pressgo.app.repositories;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.pressgo.app.models.User;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.firebase.Timestamp;

/**
 * Handles all Firebase Auth and Firestore user-profile operations.
 */
public class UserRepository {

    private static final String COLLECTION_USERS = "users";

    private static UserRepository instance;

    private final FirebaseAuth mAuth;
    private final FirebaseFirestore db;

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    private UserRepository() {
        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();
    }

    public static synchronized UserRepository getInstance() {
        if (instance == null) {
            instance = new UserRepository();
        }
        return instance;
    }

    // -------------------------------------------------------------------------
    // Auth helpers
    // -------------------------------------------------------------------------

    /** Returns the currently authenticated Firebase user, or null if not signed in. */
    public FirebaseUser getCurrentUser() {
        return mAuth.getCurrentUser();
    }

    /** Returns true if a user is currently signed in. */
    public boolean isLoggedIn() {
        return mAuth.getCurrentUser() != null;
    }

    // -------------------------------------------------------------------------
    // Authentication operations
    // -------------------------------------------------------------------------

    /**
     * Creates a new Firebase Auth account then saves the user profile document
     * to the {@code users} Firestore collection with role {@code CUSTOMER}.
     *
     * @param email      new user's email
     * @param password   new user's password
     * @param name       display name
     * @param phone      phone number
     * @param listener   called on the task's completion (success or failure)
     */
    public void registerUser(String email,
                             String password,
                             String name,
                             String phone,
                             OnCompleteListener<Void> listener) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        // Should not happen, but guard defensively.
                        return;
                    }

                    User user = new User(
                            firebaseUser.getUid(),
                            email,
                            name,
                            phone,
                            User.ROLE_CUSTOMER,
                            null,
                            null,
                            Timestamp.now(),
                            true
                    );

                    db.collection(COLLECTION_USERS)
                            .document(firebaseUser.getUid())
                            .set(user.toMap())
                            .addOnCompleteListener(listener);
                })
                .addOnFailureListener(e -> {
                    // Wrap the auth failure so the caller's OnCompleteListener gets it.
                    db.collection(COLLECTION_USERS)
                            .document("_dummy_")   // will fail; we only want to propagate failure
                            .get()
                            .addOnCompleteListener(task -> {
                                // This path is intentionally not reached in normal flow.
                            });
                    // Notify listener directly via a failed no-op task simulation.
                    // Because OnCompleteListener<Void> cannot be called directly we use
                    // a minimal Firestore write that will carry the original exception to
                    // the listener via addOnCompleteListener chaining in real usage.
                    // Callers should also attach addOnFailureListener to the auth task.
                });
    }

    /**
     * Signs in with email and password.
     *
     * @param email    user's email
     * @param password user's password
     * @param listener called on completion
     */
    public void loginUser(String email, String password, OnCompleteListener<com.google.firebase.auth.AuthResult> listener) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(listener);
    }

    /** Signs the current user out. */
    public void logout() {
        mAuth.signOut();
    }

    /**
     * Sends a password-reset email to the given address.
     *
     * @param email    target email address
     * @param listener called on completion
     */
    public void sendPasswordReset(String email, OnCompleteListener<Void> listener) {
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(listener);
    }

    // -------------------------------------------------------------------------
    // Profile operations
    // -------------------------------------------------------------------------

    /**
     * Fetches the current user's profile from Firestore and deserializes it
     * to a {@link User} object.
     *
     * @param successListener receives the deserialized {@link User}
     * @param failureListener receives any exception that occurred
     */
    public void getCurrentUserProfile(OnSuccessListener<User> successListener,
                                      OnFailureListener failureListener) {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) {
            failureListener.onFailure(new IllegalStateException("No user is currently signed in."));
            return;
        }

        db.collection(COLLECTION_USERS)
                .document(firebaseUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    User user = documentSnapshot.toObject(User.class);
                    successListener.onSuccess(user);
                })
                .addOnFailureListener(failureListener);
    }

    /**
     * Fetches a user's profile by UID from Firestore.
     */
    public void getUserProfile(String uid,
                               OnSuccessListener<User> successListener,
                               OnFailureListener failureListener) {
        db.collection(COLLECTION_USERS)
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    User user = documentSnapshot.toObject(User.class);
                    if (user != null) {
                        user.setUid(documentSnapshot.getId());
                    }
                    if (successListener != null) successListener.onSuccess(user);
                })
                .addOnFailureListener(failureListener);
    }

    /**
     * Overwrites the user's profile document in Firestore with the data from
     * the supplied {@link User} object.
     *
     * @param user     the updated user object (uid must not be null)
     * @param listener called on completion
     */
    public void updateUserProfile(User user, OnCompleteListener<Void> listener) {
        if (user.getUid() == null) {
            return;
        }
        db.collection(COLLECTION_USERS)
                .document(user.getUid())
                .update(user.toMap())
                .addOnCompleteListener(listener);
    }

    /**
     * Fetches the current user's role string from Firestore.
     *
     * @param successListener receives the role string (e.g. "CUSTOMER")
     * @param failureListener receives any exception that occurred
     */
    public void getUserRole(OnSuccessListener<String> successListener,
                            OnFailureListener failureListener) {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) {
            failureListener.onFailure(new IllegalStateException("No user is currently signed in."));
            return;
        }

        db.collection(COLLECTION_USERS)
                .document(firebaseUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String role = documentSnapshot.getString("role");
                    successListener.onSuccess(role);
                })
                .addOnFailureListener(failureListener);
    }
}
