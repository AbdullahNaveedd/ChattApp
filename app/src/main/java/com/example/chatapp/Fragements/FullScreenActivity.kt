package com.example.chatapp.Auth.Message

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.chatapp.R

class FullScreenActivity : AppCompatActivity() {

    private lateinit var photoView: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var closeButton: ImageView

    companion object {
        const val EXTRA_IMAGE_URL = "image_url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        supportActionBar?.hide()

        setContentView(R.layout.activity_full_screen)

        initViews()
        setupImage()
        setupCloseButton()
    }

    private fun initViews() {
        photoView = findViewById(R.id.photo_view)
        progressBar = findViewById(R.id.progress_bar)
        closeButton = findViewById(R.id.btn_close)
    }

    private fun setupImage() {
        val imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL)

        if (imageUrl.isNullOrEmpty()) {
            finish()
            return
        }

        progressBar.visibility = View.VISIBLE

        Glide.with(this)
            .load(imageUrl)
            .fitCenter()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    progressBar.visibility = View.GONE
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    progressBar.visibility = View.GONE
                    return false
                }
            })
            .into(photoView)
    }

    private fun setupCloseButton() {
        closeButton.setOnClickListener {
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}