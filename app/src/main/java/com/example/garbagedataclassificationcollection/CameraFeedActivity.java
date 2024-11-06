package com.example.garbagedataclassificationcollection;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.ActivityCompat;
import androidx.documentfile.provider.DocumentFile;


import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class CameraFeedActivity extends AppCompatActivity implements DataCommunicationInterface{
    public static final String CAMERA_FEED= "Camera Feed";
    public static final String CAMERA_ACCESS= "Camera Access";
    public static final int REQUEST_CAM_PERMISSION = 3;
    private static final int PREVIEW_STATE=1;
    private static final int WAIT_LOCK_STATE=2;
    private static final String IO_EXCEPTION="OIException";
    private int captureState = PREVIEW_STATE;


    public FloatingActionButton picBtn;
    public int nbrImg = 1;
    public boolean isShutteringPhoto=false;

    private TextureView camFeed;
    private SurfaceTexture surfaceTexture;
    private TextureView.SurfaceTextureListener camFeedSurfaceTexture = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {

            setupCamera(i, i1);
            connectCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

        }
    };

    private CameraDevice cam;
    private CameraDevice.StateCallback camStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            cam = cameraDevice;

            startPreview();

        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
            cam = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            cameraDevice.close();
            cam=null;
        }
    };

    private String camId;
    private Size camPreviewSize;

    private int totalRotation;
    private Size camImageSize;

    private String imageName;
    private ImageReader imgReader;
    private DocumentFile outputImageFile;

    private final ImageReader.OnImageAvailableListener imgAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(@NonNull ImageReader imageReader) {
            // return the result to the activity (Byte[] or buffer
            //Toast.makeText(CameraFeedActivity.this, "Inside Listener", Toast.LENGTH_SHORT).show();
            Image latestImg=imageReader.acquireLatestImage();
            if(latestImg!=null){
                bgHandler.post(new ImageSaver(latestImg));
            }

        }
    };

    private class ImageSaver implements Runnable{

        private Image img;
        public ImageSaver(Image image){
            img=image;
        }
        @Override
        public void run() {
            ByteBuffer buffer = img.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            // Todo: createImage & fill it with data
            OutputStream out=null;
            try{
                out = getContentResolver().openOutputStream(outputImageFile.getUri());
                if(out != null){
                    out.write(bytes);
                }
                Toast.makeText(CameraFeedActivity.this, "Wrote File successfully", Toast.LENGTH_SHORT).show();
            }catch (IOException e){
                Log.d(IO_EXCEPTION, "Error in saving image p1", e);
            }finally {
                // after clearing
                if(out!=null){
                    try{
                        out.close();

                    }catch (IOException e){
                        Log.d(IO_EXCEPTION, "error in saving message p2", e);
                    }
                }
                buffer.clear();
                img.close();

            }
        }
    }

    private Handler bgHandler;
    private HandlerThread bgThread;
    private Handler uiHandler;

    private static SparseIntArray ORIENTATIONS = new SparseIntArray();
    static{
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    private static class CompareSizeByArea implements Comparator<Size> {

        @Override
        public int compare(Size size, Size t1) {
            return Long.signum((long)size.getHeight()*size.getWidth() / (long)t1.getHeight()*t1.getWidth()); // shouldn't I add () around t1 width*height?
        }
    }

    private CaptureRequest.Builder capPreviewBuilder;
    private CameraCaptureSession previewCapSess;
    private CameraCaptureSession.CaptureCallback previewCapSessCallback= new CameraCaptureSession.CaptureCallback() {
        private void process(CaptureResult res){
            switch (captureState){
                case PREVIEW_STATE:
                    break;
                case WAIT_LOCK_STATE:
                    captureState= PREVIEW_STATE;// to only capture 1 image at a time, change this to support shutter image
                    Integer af_state = res.get(CaptureResult.CONTROL_AF_STATE);
                    if(af_state!=null){
                        if(af_state== CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || af_state== CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED || af_state==CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED){
                            Toast.makeText(CameraFeedActivity.this, "Focused!", Toast.LENGTH_SHORT).show();
                            startTakingPicture();
                        }else{
                            Toast.makeText(CameraFeedActivity.this, "Wait until focused", Toast.LENGTH_SHORT).show();
                        }
                    }
                    break;
            }
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            process(result);
        }
    };
    private CaptureRequest.Builder capImageBuilder;

    // attributes relative to prev activity
    private DocumentFile dataSetDirectory;
    private DocumentFile dataset;
    private DocumentFile garbageClassFolder;
    private String garbageClassName;
    private int garbageClassNumber;
    private TextView counterTxt;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_camera_feed);
        camFeed = findViewById(R.id.cam_feed);
        counterTxt= findViewById(R.id.img_counter);
        picBtn = findViewById(R.id.pic_btn);
        picBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                lockFocus();
            }
        });
        findViewById(R.id.back_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        // getting info from prev activity
        Bundle b = getIntent().getExtras();
        Uri dirUri;
        Uri csvUri;

        if(Build.VERSION.SDK_INT>=33){
            dirUri = b.getParcelable(GARBAGE_CLASS_FOLDER, Uri.class);
            csvUri = b.getParcelable(CSV_FILE, Uri.class);
        }else{
            dirUri= (Uri) b.getParcelable(GARBAGE_CLASS_FOLDER);
            csvUri= (Uri) b.getParcelable(CSV_FILE);
        }
        if(dirUri!=null && csvUri!=null){
            garbageClassFolder = DocumentFile.fromTreeUri(this, dirUri);
            dataset = DocumentFile.fromSingleUri(this, csvUri);
            garbageClassName = b.getString(GARBAGE_CLASS_NAME);
            garbageClassNumber = b.getInt(GARBAGE_CLASS_NUMBER);
        }
        counterTxt.setText(garbageClassNumber+"");
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        View decorView = getWindow().getDecorView();
        if(hasFocus){
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                |View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                |View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                |View.SYSTEM_UI_FLAG_FULLSCREEN
                |View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                |View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        doingOnResume();
    }
    private void doingOnResume(){
        startBgThread();
        if(camFeed.isAvailable()){
            setupCamera(camFeed.getWidth(), camFeed.getHeight());
            connectCamera();
        }else{
            camFeed.setSurfaceTextureListener(camFeedSurfaceTexture);
        }
    }

    // in case we leave our app, we better free cam resource
    @Override
    protected void onPause(){
        doingOnPause();
        super.onPause();
    }

    private void doingOnPause(){
        closeCamera();
        stopBgThread();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_CAM_PERMISSION){
            if(grantResults[0]!=PackageManager.PERMISSION_GRANTED){
                Toast.makeText(getApplicationContext(), "Can't collect data without camera permission", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    // setting up camera (getting its id)
    private void setupCamera(int width, int height){
        CameraManager camManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try{
            for(String camId: camManager.getCameraIdList()){
                CameraCharacteristics camChar = camManager.getCameraCharacteristics(camId);
                int lensFacingDir = camChar.get(CameraCharacteristics.LENS_FACING);
                if(lensFacingDir == CameraCharacteristics.LENS_FACING_BACK){
                    // adjusting orientation for calculating preview size
                    StreamConfigurationMap map = camChar.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    int deviceOrien = getWindowManager().getDefaultDisplay().getRotation();
                    totalRotation = sensorToDeviceOrien(camChar, deviceOrien);
                    boolean isPortrait = totalRotation==90 || totalRotation==270;
                    int rotatedWidth=width;
                    int rotatedHeight = height;
                    if(!isPortrait){
                        rotatedHeight=width;
                        rotatedWidth=height;
                    }
                    camPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),rotatedWidth, rotatedHeight);
                    camImageSize = chooseOptimalSize(map.getOutputSizes(ImageFormat.JPEG),rotatedWidth, rotatedHeight);
                    imgReader = ImageReader.newInstance(camImageSize.getWidth(), camImageSize.getHeight(), ImageFormat.JPEG, 1);
                    imgReader.setOnImageAvailableListener(imgAvailableListener,bgHandler);
                    this.camId = camId;
                    return;
                }
            }
        }catch (CameraAccessException e){
            Log.d(CAMERA_ACCESS, "Error Accessing CameraId", e);
        }
    }

    private void connectCamera(){
        CameraManager camManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if(shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)){
                Toast.makeText(this,"Camera permission required for the app", Toast.LENGTH_LONG).show();
            }
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAM_PERMISSION);
        }else{
            try{
                camManager.openCamera(camId,camStateCallback,bgHandler);
            }catch (CameraAccessException e){
                Log.d(CAMERA_ACCESS, "Error cam permission", e);
            }
        }
    }

    private void startPreview(){
        surfaceTexture = camFeed.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(camPreviewSize.getWidth(), camPreviewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture); // should be freed after use
        try{
           capPreviewBuilder = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
           capPreviewBuilder.addTarget(previewSurface);

           cam.createCaptureSession(Arrays.asList(previewSurface, imgReader.getSurface()), new CameraCaptureSession.StateCallback() {
               @Override
               public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                   previewCapSess = cameraCaptureSession;
                   try {
                       capPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                       previewCapSess.setRepeatingRequest(capPreviewBuilder.build(), null, bgHandler);
                   } catch (CameraAccessException e) {
                       Log.d(CAMERA_ACCESS, "Error in setting up cam preview"+e);
                       throw new RuntimeException(e);
                   }
               }

               @Override
               public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                   Toast.makeText(CameraFeedActivity.this, "Error in previewing camera", Toast.LENGTH_SHORT).show();
               }
           }, null);
        }catch(CameraAccessException e){
            Log.d(CAMERA_ACCESS, "error in creating capture req: "+e);
        }
    }

    private void startTakingPicture(){
        try {
            capImageBuilder = cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            capImageBuilder.set(CaptureRequest.JPEG_ORIENTATION, totalRotation);
            capImageBuilder.addTarget(imgReader.getSurface());
            CameraCaptureSession.CaptureCallback imgCapCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    // The counter ui is updated in the main ui thread
                    garbageClassNumber+=1;
                    outputImageFile = createImageFile();
                    writeDataToDataSet(garbageClassName+"/"+imageName);
                    uiHandler.post(()->{
                        counterTxt.setText(garbageClassNumber+"");
                    });
                }

            };
            Toast.makeText(this, "StartTakingPicture toast", Toast.LENGTH_SHORT).show();
            previewCapSess.capture(capImageBuilder.build(), imgCapCallback, null); // it's called in a method within bgHandler, so it's null here
        }catch (CameraAccessException e){
            Log.d(CAMERA_ACCESS, "Error in start taking picture "+e);
        }
    }
    private void closeCamera(){
        if(cam!=null){
            cam.close();

            cam=null;
        }
    }
    private void startBgThread(){
        if(bgThread==null || !bgThread.isAlive()){
            bgThread = new HandlerThread("CameraFeed");
            bgThread.start();
            bgHandler = new Handler(bgThread.getLooper());
            uiHandler = new Handler(Looper.getMainLooper());
        }
    }

    private void stopBgThread(){
        if(bgThread!=null){
            bgHandler.removeCallbacksAndMessages(null);
            uiHandler.removeCallbacksAndMessages(null);
            bgThread.quitSafely();
            try{
                bgThread.join();
                bgThread = null;
                bgHandler = null;

            }catch (InterruptedException e){
                Log.d(CAMERA_FEED,"Error stopping Bg Thread", e);
            }
        }
    }

    private static int sensorToDeviceOrien(@NonNull CameraCharacteristics camChar, int deviceOrien){
        int sensorOrien = camChar.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrien = ORIENTATIONS.get(deviceOrien);
        return (sensorOrien+deviceOrien+360)%360;
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        List<Size> bigEnough = new ArrayList<>();
        double targetRatio = (double) width / height;
        Size closestSize = null;
        double minDiff = Double.MAX_VALUE;

        for (Size option : choices) {
            double optionRatio = (double) option.getWidth() / option.getHeight();
            // check if aspect ratio is close enough
            if (Math.abs(optionRatio - targetRatio) < 0.1 && option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }

            // just in case no aspect ratio is found
            double areaDiff = Math.abs((option.getWidth() * option.getHeight()) - (width * height));
            if (areaDiff < minDiff) {
                closestSize = option;
                minDiff = areaDiff;
            }
        }

        if (!bigEnough.isEmpty()) {
            return Collections.min(bigEnough, new CompareSizeByArea());
        } else if (closestSize != null) {
            return closestSize;
        } else {
            return choices[0];
        }
    }

    private void lockFocus(){
        captureState = WAIT_LOCK_STATE;

        try {
            previewCapSess.capture(capPreviewBuilder.build(),previewCapSessCallback,bgHandler);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private DocumentFile createImageFile(){

        String timestamp = new SimpleDateFormat("yyyy_MM_dd hh:mm:ss").format(new Date());
        imageName = garbageClassName+"_"+garbageClassNumber+"_"+timestamp;
        return garbageClassFolder.createFile("image/jpeg", imageName);
    }

    private void writeDataToDataSet(String imageLocalDirectory){
        if(dataset!=null && dataset.canWrite()){
            try{
                OutputStream out = getContentResolver().openOutputStream(dataset.getUri(), "wa");
                String initData = "\n"+garbageClassName+","+imageLocalDirectory;
                out.write(initData.getBytes());
                out.close();

            }catch(IOException e){
                Toast.makeText(this, ""+e, Toast.LENGTH_SHORT).show();
            }
        }
    }
}