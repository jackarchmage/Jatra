package com.jks.jatrav3

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.LightEstimate
import com.google.ar.core.Plane
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.ArCoreApk
import com.jks.jatrav3.databinding.ActivityVrviewBinding
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.getUpdatedPlanes
import io.github.sceneview.ar.getDescription
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VRView : AppCompatActivity() {

    private lateinit var binding: ActivityVrviewBinding

    private var modelUrl: String? = null

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            setupSceneView()
        } else {
            Toast.makeText(this, "Camera permission is required for AR", Toast.LENGTH_LONG).show()
        }
    }

    // UI state
    private var isLoading = false
        set(value) {
            field = value
            binding.loadingView.isGone = !value
        }

    // anchor holder (null when nothing placed)
    private var anchorNode: AnchorNode? = null
        set(value) {
            if (field != value) {
                field = value
            }
        }

    private var trackingFailureReason: TrackingFailureReason? = null
        set(value) {
            if (field != value) {
                field = value
            }
        }

    // the model node we placed (nullable until model is loaded & attached)
    private var placedModelNode: ModelNode? = null

    // slider scale range
    private val MIN_SCALE = 0.05f   // 5% of original
    private val MAX_SCALE = 3.0f    // 300% of original
    private var currentScale = 1.0f // current multiplier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVrviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // read model url from intent
        modelUrl = intent?.getStringExtra("MODEL_URL")?.takeIf { it.isNotBlank() }

        if (modelUrl.isNullOrBlank()) {
            Toast.makeText(this, "No model URL provided", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.btnClose.setOnClickListener { finish() }

        // place button - places on the first detected plane center (or you can add your own selection UI).
        binding.btnPlace.setOnClickListener {
            // if we already have anchor, do nothing or re-place
            if (anchorNode != null) return@setOnClickListener

            // try to place on a detected plane center if one exists
            val sceneView = binding.arSceneView
            val plane = sceneView.session?.let { sess ->
                // get a recent frame and use its planes
                sceneView.frame?.getUpdatedPlanes()
                    ?.firstOrNull { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
            }
            plane?.let {
                addAnchorNode(it.createAnchor(it.centerPose))
            } ?: run {
                Toast.makeText(this, "No horizontal plane detected yet", Toast.LENGTH_SHORT).show()
            }
        }
        // zoom slider setup
        setupZoomSlider()

        // camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestCamera.launch(Manifest.permission.CAMERA)
        } else {
            setupSceneView()
        }
    }
    private fun setupZoomSlider() {
        // Ensure layout has seekZoom (SeekBar) and zoomValue (TextView) via view binding
        val seek = binding.seekZoom
        val zoomLabel = binding.zoomValue

        // configure seek: use 0..1000 for fine-grain control
        val SEEK_MAX = 1000
        seek.max = SEEK_MAX
        // initial progress corresponds to currentScale (1.0)
        val initialProgress = scaleToProgress(currentScale, SEEK_MAX)
        seek.progress = initialProgress
        zoomLabel.text = formatScaleLabel(currentScale)

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                // convert progress to scale
                currentScale = progressToScale(progress, SEEK_MAX)
                zoomLabel.text = formatScaleLabel(currentScale)

                // apply immediately to placed model if any
                placedModelNode?.let { modelNode ->
                    runOnUiThread {
                        val ok = applyScaleToModelNode(modelNode, currentScale)
                        if (!ok) {
                            // If it fails, notify once (so it's obvious)
                            Toast.makeText(this@VRView, "Could not apply scale to model (api mismatch).", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun formatScaleLabel(scale: Float): String {
        // show percent or multiplier
        val percent = (scale * 100).toInt()
        return "$percent %"
    }

    private fun scaleToProgress(scale: Float, max: Int): Int {
        val t = (scale - MIN_SCALE) / (MAX_SCALE - MIN_SCALE)
        return (t.coerceIn(0f, 1f) * max).toInt()
    }

    private fun progressToScale(progress: Int, max: Int): Float {
        val t = (progress.toFloat() / max.toFloat()).coerceIn(0f, 1f)
        return MIN_SCALE + t * (MAX_SCALE - MIN_SCALE)
    }


    private fun ensureArCoreInstalled(): Boolean {
        val installStatus =
            ArCoreApk.getInstance().requestInstall(this, /*userRequestedInstall=*/ false)
        return when (installStatus) {
            ArCoreApk.InstallStatus.INSTALLED -> true
            ArCoreApk.InstallStatus.INSTALL_REQUESTED -> false
        }
    }

    private fun setupSceneView() {
        if (!ensureArCoreInstalled()) return

        val sceneView = binding.arSceneView

        // attach lifecycle so SceneView handles resume/pause automatically
        sceneView.lifecycle = lifecycle

        // enable plane display
        sceneView.planeRenderer.isEnabled = true

        // configure session (depth mode, light estimation)
        sceneView.configureSession { session, config ->
            config.depthMode = if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                Config.DepthMode.AUTOMATIC
            } else {
                Config.DepthMode.DISABLED
            }
            config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
            config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
        }

        // track plane detection to auto-add an anchor when none placed yet
        sceneView.onSessionUpdated = { _, frame ->
            if (anchorNode == null) {
                frame.getUpdatedPlanes()
                    .firstOrNull { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
                    ?.let { plane ->
                        addAnchorNode(plane.createAnchor(plane.centerPose))
                    }
            }
        }

        // track AR tracking failure so we can show instruction text
        sceneView.onTrackingFailureChanged = { reason ->
            this@VRView.trackingFailureReason = reason
        }

        // per-frame: apply emissive fallback based on ARCore LightEstimate
        // onSessionUpdated gives us (session, frame) each frame.
        sceneView.onSessionUpdated = { _, frame ->
            // auto-add anchor when none placed (optional)
            if (anchorNode == null) {
                frame.getUpdatedPlanes()
                    .firstOrNull { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
                    ?.let { plane ->
                        addAnchorNode(plane.createAnchor(plane.centerPose))
                    }
            }

            // per-frame light estimate -> apply fallback lighting/emissive to the placed model
            placedModelNode?.let { modelNode ->
                // do not block the AR frame callback — launch coroutine
                lifecycleScope.launch {
                    applyLightEstimateToModel(frame, modelNode)
                }
            }
        }

    }

    private fun addAnchorNode(anchor: Anchor) {
        val sceneView = binding.arSceneView

        sceneView.addChildNode(
            AnchorNode(sceneView.engine, anchor)
                .apply {
                    isEditable = true
                    lifecycleScope.launch {
                        isLoading = true
                        // build and attach model node; capture it in placedModelNode
                        val modelNode = buildModelNode()
                        modelNode?.let { addChildNode(it); placedModelNode = it }
                        isLoading = false
                    }
                    anchorNode = this
                }
        )
    }

    suspend fun buildModelNode(): ModelNode? {
        val sceneView = binding.arSceneView
        val url = modelUrl ?: return null

        return withContext(Dispatchers.IO) {
            try {
                sceneView.modelLoader.loadModelInstance(url)
            } catch (e: Exception) {
                null
            }
        }?.let { modelInstance ->
            ModelNode(
                modelInstance = modelInstance,
                scaleToUnits = 0.5f,
                centerOrigin = Position(y = -0.5f)
            ).apply {
                isEditable = false
            }
        }
    }
    private fun applyScaleToModelNode(modelNode: io.github.sceneview.node.ModelNode, scaleMultiplier: Float): Boolean {
        // Try multiple common APIs. Return true on first success.
        try {
            // 1) try a direct public float property `scale` (Kotlin/Java field)
            try {
                val f = modelNode::class.java.getDeclaredField("scale")
                f.isAccessible = true
                if (f.type == Float::class.javaPrimitiveType || f.type == java.lang.Float::class.java) {
                    f.setFloat(modelNode, scaleMultiplier)
                    return true
                }
                // if it's a Vector3, we'll handle below
            } catch (_: NoSuchFieldException) { /* continue */ }

            // 2) try setScale(float)
            try {
                val m = modelNode::class.java.getMethod("setScale", Float::class.javaPrimitiveType)
                m.invoke(modelNode, scaleMultiplier)
                return true
            } catch (_: NoSuchMethodException) { /* continue */ }

            // 3) try scale as property with setter setScale(java.lang.Float)
            try {
                val m = modelNode::class.java.getMethod("setScale", java.lang.Float::class.java)
                m.invoke(modelNode, java.lang.Float.valueOf(scaleMultiplier))
                return true
            } catch (_: NoSuchMethodException) { /* continue */ }

            // 4) try Vector3-based APIs: setScale(Vector3) or setLocalScale(Vector3) or field 'scale' of Vector3 type
            try {
                val vecClass = Class.forName("io.github.sceneview.math.Vector3")
                val constructor = vecClass.getConstructor(Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                val vec = constructor.newInstance(scaleMultiplier, scaleMultiplier, scaleMultiplier)
                // try setScale(Vector3)
                try {
                    val m = modelNode::class.java.getMethod("setScale", vecClass)
                    m.invoke(modelNode, vec)
                    return true
                } catch (_: NoSuchMethodException) { /* continue */ }

                // try setLocalScale(Vector3)
                try {
                    val m2 = modelNode::class.java.getMethod("setLocalScale", vecClass)
                    m2.invoke(modelNode, vec)
                    return true
                } catch (_: NoSuchMethodException) { /* continue */ }

                // try field 'scale' of Vector3 type
                try {
                    val field = modelNode::class.java.getDeclaredField("scale")
                    field.isAccessible = true
                    if (field.type == vecClass) {
                        field.set(modelNode, vec)
                        return true
                    }
                } catch (_: NoSuchFieldException) { /* continue */ }
            } catch (_: ClassNotFoundException) { /* No Vector3 class available -> skip */ }

            // 5) try float property 'localScale' if exists
            try {
                val f2 = modelNode::class.java.getDeclaredField("localScale")
                f2.isAccessible = true
                if (f2.type == Float::class.javaPrimitiveType || f2.type == java.lang.Float::class.java) {
                    f2.setFloat(modelNode, scaleMultiplier)
                    return true
                }
            } catch (_: NoSuchFieldException) { /* continue */ }

            // 6) try methods used in some libs: setWorldScale(float) or setLocalScale(float)
            try {
                val m3 = modelNode::class.java.getMethod("setWorldScale", Float::class.javaPrimitiveType)
                m3.invoke(modelNode, scaleMultiplier)
                return true
            } catch (_: NoSuchMethodException) { /* continue */ }

            try {
                val m4 = modelNode::class.java.getMethod("setLocalScale", Float::class.javaPrimitiveType)
                m4.invoke(modelNode, scaleMultiplier)
                return true
            } catch (_: NoSuchMethodException) { /* continue */ }

            // If all reflection attempts fail, try a last-resort approach: change the modelInstance nodes scale if accessible
            try {
                val modelInstanceField = modelNode::class.java.getDeclaredField("modelInstance")
                modelInstanceField.isAccessible = true
                val modelInstance = modelInstanceField.get(modelNode) ?: return false
                // Many GLTF loaders expose a transformation on modelInstance, try 'setScale' on it
                try {
                    val m = modelInstance::class.java.getMethod("setScale", Float::class.javaPrimitiveType)
                    m.invoke(modelInstance, scaleMultiplier)
                    return true
                } catch (_: NoSuchMethodException) { /* continue */ }
            } catch (_: NoSuchFieldException) { /* continue */ }

        } catch (t: Throwable) {
            // reflect failed totally — swallow but return false so caller can notify
            t.printStackTrace()
        }
        return false
    }


    /**
     * Read ARCore LightEstimate and set a simple emissive on the model so it is visible
     * when environment lighting / IBL is absent or too dark.
     */
    private suspend fun applyLightEstimateToModel(frame: Frame, modelNode: ModelNode?) {
        if (modelNode == null) return

        withContext(Dispatchers.Default) {
            try {
                val le: LightEstimate = frame.lightEstimate
                if (le.state != LightEstimate.State.VALID) {
                    // default subtle emissive when no valid estimate
                    withContext(Dispatchers.Main) {
                        setModelEmissive(modelNode, 0.25f, 0.25f, 0.25f, 0.4f)
                    }
                    return@withContext
                }

                val intensity = le.pixelIntensity.coerceAtLeast(0f) // typical ~0..2 (tweak)
                val colorCorrection = FloatArray(4)
                le.getColorCorrection(colorCorrection, 0) // RGBA

                val r = colorCorrection[0].coerceIn(0f, 1f)
                val g = colorCorrection[1].coerceIn(0f, 1f)
                val b = colorCorrection[2].coerceIn(0f, 1f)

                // Tune these multipliers to get desired brightness
                val emissiveStrength = (0.4f + intensity * 0.8f).coerceIn(0f, 6f)
                withContext(Dispatchers.Main) {
                    setModelEmissive(modelNode, r, g, b, emissiveStrength)
                }
            } catch (e: Exception) {
                // swallow - lighting update is non-fatal
            }
        }
    }

    /**
     * Attempt to set emissive parameter(s) on the model's material instances.
     *
     * This is defensive: SceneView / Filament wrappers vary. We try several common
     * parameter names via reflection and ignore failures.
     *
     * @param r,g,b color components [0..1]
     * @param strength multiplier to scale color (>=0)
     */
    private fun setModelEmissive(modelNode: ModelNode, r: Float, g: Float, b: Float, strength: Float) {
        val modelInstanceObj = try {
            // ModelNode keeps a reference to the model instance (name may vary across versions)
            val field = modelNode::class.java.getDeclaredField("modelInstance")
            field.isAccessible = true
            field.get(modelNode)
        } catch (t: Throwable) {
            null
        } ?: return

        // build emissive vec4
        val emissive = floatArrayOf(r * strength, g * strength, b * strength, 1f)

        try {
            // common property: materialInstances (Iterable)
            val materialInstances = try {
                val mField = modelInstanceObj.javaClass.getDeclaredField("materialInstances")
                mField.isAccessible = true
                mField.get(modelInstanceObj)
            } catch (_: Throwable) {
                null
            }

            // If we have a list/iterable of material instances, try to set parameters
            (materialInstances as? Iterable<*>)?.forEach { mat ->
                if (mat == null) return@forEach
                try {
                    // Try to find a setParameter(name, float[]) or setParameter(name, FloatArray) method
                    val setParam = mat.javaClass.methods.firstOrNull { m ->
                        m.name.equals("setParameter", ignoreCase = true)
                                && m.parameterTypes.size == 2
                    }
                    if (setParam != null) {
                        try {
                            // try emissiveFactor first (common)
                            setParam.invoke(mat, "emissiveFactor", emissive)
                        } catch (_: Throwable) {
                            try {
                                setParam.invoke(mat, "emissive", emissive)
                            } catch (_: Throwable) {
                                // ignore
                            }
                        }
                    } else {
                        // try filament-like setParameterVec4 or setFloat4 variants
                        val alt = mat.javaClass.methods.firstOrNull { m ->
                            (m.name.contains("set") && m.parameterTypes.size == 2)
                        }
                        // avoid calling unknowns blindly — skip if none
                        alt?.let {
                            try {
                                it.invoke(mat, "emissiveFactor", emissive)
                            } catch (_: Throwable) {
                            }
                        }
                    }
                } catch (_: Throwable) {
                    // ignore per-material failures
                }
            }
        } catch (_: Throwable) {
            // ignore global failures
        }
    }
}
