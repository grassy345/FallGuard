package com.fallguard.app

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * FallEventAdapter — Displays fall events as compact cards in a RecyclerView.
 *
 * Each card shows:
 *  - Color strip (red = fall, amber = suspicious)
 *  - Status label, formatted date/time, relative time
 *  - Acknowledged badge
 *  - Video thumbnail (or processing/error placeholder)
 *  - Save button (when video is available)
 */
class FallEventAdapter(
    private val onThumbnailClick: (FallEvent) -> Unit,
    private val onSaveClick: (FallEvent) -> Unit,
    private val onLongPress: ((FallEvent) -> Unit)? = null
) : ListAdapter<FallEvent, FallEventAdapter.ViewHolder>(DiffCallback) {

    companion object {
        private const val INPUT_FORMAT = "dd-MM-yyyy HH:mm:ss"
        private const val DISPLAY_FORMAT = "dd/MM/yyyy hh:mm:ss a"

        /** DiffUtil — efficiently updates only changed items */
        private val DiffCallback = object : DiffUtil.ItemCallback<FallEvent>() {
            override fun areItemsTheSame(old: FallEvent, new: FallEvent) = old.id == new.id
            override fun areContentsTheSame(old: FallEvent, new: FallEvent) = old == new
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val colorStrip: View = view.findViewById(R.id.colorStrip)
        val statusLabel: TextView = view.findViewById(R.id.statusLabel)
        val dateTime: TextView = view.findViewById(R.id.dateTime)
        val relativeTime: TextView = view.findViewById(R.id.relativeTime)
        val acknowledgedBadge: TextView = view.findViewById(R.id.acknowledgedBadge)
        val saveButton: ImageButton = view.findViewById(R.id.saveButton)
        val videoThumbnail: ImageView = view.findViewById(R.id.videoThumbnail)
        val playOverlay: ImageView = view.findViewById(R.id.playOverlay)
        val processingPlaceholder: LinearLayout = view.findViewById(R.id.processingPlaceholder)
        val errorPlaceholder: LinearLayout = view.findViewById(R.id.errorPlaceholder)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_fall_event, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = getItem(position)
        val context = holder.itemView.context

        // Long-press: delete card (dev feature)
        holder.itemView.setOnLongClickListener {
            onLongPress?.invoke(event)
            true
        }

        // Color strip: red for FALL_DETECTED, amber for SUSPICIOUS
        val stripColor = if (event.fallStatus == "FALL_DETECTED")
            ContextCompat.getColor(context, R.color.alert_red)
        else
            ContextCompat.getColor(context, R.color.alert_amber)
        holder.colorStrip.setBackgroundColor(stripColor)

        // Status label
        holder.statusLabel.text = if (event.fallStatus == "FALL_DETECTED")
            "🔴 Fall Detected" else "🟠 Suspicious Activity"

        // Formatted date/time (DD/MM/YYYY hh:mm:ss AM/PM)
        holder.dateTime.text = formatTimestamp(event.timestamp)

        // Relative time ("2 hours ago", "Yesterday")
        val parsedDate = parseTimestamp(event.timestamp)
        if (parsedDate != null) {
            holder.relativeTime.text = DateUtils.getRelativeTimeSpanString(
                parsedDate.time,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
        } else {
            holder.relativeTime.text = ""
        }

        // Acknowledged badge
        holder.acknowledgedBadge.visibility = if (event.acknowledged) View.VISIBLE else View.GONE

        // Video thumbnail handling
        if (event.clipUrl != null && event.clipUrl.isNotEmpty()) {
            // Video URL is available — load thumbnail with Glide
            holder.processingPlaceholder.visibility = View.GONE
            holder.errorPlaceholder.visibility = View.GONE
            holder.videoThumbnail.visibility = View.VISIBLE
            holder.playOverlay.visibility = View.VISIBLE
            holder.saveButton.visibility = View.VISIBLE

            Glide.with(context)
                .load(event.clipUrl)
                .transform(CenterCrop(), RoundedCorners(8))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_delete)
                .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(
                        e: com.bumptech.glide.load.engine.GlideException?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        // Dead link — show error placeholder
                        holder.videoThumbnail.visibility = View.GONE
                        holder.playOverlay.visibility = View.GONE
                        holder.saveButton.visibility = View.GONE
                        holder.errorPlaceholder.visibility = View.VISIBLE
                        return true
                    }

                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable,
                        model: Any,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        dataSource: com.bumptech.glide.load.DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        return false // Let Glide handle displaying
                    }
                })
                .into(holder.videoThumbnail)

            // Click: play video
            holder.videoThumbnail.setOnClickListener { onThumbnailClick(event) }
            holder.playOverlay.setOnClickListener { onThumbnailClick(event) }

            // Click: save video
            holder.saveButton.setOnClickListener { onSaveClick(event) }
        } else {
            // No clip_url yet — show processing placeholder
            holder.videoThumbnail.visibility = View.GONE
            holder.playOverlay.visibility = View.GONE
            holder.saveButton.visibility = View.GONE
            holder.errorPlaceholder.visibility = View.GONE
            holder.processingPlaceholder.visibility = View.VISIBLE
        }
    }

    private fun formatTimestamp(timestamp: String): String {
        return try {
            val inputFmt = SimpleDateFormat(INPUT_FORMAT, Locale.getDefault())
            val outputFmt = SimpleDateFormat(DISPLAY_FORMAT, Locale.US)
            val date = inputFmt.parse(timestamp)
            if (date != null) outputFmt.format(date).uppercase() else timestamp
        } catch (e: Exception) {
            timestamp
        }
    }

    private fun parseTimestamp(timestamp: String): java.util.Date? {
        return try {
            SimpleDateFormat(INPUT_FORMAT, Locale.getDefault()).parse(timestamp)
        } catch (e: Exception) {
            null
        }
    }
}
