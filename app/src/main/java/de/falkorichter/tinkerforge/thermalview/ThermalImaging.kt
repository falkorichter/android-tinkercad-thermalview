package de.falkorichter.tinkerforge.thermalview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.Toast
import com.tinkerforge.BrickletThermalImaging
import com.tinkerforge.IPConnection


class Thermalview @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,

) : View(context, attrs, defStyleAttr) {

    private var bitmap: Bitmap? = null
    val thermalImaging = ThermalImaging(context)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        thermalImaging.liveVideo { bitmap ->
            this.bitmap = bitmap
            postInvalidate()
        }
    }


    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if (bitmap != null) {
            canvas?.drawBitmap(bitmap!!, null, Rect(0,0,80,60), null)
        }
    }


}

class ThermalImaging(val context: Context) {

    private lateinit var thread: Thread

    // Creates standard thermal image color palette (blue=cold, red=hot)
    private val paletteR = IntArray(256)
    private val paletteG = IntArray(256)
    private val paletteB = IntArray(256)

    fun createThermalImageColorPalette() {
        // The palette is gnuplot's PM3D palette.
        // See here for details: https://stackoverflow.com/questions/28495390/thermal-imaging-palette
        for (x in 0..255) {
            paletteR[x] = (255 * Math.sqrt(x / 255.0)).toInt()
            paletteG[x] = (255 * Math.pow(x / 255.0, 3.0)).toInt()
            if (Math.sin(2 * Math.PI * (x / 255.0)) >= 0.0) {
                paletteB[x] = (255 * Math.sin(2 * Math.PI * (x / 255.0))).toInt()
            } else {
                paletteB[x] = 0
            }
        }
    }

    fun liveVideo(callback : (Bitmap) -> Unit) {
        thread = Thread {
            try {
                createThermalImageColorPalette()
                val ipcon = IPConnection() // Create IP connection
                val ti = BrickletThermalImaging(UID, ipcon) // Create device object
                ipcon.connect(HOST, Companion.PORT) // Connect to brickd
                // Don't use device before ipcon is connected

                // Enable high contrast image transfer for callback
                ti.imageTransferConfig = BrickletThermalImaging.IMAGE_TRANSFER_CALLBACK_HIGH_CONTRAST_IMAGE

                // Add and implement high contrast image listener
                ti.addHighContrastImageListener { image -> // Use palette mapping to create thermal image coloring
                    //int color = (A & 0xff) << 24 | (B & 0xff) << 16 | (G & 0xff) << 8 | (R & 0xff)
                    val bitmap = Bitmap.createBitmap(Companion.WIDTH, Companion.HEIGHT, Bitmap.Config.ARGB_8888)
                    for (i in 0 until Companion.WIDTH * Companion.HEIGHT) {
                        image[i] =
                            255 shl 24 or (paletteR[image[i]] shl 16) or (paletteG[image[i]] shl 8) or (paletteB[image[i]] shl 0)
                    }
                    bitmap.setPixels(image, 0, WIDTH, 0, 0, WIDTH, Companion.HEIGHT)
                    callback(bitmap)
                }
            } catch (e: Exception) {
                println(e.message)
                Log.e("termal", "something terrible happened ${e.message}")
            }
        }
        thread.start()
    }

    companion object {
        private const val HOST = "192.168.178.113"
        private const val PORT = 4223

        // Change XYZ to the UID of your Thermal Imaging Bricklet
        private const val UID = "HkL"
        private const val WIDTH = 80
        private const val HEIGHT = 60
        private const val SCALE = 5
    }
}