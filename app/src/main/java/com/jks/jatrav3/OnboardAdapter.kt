package com.jks.jatrav3

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OnboardAdapter(private val items: List<OnboardPage.OnboardPage>) :
    RecyclerView.Adapter<OnboardAdapter.PageVH>() {

    inner class PageVH(view: View) : RecyclerView.ViewHolder(view) {
        val ivBackground: ImageView = view.findViewById(R.id.ivBackground)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val ivSmall: ImageView = view.findViewById(R.id.ivSmallIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_onboard, parent, false)
        return PageVH(v)
    }

    override fun onBindViewHolder(holder: PageVH, position: Int) {
        val page = items[position]
        holder.ivBackground.setImageResource(page.imageRes)
        holder.tvTitle.text = page.title
        if (page.indicatorRes != null) {
            holder.ivSmall.setImageResource(page.indicatorRes)
            holder.ivSmall.visibility = View.VISIBLE
        } else {
            holder.ivSmall.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = items.size
}
