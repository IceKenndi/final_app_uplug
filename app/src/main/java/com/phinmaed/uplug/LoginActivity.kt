package com.phinmaed.uplug

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleClient: GoogleSignInClient
    private val db by lazy { FirebaseFirestore.getInstance() }

    private val googleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val token = account.idToken
                if (token.isNullOrEmpty()) {
                    toast("Google sign-in failed: missing token")
                    return@registerForActivityResult
                }
                firebaseAuthWithGoogle(token)
            } catch (e: ApiException) {
                toast("Google sign-in failed: ${e.statusCode}")
                Log.e("LoginActivity", "Google sign-in ApiException", e)
            } catch (e: Exception) {
                toast("Google sign-in failed: ${e.message}")
                Log.e("LoginActivity", "Google sign-in Exception", e)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        auth.currentUser?.let { u ->
            upsertUserDoc(
                u.uid,
                u.email ?: "",
                u.displayName ?: ""
            )
            return
        }

        setContentView(R.layout.activity_login)

        animateLoginIntro()

        val webClientId = getString(R.string.default_web_client_id)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()

        googleClient = GoogleSignIn.getClient(this, gso)

        findViewById<MaterialButton>(R.id.googleBtn).setOnClickListener {
            googleLauncher.launch(googleClient.signInIntent)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)

        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) {
                    toast("Auth failed: user is null")
                    return@addOnSuccessListener
                }

                val email = user.email ?: ""
                if (!email.endsWith("@phinmaed.com", ignoreCase = true)) {
                    forceSignOut("Please use your PHINMAED account.")
                    return@addOnSuccessListener
                }

                upsertUserDoc(user.uid, email, user.displayName ?: "")
            }
            .addOnFailureListener { e ->
                toast("Auth failed: ${e.message}")
                Log.e("LoginActivity", "Firebase auth failed", e)
            }
    }

    private fun upsertUserDoc(uid: String, email: String, displayName: String) {
        val userRef = db.collection("users").document(uid)
        val authUser = FirebaseAuth.getInstance().currentUser
        val photoURL = authUser?.photoUrl?.toString() ?: ""

        userRef.get().addOnSuccessListener { doc ->
            val role = doc.getString("role") ?: "student"
            val deptWall = doc.getString("deptWall") ?: ""

            val data = hashMapOf<String, Any>(
                "email" to email,
                "displayName" to displayName,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            if (!doc.exists()) {
                data["role"] = "student"
                data["createdAt"] = FieldValue.serverTimestamp()
                data["canPostDept"] = false
            }

            userRef.set(data, SetOptions.merge())
                .addOnSuccessListener {
                    val finalRole = if (doc.exists()) role else "student"
                    val finalDeptWall = if (doc.exists()) deptWall else ""

                    upsertPublicProfile(
                        uid = uid,
                        displayName = displayName,
                        role = finalRole,
                        photoURL = photoURL,
                        deptWall = finalDeptWall
                    )

                    routeUser(uid, email)
                }
                .addOnFailureListener { e ->
                    toast("User setup failed: ${e.message}")
                    Log.e("LoginActivity", "Upsert /users failed", e)
                }
        }.addOnFailureListener { e ->
            toast("User read failed: ${e.message}")
            Log.e("LoginActivity", "Read /users failed", e)
        }
    }

    private fun upsertPublicProfile(
        uid: String,
        displayName: String,
        role: String,
        photoURL: String,
        deptWall: String
    ) {
        val profileRef = db.collection("publicProfiles").document(uid)

        val data = hashMapOf<String, Any>(
            "displayName" to displayName,
            "role" to role,
            "photoURL" to photoURL,
            "deptWall" to deptWall,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        profileRef.set(data, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("LoginActivity", "Upsert /publicProfiles success for $uid")
            }
            .addOnFailureListener { e ->
                Log.e("LoginActivity", "Upsert /publicProfiles failed", e)
            }
    }

    private fun routeUser(uid: String, email: String?) {
        if (email.isNullOrEmpty() || !email.endsWith("@phinmaed.com", ignoreCase = true)) {
            forceSignOut("Please sign in using your PHINMAED account.")
            return
        }

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                val deptWall = doc.getString("deptWall") ?: ""
                val next = if (deptWall.isBlank()) {
                    DepartmentSetupActivity::class.java
                } else {
                    MainActivity::class.java
                }
                startActivity(Intent(this, next))
                finish()
            }
            .addOnFailureListener {
                startActivity(Intent(this, DepartmentSetupActivity::class.java))
                finish()
            }
    }

    private fun forceSignOut(message: String) {
        auth.signOut()
        googleClient.revokeAccess().addOnCompleteListener {
            toast(message)
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun animateLoginIntro() {
        val logo = findViewById<ImageView>(R.id.logoImage)
        val loginCard = findViewById<View>(R.id.loginCard)
        val appName = findViewById<View>(R.id.appName)
        val tagline = findViewById<View>(R.id.tagline)

        logo.alpha = 0f
        logo.scaleX = 0.94f
        logo.scaleY = 0.94f
        logo.translationY = -10f

        appName.alpha = 0f
        appName.translationY = 12f

        tagline.alpha = 0f
        tagline.translationY = 12f

        loginCard.alpha = 0f
        loginCard.translationY = 40f

        logo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(260)
            .start()

        appName.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(110)
            .setDuration(220)
            .start()

        tagline.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(150)
            .setDuration(220)
            .start()

        loginCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(210)
            .setDuration(300)
            .start()
    }
}