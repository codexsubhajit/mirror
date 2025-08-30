//Perfect Face
package com.example.modernandroidui.luxand

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.util.Base64
import android.util.Log
import android.view.View
import androidx.camera.core.ImageProxy
import com.luxand.FSDK
import com.example.modernandroidui.session.SessionManager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt
class LowPassFilter {
    private val a = 0.325f
    private var y: Float? = null
    fun pass(x: Float): Float {
        y = a * x + (1 - a) * (if (y == null) x else y)!!
        return y!!
    }
}
class ImageLightCondition {
    fun calculateHistogram(bitmap: Bitmap): IntArray {
        val width = bitmap.width
        val height = bitmap.height
        val histogram = IntArray(256)

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)
                val intensity = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                histogram[intensity]++
            }
        }

        return histogram
    }
    fun hasBacklightPeaks(bitmap: Bitmap): Boolean {
        val histogram = calculateHistogram(bitmap)
        val peakThreshold = 100 // Adjust the threshold as needed
        val peaks = histogram.filter { count -> count > peakThreshold }
        return peaks.isNotEmpty()
    }

    fun convertToGrayscale(bitmap: Bitmap): Bitmap {
        val grayscaleBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(grayscaleBitmap)
        val paint = Paint()
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)
        val colorMatrixFilter = ColorMatrixColorFilter(colorMatrix)
        paint.colorFilter = colorMatrixFilter
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return grayscaleBitmap
    }
    fun getROI(featurePoint: Pair<Double, Double>): Pair<IntRange, IntRange> {
        // Define the size of the ROI around the feature point
        val roiSize = 50 // Adjust as needed

        // Calculate the boundaries of the ROI
        val xStart = (featurePoint.first - roiSize).toInt()
        val xEnd = (featurePoint.first + roiSize).toInt()
        val yStart = (featurePoint.second - roiSize).toInt()
        val yEnd = (featurePoint.second + roiSize).toInt()

        return Pair(xStart..xEnd, yStart..yEnd)
    }
    fun calculatePixelDensity(image: Bitmap, roi: Pair<IntRange, IntRange>): Double {
        var totalIntensity = 0
        var totalPixels = 0

        val startX = roi.first.start.coerceAtLeast(0)
        val startY = roi.second.start.coerceAtLeast(0)
        val endX = roi.first.endInclusive.coerceAtMost(image.width - 1)
        val endY = roi.second.endInclusive.coerceAtMost(image.height - 1)

        for (x in startX..endX) {
            for (y in startY..endY) {
                val pixel = image.getPixel(x, y)

                // Extract intensity (assuming grayscale image)
                val intensity = Color.red(pixel)

                totalIntensity += intensity
                totalPixels++
            }
        }

        // Calculate average pixel intensity
        val averageIntensity = if (totalPixels > 0) {
            totalIntensity.toDouble() / totalPixels
        } else {
            0.0
        }

        return averageIntensity
    }
    fun calculateAverageIntensity(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        var totalIntensity = 0.0

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)
                val intensity = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3.0
                totalIntensity += intensity
            }
        }

        val pixelCount = width * height
        return totalIntensity / pixelCount
    }
    fun isBacklit(bitmap: Bitmap): Boolean {
        val grayscaleBitmap = convertToGrayscale(bitmap)
        val averageIntensity = calculateAverageIntensity(grayscaleBitmap)
        val backlightThreshold = 150.0 // Adjust the threshold as needed
        return averageIntensity < backlightThreshold
    }

    fun checkLightCondition(bitmap: Bitmap): Boolean {
        // Get the width and height of the bitmap
        val width = bitmap.width
        val height = bitmap.height

        // Variables to store total brightness and pixel count
        var totalBrightness = 0.0
        var pixelCount = 0

        // Iterate through each pixel in the bitmap
        for (x in 0 until width) {
            for (y in 0 until height) {
                // Get the color of the pixel
                val color = bitmap.getPixel(x, y)

                // Extract the red, green, and blue components
                val red = Color.red(color)
                val green = Color.green(color)
                val blue = Color.blue(color)

                // Calculate brightness using a simple formula (adjust weights as needed)
                val brightness = 0.299 * red + 0.587 * green + 0.114 * blue

                // Update total brightness and pixel count
                totalBrightness += brightness
                pixelCount++
            }
        }

        // Calculate the average brightness
        val averageBrightness = totalBrightness / pixelCount

        //Log.d("stoken LIB",averageBrightness.toString())

        // You can set a threshold to classify the light condition based on average brightness
        val threshold = 128.0 // Adjust the threshold as needed

        // Return the result based on the threshold
        if (averageBrightness < 100 ) {
            return true;
        }
        return false

    }
}
object FacesProcessor {
    private var lastLoadedTimestamp: Long = 0L
    fun shouldReload(file: java.io.File): Boolean = file.exists() && file.lastModified() > lastLoadedTimestamp
    fun updateLastLoaded(file: java.io.File) { lastLoadedTimestamp = file.lastModified() }

    // For stable face tracking (angle extraction reliability)
    private var lastFaceId: Long = 0L
    private var stableFrameCount: Int = 0

    private const val MAX_FACES = 1
    private const val MAX_NAME_SIZE = 256L

    private val rgbImageMode = FSDK.FSDK_IMAGEMODE().apply { mode = FSDK.FSDK_IMAGEMODE.FSDK_IMAGE_COLOR_24BIT }
    private val tracker = FSDK.HTracker()
    private val ids = LongArray(MAX_FACES)
    private val faceCount = longArrayOf(0)
    private var availface = false
    private val pan = LowPassFilter()
    private val tilt = LowPassFilter()
    //lateinit var activity: MainActivity2




    class Face(val id: Long) {
        val rollAngle: Float?
            get() {
                val result = arrayOf("")
                val status = FSDK.GetTrackerFacialAttribute(tracker, 0, id, "Angles", result, 256)
                if (status == FSDK.FSDKE_OK && result[0].contains("roll")) {
                    val pairs = result[0].split(";")
                    for (pair in pairs) {
                        val kv = pair.split("=")
                        if (kv.size == 2 && kv[0].trim().equals("roll", ignoreCase = true)) {
                            return kv[1].toFloatOrNull()
                        }
                    }
                }
                return null
            }

        val livenessScore: Float?
            get() {
                val result = arrayOf("")
                val status = FSDK.GetTrackerFacialAttribute(tracker, 0, id, "Liveness", result, 256)
                if (status == FSDK.FSDKE_OK && result[0].contains("Liveness")) {
                    val pairs = result[0].split(";")
                    for (pair in pairs) {
                        val kv = pair.split("=")
                        if (kv.size == 2 && kv[0].trim().equals("Liveness", ignoreCase = true)) {
                            return kv[1].toFloatOrNull()
                        }
                    }
                }
                return null
            }

        private val face: FSDK.TFace = FSDK.TFace()

        var name: String
            get() {
                val value = Array(1) { "" }
                FSDK.GetAllNames(tracker, id, value, MAX_NAME_SIZE)
                return value[0]
            }
            set(value) {
                FSDK.SetName(tracker, id, value)
            }

        val rect: Rect
            get() {
                return Rect(face.bbox.p0.x, face.bbox.p0.y, face.bbox.p1.x, face.bbox.p1.y)
            }

        init {
            FSDK.GetTrackerFace(tracker, 0, id, face)
        }

        fun lock() {
            FSDK.LockID(tracker, id)
        }

        fun unlock() {
            FSDK.UnlockID(tracker, id)
        }

        // Add panAngle and tiltAngle properties using GetTrackerFacialAttribute
        val panAngle: Float?
            get() {
                val result = arrayOf("")
                val status = FSDK.GetTrackerFacialAttribute(tracker, 0, id, "Angles", result, 256)
                if (status == FSDK.FSDKE_OK && result[0].contains("pan")) {
                    val pairs = result[0].split(";")
                    for (pair in pairs) {
                        val kv = pair.split("=")
                        if (kv.size == 2 && kv[0].trim().equals("pan", ignoreCase = true)) {
                            return kv[1].toFloatOrNull()
                        }
                    }
                }
                return null
            }
        val tiltAngle: Float?
            get() {
                val result = arrayOf("")
                val status = FSDK.GetTrackerFacialAttribute(tracker, 0, id, "Angles", result, 256)
                if (status == FSDK.FSDKE_OK && result[0].contains("tilt")) {
                    val pairs = result[0].split(";")
                    for (pair in pairs) {
                        val kv = pair.split("=")
                        if (kv.size == 2 && kv[0].trim().equals("tilt", ignoreCase = true)) {
                            return kv[1].toFloatOrNull()
                        }
                    }
                }
                return null
            }

    }

    init {
        FSDK.ActivateLibrary("MK+N5q+SIfht9Z+j4w4AJQEtaRhEpdITWAqSsJkaMur14PSCPQHcCpQJmUG9L5/MqzXdUV+5c/nN93OHYMPJa9FCdWqUXVd3/AS8geg72msTJZfCX6DFUhs+1rIB6jQzKR3NUM/W+VdVU/HV1cvkuR1mEfLKjhwEk6rP1mOAsy8=");
        FSDK.Initialize()

    }


    private fun fillRGBBytes(rgb: ByteArray, r: Int, g: Int, b: Int, y: Int, index: Int) {
        rgb[index + 2] = (y + r).coerceIn(0, 255).toByte()
        rgb[index + 1] = (y - g).coerceIn(0, 255).toByte()
        rgb[index + 0] = (y + b).coerceIn(0, 255).toByte()
    }

    private fun toUnsigned(a: Int): Int {
        return (a + 256) % 256
    }
    fun byteArrayToString(byteArray: ByteArray): String {
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }
    fun stringToByteArray(base64String: String): ByteArray {
        return Base64.decode(base64String, Base64.DEFAULT)
    }

    fun yuvToRGB(image: ImageProxy): ByteArray {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        yBuffer.rewind()
        uBuffer.rewind()
        vBuffer.rewind()

        val ySize = yBuffer.remaining()
        val nv21 = ByteArray(ySize + image.width * image.height / 2)

        var position = 0
        for (row in 0 until image.height) {
            yBuffer[nv21, position, image.width]
            position += image.width
            yBuffer.position(ySize.coerceAtMost(yBuffer.position() - image.width + yPlane.rowStride))
        }

        val chromaHeight = image.height / 2
        val chromaWidth = image.width / 2
        val vRowStride = vPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val uPixelStride = uPlane.pixelStride

        val vLineBuffer = ByteArray(vRowStride)
        val uLineBuffer = ByteArray(uRowStride)

        for (row in 0 until chromaHeight) {
            vBuffer[vLineBuffer, 0, vRowStride.coerceAtMost(vBuffer.remaining())]
            uBuffer[uLineBuffer, 0, uRowStride.coerceAtMost(uBuffer.remaining())]

            var vLineBufferPosition = 0
            var uLineBufferPosition = 0
            for (col in 0 until chromaWidth) {
                nv21[position++] = vLineBuffer[vLineBufferPosition]
                nv21[position++] = uLineBuffer[uLineBufferPosition]
                vLineBufferPosition += vPixelStride
                uLineBufferPosition += uPixelStride
            }
        }

        val rgb = ByteArray(image.width * image.height * 3)

        var outIndex: Int
        val outStride = 3 * image.width

        var yIndex: Int
        var cIndex = image.width * image.height

        for (i in 0 until image.height / 2) {
            yIndex = 2 * i * image.width
            outIndex = 6 * i * image.width

            for (j in 0 until image.width / 2) {
                val u = toUnsigned(nv21[cIndex].toInt())
                val v = toUnsigned(nv21[cIndex + 1].toInt())

                val r = (91881 * v shr 16) - 179
                val g = ((22544 * u + 46793 * v) shr 16) - 135
                val b = (116129 * u shr 16) - 226

                fillRGBBytes(rgb, r, g, b, toUnsigned(nv21[yIndex].toInt()), outIndex)
                fillRGBBytes(rgb, r, g, b, toUnsigned(nv21[yIndex + 1].toInt()), outIndex + 3)

                fillRGBBytes(rgb, r, g, b, toUnsigned(nv21[yIndex + image.width].toInt()), outIndex + outStride)
                fillRGBBytes(rgb, r, g, b, toUnsigned(nv21[yIndex + image.width + 1].toInt()), outIndex + outStride + 3)

                yIndex += 2
                cIndex += 2
                outIndex += 6
            }
        }

        return rgb
    }

    private fun setTrackerParameters() {
//        FSDK.SetTrackerMultipleParameters(
//            tracker,
//            "FaceDetection2PatchSize=256;" +
//                    "Threshold=0.8;" +
//                    "Threshold2=0.9",
//            intArrayOf(0)
//        )
//        "InternalResizeWidth=64;" +
        FSDK.SetTrackerMultipleParameters(
            tracker,
            "FaceDetection2PatchSize=256;" +
                    "TrimOutOfScreenFaces=true;"+
                    "TrimFacesWithUncertainFacialFeatures=true;"+
                    "Threshold=0.8;" +
                    "MemoryLimit=30000;" +
                    "Threshold2=0.9"+
                    "DetectFacialFeatures=true;" +
                    "HandleArbitraryRotations=true;" +
                    "DetermineFaceRotationAngle=true;" +
                    "DetectExpression=true;" +
                    "DetectAngles=true;"+
                    "DetectLiveness=true",
            intArrayOf(0)
        )
    }
    fun load(file: java.io.File) {
        if (com.luxand.FSDK.LoadTrackerMemoryFromFile(tracker, file.absolutePath) != com.luxand.FSDK.FSDKE_OK) {
            com.luxand.FSDK.CreateTracker(tracker)
            clear()
            return
        }
        setTrackerParameters()
        updateLastLoaded(file)
    }

    fun clear() {
        FSDK.ClearTracker(tracker)
        FSDK.SetTrackerParameter(tracker, "DetectionVersion", "2")

        setTrackerParameters()
    }
    fun clearV2() {
        FSDK.ClearTracker(tracker)
    }

    fun save(file: File) {
        FSDK.SaveTrackerMemoryToFile(tracker, file.absolutePath)


        //Log.d("Stoken T",tracker.toString());
    }
    fun getTracker():FSDK.HTracker {
        return tracker;

    }

    fun saveAndGetTracker(file: File,fid:Long): String {
        FSDK.SaveTrackerMemoryToFile(tracker, file.absolutePath)



//        Log.d("Stoken T",tracker.toString());
//        val buffer= byteArrayOf();
//        FSDK.SaveTrackerMemoryToBuffer(tracker,buffer)
        //String(buffer, Charsets.UTF_8);
        return ""

    }

    fun calculateDistance(point1: Pair<Double, Double>, point2: Pair<Double, Double>): Double {
        return sqrt((point2.first - point1.first).pow(2) + (point2.second - point1.second).pow(2))
    }
    fun Double.toDegrees(): Double {
        return Math.toDegrees(this)
    }
    fun calculatePanAngle(leftEye: Point, rightEye: Point): Double {
        return atan2((rightEye.y - leftEye.y).toDouble(), (rightEye.x - leftEye.x).toDouble()).toDegrees()
    }

    // Function to calculate the tilt angle
    fun calculateTiltAngle(leftEye: Point, rightEye: Point, innerCorner: Point, outerCorner: Point): Double {
        val horizontalDistance = outerCorner.x - innerCorner.x
        val verticalDistance = outerCorner.y - innerCorner.y
        val eyeMidpoint = Point((leftEye.x + rightEye.x) / 2, (leftEye.y + rightEye.y) / 2)

        val tiltAngleRad = atan2(verticalDistance.toDouble(), horizontalDistance.toDouble())
        val eyeDistance = Math.hypot((rightEye.x - leftEye.x).toDouble(),
            (rightEye.y - leftEye.y).toDouble()
        )
        val tiltAngleDeg = tiltAngleRad.toDegrees() + atan2((eyeMidpoint.y - innerCorner.y).toDouble(),
            (eyeMidpoint.x - innerCorner.x).toDouble()
        ).toDegrees()

        return tiltAngleDeg
    }
    private fun getState()
    {
        var pan = "0";
        var tilt = "0";
        val result = arrayOf("")
        if (FSDK.GetTrackerFacialAttribute(tracker, 0, ids[0], "Angles", result, 256) != FSDK.FSDKE_OK)
            return;
        for (pair in result[0].split(";")) {
            val values = pair.split("=".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
            if (values[0].equals("pan", ignoreCase = true)) {
                pan = values[1].toString();
//                continue
            }
            if (values[0].equals("tilt", ignoreCase = true)) {
                tilt = values[1].toString()
            }
        }
        Log.d("stoken main","pan val - $pan")
        Log.d("stoken main","tilt val - $tilt")
    }
    // Improved and stable brightness analysis for face detection
    private fun analyzeImageBrightness(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        var totalBrightness = 0L

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val red = (pixel shr 16) and 0xff
                val green = (pixel shr 8) and 0xff
                val blue = pixel and 0xff
                val brightness = (red + green + blue) / 3
                totalBrightness += brightness
            }
        }

        val avgBrightness = totalBrightness / (width * height)


        return avgBrightness < 80  // Threshold for low light detection
    }





    fun acceptV3(imageProxy: ImageProxy): Array<Face> {
        // Do NOT call setTrackerParameters() here; only after tracker creation/load/clear
        val data = yuvToRGB(imageProxy)
        val image = FSDK.HImage()
        FSDK.LoadImageFromBuffer(image, data, imageProxy.width, imageProxy.height, imageProxy.width * 3, rgbImageMode)
        FSDK.FeedFrame(tracker, 0, image, faceCount, ids)

        var pan: Float? = null
        var tilt: Float? = null
        var roll: Float? = null
        val resultLiveness = arrayOf("")
        var livenessScore2: Float? = null
        val imageBit = imageProxy.toBitmap()
        var eyesMissing = false
        var eyesTooClose = false
        var chin = false
        var nose = false


        //Log.d("acceptV3", "faceCount=${faceCount[0]}, id=${if (faceCount[0] > 0) ids[0] else "none"}")

        if (faceCount[0] > 0 && ids[0] != 0L) {
            // Log face rectangle size
            val face = FSDK.TFace()
            val statusFace = FSDK.GetTrackerFace(tracker, 0, ids[0], face)
            if (statusFace == FSDK.FSDKE_OK) {
                val width = face.bbox.p1.x - face.bbox.p0.x
                val height = face.bbox.p1.y - face.bbox.p0.y
                //Log.d("acceptV3", "Face rect: (${face.bbox.p0.x},${face.bbox.p0.y})-(${face.bbox.p1.x},${face.bbox.p1.y}), size=${width}x${height}")
            }
            if (ids[0] == lastFaceId) {
                stableFrameCount++
            } else {
                lastFaceId = ids[0]
                stableFrameCount = 1
            }
            //if (stableFrameCount >= 3) { // Only try after 3 consecutive frames
                val result = arrayOf("")
                val status = FSDK.GetTrackerFacialAttribute(tracker, 0, ids[0], "Angles", result, 256)
                // Fallback: try image-based attribute extraction
                val facePosition = FSDK.TFacePosition()
                if (FSDK.DetectFace(image, facePosition) == FSDK.FSDKE_OK) {
                    Log.i("acceptV3", "Image-Detected")
                    val feature = FSDK.FSDK_Features()
                    if (FSDK.DetectFacialFeaturesInRegion(image, facePosition, feature) == FSDK.FSDKE_OK) {
                        // --- Eye occlusion check ---
//                        val leftEye = feature.features[FSDK.FSDKP_LEFT_EYE]
//                        val rightEye = feature.features[FSDK.FSDKP_RIGHT_EYE]
//                        val nosetip = feature.features[FSDK.FSDKP_NOSE_TIP]
//                        val chin = feature.features[FSDK.FSDKP_CHIN_BOTTOM]
//                         eyesMissing = (leftEye.x == 0 && leftEye.y == 0) || (rightEye.x == 0 && rightEye.y == 0)
//                        Log.i("acceptV3D", "nosetip ${nosetip.x}:${nosetip.y}  chin ${chin.x}:${chin.y}")
//                         eyesTooClose = kotlin.math.abs(leftEye.x - rightEye.x) < 10 // px threshold, tune as needed

                            val resultImg = arrayOf("")
                            Log.i("acceptV3", "DetectFacialFeaturesInRegion")
                            if (FSDK.DetectFacialAttributeUsingFeatures(image, feature, "Angles", resultImg, 256) == FSDK.FSDKE_OK && resultImg[0].isNotEmpty()) {
                                Log.i("acceptV3", "Image-based Angles: ${resultImg[0]}")
                                FSDK.DetectFacialAttributeUsingFeatures(
                                    image,
                                    feature,
                                    "Confidence",
                                    resultLiveness,
                                    1024
                                )
                                FSDK.GetTrackerFacialAttribute(
                                    tracker,
                                    0,
                                    ids[0],
                                    "Confidence",
                                    resultLiveness,
                                    1024
                                );
                                Log.i("acceptV3D", "Liveness ${resultLiveness[0]}")
                                livenessScore2 = resultLiveness[0].split("Confidence=")[0].toFloatOrNull()

                                for (pair in resultImg[0].split(";")) {
                                    val values = pair.split("=")
                                    if (values.size == 2) {
                                        when (values[0].trim().lowercase()) {
                                            "pan" -> pan = values[1].toFloatOrNull()
                                            "tilt" -> tilt = values[1].toFloatOrNull()
                                            "roll" -> roll = values[1].toFloatOrNull()
                                        }
                                    }
                                }
                            }

                    }
                }
//            } else {
//                SessionManager.lightCondition = 0
//                Log.d("acceptV3", "Waiting for stable face tracking: stableFrameCount=$stableFrameCount")
//            }
        } else {
            lastFaceId = 0L
            stableFrameCount = 0
            //Log.d("acceptV3", "No valid face ID for attribute extraction")
        }

        // Analyze brightness using analyzeImageBrightness
        //val bitmap = try { imageProxy.toBitmap() } catch (e: Exception) { null }
        val lowLight = analyzeImageBrightness(imageBit)
        if (lowLight) {
            Log.d("acceptV3", "Low lighting condition")
            SessionManager.lightCondition = 0
            SessionManager.setText("Low lighting condition")
        } else {
            if (pan != null && tilt != null && roll != null) {
                // Thresholds based on provided angle data for user-friendliness (relaxed for more flexibility)
                val panThreshold = 25f
                val tiltThreshold = 12f
                val rollThreshold = 38f

                // --- Face distance check ---
                var faceIsCloseEnough = false
                if (faceCount[0] > 0 && ids[0] != 0L) {
                    val faceBox = FSDK.TFace()
                    if (FSDK.GetTrackerFace(tracker, 0, ids[0], faceBox) == FSDK.FSDKE_OK) {
                        val faceWidth = faceBox.bbox.p1.x - faceBox.bbox.p0.x
                        val faceHeight = faceBox.bbox.p1.y - faceBox.bbox.p0.y
                        // You may need to tune these thresholds for your camera/resolution
                        val minFaceWidth = 180
                        val minFaceHeight = 180
                        faceIsCloseEnough = faceWidth >= minFaceWidth && faceHeight >= minFaceHeight
                        Log.d("acceptV3", "Face size: ${faceWidth}x${faceHeight}, closeEnough=$faceIsCloseEnough")
                    }
                }

//                if (!faceIsCloseEnough) {
//                    Log.d("acceptV3", "Move closer to the camera")
//                    SessionManager.lightCondition = 0
//                    SessionManager.setText("Move closer to the camera")
//                } else
                if (pan > -panThreshold && pan < panThreshold && tilt < tiltThreshold && tilt > -tiltThreshold && roll < rollThreshold && roll > -rollThreshold) {
//                    if (eyesMissing || eyesTooClose) {
//                        val statusMessage = "Please keep both eyes visible for registration."
//                        Log.d("acceptV3", statusMessage)
//                        SessionManager.lightCondition = 0
//                        SessionManager.setText(statusMessage)
//                    }
//                        if (livenessScore2 != null && livenessScore2 < 0.5f) {
//
//                            val statusMessage = "Face is partially occluded or of low quality. Please remove any obstruction and try again."
//                            Log.d("acceptV3", statusMessage)
//                            SessionManager.lightCondition = 0
//                            SessionManager.setText(statusMessage)
//                        }
                        //else{
                            Log.d("acceptV3", "Perfect! Hold on")
                            SessionManager.lightCondition = 1
                            SessionManager.setText("Perfect! Hold on")
                        //}


                }
                else {
                    Log.d("acceptV3", "Keep your face straight!")
                    SessionManager.lightCondition = 0
                    SessionManager.setText("Keep your face straight!")
                }
            } else {
                SessionManager.lightCondition = 0
                SessionManager.setText("Face not detected")
                Log.d("acceptV3", "Face not detected or angles not available")
            }
        }

        FSDK.FreeImage(image)
        return Array(faceCount[0].toInt()) { i -> Face(ids[i]) }
    }

}


