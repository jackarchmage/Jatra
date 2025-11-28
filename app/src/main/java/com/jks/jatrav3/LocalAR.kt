package com.jks.jatrav3

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.addAugmentedImage
import io.github.sceneview.ar.arcore.getUpdatedAugmentedImages
import io.github.sceneview.ar.node.AugmentedImageNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import kotlin.invoke

class LocalAR : AppCompatActivity() {
    lateinit var sceneview : ARSceneView
    val augmentedImageNodes = mutableListOf<AugmentedImageNode>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_local_ar)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets }
        sceneview = findViewById(R.id.sceneView)
        sceneview.apply { configureSession { session, config -> config.addAugmentedImage(session,"qrcode",assets.open("augmentedimages/blue.jpg").use (BitmapFactory::decodeStream) ) }
            onSessionUpdated={ session, frame -> frame.getUpdatedAugmentedImages().forEach { augmentedImage -> if (augmentedImageNodes.none{it.imageName==augmentedImage.name}) { val node = AugmentedImageNode(sceneview.engine, augmentedImage).apply{
                    when(augmentedImage.name){ "qrcode"->{ addChildNode(ModelNode( modelInstance = modelLoader.createModelInstance( assetFileLocation = "models/somen.glb" ), scaleToUnits = 0.3f, centerOrigin = Position(0.0f) )) }} }
                sceneview.addChildNode(node)
                augmentedImageNodes += node
            }
            }
            }
        }
    }
}