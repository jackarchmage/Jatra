package com.jks.jatrav3

import android.Manifest
import android.content.res.AssetManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.addAugmentedImage
import io.github.sceneview.ar.arcore.getUpdatedAugmentedImages
import io.github.sceneview.ar.node.AugmentedImageNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

class AugmentHostActivity : AppCompatActivity() {

    companion object {
        const val BLUE_PRINT_URL = "BLUE_PRINT_URL"
        const val MODEL_URL = "MODEL_URL"
        private const val REQ_CAMERA = 101
        private const val TAG = "AugmentHostDebug"
    }

    private lateinit var sceneView: ARSceneView
    private lateinit var btnStartAR: Button
    private lateinit var btnPlace: Button
    private lateinit var tvHint: TextView
    private lateinit var iv: ImageView

    private var targetImageUrl: String? = null
    private var modelUrl: String? = null
    private var targetFile: File? = null
    private var modelFile: File? = null

    private val augmentedImageNodes = mutableListOf<AugmentedImageNode>()
    private val placedModelNodes = mutableListOf<ModelNode>()

    // last seen augmented image (PAUSED or TRACKING)
    private var lastSeenAugmentedImage: com.google.ar.core.AugmentedImage? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_augment_host)

        sceneView = findViewById(R.id.sceneView)
        btnStartAR = findViewById(R.id.btnStartAR)
        btnPlace = findViewById(R.id.btnPlace)
        tvHint = findViewById(R.id.tvHint)
        iv = findViewById(R.id.iv_image)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom)
            insets
        }

        targetImageUrl = intent?.getStringExtra(BLUE_PRINT_URL)?.takeIf { it.isNotBlank() }
        modelUrl = intent?.getStringExtra(MODEL_URL)?.takeIf { it.isNotBlank() }

        btnStartAR.setOnClickListener {
            btnStartAR.isEnabled = false
            tvHint.text = "Preparing..."
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
            } else {
                startDownloadAndAR()
            }
        }

        setupPlaceButton()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show()
                startDownloadAndAR()
            } else {
                tvHint.text = "Camera permission required"
                Toast.makeText(this, "Camera permission required for AR", Toast.LENGTH_LONG).show()
                btnStartAR.isEnabled = true
            }
        }
    }

    private fun setupPlaceButton() {
        btnPlace.isEnabled = false
        btnPlace.setOnClickListener {
            btnPlace.isEnabled = false
            tryPlaceModel()
        }
    }

    private fun startDownloadAndAR() {
        lifecycleScope.launch {
            tvHint.text = "Downloading..."
            val ok = withContext(Dispatchers.IO) {
                try {
                    targetFile = targetFile ?: downloadAndReport(targetImageUrl, "target.jpg")
                    modelFile = modelFile ?: downloadAndReport(modelUrl, "model.glb")
                    runOnUiThread { iv.setImageURI(targetFile?.toUri()) }
                    (targetFile != null && modelFile != null)
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

            if (!ok) {
                tvHint.text = "Download failed"
                Toast.makeText(this@AugmentHostActivity, "Failed to download assets. Check URLs.", Toast.LENGTH_LONG).show()
                btnStartAR.isEnabled = true
                return@launch
            }

            try {
                // attempt auto-scan widths: quick sweep (0.15, 0.21, 0.297) with small deltas
                val bmp = loadBitmapHighQuality(targetFile!!)
                if (bmp == null) {
                    tvHint.text = "Invalid image"
                    btnStartAR.isEnabled = true
                    return@launch
                }

                // Auto-scan widths (returns true if any width produced a TRACKING image)
                val scanned = autoScanWidthsQuick(bmp)
                if (!scanned) {
                    runOnUiThread {
                        tvHint.text = "Auto-scan failed — image not tracked. Try better lighting or different print size."
                        Toast.makeText(this@AugmentHostActivity, "Auto-scan failed: no tracking found", Toast.LENGTH_LONG).show()
                        btnStartAR.isEnabled = true
                        btnPlace.isEnabled = true // allow manual placement fallback
                    }
                } else {
                    runOnUiThread {
                        btnPlace.isEnabled = true
                        tvHint.text = "Ready — point camera at image and tap Place"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                tvHint.text = "AR setup failed: ${e.message}"
                Toast.makeText(this@AugmentHostActivity, "AR setup failed: ${e.message}", Toast.LENGTH_LONG).show()
                btnStartAR.isEnabled = true
            }
        }
    }

    // robust HTTP download (suspend)
    private suspend fun downloadWithStream(urlString: String?, filename: String): File? {
        if (urlString.isNullOrBlank()) {
            runOnUiThread { tvHint.text = "Download URL is empty" }
            Log.d(TAG, "downloadWithStream: empty url for $filename")
            return null
        }

        return withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            var outFile: File? = null
            try {
                Log.d(TAG, "Starting download: $urlString")
                val url = URL(urlString)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android) SceneViewClient/1.0")
                }

                conn.connect()
                val code = conn.responseCode
                val contentType = conn.contentType
                Log.d(TAG, "HTTP $code contentType=$contentType for $urlString")

                if (code !in 200..299) {
                    val msg = "HTTP $code while downloading $filename"
                    Log.e(TAG, msg)
                    runOnUiThread { tvHint.text = msg }
                    conn.errorStream?.bufferedReader()?.use { es ->
                        val err = es.readText()
                        Log.d(TAG, "Server error body (truncated): ${err.take(512)}")
                    }
                    return@withContext null
                }

                outFile = File(cacheDir, filename)
                conn.inputStream.use { input ->
                    BufferedInputStream(input).use { bis ->
                        FileOutputStream(outFile).use { outStream ->
                            val buffer = ByteArray(8 * 1024)
                            var read: Int
                            val job = coroutineContext[Job]
                            while (true) {
                                if (job != null && !job.isActive) {
                                    Log.d(TAG, "Download cancelled for $filename")
                                    try { outStream.flush() } catch (_: Exception) {}
                                    try { outFile.delete() } catch (_: Exception) {}
                                    return@withContext null
                                }
                                read = bis.read(buffer)
                                if (read == -1) break
                                outStream.write(buffer, 0, read)
                            }
                            outStream.flush()
                        }
                    }
                }

                Log.d(TAG, "Download finished: ${outFile!!.absolutePath}")
                runOnUiThread { tvHint.text = "Downloaded: ${outFile.name}" }
                return@withContext outFile
            } catch (e: Exception) {
                Log.e(TAG, "Download error for $urlString: ${e.message}", e)
                runOnUiThread { tvHint.text = "Download error: ${e.message}" }
                try { outFile?.delete() } catch (_: Exception) {}
                return@withContext null
            } finally {
                try { conn?.disconnect() } catch (_: Exception) {}
            }
        }
    }

    private suspend fun downloadAndReport(url: String?, filename: String): File? {
        runOnUiThread { tvHint.text = "Downloading $filename..." }
        val f = downloadWithStream(url, filename)
        if (f == null) {
            runOnUiThread { tvHint.text = "Failed to download $filename" }
            Log.d(TAG, "downloadAndReport: failed $filename url=$url")
        } else {
            runOnUiThread { tvHint.text = "Downloaded $filename" }
            Log.d(TAG, "downloadAndReport: ok $filename -> ${f.absolutePath}")
        }
        return f
    }

    /** High-quality decode to ARGB_8888 */
    private fun loadBitmapHighQuality(file: File): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            file.inputStream().use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            Log.e(TAG, "loadBitmapHighQuality failed: ${e.message}", e)
            null
        }
    }

    /**
     * Auto-scan widths quickly. It will try candidate widths and small +/- deltas (sweep).
     * Returns true if any width produced a TRACKING augmented image during the scan period.
     */
    private suspend fun autoScanWidthsQuick(bitmap: Bitmap): Boolean {
        val baseCandidates = listOf(0.15f, 0.21f, 0.297f) // meters (approx A6, A4 width, A3 width)
        val sweep = listOf(-0.10f, -0.05f, 0f, 0.05f, 0.10f) // -10% .. +10%
        val perWidthTimeoutMs = 8000L
        val initialWaitMs = 1200L

        for (base in baseCandidates) {
            for (d in sweep) {
                val width = base * (1f + d)
                Log.d(TAG, "Auto-scan trying width=$width")
                runOnUiThread { tvHint.text = "Scanning width=${"%.3f".format(width)}m..." }

                // Recreate SceneView and configure DB with this width
                recreateSceneViewAndConfigure(bitmap, width)

                // give session a short time to start / warm up
                delay(initialWaitMs)

                // poll up to perWidthTimeoutMs waiting for any TRACKING augmented image
                var elapsed = 0L
                val pollInterval = 500L
                var found = false
                while (elapsed < perWidthTimeoutMs) {
                    // cooperative cancellation
                    val job = coroutineContext[Job]
                    if (job != null && !job.isActive) return false

                    // check latest frame and updated augmented images
                    try {
                        val frame = try { sceneView.session?.update() } catch (e: Exception) { null }
                        if (frame != null) {
                            val updated = try { frame.getUpdatedAugmentedImages() } catch (e: Exception) { emptyList<com.google.ar.core.AugmentedImage>() }
                            val states = updated.map { it.trackingState }
                            Log.d(TAG, "width=$width frame updatedCount=${updated.size} states=$states")
                            // if any image is TRACKING -> success
                            val anyTracking = updated.any { it.trackingState == TrackingState.TRACKING }
                            // store last seen image for place button fallback
                            updated.forEach {
                                if (it.trackingState == TrackingState.TRACKING || it.trackingState == TrackingState.PAUSED) {
                                    lastSeenAugmentedImage = it
                                }
                            }
                            if (anyTracking) {
                                Log.d(TAG, "Auto-scan success for width=$width")
                                found = true
                                break
                            }
                        } else {
                            Log.d(TAG, "width=$width frame==null")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "auto-scan frame check failed: ${e.message}")
                    }

                    delay(pollInterval)
                    elapsed += pollInterval
                }

                if (found) return true

                Log.d(TAG, "Auto-scan QUICK failed for width=$width (no TRACKING)")
            }
        }

        Log.d(TAG, "Auto-scan QUICK complete: none succeeded")
        return false
    }

    /** Recreate SceneView and configure runtime AugmentedImageDatabase with provided bitmap and width. */
    private fun recreateSceneViewAndConfigure(augmentedBitmap: Bitmap, physicalWidthMeters: Float) {
        runOnUiThread {
            try {
                try {
                    if (::sceneView.isInitialized) sceneView.destroy()
                } catch (e: Exception) {
                    Log.w(TAG, "sceneView.destroy() threw: ${e.message}")
                }

                val parent = findViewById<ViewGroup>(R.id.main)
                val old = parent.findViewById<ARSceneView?>(R.id.sceneView)
                old?.let { parent.removeView(it) }

                val newScene = ARSceneView(this).apply {
                    id = R.id.sceneView
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }

                parent.addView(newScene, 0)
                sceneView = newScene

                sceneView.configureSession { session, config ->
                    try {
                        // create runtime DB
                        val db = AugmentedImageDatabase(session)
                        val addIndex = try {
                            db.addImage("user_target", augmentedBitmap, physicalWidthMeters)
                        } catch (e: Exception) {
                            Log.e(TAG, "addImage threw: ${e.message}", e)
                            -1
                        }
                        Log.d(TAG, "AugmentedImageDatabase.addImage returned index=$addIndex (w=$physicalWidthMeters)")
                        if (addIndex < 0) {
                            runOnUiThread { tvHint.text = "addImage failed (index=$addIndex)" }
                        }
                        config.augmentedImageDatabase = db
                        try {
                            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        } catch (_: Exception) { /* ignore */ }
                        Log.d(TAG, "Configured session with DB (w=$physicalWidthMeters)")
                        runOnUiThread { tvHint.text = "Image registered; scanning..." }
                    } catch (e: Exception) {
                        Log.e(TAG, "configureSession/DB creation failed: ${e.message}", e)
                        runOnUiThread { tvHint.text = "Failed to add augmented image DB: ${e.message}" }
                    }
                }

                // handle session updates
                sceneView.onSessionUpdated = { session, frame -> handleSessionUpdate(session, frame) }

                // lighting attempt (best-effort)
//                ensureSceneLighting()

                Log.d(TAG, "Recreated SceneView and attached handler.")
                runOnUiThread { tvHint.text = "ARSceneView recreated — looking for image..." }
            } catch (e: Exception) {
                Log.e(TAG, "recreateSceneViewAndConfigure error: ${e.message}", e)
                runOnUiThread { tvHint.text = "Failed to recreate SceneView: ${e.message}" }
            }
        }
    }

    /** Centralized session update handler */
    private fun handleSessionUpdate(session: com.google.ar.core.Session?, frame: com.google.ar.core.Frame?) {
        if (frame == null) return

        // brief camera status to UI
        try {
            val camState = frame.camera.trackingState
            val camFail = try { frame.camera.trackingFailureReason } catch (_: Exception) { null }
            runOnUiThread {
                tvHint.text = "Camera: $camState${if (camFail != null) " ($camFail)" else ""}"
            }
        } catch (_: Exception) {}

        // process updated augmented images
        try {
            frame.getUpdatedAugmentedImages().forEach { augmentedImage ->
                // remember last seen image for Place fallback
                if (augmentedImage.trackingState == TrackingState.TRACKING || augmentedImage.trackingState == TrackingState.PAUSED) {
                    lastSeenAugmentedImage = augmentedImage
                    runOnUiThread { btnPlace.isEnabled = true }
                }

                // add model on TRACKING (only once per image name)
                if (augmentedImage.trackingState == TrackingState.TRACKING &&
                    augmentedImageNodes.none { it.imageName == augmentedImage.name }
                ) {
                    val node = AugmentedImageNode(sceneView.engine, augmentedImage)
                    try {
                        val modelInstance = sceneView.modelLoader.createModelInstance(file = modelFile!!)
                        makeModelUnlitIfPossible(modelInstance)

                        val modelNode = ModelNode(
                            modelInstance = modelInstance,
                            scaleToUnits = 0.15f,
                            centerOrigin = Position(0f)
                        )
                        modelNode.position = Position(0f, 0.02f, 0f)
                        node.addChildNode(modelNode)

                        sceneView.addChildNode(node)
                        augmentedImageNodes += node

                        Log.d(TAG, "Placed model for ${augmentedImage.name}")
                        runOnUiThread { tvHint.text = "Placed: ${augmentedImage.name}" }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to place model for ${augmentedImage.name}: ${e.message}", e)
                        runOnUiThread { tvHint.text = "Placement failed: ${e.message}" }
                    }
                } else {
                    // show detected/paused info
                    if (augmentedImage.trackingState == TrackingState.PAUSED) {
                        val camFail = try { frame.camera.trackingFailureReason } catch (e: Exception) { null }
                        val msg = cameraFailureReasonToMessage(camFail)
                        runOnUiThread { tvHint.text = "Detected '${augmentedImage.name}' — $msg" }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "onSessionUpdated error: ${e.message}")
        }
    }

    /** Place button flow: tries to place on tracked image, otherwise anchor in front of camera */
    private fun tryPlaceModel() {
        lifecycleScope.launch {
            runOnUiThread { tvHint.text = "Placing model..." }

            val img = lastSeenAugmentedImage
            if (img != null && img.trackingState == TrackingState.TRACKING) {
                runOnUiThread { tvHint.text = "Placing on tracked image..." }
                placeOnAugmentedImage(img)
                return@launch
            }

            val frame = try { sceneView.session?.update() } catch (e: Exception) { null }
            val cameraPose = try { frame?.camera?.pose } catch (e: Exception) { null }

            if (cameraPose != null) {
                runOnUiThread { tvHint.text = "Image not tracked — placing in front of camera..." }
                val distanceMeters = 0.6f
                val placePose = cameraPose.compose(com.google.ar.core.Pose.makeTranslation(0f, 0f, -distanceMeters))
                val anchor = try { sceneView.session?.createAnchor(placePose) } catch (e: Exception) { null }

                if (anchor != null) {
                    placeModelAtAnchor(anchor)
                    return@launch
                } else {
                    placeModelFallbackCameraRelative(cameraPose, distanceMeters)
                    return@launch
                }
            } else {
                placeModelFallbackSimple()
            }
        }
    }

    private fun placeOnAugmentedImage(img: com.google.ar.core.AugmentedImage) {
        try {
            val node = AugmentedImageNode(sceneView.engine, img)
            val modelInstance = sceneView.modelLoader.createModelInstance(file = modelFile!!)
            makeModelUnlitIfPossible(modelInstance)
            val modelNode = ModelNode(
                modelInstance = modelInstance,
                scaleToUnits = 0.2f,
                centerOrigin = Position(0f)
            )
            modelNode.position = Position(0f, 0.01f, 0f)
            node.addChildNode(modelNode)
            sceneView.addChildNode(node)
            augmentedImageNodes += node
            runOnUiThread { tvHint.text = "Placed on image" }
        } catch (e: Exception) {
            Log.e(TAG, "placeOnAugmentedImage failed: ${e.message}", e)
            runOnUiThread { tvHint.text = "Failed to place on image: ${e.message}"; btnPlace.isEnabled = true }
        }
    }

    private fun placeModelAtAnchor(anchor: com.google.ar.core.Anchor) {
        try {
            val modelInstance = sceneView.modelLoader.createModelInstance(file = modelFile!!)
            makeModelUnlitIfPossible(modelInstance)
            val modelNode = ModelNode(
                modelInstance = modelInstance,
                scaleToUnits = 0.2f,
                centerOrigin = Position(0f)
            )
            val p = anchor.pose
            modelNode.position = Position(p.tx(), p.ty(), p.tz())
            sceneView.addChildNode(modelNode)
            placedModelNodes += modelNode
            runOnUiThread { tvHint.text = "Placed at anchor" }
        } catch (e: Exception) {
            Log.e(TAG, "placeModelAtAnchor failed: ${e.message}", e)
            runOnUiThread { tvHint.text = "Failed to place at anchor: ${e.message}"; btnPlace.isEnabled = true }
        }
    }

    private fun placeModelFallbackCameraRelative(cameraPose: com.google.ar.core.Pose, distanceMeters: Float) {
        try {
            val placePose = cameraPose.compose(com.google.ar.core.Pose.makeTranslation(0f, 0f, -distanceMeters))
            val tx = placePose.tx()
            val ty = placePose.ty()
            val tz = placePose.tz()
            val modelInstance = sceneView.modelLoader.createModelInstance(file = modelFile!!)
            makeModelUnlitIfPossible(modelInstance)
            val modelNode = ModelNode(
                modelInstance = modelInstance,
                scaleToUnits = 0.2f,
                centerOrigin = Position(0f)
            )
            modelNode.position = Position(tx, ty, tz)
            sceneView.addChildNode(modelNode)
            placedModelNodes += modelNode
            runOnUiThread { tvHint.text = "Placed camera-relative" }
        } catch (e: Exception) {
            Log.e(TAG, "placeModelFallbackCameraRelative failed: ${e.message}", e)
            runOnUiThread { tvHint.text = "Fallback placement failed: ${e.message}"; btnPlace.isEnabled = true }
        }
    }

    private fun placeModelFallbackSimple() {
        try {
            val modelInstance = sceneView.modelLoader.createModelInstance(file = modelFile!!)
            makeModelUnlitIfPossible(modelInstance)
            val modelNode = ModelNode(
                modelInstance = modelInstance,
                scaleToUnits = 0.2f,
                centerOrigin = Position(0f)
            )
            modelNode.position = Position(0f, 0f, -0.5f)
            sceneView.addChildNode(modelNode)
            placedModelNodes += modelNode
            runOnUiThread { tvHint.text = "Placed simple fallback" }
        } catch (e: Exception) {
            Log.e(TAG, "placeModelFallbackSimple failed: ${e.message}", e)
            runOnUiThread { tvHint.text = "Simple fallback failed: ${e.message}"; btnPlace.isEnabled = true }
        }
    }

    /**
     * Try to make a model instance unlit (so it's visible without scene lighting).
     * Best-effort: uses reflection where API differs.
     */
    private fun makeModelUnlitIfPossible(modelInstance: Any?) {
        if (modelInstance == null) return
        try {
            val candidates = listOf("setLighting", "setIsLightingEnabled", "setIsLit", "setUnlit", "setUnlitMode", "setEnableLighting")
            for (name in candidates) {
                try {
                    val method = modelInstance::class.java.methods.firstOrNull {
                        it.name.equals(name, ignoreCase = true) &&
                                it.parameterTypes.size == 1 &&
                                (it.parameterTypes[0] == Boolean::class.java || it.parameterTypes[0] == java.lang.Boolean.TYPE)
                    }
                    if (method != null) {
                        method.invoke(modelInstance, false)
                        Log.d(TAG, "makeModelUnlitIfPossible: invoked $name on ${modelInstance::class.simpleName}")
                        return
                    }
                } catch (_: Exception) { }
            }
        } catch (e: Exception) {
            Log.w(TAG, "makeModelUnlitIfPossible failed: ${e.message}")
        }
    }

    /** Convert ARCore TrackingFailureReason to message. */
    private fun cameraFailureReasonToMessage(reason: TrackingFailureReason?): String {
        return when (reason) {
            TrackingFailureReason.NONE -> "Camera ok — move device slowly until stable."
            TrackingFailureReason.BAD_STATE -> "Bad device state. Try restarting the app."
            TrackingFailureReason.INSUFFICIENT_FEATURES -> "Too few visual features. Use a higher-contrast image or move slowly."
            TrackingFailureReason.EXCESSIVE_MOTION -> "Too much motion. Hold device steady."
            TrackingFailureReason.INSUFFICIENT_LIGHT -> "Too dark. Increase lighting."
            TrackingFailureReason.CAMERA_UNAVAILABLE -> "Camera unavailable. Close other camera apps."
            null -> "Trying to get a stable tracking pose..."
            else -> "Tracking paused: $reason"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { if (::sceneView.isInitialized) sceneView.destroy() } catch (e: Exception) { Log.w(TAG, "destroy threw: ${e.message}") }
    }
}
