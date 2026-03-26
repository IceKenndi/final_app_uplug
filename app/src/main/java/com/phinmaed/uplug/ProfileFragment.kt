package com.phinmaed.uplug

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var googleSignInClient: GoogleSignInClient

    private lateinit var profileSkeleton: View
    private lateinit var profileContent: View

    private lateinit var postsSkeleton: View
    private lateinit var postsRecycler: RecyclerView
    private lateinit var emptyPostsText: TextView
    private lateinit var postAdapter: ProfilePostAdapter

    private lateinit var profileMenuBtn: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val firebaseUser = FirebaseAuth.getInstance().currentUser ?: return
        val myUid = firebaseUser.uid

        profileSkeleton = view.findViewById(R.id.profileSkeleton)
        profileContent = view.findViewById(R.id.profileContent)

        postsSkeleton = view.findViewById(R.id.postsSkeleton)
        postsRecycler = view.findViewById(R.id.profilePostsRecycler)
        emptyPostsText = view.findViewById(R.id.emptyPostsText)

        val nameTv = view.findViewById<TextView>(R.id.nameTv)
        val bioTv = view.findViewById<TextView>(R.id.bioTv)
        val profileImg = view.findViewById<ImageView>(R.id.profilePreview)
        val emailTv = view.findViewById<TextView>(R.id.detail_email)
        val departmentTv = view.findViewById<TextView>(R.id.detail_department)

        profileMenuBtn = view.findViewById(R.id.profileMenuBtn)
        profileMenuBtn.visibility = View.VISIBLE

        // optional: hide the message button if it still exists in XML
        view.findViewById<View?>(R.id.messageProfileBtn)?.visibility = View.GONE

        postAdapter = ProfilePostAdapter()
        postsRecycler.layoutManager = LinearLayoutManager(requireContext())
        postsRecycler.adapter = postAdapter

        showProfileSkeleton(true)
        showPostsSkeleton(true)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireContext(), gso)

        profileMenuBtn.setOnClickListener { showPopupMenu(profileMenuBtn) }

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(myUid)
            .get()
            .addOnSuccessListener { doc ->
                val displayName = doc.getString("displayName") ?: ""
                val deptWall = doc.getString("deptWall") ?: ""
                val deptName = doc.getString("deptName") ?: ""
                val role = doc.getString("role") ?: "student"
                val email = doc.getString("email") ?: (firebaseUser.email ?: "")

                nameTv.text = displayName
                bioTv.text = if (role == "faculty") "$deptWall | Faculty" else "$deptWall | Student"
                emailTv.text = email
                departmentTv.text = deptName

                val photoUrl = firebaseUser.photoUrl?.toString().orEmpty()

                Glide.with(this)
                    .load(photoUrl)
                    .placeholder(R.drawable.profile)
                    .error(R.drawable.profile)
                    .circleCrop()
                    .into(profileImg)

                showProfileSkeleton(false)
            }
            .addOnFailureListener {
                toast("Failed to load profile")
                showProfileSkeleton(false)
            }

        fetchUserPosts(myUid)
    }

    private fun fetchUserPosts(uid: String) {
        FirebaseFirestore.getInstance()
            .collection("posts")
            .whereEqualTo("authorUid", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                val posts = result.documents.mapNotNull { doc ->
                    doc.toObject(Post::class.java)?.copy(id = doc.id)
                }

                showPostsSkeleton(false)
                postAdapter.submitList(posts)

                postsRecycler.visibility = if (posts.isNotEmpty()) View.VISIBLE else View.GONE
                emptyPostsText.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { e ->
                showPostsSkeleton(false)
                postsRecycler.visibility = View.GONE
                emptyPostsText.visibility = View.VISIBLE
                emptyPostsText.text = "Couldn’t load posts"

                android.util.Log.e("ProfileFragment", "fetchUserPosts failed", e)
                toast("Posts failed: ${e.message}")
            }
    }

    private fun showProfileSkeleton(show: Boolean) {
        profileSkeleton.visibility = if (show) View.VISIBLE else View.GONE
        profileContent.visibility = if (show) View.INVISIBLE else View.VISIBLE
    }

    private fun showPostsSkeleton(show: Boolean) {
        postsSkeleton.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            postsRecycler.visibility = View.GONE
            emptyPostsText.visibility = View.GONE
        }
    }

    private fun showPopupMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.profile_settings_menu, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.logout -> { showLogoutDialog(); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure?")
            .setPositiveButton("Yes") { _, _ -> logout() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logout() {
        FirebaseAuth.getInstance().signOut()
        googleSignInClient.signOut().addOnCompleteListener {
            val intent = Intent(requireActivity(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}