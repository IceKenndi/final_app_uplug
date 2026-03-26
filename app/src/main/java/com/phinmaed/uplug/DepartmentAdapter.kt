package com.phinmaed.uplug

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class DepartmentAdapter(
    private val items: List<departmentData>,
    private val onClick: (departmentData) -> Unit
) : RecyclerView.Adapter<DepartmentAdapter.DepartmentViewHolder>() {

    companion object {
        private const val PAYLOAD_FLIP = "flip"
    }

    private var selectedPosition = -1

    inner class DepartmentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.departmentCard)
        val icon: ImageView = view.findViewById(R.id.departmentIcon)
        val backBgLogo: ImageView = view.findViewById(R.id.backBgLogo)
        val shortName: TextView = view.findViewById(R.id.tv_departmentName)
        val fullName: TextView = view.findViewById(R.id.tv_departmentFullName)
        val checkIcon: ImageView = view.findViewById(R.id.checkIcon)
        val frontFace: View = view.findViewById(R.id.frontFace)
        val backFace: View = view.findViewById(R.id.backFace)
        val backCheckBadge: ImageView = view.findViewById(R.id.backCheckBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DepartmentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_departments, parent, false)
        return DepartmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: DepartmentViewHolder, position: Int) {
        val item = items[position]
        val selected = position == selectedPosition

        bindContent(holder, item)
        setupCamera(holder)
        bindStaticState(holder, selected)

        holder.card.setOnClickListener {
            val clickedPosition = holder.bindingAdapterPosition
            if (clickedPosition == RecyclerView.NO_POSITION) return@setOnClickListener

            val clickedItem = items[clickedPosition]
            if (selectedPosition == clickedPosition) return@setOnClickListener

            val previous = selectedPosition
            selectedPosition = clickedPosition

            if (previous != -1) notifyItemChanged(previous, PAYLOAD_FLIP)
            notifyItemChanged(selectedPosition, PAYLOAD_FLIP)

            onClick(clickedItem)
        }
    }

    override fun onBindViewHolder(
        holder: DepartmentViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }

        val item = items[position]
        val selected = position == selectedPosition

        bindContent(holder, item)
        setupCamera(holder)
        animateFlip(holder, selected)
    }

    private fun bindContent(holder: DepartmentViewHolder, item: departmentData) {
        holder.icon.setImageResource(item.icon)
        holder.backBgLogo.setImageResource(item.icon)
        holder.shortName.text = item.departmentName
        holder.fullName.text = item.departmentFullName
    }

    private fun setupCamera(holder: DepartmentViewHolder) {
        val scale = holder.itemView.resources.displayMetrics.density
        val distance = 12000 * scale
        holder.card.cameraDistance = distance
        holder.frontFace.cameraDistance = distance
        holder.backFace.cameraDistance = distance
    }

    private fun bindStaticState(holder: DepartmentViewHolder, selected: Boolean) {
        holder.frontFace.animate().cancel()
        holder.backFace.animate().cancel()
        holder.card.animate().cancel()

        holder.card.strokeWidth = if (selected) 2 else 1
        holder.card.strokeColor = if (selected) 0xFF1F6A4E.toInt() else 0xFFDCE5DE.toInt()
        holder.card.setCardBackgroundColor(
            if (selected) 0xFFF3FAF6.toInt() else 0xFFFDFEFC.toInt()
        )

        holder.checkIcon.visibility = if (selected) View.VISIBLE else View.GONE
        holder.backCheckBadge.visibility = if (selected) View.VISIBLE else View.GONE

        holder.card.scaleX = if (selected) 1.04f else 1f
        holder.card.scaleY = if (selected) 1.04f else 1f
        holder.card.translationY = if (selected) -8f else 0f
        holder.card.rotationX = 0f
        holder.card.rotationY = 0f
        holder.card.cardElevation = if (selected) 14f else 0f

        if (selected) {
            holder.frontFace.visibility = View.GONE
            holder.backFace.visibility = View.VISIBLE
            holder.frontFace.rotationY = 90f
            holder.backFace.rotationY = 0f
        } else {
            holder.frontFace.visibility = View.VISIBLE
            holder.backFace.visibility = View.GONE
            holder.frontFace.rotationY = 0f
            holder.backFace.rotationY = -90f
        }
    }

    private fun animateFlip(holder: DepartmentViewHolder, selected: Boolean) {
        holder.frontFace.animate().cancel()
        holder.backFace.animate().cancel()
        holder.card.animate().cancel()

        holder.card.strokeWidth = if (selected) 2 else 1
        holder.card.strokeColor = if (selected) 0xFF1F6A4E.toInt() else 0xFFDCE5DE.toInt()
        holder.card.setCardBackgroundColor(
            if (selected) 0xFFF3FAF6.toInt() else 0xFFFDFEFC.toInt()
        )

        holder.checkIcon.visibility = if (selected) View.VISIBLE else View.GONE
        holder.backCheckBadge.visibility = if (selected) View.VISIBLE else View.GONE

        holder.card.animate()
            .scaleX(if (selected) 1.04f else 1f)
            .scaleY(if (selected) 1.04f else 1f)
            .translationY(if (selected) -8f else 0f)
            .rotationX(if (selected) 2f else 0f)
            .rotationY(if (selected) -2f else 0f)
            .setDuration(180)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                holder.card.animate()
                    .rotationX(0f)
                    .rotationY(0f)
                    .setDuration(120)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
            .start()

        holder.card.cardElevation = if (selected) 14f else 0f

        if (selected) {
            holder.frontFace.visibility = View.VISIBLE
            holder.frontFace.rotationY = 0f

            holder.frontFace.animate()
                .rotationY(90f)
                .setDuration(160)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        holder.frontFace.visibility = View.GONE
                        holder.backFace.visibility = View.VISIBLE
                        holder.backFace.rotationY = -90f
                        holder.backFace.animate()
                            .rotationY(0f)
                            .setDuration(160)
                            .setInterpolator(AccelerateDecelerateInterpolator())
                            .setListener(null)
                            .start()
                    }
                })
                .start()
        } else {
            holder.backFace.visibility = View.VISIBLE
            holder.backFace.rotationY = 0f

            holder.backFace.animate()
                .rotationY(-90f)
                .setDuration(160)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        holder.backFace.visibility = View.GONE
                        holder.frontFace.visibility = View.VISIBLE
                        holder.frontFace.rotationY = 90f
                        holder.frontFace.animate()
                            .rotationY(0f)
                            .setDuration(160)
                            .setInterpolator(AccelerateDecelerateInterpolator())
                            .setListener(null)
                            .start()
                    }
                })
                .start()
        }
    }

    override fun getItemCount(): Int = items.size
}