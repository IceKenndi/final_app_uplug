package com.phinmaed.uplug

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class MainActivity : AppCompatActivity() {

    private lateinit var navHost: NavHostFragment
    private lateinit var bottomNavigationView: BottomNavigationView

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var departmentWall: String = ""
    private var latestSeenHomePostId: String? = null
    private var pendingHomeNewPostsCount = 0
    private var homePostListener: ListenerRegistration? = null

    var pendingNewsFilterFromHome: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        window.statusBarColor = android.graphics.Color.parseColor("#EEF1EC")
        window.navigationBarColor = android.graphics.Color.parseColor("#EEF1EC")


        bottomNavigationView = findViewById(R.id.bottomNav)
        navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController

        bottomNavigationView.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.homeFragment) {
                getVisibleHomeFragment()?.onHomeTabOpened()
            } else {
                getVisibleHomeFragment()?.let {
                    if (pendingHomeNewPostsCount > 0) {
                        showHomeBadge(pendingHomeNewPostsCount)
                    }
                }
            }
        }

        bottomNavigationView.setOnItemReselectedListener { item ->
            if (item.itemId == R.id.homeFragment) {
                getVisibleHomeFragment()?.onHomeTabOpened()
            }
        }

        startHomePostMonitoring()
    }

    override fun onDestroy() {
        super.onDestroy()
        homePostListener?.remove()
        homePostListener = null
    }

    private fun getVisibleHomeFragment(): HomeFragment? {
        val currentFragment = navHost.childFragmentManager.primaryNavigationFragment
        return currentFragment as? HomeFragment
    }

    fun isHomeSelected(): Boolean {
        return bottomNavigationView.selectedItemId == R.id.homeFragment
    }

    fun clearHomeIndicators() {
        pendingHomeNewPostsCount = 0
        clearHomeBadge()
    }

    fun markCurrentTopPostAsSeen(postId: String?) {
        latestSeenHomePostId = postId
    }

    private fun startHomePostMonitoring() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                departmentWall = userDoc.getString("deptWall") ?: ""
                if (departmentWall.isBlank()) return@addOnSuccessListener

                val allowedWalls = listOf(departmentWall, "official")

                homePostListener?.remove()
                homePostListener = db.collection("posts")
                    .whereIn("wall", allowedWalls)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(1)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("MainActivity", "homePostListener failed", error)
                            return@addSnapshotListener
                        }

                        if (snapshot == null || snapshot.isEmpty) return@addSnapshotListener

                        val newestId = snapshot.documents.first().id

                        if (latestSeenHomePostId == null) {
                            latestSeenHomePostId = newestId
                            return@addSnapshotListener
                        }

                        if (newestId != latestSeenHomePostId) {
                            pendingHomeNewPostsCount++

                            if (isHomeSelected()) {
                                hideHomeNavIndicatorOnly()
                                getVisibleHomeFragment()?.showBannerFromActivity(pendingHomeNewPostsCount)
                            } else {
                                showHomeBadge(pendingHomeNewPostsCount)
                            }
                        }
                    }
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Failed to load deptWall", e)
            }
    }

    fun showHomeBadge(count: Int) {
        if (count <= 0) {
            clearHomeBadge()
            return
        }

        if (isHomeSelected()) {
            clearHomeBadge()
            return
        }

        val badge = bottomNavigationView.getOrCreateBadge(R.id.homeFragment)
        badge.isVisible = true
        badge.backgroundColor = ContextCompat.getColor(this, android.R.color.holo_red_dark)
        badge.badgeGravity = BadgeDrawable.TOP_END
        badge.number = if (count > 99) 99 else count
    }

    fun openNewsFromHome(filter: String) {
        pendingNewsFilterFromHome = filter
        bottomNavigationView.selectedItemId = R.id.newsFragment
    }

    fun clearHomeBadge() {
        bottomNavigationView.removeBadge(R.id.homeFragment)
    }

    fun hideHomeNavIndicatorOnly() {
        val badge = bottomNavigationView.getOrCreateBadge(R.id.homeFragment)
        badge.isVisible = false
    }

    fun consumeHomeIndicators() {
        pendingHomeNewPostsCount = 0
        clearHomeBadge()
    }

    fun restoreHomeIndicatorIfNeeded() {
        if (pendingHomeNewPostsCount > 0 && !isHomeSelected()) {
            showHomeBadge(pendingHomeNewPostsCount)
        }
    }
    fun getPendingHomeNewPostsCount(): Int {
        return pendingHomeNewPostsCount
    }
}