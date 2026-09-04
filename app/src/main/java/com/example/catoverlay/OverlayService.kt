package com.example.catoverlay

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast

class OverlayService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL_ID = "cat_overlay_channel"
        private const val NOTIF_ID = 42
    }

    private lateinit var windowManager: WindowManager
    private lateinit var captureManager: CaptureManager
    private val handler = Handler(Looper.getMainLooper())

    private var frameView: FrameLayout? = null
    private var frameParams: WindowManager.LayoutParams? = null
    private var controlsView: View? = null
    private var controlsParams: WindowManager.LayoutParams? = null
    private val markerViews = mutableListOf<View>()
    private val debugViews = mutableListOf<View>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        captureManager = CaptureManager(this)
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && frameView == null) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
            try {
                if (data != null) {
                    captureManager.start(resultCode, data)
                }
                addCalibrationFrame()
                addControlPanel()
                Toast.makeText(this, "Overlay added — look for a red box + dark control bar", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Couldn't start overlay: ${e.message}. Check that " +
                        "\"Display over other apps\" is ON for Cat Overlay in Settings > Apps.",
                    Toast.LENGTH_LONG
                ).show()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    // ---------------- Calibration frame (drag to move, handle to resize) ----------------

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun addCalibrationFrame() {
        val frame = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.frame_border)
        }
        val label = android.widget.TextView(this).apply {
            text = "DRAG ME onto the grid\n(pinch corner to resize)"
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#CC000000"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            textSize = 12f
        }
        frame.addView(
            label,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            )
        )
        val handleSize = dp(28)
        val resizeHandle = View(this).apply {
            setBackgroundResource(R.drawable.resize_handle)
        }
        frame.addView(
            resizeHandle,
            FrameLayout.LayoutParams(handleSize, handleSize, Gravity.BOTTOM or Gravity.END)
        )

        val defaultSize = dp(280)
        val params = WindowManager.LayoutParams(
            defaultSize, defaultSize,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(40)
            y = dp(120)
        }

        // Drag whole frame (touch anywhere except the resize handle).
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
        frame.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchX = event.rawX; touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(frame, params)
                    true
                }
                else -> false
            }
        }

        // Drag the corner handle to resize.
        var startW = 0; var startH = 0
        resizeHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startW = params.width; startH = params.height
                    touchX = event.rawX; touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val newW = (startW + (event.rawX - touchX)).toInt().coerceAtLeast(100)
                    val newH = (startH + (event.rawY - touchY)).toInt().coerceAtLeast(100)
                    params.width = newW
                    params.height = newH
                    windowManager.updateViewLayout(frame, params)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(frame, params)
        frameView = frame
        frameParams = params
    }

    // ---------------- Control panel (rows/cols + scan/clear/stop) ----------------

    private fun addControlPanel() {
        val view = LayoutInflaterCompat.inflate(this, R.layout.overlay_controls)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
        view.findViewById<View>(R.id.dragHandle).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchX = event.rawX; touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }

        view.findViewById<Button>(R.id.btnDebug).setOnClickListener { performDebugScan(view) }
        view.findViewById<Button>(R.id.btnScan).setOnClickListener { performScan(view) }
        view.findViewById<Button>(R.id.btnClear).setOnClickListener { clearMarkers(); clearDebugGrid() }
        view.findViewById<Button>(R.id.btnStop).setOnClickListener { stopSelf() }

        windowManager.addView(view, params)
        controlsView = view
        controlsParams = params
    }

    // ---------------- Scan / solve / draw ----------------

    private fun performScan(controls: View) {
        val frame = frameView ?: return
        val fParams = frameParams ?: return
        val rows = controls.findViewById<EditText>(R.id.editRows).text.toString().toIntOrNull() ?: 9
        val cols = controls.findViewById<EditText>(R.id.editCols).text.toString().toIntOrNull() ?: 9
        val tolerance = controls.findViewById<EditText>(R.id.editTolerance).text.toString().toIntOrNull() ?: 30

        clearMarkers()
        clearDebugGrid()
        // Hide the frame border itself so it isn't sampled as part of the board.
        frame.visibility = View.INVISIBLE

        handler.postDelayed({
            val bitmap = captureManager.latest()
            if (bitmap == null) {
                Toast.makeText(this, "No frame captured yet, try again", Toast.LENGTH_SHORT).show()
                frame.visibility = View.VISIBLE
                return@postDelayed
            }

            val loc = IntArray(2)
            frame.getLocationOnScreen(loc)
            val left = loc[0].coerceIn(0, bitmap.width - 1)
            val top = loc[1].coerceIn(0, bitmap.height - 1)
            val width = fParams.width.coerceAtMost(bitmap.width - left)
            val height = fParams.height.coerceAtMost(bitmap.height - top)

            if (width <= 0 || height <= 0) {
                Toast.makeText(this, "Frame is outside the captured screen", Toast.LENGTH_SHORT).show()
                frame.visibility = View.VISIBLE
                return@postDelayed
            }

            val crop = android.graphics.Bitmap.createBitmap(bitmap, left, top, width, height)
            val board = GridColorReader.readColors(crop, rows, cols, tolerance)
            val solution = CatSolver.solve(board.ids)

            frame.visibility = View.VISIBLE

            if (solution == null) {
                Toast.makeText(
                    this,
                    "No valid placement found (${board.palette.size} colors detected) — try Debug to check detection, or adjust tolerance",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                drawMarkers(solution, rows, cols, left, top, width, height)
            }
        }, 150)
    }

    /** Shows each cell's detected color id as a small numbered swatch, without solving.
     *  Use this to sanity-check that cells you can see are the same color really got
     *  the same id, and adjacent cells of different colors got different ids. */
    private fun performDebugScan(controls: View) {
        val frame = frameView ?: return
        val fParams = frameParams ?: return
        val rows = controls.findViewById<EditText>(R.id.editRows).text.toString().toIntOrNull() ?: 9
        val cols = controls.findViewById<EditText>(R.id.editCols).text.toString().toIntOrNull() ?: 9
        val tolerance = controls.findViewById<EditText>(R.id.editTolerance).text.toString().toIntOrNull() ?: 30

        clearMarkers()
        clearDebugGrid()
        frame.visibility = View.INVISIBLE

        handler.postDelayed({
            val bitmap = captureManager.latest()
            if (bitmap == null) {
                Toast.makeText(this, "No frame captured yet, try again", Toast.LENGTH_SHORT).show()
                frame.visibility = View.VISIBLE
                return@postDelayed
            }

            val loc = IntArray(2)
            frame.getLocationOnScreen(loc)
            val left = loc[0].coerceIn(0, bitmap.width - 1)
            val top = loc[1].coerceIn(0, bitmap.height - 1)
            val width = fParams.width.coerceAtMost(bitmap.width - left)
            val height = fParams.height.coerceAtMost(bitmap.height - top)

            if (width <= 0 || height <= 0) {
                Toast.makeText(this, "Frame is outside the captured screen", Toast.LENGTH_SHORT).show()
                frame.visibility = View.VISIBLE
                return@postDelayed
            }

            val crop = android.graphics.Bitmap.createBitmap(bitmap, left, top, width, height)
            val board = GridColorReader.readColors(crop, rows, cols, tolerance)

            frame.visibility = View.VISIBLE
            drawDebugGrid(board, rows, cols, left, top, width, height)
            Toast.makeText(this, "${board.palette.size} distinct colors detected — tap Debug again after adjusting tol", Toast.LENGTH_LONG).show()
        }, 150)
    }

    private fun drawDebugGrid(board: GridColorReader.ColorBoard, rows: Int, cols: Int, left: Int, top: Int, width: Int, height: Int) {
        val cellW = width / cols
        val cellH = height / rows
        val badgeSize = (minOf(cellW, cellH) * 0.7).toInt().coerceAtLeast(20)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val id = board.ids[r][c]
                val label = android.widget.TextView(this).apply {
                    text = id.toString()
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(android.graphics.Color.argb(200, 0, 0, 0))
                    gravity = Gravity.CENTER
                    textSize = 10f
                }
                val params = WindowManager.LayoutParams(
                    badgeSize, badgeSize,
                    overlayType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = left + c * cellW + cellW / 2 - badgeSize / 2
                    y = top + r * cellH + cellH / 2 - badgeSize / 2
                }
                windowManager.addView(label, params)
                debugViews.add(label)
            }
        }
    }

    private fun clearDebugGrid() {
        for (v in debugViews) {
            runCatching { windowManager.removeView(v) }
        }
        debugViews.clear()
    }

    private fun drawMarkers(catCol: IntArray, rows: Int, cols: Int, left: Int, top: Int, width: Int, height: Int) {
        val cellW = width / cols
        val cellH = height / rows
        val dotSize = (minOf(cellW, cellH) * 0.6).toInt().coerceAtLeast(16)

        for (r in catCol.indices) {
            val c = catCol[r]
            val dot = View(this).apply { setBackgroundResource(R.drawable.cat_dot) }
            val params = WindowManager.LayoutParams(
                dotSize, dotSize,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = left + c * cellW + cellW / 2 - dotSize / 2
                y = top + r * cellH + cellH / 2 - dotSize / 2
            }
            windowManager.addView(dot, params)
            markerViews.add(dot)
        }
    }

    private fun clearMarkers() {
        for (v in markerViews) {
            runCatching { windowManager.removeView(v) }
        }
        markerViews.clear()
    }

    // ---------------- Lifecycle / notification ----------------

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Cat Overlay", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Cat Overlay running")
            .setContentText("Tap Stop in the floating panel to end.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearMarkers()
        clearDebugGrid()
        frameView?.let { runCatching { windowManager.removeView(it) } }
        controlsView?.let { runCatching { windowManager.removeView(it) } }
        captureManager.stop()
    }
}

/** Tiny helper so we don't need a LayoutInflater field wired up separately. */
private object LayoutInflaterCompat {
    fun inflate(service: Service, layoutRes: Int): View {
        val inflater = service.getSystemService(Service.LAYOUT_INFLATER_SERVICE) as android.view.LayoutInflater
        return inflater.inflate(layoutRes, null)
    }
}
