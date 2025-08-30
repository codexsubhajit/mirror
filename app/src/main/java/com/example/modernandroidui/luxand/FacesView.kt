package com.example.modernandroidui.luxand

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Canvas
import android.graphics.Matrix

import androidx.annotation.OptIn
import androidx.camera.view.TransformExperimental

import androidx.core.graphics.toRectF

import android.view.View
import android.content.Context
import android.util.AttributeSet

class FacesView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    init {
        setWillNotDraw(false)
    }

    var facesTransform: Matrix? = null

    private val paint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val textPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL_AND_STROKE
        textAlign = Paint.Align.CENTER
        textSize = 42.0F
    }

    inner class FaceRect(val face: FacesProcessor.Face) {

        var rectF: RectF? = null
            private set

        init {
            if (facesTransform != null) {
                rectF = face.rect.toRectF()
                facesTransform?.mapRect(rectF)

            }
        }

    }

    private var faceRects: Array<FaceRect> = arrayOf()

    fun setFaces(faces: Array<FacesProcessor.Face>) {
        faceRects = Array(faces.size) { i -> FaceRect(faces[i]) }
        postInvalidate()
    }
    @OptIn(TransformExperimental::class) override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (faceRects.isEmpty()) {
            return
        }
        for (faceRect in faceRects) {
            val rect = faceRect.rectF ?: continue

            canvas.drawRect(rect, paint)

            faceRect.face.lock()
            val name = faceRect.face.name
            faceRect.face.unlock()
            if (name != "") {
                canvas.drawText(name, rect.centerX(), rect.bottom + 50, textPaint)
            }
        }
    }

    fun getFaceContainingPoint(x: Float, y: Float): FacesProcessor.Face? {
        for (faceRect in faceRects) {
            val rect = faceRect.rectF ?: continue

            if (rect.contains(x, y)) {
                return faceRect.face
            }
        }

        return null
    }

    @Suppress("RedundantOverride")
    override fun performClick(): Boolean {
        return super.performClick()
    }

}