package com.phinmaed.uplug

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ScrollView
import android.widget.Toast
import androidx.core.widget.NestedScrollView

class HomeFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var pinnedPager: ViewPager2
    private lateinit var pinnedDots: LinearLayout
    private lateinit var latestRecycler: RecyclerView

    private lateinit var filterOfficial: TextView
    private lateinit var filterDepartment: TextView

    private val pinnedPosts = mutableListOf<Post>()
    private val latestPosts = mutableListOf<Post>()

    private var currentFilter = "official"
    private var departmentWall = ""

    private var pinnedPageCallback: ViewPager2.OnPageChangeCallback? = null

    private lateinit var btnViewAll: TextView

    private lateinit var latestSection: LinearLayout
    private lateinit var homeScrollView: NestedScrollView

    private lateinit var pinnedContent: LinearLayout
    private lateinit var pinnedSkeleton: LinearLayout
    private lateinit var latestSkeleton: LinearLayout
    private lateinit var pinnedEmptyCard: View
    private lateinit var latestEmptyCard: View
    private lateinit var latestHeader: LinearLayout

    private lateinit var pinnedHeader: TextView

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        pinnedPager = view.findViewById(R.id.pinnedPager)
        pinnedDots = view.findViewById(R.id.pinnedDots)
        latestRecycler = view.findViewById(R.id.latestPostsRecycler)
        filterOfficial = view.findViewById(R.id.filterOfficial)
        filterDepartment = view.findViewById(R.id.filterDepartment)
        btnViewAll = view.findViewById(R.id.btnViewAll)
        latestSection = view.findViewById(R.id.latestSection)
        homeScrollView = view.findViewById(R.id.homeScrollView)

        pinnedContent = view.findViewById(R.id.pinnedContent)
        pinnedSkeleton = view.findViewById(R.id.pinnedSkeleton)
        latestSkeleton = view.findViewById(R.id.latestSkeleton)
        pinnedEmptyCard = view.findViewById(R.id.pinnedEmptyCard)
        latestEmptyCard = view.findViewById(R.id.latestEmptyCard)

        latestHeader = view.findViewById(R.id.latestHeader)
        pinnedHeader = view.findViewById(R.id.pinnedHeader)

        latestRecycler.layoutManager = LinearLayoutManager(requireContext())

        setupPinnedPager()

        filterOfficial.setOnClickListener {
            currentFilter = "official"
            updateFilterUi()
            loadPosts()
        }

        filterDepartment.setOnClickListener {
            currentFilter = "department"
            updateFilterUi()
            loadPosts()
        }

        btnViewAll.setOnClickListener {
            animateLatestSectionOpen()
        }

        updateFilterUi()
        loadUserDepartment()

        return view
    }

    override fun onDestroyView() {
        pinnedPageCallback?.let { callback ->
            if (::pinnedPager.isInitialized) {
                pinnedPager.unregisterOnPageChangeCallback(callback)
            }
        }
        pinnedPageCallback = null
        super.onDestroyView()
    }

    private fun showHomeLoading() {
        pinnedHeader.alpha = 0.5f
        latestHeader.alpha = 0.5f

        pinnedSkeleton.visibility = View.VISIBLE
        latestSkeleton.visibility = View.VISIBLE

        pinnedContent.visibility = View.GONE
        latestSection.visibility = View.GONE
        pinnedEmptyCard.visibility = View.GONE
        latestEmptyCard.visibility = View.GONE

        // Keep headers real
        pinnedHeader.visibility = View.VISIBLE
        latestHeader.visibility = View.VISIBLE

        startSkeletonPulse(pinnedSkeleton)
        startSkeletonPulse(latestSkeleton)
    }

    private fun hideHomeLoading() {
        pinnedHeader.alpha = 1f
        latestHeader.alpha = 1f

        pinnedSkeleton.clearAnimation()
        latestSkeleton.clearAnimation()

        pinnedSkeleton.visibility = View.GONE
        latestSkeleton.visibility = View.GONE
    }

    private fun startSkeletonPulse(target: View) {
        target.alpha = 0.55f
        target.animate()
            .alpha(1f)
            .setDuration(700)
            .withEndAction {
                if (!isAdded || target.visibility != View.VISIBLE) return@withEndAction
                target.animate()
                    .alpha(0.55f)
                    .setDuration(700)
                    .withEndAction {
                        if (!isAdded || target.visibility != View.VISIBLE) return@withEndAction
                        startSkeletonPulse(target)
                    }
                    .start()
            }
            .start()
    }

    private fun animateLatestSectionOpen() {
        btnViewAll.isEnabled = false

        btnViewAll.animate()
            .alpha(0.6f)
            .translationX(12f)
            .setDuration(120)
            .withEndAction {
                openNewsTabWithFilter()

                btnViewAll.postDelayed({
                    if (!isAdded) return@postDelayed
                    btnViewAll.alpha = 1f
                    btnViewAll.translationX = 0f
                    btnViewAll.isEnabled = true
                }, 120)
            }
            .start()
    }

    private fun openNewsTabWithFilter() {
        val activity = requireActivity() as MainActivity
        activity.openNewsFromHome(currentFilter)
    }

    private fun setupPinnedPager() {
        pinnedPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        pinnedPager.offscreenPageLimit = 1
        pinnedPager.clipToPadding = true
        pinnedPager.clipChildren = true
        pinnedPager.setPageTransformer(null)
        pinnedPager.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        val recyclerView = pinnedPager.getChildAt(0) as RecyclerView
        recyclerView.overScrollMode = RecyclerView.OVER_SCROLL_NEVER
        recyclerView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        recyclerView.clipToPadding = true
        recyclerView.clipChildren = true
        recyclerView.setPadding(0, 0, 0, 0)
    }

    private fun updateFilterUi() {
        if (currentFilter == "official") {
            filterOfficial.setBackgroundResource(R.drawable.filter_chip_selected)
            filterDepartment.setBackgroundResource(R.drawable.filter_chip_unselected)
            filterOfficial.setTextColor(Color.WHITE)
            filterDepartment.setTextColor(Color.parseColor("#333333"))
        } else {
            filterOfficial.setBackgroundResource(R.drawable.filter_chip_unselected)
            filterDepartment.setBackgroundResource(R.drawable.filter_chip_selected)
            filterOfficial.setTextColor(Color.parseColor("#333333"))
            filterDepartment.setTextColor(Color.WHITE)
        }
    }

    private fun loadUserDepartment() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                departmentWall = doc.getString("deptWall") ?: ""
                loadPosts()
            }
            .addOnFailureListener {
                departmentWall = ""
                loadPosts()
            }
    }

    private fun loadPosts() {
        val wall = if (currentFilter == "official") "official" else departmentWall

        if (wall.isBlank()) {
            clearPostSections()
            return
        }

        showHomeLoading()

        val pinnedQuery = db.collection("posts")
            .whereEqualTo("wall", wall)
            .whereEqualTo("pinned", true)
            .orderBy("pinnedAt", Query.Direction.DESCENDING)
            .limit(10)

        val latestQuery = db.collection("posts")
            .whereEqualTo("wall", wall)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(30)

        Tasks.whenAllSuccess<com.google.firebase.firestore.QuerySnapshot>(
            pinnedQuery.get(),
            latestQuery.get()
        ).addOnSuccessListener { results ->

            val pinnedSnap = results[0]
            val latestSnap = results[1]

            val pinnedRaw = pinnedSnap.documents.mapNotNull { doc ->
                doc.toObject(Post::class.java)?.copy(id = doc.id)
            }

            val latestRaw = latestSnap.documents.mapNotNull { doc ->
                doc.toObject(Post::class.java)?.copy(id = doc.id)
            }

            val combined = (pinnedRaw + latestRaw).distinctBy { it.id }

            attachAuthorProfilePhotos(combined) { hydratedPosts ->
                val hydratedMap = hydratedPosts.associateBy { it.id }

                pinnedPosts.clear()
                latestPosts.clear()

                pinnedPosts.addAll(
                    pinnedRaw
                        .mapNotNull { hydratedMap[it.id] }
                        .filter { it.pinned }
                        .sortedByDescending {
                            it.pinnedAt?.toDate()?.time
                                ?: it.createdAt?.toDate()?.time
                                ?: 0L
                        }
                )

                latestPosts.addAll(
                    latestRaw
                        .mapNotNull { hydratedMap[it.id] }
                        .filter { !it.pinned && isWithinLast24HoursPH(it) }
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                        .take(5)
                )

                bindPinnedSection()
                bindLatestSection()

                hideHomeLoading()
                updateEmptyStates()
                fadeInLoadedContent()
            }
        }.addOnFailureListener {
            hideHomeLoading()
            updateEmptyStates()
            fadeInLoadedContent()
        }
    }

    private fun updateEmptyStates() {
        val hasPinned = pinnedPosts.isNotEmpty()
        val hasLatest = latestPosts.isNotEmpty()

        pinnedContent.visibility = if (hasPinned) View.VISIBLE else View.GONE
        pinnedEmptyCard.visibility = if (hasPinned) View.GONE else View.VISIBLE

        // Keep latest header always visible
        latestHeader.visibility = View.VISIBLE

        latestSection.visibility = if (hasLatest) View.VISIBLE else View.GONE
        latestEmptyCard.visibility = if (hasLatest) View.GONE else View.VISIBLE

        // Optional: hide View All when there are no latest posts
        btnViewAll.visibility = if (hasLatest) View.VISIBLE else View.GONE
    }

    private fun fadeInLoadedContent() {
        listOf(pinnedContent, latestSection, pinnedEmptyCard, latestEmptyCard).forEach { view ->
            if (view.visibility == View.VISIBLE) {
                view.alpha = 0f
                view.animate()
                    .alpha(1f)
                    .setDuration(220)
                    .start()
            }
        }
    }

    private fun bindPinnedSection() {
        pinnedPageCallback?.let { pinnedPager.unregisterOnPageChangeCallback(it) }
        pinnedPageCallback = null

        val adapter = PinnedAdapter(
            posts = pinnedPosts,
            onClick = { post -> openFullPost(post) },
            onAuthorClick = { post -> showPublicProfileDialog(post) }
        )

        pinnedPager.adapter = adapter

        val realCount = adapter.getRealCount()

        if (realCount <= 0) {
            pinnedPager.visibility = View.GONE
            pinnedDots.visibility = View.GONE
            return
        }

        pinnedPager.visibility = View.VISIBLE
        setupPinnedDots(realCount, 0)

        if (realCount == 1) {
            pinnedPager.setCurrentItem(0, false)
            return
        }

        val startPosition = getInfiniteStartPosition(realCount)

        pinnedPageCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePinnedDots(position % realCount)
            }
        }

        pinnedPager.registerOnPageChangeCallback(pinnedPageCallback!!)
        pinnedPager.setCurrentItem(startPosition, false)
    }

    private fun bindLatestSection() {
        latestRecycler.adapter = PostAdapter(
            posts = latestPosts,
            onClick = { post -> openFullPost(post) },
            onAuthorClick = { post -> showPublicProfileDialog(post) }
        )
    }

    private fun isWithinLast24HoursPH(post: Post): Boolean {
        val createdAt = post.createdAt ?: return false
        val nowMillis = System.currentTimeMillis()
        val postMillis = createdAt.toDate().time
        val diff = nowMillis - postMillis
        return diff in 0..(24 * 60 * 60 * 1000)
    }

    private fun clearPostSections() {
        pinnedPosts.clear()
        latestPosts.clear()

        pinnedPageCallback?.let { pinnedPager.unregisterOnPageChangeCallback(it) }
        pinnedPageCallback = null

        pinnedPager.adapter = PinnedAdapter(emptyList(), {}, {})
        latestRecycler.adapter = PostAdapter(emptyList(), {}, {})

        pinnedPager.visibility = View.GONE
        pinnedDots.visibility = View.GONE
        pinnedContent.visibility = View.GONE
        latestSection.visibility = View.GONE
    }

    private fun setupPinnedDots(count: Int, activeIndex: Int) {
        pinnedDots.removeAllViews()

        if (count <= 1) {
            pinnedDots.visibility = View.GONE
            return
        }

        pinnedDots.visibility = View.VISIBLE

        repeat(count) { index ->
            val dot = ImageView(requireContext())
            val size = (8 * resources.displayMetrics.density).toInt()
            val margin = (4 * resources.displayMetrics.density).toInt()

            val params = LinearLayout.LayoutParams(size, size)
            params.setMargins(margin, 0, margin, 0)
            dot.layoutParams = params
            dot.setImageResource(
                if (index == activeIndex) R.drawable.dot_active
                else R.drawable.dot_inactive
            )
            pinnedDots.addView(dot)
        }
    }

    private fun updatePinnedDots(activeIndex: Int) {
        for (i in 0 until pinnedDots.childCount) {
            val dot = pinnedDots.getChildAt(i) as ImageView
            dot.setImageResource(
                if (i == activeIndex) R.drawable.dot_active
                else R.drawable.dot_inactive
            )
        }
    }

    private fun getInfiniteStartPosition(realCount: Int): Int {
        if (realCount <= 1) return 0
        val middle = Int.MAX_VALUE / 2
        return middle - (middle % realCount)
    }

    private fun attachAuthorProfilePhotos(posts: List<Post>, onDone: (List<Post>) -> Unit) {
        val uniqueUids = posts.mapNotNull { it.authorUid.takeIf { uid -> uid.isNotBlank() } }.distinct()

        if (uniqueUids.isEmpty()) {
            onDone(posts)
            return
        }

        val tasks = uniqueUids.map { uid ->
            db.collection("publicProfiles").document(uid).get()
        }

        Tasks.whenAllComplete(tasks)
            .addOnSuccessListener { results ->
                val photoMap = mutableMapOf<String, String>()

                for (i in results.indices) {
                    val task = results[i]
                    val uid = uniqueUids[i]

                    if (task.isSuccessful) {
                        val doc = task.result as? DocumentSnapshot
                        if (doc != null && doc.exists()) {
                            photoMap[uid] = doc.getString("photoURL") ?: ""
                        }
                    }
                }

                val updated = posts.map { post ->
                    post.copy(photoUrl = photoMap[post.authorUid] ?: "")
                }

                onDone(updated)
            }
            .addOnFailureListener {
                onDone(posts)
            }
    }

    private fun openFullPost(post: Post) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val view = layoutInflater.inflate(R.layout.home_post_item, null)
        val holder = PostAdapter.PostViewHolder(view)

        holder.userName.text = post.authorName
        holder.postWall.text = post.wall.uppercase()
        holder.postTitle.text = post.title
        holder.postContent.text = post.content
        holder.postTime.text = formatTime(post.createdAt)

        Glide.with(requireContext())
            .load(post.photoUrl)
            .placeholder(R.drawable.profile)
            .error(R.drawable.profile)
            .circleCrop()
            .into(holder.userProfilePic)

        when (post.wall.lowercase()) {
            "official", "cite" -> holder.wallIcon.setImageResource(R.drawable.cite)
            else -> holder.wallIcon.setImageResource(R.drawable.profile)
        }

        holder.authorClickableArea.setOnClickListener {
            dialog.dismiss()
            showPublicProfileDialog(post)
        }

        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()

        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showPublicProfileDialog(post: Post) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_public_profile)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val profileImage = dialog.findViewById<ImageView>(R.id.dialogProfileImage)
        val profileName = dialog.findViewById<TextView>(R.id.dialogProfileName)
        val profileSubtitle = dialog.findViewById<TextView>(R.id.dialogProfileSubtitle)
        val btnMessage = dialog.findViewById<TextView>(R.id.btnMessage)
        val btnClose = dialog.findViewById<TextView>(R.id.btnClose)

        profileName.text = post.authorName
        profileSubtitle.text = "PHINMA User"

        Glide.with(requireContext())
            .load(post.photoUrl)
            .placeholder(R.drawable.profile)
            .error(R.drawable.profile)
            .circleCrop()
            .into(profileImage)

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        btnMessage.setOnClickListener {
            val myUid = auth.currentUser?.uid
            val otherUid = post.authorUid

            if (myUid.isNullOrBlank()) {
                dialog.dismiss()
                return@setOnClickListener
            }

            if (otherUid.isBlank()) {
                Toast.makeText(requireContext(), "User ID missing", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (myUid == otherUid) {
                Toast.makeText(requireContext(), "You can't message yourself", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialog.dismiss()

            val threadId = if (myUid < otherUid) {
                "${myUid}_${otherUid}"
            } else {
                "${otherUid}_${myUid}"
            }

            val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                putExtra("threadId", threadId)
                putExtra("otherUid", otherUid)
                putExtra("otherUserName", post.authorName)
                putExtra("otherUserPhotoUrl", post.photoUrl ?: "")
            }

            startActivity(intent)
        }

        dialog.show()

        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }


    private fun buildThreadId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"
    }

    private fun openChatThread(
        threadId: String,
        otherUid: String,
        otherName: String,
        otherPhotoUrl: String?
    ) {
        val intent = Intent(requireContext(), ChatActivity::class.java).apply {
            putExtra("threadId", threadId)
            putExtra("otherUid", otherUid)
            putExtra("otherUserName", otherName)
            putExtra("otherUserPhotoUrl", otherPhotoUrl ?: "")
        }
        startActivity(intent)
    }

    private fun formatTime(timestamp: Timestamp?): String {
        if (timestamp == null) return ""

        val now = System.currentTimeMillis()
        val postTime = timestamp.toDate().time
        val diff = now - postTime

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            seconds < 60 -> "${seconds}s"
            minutes < 60 -> "${minutes}m"
            hours < 24 -> "${hours}h"
            days == 1L -> "Yesterday"
            days < 7 -> "${days}d"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(timestamp.toDate())
        }
    }

    fun onHomeTabOpened() {}

    fun showBannerFromActivity(count: Int) {}
}