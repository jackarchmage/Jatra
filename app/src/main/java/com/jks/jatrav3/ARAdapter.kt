package com.jks.jatrav3
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jks.jatrav3.api.SuperArUser
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ARAdapter(
    private val onItemClick: (SuperArUser) -> Unit
) : ListAdapter<SuperArUser, ARAdapter.VH>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.item_ar_file, parent, false)
        return VH(view, onItemClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(itemView: View, private val onClick: (SuperArUser) -> Unit) :
        RecyclerView.ViewHolder(itemView) {

        private val tvTitle: TextView? = itemView.findViewById(R.id.tvTitle)
        private val tvSub: TextView? = itemView.findViewById(R.id.tvSub)
        private val tvDate: TextView? = itemView.findViewById(R.id.tvDate)

        fun bind(item: SuperArUser) {
            val fileName = item.file_link
                ?.substringAfterLast('/')
                ?.replace("%20", " ")
                ?: item._id

            tvTitle?.text = fileName
            tvSub?.text = "${item.request_type ?: "-"} · ${item.payment_status ?: "-"}"

            tvDate?.text = item.createdAt?.let { iso ->
                try {
                    parseIsoToDisplay(iso)
                } catch (e: Exception) {
                    iso // fallback to raw string
                }
            } ?: ""

            itemView.setOnClickListener { onClick(item) }
        }

        // Parse common ISO-8601 variants to a nice display string (works on API < 26)
        private fun parseIsoToDisplay(iso: String): String {
            val inputPatterns = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX"
            )

            var parsed: Date? = null
            for (pattern in inputPatterns) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                    // If pattern ends with literal 'Z', set timezone to UTC
                    if (pattern.endsWith("'Z'")) {
                        sdf.timeZone = TimeZone.getTimeZone("UTC")
                    }
                    parsed = sdf.parse(iso)
                    if (parsed != null) break
                } catch (e: ParseException) {
                    // try next pattern
                } catch (e: IllegalArgumentException) {
                    // pattern unsupported on some Android versions (ignore)
                }
            }

            if (parsed == null) {
                // last resort: attempt to trim timezone 'Z' and parse without timezone
                try {
                    val fallback = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                    parsed = fallback.parse(iso.replace("Z", ""))
                } catch (e: Exception) {
                    // give up and throw, caller will handle
                    throw IllegalArgumentException("Unparseable date: $iso")
                }
            }

            val out = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            out.timeZone = TimeZone.getDefault() // display in device local timezone
            return out.format(parsed!!)
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<SuperArUser>() {
        override fun areItemsTheSame(oldItem: SuperArUser, newItem: SuperArUser): Boolean {
            return oldItem._id == newItem._id
        }

        override fun areContentsTheSame(oldItem: SuperArUser, newItem: SuperArUser): Boolean {
            return oldItem == newItem
        }
    }
}