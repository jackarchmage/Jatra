package com.jks.jatrav3

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.addAugmentedImage
import io.github.sceneview.ar.arcore.getUpdatedAugmentedImages
import io.github.sceneview.ar.node.AugmentedImageNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AugmentHostActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TARGET_URL = "extra_target_url"
        const val EXTRA_MODEL_URL = "extra_model_url"
    }

    private lateinit var sceneView: ARSceneView
    private lateinit var btnStartAR: Button
    private lateinit var tvHint: TextView

    private var targetImageUrl: String? = null
    private var modelUrl: String? = null
    private var targetFile: File? = null
    private var modelFile: File? = null

    // keep track of placed augmented image nodes
    private val augmentedImageNodes = mutableListOf<AugmentedImageNode>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_augment_host)

        sceneView = findViewById(R.id.sceneView)
        btnStartAR = findViewById(R.id.btnStartAR)
        tvHint = findViewById(R.id.tvHint)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom)
            insets
        }

        targetImageUrl = intent.getStringExtra(EXTRA_TARGET_URL)
        modelUrl = intent.getStringExtra(EXTRA_MODEL_URL)

        btnStartAR.setOnClickListener {
            lifecycleScope.launch {
                tvHint.text = "Downloading..."
                targetFile = downloadFile(targetImageUrl, "target.jpg")
                modelFile = downloadFile(modelUrl, "model.glb")

                if (targetFile == null || modelFile == null) {
                    tvHint.text = "Download failed"
                } else {
                    startAR()
                }
            }
        }
    }

    private suspend fun downloadFile(urlStr: String?, name: String): File? {
        if (urlStr.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.connect()
                if (conn.responseCode !in 200..299) return@withContext null
                val file = File(cacheDir, name)
                conn.inputStream.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                file
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun startAR() {
        tvHint.text = "Point camera at the image"

        sceneView.configureSession { session, config ->
            val bmp = targetFile!!.inputStream().use { BitmapFactory.decodeStream(it) }
            config.addAugmentedImage(session, "target", bmp, 0.15f)
        }

        sceneView.onSessionUpdated = { _, frame ->
            frame.getUpdatedAugmentedImages().forEach { img ->
                if (augmentedImageNodes.none { it.imageName == img.name }) {
                    val node = AugmentedImageNode(sceneView.engine, img).apply {
                        addChildNode(
                            ModelNode(
                                modelInstance = sceneView.modelLoader.createModelInstance(
                                    file = modelFile!!    // ✅ FIX: use file overload
                                ),
                                scaleToUnits = 0.1f,
                                centerOrigin = Position(0f)
                            )
                        )
                    }
                    sceneView.addChildNode(node)
                    augmentedImageNodes += node
                }
            }
        }
    }



    override fun onDestroy() {
        super.onDestroy()
        if (::sceneView.isInitialized) sceneView.destroy()
    }
}
