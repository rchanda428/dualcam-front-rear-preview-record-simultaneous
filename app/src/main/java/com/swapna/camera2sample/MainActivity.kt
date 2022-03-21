package com.swapna.camera2sample

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileDescriptor
import java.lang.Exception
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private var cameraDevice: CameraDevice? = null
    private var cameraDeviceFront: CameraDevice? = null

    private lateinit var cameraID: String
    private lateinit var cameraIDFront: String

    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private lateinit var captureRequestBuilderFront: CaptureRequest.Builder

    private var cameraCaptureSession: CameraCaptureSession? = null

    lateinit var captureStateCallback: CameraCaptureSession.StateCallback
    lateinit var captureStateCallbackFront: CameraCaptureSession.StateCallback

    private var imageReader:ImageReader? = null
    lateinit var onImageAvailableListener: ImageReader.OnImageAvailableListener

    private lateinit var previewSize: Size
    private lateinit var previewSizeFront: Size

    private lateinit var previewSurface:Surface
    private lateinit var previewSurfaceFront:Surface

    /*An additional thread for running tasks that shouldn't block the UI.*/
    private var backgroundHandlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var backgroundHandlerThreadFront: HandlerThread? = null
    private var backgroundHandlerFront: Handler? = null

    private val cameraManager: CameraManager by lazy {
        getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val cameraManagerFront: CameraManager by lazy {
        getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    lateinit var mediaRecorder: MediaRecorder
    lateinit var mediaRecorderFront: MediaRecorder
    private lateinit var countDownTimer: CountDownTimer

    //Save to FILE
    private var file: File? = null

    // Number of seconds displayed on the stopwatch.
    private var seconds = 0
    private var isRunning = false
    // whether the stopwatch was running ,before the activity was paused.
    private var wasRunning = false


    private var isRunningFront = false
    // whether the stopwatch was running ,before the activity was paused.
    private var wasRunningFront = false


    var timerHandler: Handler? = null
    var r:Runnable? = null



    val orientations : SparseIntArray = SparseIntArray(4).apply {
        append(Surface.ROTATION_0, 0)
        append(Surface.ROTATION_90, 90)
        append(Surface.ROTATION_180, 180)
        append(Surface.ROTATION_270, 270)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            CameraPermissionHelper.requestCameraPermission(this)
            return
        }
        initializeTextureViewRear()
        initializeTextureViewFront()


        bt_rearvideo.setOnClickListener {
            if (!isRunning) {
                isRunning = true
                takeVideoRear()
                bt_rearvideo.text = "STOP VIDEO"
            } else {
                isRunning = false
                wasRunning = true
                mediaRecorder.stop()
                seconds = 0
                Toast.makeText(this@MainActivity, "Recording saved", Toast.LENGTH_SHORT)
                    .show()
                bt_rearvideo.text = "START VIDEO"
            }
        }

        bt_frontvideo.setOnClickListener {
            if (!isRunningFront) {
                isRunningFront = true
                takeVideoFront()
                bt_frontvideo.text = "STOP VIDEO"
            } else {
                isRunningFront = false
                wasRunningFront = true
                mediaRecorderFront.stop()
                //seconds = 0
                Toast.makeText(this@MainActivity, "Front Recording saved", Toast.LENGTH_SHORT)
                    .show()
                bt_frontvideo.text = "START VIDEO"
            }
        }

    }

    @SuppressLint("SimpleDateFormat")
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getFileDescriptor(context: Context): FileDescriptor {
        var audioUri: Uri? = null
        val resolver: ContentResolver = context.contentResolver
        val videoCollection: Uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val date = SimpleDateFormat("yyyyMMdd_hh:mm:ss").format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, " Camera2Video_${date}.mp4")
            put(MediaStore.Audio.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, getAudioDirectoryPath())
        }
        audioUri = resolver.insert(videoCollection, values)
        val parcelFileDescriptor: ParcelFileDescriptor =
            resolver.openFileDescriptor(audioUri!!, "wt")!!
        return parcelFileDescriptor.fileDescriptor
    }

    private fun getFilePath(context: Context): String {
        val directory: File =
            getAppSpecificAlbumStorageDir(context, Environment.DIRECTORY_MOVIES, "Camera2Video")
        val date = SimpleDateFormat("yyyyMMdd_hh:mm:ss").format(Date())
        val file = File(directory, "${date}.mp4")
        return file.absolutePath
    }

    private fun getAppSpecificAlbumStorageDir(
        context: Context,
        albumName: String,
        subAlbumName: String
    ): File {
        // Get the pictures directory that's inside the app-specific directory on
        // external storage.
        val file = File(
            context.getExternalFilesDir(
                albumName
            ), subAlbumName
        )
        if (!file.mkdirs()) {
            Log.e("fssfsf", "Directory not created")
        }
        return file
    }

    private fun getAudioDirectoryPath(): String {
        return Environment.DIRECTORY_MOVIES + File.separator + "Camera2Video" + File.separator
    }

    private fun takeVideoFront() {
        /*setting up our mediaRecoder,*/
        mediaRecorderFront = MediaRecorder()
        mediaRecorderFront.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mediaRecorderFront.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorderFront.setVideoEncoder(MediaRecorder.VideoEncoder.H264)

        mediaRecorderFront.setVideoSize(1920, 1080)
        mediaRecorderFront.setVideoFrameRate(30)
        //mediaRecorderFront.setOrientationHint(90)


        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            mediaRecorderFront.setOutputFile(getFilePath(this))
        } else {
            mediaRecorderFront.setOutputFile(getFileDescriptor(this))
        }
        mediaRecorderFront.setVideoEncodingBitRate(10_000_000)
        //try{
        mediaRecorderFront.prepare()
        /*
        } catch (e:Exception) {
            e.printStackTrace()
            Log.e("Rear:", "mediaRecorder prepare failed")
        }
         */

        /* Now create a capture request and a capture session.*/
        val recordingSurface = mediaRecorderFront.surface
        captureRequestBuilderFront = cameraDeviceFront!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        captureRequestBuilderFront.addTarget(previewSurfaceFront)
        captureRequestBuilderFront.addTarget(recordingSurface)

        val captureStateVideoCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigureFailed(session: CameraCaptureSession) {
            }
            override fun onConfigured(session: CameraCaptureSession) {
                session.setRepeatingRequest(captureRequestBuilderFront.build(),
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureProgressed(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            partialResult: CaptureResult
                        ) {
                        }

                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {

                        }
                    },
                    backgroundHandlerFront)

                try {
                    mediaRecorderFront.start()
                } catch (e:Exception) {
                    e.printStackTrace()
                    Log.e("Rear:", "mediaRecorder start failed")
                }
                runOnUiThread {
                    //startTimer()
                    Toast.makeText(this@MainActivity, "Front Recording started", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
        cameraDeviceFront?.createCaptureSession(
            listOf(previewSurfaceFront, recordingSurface),
            captureStateVideoCallback,
            backgroundHandlerFront)
    }


    private fun takeVideoRear() {
        /*setting up our mediaRecoder,*/
        mediaRecorder = MediaRecorder()
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)

        mediaRecorder.setVideoSize(1920, 1080)
        mediaRecorder.setVideoFrameRate(30)
        //mediaRecorder.setOrientationHint(90)


        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            mediaRecorder.setOutputFile(getFilePath(this))
        } else {
            mediaRecorder.setOutputFile(getFileDescriptor(this))
        }
        mediaRecorder.setVideoEncodingBitRate(10_000_000)
        //try{
            mediaRecorder.prepare()
        /*
        } catch (e:Exception) {
            e.printStackTrace()
            Log.e("Rear:", "mediaRecorder prepare failed")
        }

         */


        /* Now create a capture request and a capture session.*/
        val recordingSurface = mediaRecorder.surface
        captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        captureRequestBuilder.addTarget(previewSurface)
        captureRequestBuilder.addTarget(recordingSurface)

        val captureStateVideoCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigureFailed(session: CameraCaptureSession) {
            }
            override fun onConfigured(session: CameraCaptureSession) {
                session.setRepeatingRequest(captureRequestBuilder.build(),
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureProgressed(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            partialResult: CaptureResult
                        ) {
                        }

                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {

                        }
                    },
                    backgroundHandler)

                try {
                    mediaRecorder.start()
                } catch (e:Exception) {
                    e.printStackTrace()
                    Log.e("Rear:", "mediaRecorder start failed")
                }
                runOnUiThread {
                    //startTimer()
                    Toast.makeText(this@MainActivity, "Recording started", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
        cameraDevice?.createCaptureSession(listOf(previewSurface, recordingSurface), captureStateVideoCallback, backgroundHandler)
    }

    /*This method is invoked for every call on requestPermissions */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(
                this,
                "Camera permission is needed to run this application",
                Toast.LENGTH_LONG
            ).show()
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this)
            }
            //finish()
        }
        recreate()
    }


    private fun initializeTextureViewFront(){
        textureViewFront.surfaceTextureListener = object : TextureView.SurfaceTextureListener {

            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    openCameraFront()
                } else {
                    return
                }
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            }
        }
        //startBackgroundThread()
    }

    private fun initializeTextureViewRear() {
        textureViewRear.surfaceTextureListener = object : TextureView.SurfaceTextureListener {

            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    openCameraRear()
                } else {
                    return
                }
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            }
        }
        //startBackgroundThread()
    }

    private fun startTimer() {
        // Creates a new Handler
        timerHandler = Handler()

        if(wasRunning)
            timerHandler?.removeCallbacks(r!!)

        // Call the post() method, passing in a new Runnable.
        // The post() method processes code without a delay, so the code in the Runnable will run almost immediately.
        r = object: Runnable {
            override fun run() {
                val hours = seconds / 3600
                val minutes = seconds % 3600 / 60
                val secs = seconds % 60

                Log.d("PShms:", "h=$hours  m=$minutes  s=$secs")

                // For display, Format the seconds into hours, minutes, and seconds.
                val time = java.lang.String
                    .format(
                        Locale.getDefault(),
                        "%d:%02d:%02d", hours,
                        minutes, secs
                    )

                Log.d("PSTime:", time.toString())
                tv_timer.text = time.toString()

                if (isRunning) {
                    seconds++
                }


                // Post the code again with a delay of 1 second.
                timerHandler?.postDelayed(this, 1000)
                //handler.postDelayed(this, 0)
            }
        }
        timerHandler?.post(r!!)
    }

    private fun openCameraFront() {
        /*1.CameraId - String*/
        for (cameraId in cameraManagerFront.cameraIdList) {
            val cameraCharacteristics = cameraManagerFront.getCameraCharacteristics(cameraId)
            //If we want to choose the rear facing camera, instead of the front facing one
            if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK)
                continue
            previewSizeFront = cameraCharacteristics
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                .getOutputSizes(ImageFormat.JPEG).maxByOrNull { it.height * it.width }!!

            cameraIDFront = cameraId
        }

        /*2. CameraDevice.StateCallback*/
        val cameraStateCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                this@MainActivity.cameraDeviceFront = camera
                createCameraPreviewSessionFront()
            }
            override fun onDisconnected(camera: CameraDevice) {
            }

            override fun onError(camera: CameraDevice, error: Int) {
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            cameraManagerFront.openCamera(cameraIDFront, cameraStateCallback, backgroundHandlerFront)
        } else {
            //permissions
            return
        }
    }


    private fun openCameraRear() {
        /*1.CameraId - String*/
        for (cameraId in cameraManager.cameraIdList) {
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
            //If we want to choose the rear facing camera, instead of the front facing one
            if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT)
                continue
            previewSize = cameraCharacteristics
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                .getOutputSizes(ImageFormat.JPEG).maxByOrNull { it.height * it.width }!!

            Log.d("Rear:", "previewSize :" + previewSize.width + " x " + previewSize.height)
            cameraID = cameraId
        }

        /*2. CameraDevice.StateCallback*/
        val cameraStateCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                this@MainActivity.cameraDevice = camera
                createCameraPreviewSessionRear()
            }
            override fun onDisconnected(camera: CameraDevice) {
            }

            override fun onError(camera: CameraDevice, error: Int) {
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            cameraManager.openCamera(cameraID, cameraStateCallback, backgroundHandler)
        } else {
            //permissions
            return
        }
    }

    /*Now camera is open, so present the camera feed to the user via the TextureView.*/
    /*Use the SurfaceTexture property of TextureView and build a CaptureRequest.*/
    private fun createCameraPreviewSessionFront() {
        val surfaceTexture : SurfaceTexture? = textureViewFront.surfaceTexture
        surfaceTexture?.setDefaultBufferSize(previewSizeFront.width, previewSizeFront.height)

        previewSurfaceFront = Surface(surfaceTexture)

        captureRequestBuilderFront = cameraDeviceFront!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilderFront.addTarget(previewSurfaceFront)

        captureStateCallbackFront = object : CameraCaptureSession.StateCallback() {
            override fun onConfigureFailed(session: CameraCaptureSession) {
            }
            override fun onConfigured(session: CameraCaptureSession) {
                //cameraCaptureSession = session

                /*CaptureCallback is null, since this is a preview.*/
                session.setRepeatingRequest(captureRequestBuilderFront!!.build(),
                    null,
                    backgroundHandlerFront
                )
            }
        }
        /* handler:null means-- current thread.*/
        //cameraDevice?.createCaptureSession(listOf(previewSurface, imageReader?.surface), captureStateCallbackFront, null)
        cameraDeviceFront?.createCaptureSession(listOf(previewSurfaceFront), captureStateCallbackFront, null)

    }



    /*Now camera is open, so present the camera feed to the user via the TextureView.*/
    /*Use the SurfaceTexture property of TextureView and build a CaptureRequest.*/
    private fun createCameraPreviewSessionRear() {
        val surfaceTexture : SurfaceTexture? = textureViewRear.surfaceTexture
        surfaceTexture?.setDefaultBufferSize(previewSize.width, previewSize.height)

        previewSurface = Surface(surfaceTexture)

        captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder.addTarget(previewSurface)

        captureStateCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigureFailed(session: CameraCaptureSession) {
            }
            override fun onConfigured(session: CameraCaptureSession) {
                cameraCaptureSession = session

                /*CaptureCallback is null, since this is a preview.*/
                cameraCaptureSession?.setRepeatingRequest(captureRequestBuilder!!.build(),
                    null,
                    backgroundHandler
                )
            }
        }
        /* handler:null means-- current thread.*/
        //cameraDevice?.createCaptureSession(listOf(previewSurface, imageReader?.surface), captureStateCallback, null)
        cameraDevice?.createCaptureSession(listOf(previewSurface), captureStateCallback, null)

    }


    private fun startBackgroundThread() {
        backgroundHandlerThread = HandlerThread("CameraVideoThreadRear")
        backgroundHandlerThread?.start()
        backgroundHandler = Handler(
            backgroundHandlerThread?.looper as Looper)

        backgroundHandlerThreadFront = HandlerThread("CameraVideoThreadFront")
        backgroundHandlerThreadFront?.start()
        backgroundHandlerFront = Handler(
            backgroundHandlerThreadFront?.looper as Looper)
    }

    private fun stopBackgroundThread() {
        backgroundHandlerThread?.quitSafely()
        backgroundHandlerThread?.join()

        backgroundHandlerThreadFront?.quitSafely()
        backgroundHandlerThreadFront?.join()
    }

    override fun onPause() {
        //closeCamera()
        stopBackgroundThread()
        super.onPause()

    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
    }

}


