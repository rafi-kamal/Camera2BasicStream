package com.example.android.camera2basic;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public final class FrameExtractor extends FragmentManager.FragmentLifecycleCallbacks {
  /**
   * An {@link AutoFitTextureView} for camera preview.
   */
  private final AutoFitTextureView autoFitTextureView;
  @Nullable private FrameProcessor frameProcessor;
  private FrameExtractorSurfaceTextureListener mSurfaceTextureListener;

  public FrameExtractor(AutoFitTextureView autoFitTextureView) {
    this.autoFitTextureView = autoFitTextureView;
  }

  public void startProcessingFrames(FrameProcessor frameProcessor) {
    this.frameProcessor = frameProcessor;
  }

  /**
   * Conversion from screen rotation to JPEG orientation.
   */
  private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

  static {
    ORIENTATIONS.append(Surface.ROTATION_0, 90);
    ORIENTATIONS.append(Surface.ROTATION_90, 0);
    ORIENTATIONS.append(Surface.ROTATION_180, 270);
    ORIENTATIONS.append(Surface.ROTATION_270, 180);
  }

  private static final String FRAGMENT_DIALOG = "dialog";
  /**
   * Tag for the {@link Log}.
   */
  private static final String TAG = "Camera2BasicFragment";

  /**
   * Camera state: Showing camera preview.
   */
  private static final int STATE_PREVIEW = 0;

  /**
   * Camera state: Waiting for the focus to be locked.
   */
  private static final int STATE_WAITING_LOCK = 1;

  /**
   * Camera state: Waiting for the exposure to be precapture state.
   */
  private static final int STATE_WAITING_PRECAPTURE = 2;

  /**
   * Camera state: Waiting for the exposure state to be something other than precapture.
   */
  private static final int STATE_WAITING_NON_PRECAPTURE = 3;

  /**
   * Camera state: Picture was taken.
   */
  private static final int STATE_PICTURE_TAKEN = 4;

  /**
   * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
   * {@link TextureView}.
   */
  private final class FrameExtractorSurfaceTextureListener implements TextureView.SurfaceTextureListener {
    private final Fragment fragment;

    public FrameExtractorSurfaceTextureListener(Fragment fragment) {
      this.fragment = fragment;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
      openCamera(fragment, width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
      configureTransform(fragment, width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
      return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {}

  };

  /**
   * ID of the current {@link CameraDevice}.
   */
  private String mCameraId;

  /**
   * A {@link CameraCaptureSession } for camera preview.
   */
  private CameraCaptureSession mCaptureSession;

  /**
   * A reference to the opened {@link CameraDevice}.
   */
  private CameraDevice mCameraDevice;

  /**
   * The {@link android.util.Size} of camera preview.
   */
  private Size mPreviewSize;

  /**
   * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
   */
  private final class FrameExtractorStateCallback extends CameraDevice.StateCallback {
    private final Fragment fragment;

    private FrameExtractorStateCallback(Fragment fragment) {
      this.fragment = fragment;
    }

    @Override
    public void onOpened(@NonNull CameraDevice cameraDevice) {
      // This method is called when the camera is opened.  We start camera preview here.
      mCameraOpenCloseLock.release();
      mCameraDevice = cameraDevice;
      createCameraPreviewSession(fragment);
    }

    @Override
    public void onDisconnected(@NonNull CameraDevice cameraDevice) {}

    @Override
    public void onError(@NonNull CameraDevice cameraDevice, int error) {}

  };

  /**
   * An additional thread for running tasks that shouldn't block the UI.
   */
  private HandlerThread mBackgroundThread;

  /**
   * A {@link Handler} for running tasks in the background.
   */
  private Handler mBackgroundHandler;

  /**
   * An {@link ImageReader} that handles still image capture.
   */
  private ImageReader mImageReader;

  /**
   * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
   * still image is ready to be saved.
   */
  private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
      = new ImageReader.OnImageAvailableListener() {

    @Override
    public void onImageAvailable(ImageReader reader) {

      Image image = null;

      try {
        if (reader != null) {
          image = reader.acquireLatestImage();
        }

      } catch (IllegalStateException e) {
        System.out.println("whoops");
      }
      if (image != null) {

        if (mBitmap == null)        //create Bitmap image first time
        {
          width_ima = smallest.getWidth();
          height_ima = smallest.getHeight();
          mBitmap = Bitmap.createBitmap(width_ima, height_ima, Bitmap.Config.RGB_565);
        }

        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        resultRGB = getActiveArray(buffer);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        mBitmap = BitmapFactory.decodeByteArray(resultRGB, 0, resultRGB.length, options);

        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        final Bitmap rotated = Bitmap.createBitmap(mBitmap, 0, 0,
            mBitmap.getWidth(), mBitmap.getHeight(),
            matrix, true);
        mBackgroundHandler.post(new Runnable() {
          @Override
          public void run() {
            if (frameProcessor != null) {
              frameProcessor.process(rotated);
            }
          }
        });
        image.close();
      }

    }

  };

  private byte[] resultRGB = new byte[0];

  public byte[] getActiveArray(ByteBuffer buffer) {
    byte[] ret = new byte[buffer.remaining()];
    if (buffer.hasArray()) {
      byte[] array = buffer.array();
      System.arraycopy(array, buffer.arrayOffset() + buffer.position(), ret, 0, ret.length);
    } else {
      buffer.slice().get(ret);
    }
    return ret;
  }

  /**
   * {@link CaptureRequest.Builder} for the camera preview
   */
  private CaptureRequest.Builder mPreviewRequestBuilder;

  /**
   * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
   */
  private CaptureRequest mPreviewRequest;

  /**
   * The current state of camera state for taking pictures.
   */
  private int mState = STATE_PREVIEW;

  /**
   * A {@link Semaphore} to prevent the app from exiting before closing the camera.
   */
  private Semaphore mCameraOpenCloseLock = new Semaphore(1);

  private Bitmap mBitmap;
  private int width_ima, height_ima;

  /**
   * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
   */
  private CameraCaptureSession.CaptureCallback mCaptureCallback
      = new CameraCaptureSession.CaptureCallback() {

    private void process(CaptureResult result) {
      switch (mState) {
        case STATE_PREVIEW: {

          break;
        }
        case STATE_WAITING_LOCK: {
          Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
          if (afState == null) {
            // captureStillPicture();
          } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
              CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
            // CONTROL_AE_STATE can be null on some devices
            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
            if (aeState == null ||
                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
              mState = STATE_PICTURE_TAKEN;
              // captureStillPicture();
            } else {
              runPrecaptureSequence();
            }
          }
          break;
        }
        case STATE_WAITING_PRECAPTURE: {
          // CONTROL_AE_STATE can be null on some devices
          Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
          if (aeState == null ||
              aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
              aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
            mState = STATE_WAITING_NON_PRECAPTURE;
          }
          break;
        }
        case STATE_WAITING_NON_PRECAPTURE: {
          // CONTROL_AE_STATE can be null on some devices
          Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
          if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
            mState = STATE_PICTURE_TAKEN;
            //  captureStillPicture();
          }
          break;
        }
      }
    }

    @Override
    public void onCaptureProgressed(@NonNull CameraCaptureSession session,
        @NonNull CaptureRequest request,
        @NonNull CaptureResult partialResult) {
      process(partialResult);
    }

    @Override
    public void onCaptureCompleted(@NonNull CameraCaptureSession session,
        @NonNull CaptureRequest request,
        @NonNull TotalCaptureResult result) {
      process(result);
    }

  };

  /**
   * Shows a {@link Toast} on the UI thread.
   *
   * @param text The message to show
   */
  private void showToast(Fragment fragment, final String text) {
    final Activity activity = fragment.getActivity();
    if (activity != null) {
      activity.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
        }
      });
    }
  }

  /**
   * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
   * width and height are at least as large as the respective requested values, and whose aspect
   * ratio matches with the specified value.
   *
   * @param choices The list of sizes that the camera supports for the intended output class
   * @param width The minimum desired width
   * @param height The minimum desired height
   * @param aspectRatio The aspect ratio
   * @return The optimal {@code Size}, or an arbitrary one if none were big enough
   */
  private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
    // Collect the supported resolutions that are at least as big as the preview Surface
    List<Size> bigEnough = new ArrayList<>();
    int w = aspectRatio.getWidth();
    int h = aspectRatio.getHeight();
    for (Size option : choices) {
      if (option.getHeight() == option.getWidth() * h / w &&
          option.getWidth() >= width && option.getHeight() >= height) {
        bigEnough.add(option);
      }
    }

    // Pick the smallest of those, assuming we found any
    if (bigEnough.size() > 0) {
      return Collections.min(bigEnough, new CompareSizesByArea());
    } else {
      Log.e(TAG, "Couldn't find any suitable preview size");
      return choices[0];
    }
  }

  @Override
  public void onFragmentResumed(FragmentManager fm, Fragment f) {
    super.onFragmentResumed(fm, f);
    startBackgroundThread();

    // When the screen is turned off and turned back on, the SurfaceTexture is already
    // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
    // a camera and start preview from here (otherwise, we wait until the surface is ready in
    // the SurfaceTextureListener).
    if (autoFitTextureView.isAvailable()) {
      openCamera(f, autoFitTextureView.getWidth(),
          autoFitTextureView.getHeight());
    } else {
      mSurfaceTextureListener = new FrameExtractorSurfaceTextureListener(f);
      autoFitTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
    }
  }

  @Override
  public void onFragmentPaused(FragmentManager fm, Fragment f) {
    closeCamera();
    stopBackgroundThread();
    super.onFragmentPaused(fm, f);
  }

  Size smallest;

  /**
   * Sets up member variables related to camera.
   *
   * @param width The width of available size for camera preview
   * @param height The height of available size for camera preview
   */
  private void setUpCameraOutputs(Fragment fragment, int width, int height) {
    CameraManager manager = (CameraManager) fragment.getActivity().getSystemService(Context.CAMERA_SERVICE);
    try {
      for (String cameraId : manager.getCameraIdList()) {
        CameraCharacteristics characteristics
            = manager.getCameraCharacteristics(cameraId);

        // We don't use a front facing camera in this sample.
        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
          continue;
        }

        StreamConfigurationMap map = characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
          continue;
        }

        // For still image captures, we use the largest available size.
        Size largest = Collections.max(
            Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
            new CompareSizesByArea());

        // For still image captures, we use the largest available size.
        smallest = Collections.min(
            Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
            new CompareSizesByArea());

        mImageReader = ImageReader.newInstance(smallest.getWidth(), smallest.getHeight(),
            ImageFormat.JPEG, /*maxImages*/2);
        mImageReader.setOnImageAvailableListener(
            mOnImageAvailableListener, mBackgroundHandler);

        // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
        // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
        // garbage capture data.
        mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
            width, height, smallest);

        // We fit the aspect ratio of TextureView to the size of preview we picked.
        int orientation = fragment.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
          autoFitTextureView.setAspectRatio(
              mPreviewSize.getWidth(), mPreviewSize.getHeight());
        } else {
          autoFitTextureView.setAspectRatio(
              mPreviewSize.getHeight(), mPreviewSize.getWidth());
        }

        mCameraId = cameraId;
        return;
      }
    } catch (CameraAccessException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      // Currently an NPE is thrown when the Camera2API is used but not supported on the
      // device this code runs.
      ErrorDialog.newInstance(fragment.getString(R.string.camera_error))
          .show(fragment.getChildFragmentManager(), FRAGMENT_DIALOG);
    }
  }

  /**
   * Opens the camera specified by {@link FrameExtractor#mCameraId}.
   */
  private void openCamera(Fragment fragment, int width, int height) throws SecurityException {

    setUpCameraOutputs(fragment, width, height);
    configureTransform(fragment, width, height);
    CameraManager manager = (CameraManager) fragment.getActivity().getSystemService(Context.CAMERA_SERVICE);
    try {
      if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
        throw new RuntimeException("Time out waiting to lock camera opening.");
      }
      manager.openCamera(mCameraId, new FrameExtractorStateCallback(fragment), mBackgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
    }
  }

  /**
   * Closes the current {@link CameraDevice}.
   */
  private void closeCamera() {
    try {
      mCameraOpenCloseLock.acquire();
      if (null != mCaptureSession) {
        mCaptureSession.close();
        mCaptureSession = null;
      }
      if (null != mCameraDevice) {
        mCameraDevice.close();
        mCameraDevice = null;
      }
      if (null != mImageReader) {
        mImageReader.close();
        mImageReader = null;
      }
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
    } finally {
      mCameraOpenCloseLock.release();
    }
  }

  /**
   * Starts a background thread and its {@link Handler}.
   */
  private void startBackgroundThread() {
    mBackgroundThread = new HandlerThread("CameraBackground");
    mBackgroundThread.start();
    mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
  }

  /**
   * Stops the background thread and its {@link Handler}.
   */
  private void stopBackgroundThread() {
    mBackgroundThread.quitSafely();
    try {
      mBackgroundThread.join();
      mBackgroundThread = null;
      mBackgroundHandler = null;
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  /**
   * Creates a new {@link CameraCaptureSession} for camera preview.
   */
  private void createCameraPreviewSession(final Fragment fragment) {
    try {
      SurfaceTexture texture = autoFitTextureView.getSurfaceTexture();
      assert texture != null;

      // We configure the size of default buffer to be the size of camera preview we want.
      texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

      // This is the output Surface we need to start preview.
      Surface surface = new Surface(texture);
      Surface mImageSurface = mImageReader.getSurface();

      // We set up a CaptureRequest.Builder with the output Surface.
      mPreviewRequestBuilder
          = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

      mPreviewRequestBuilder.addTarget(surface);
      mPreviewRequestBuilder.addTarget(mImageSurface);

      // Here, we create a CameraCaptureSession for camera preview.
      mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
          new CameraCaptureSession.StateCallback() {

            @Override
            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
              // The camera is already closed
              if (null == mCameraDevice) {
                return;
              }
              // When the session is ready, we start displaying the preview.
              mCaptureSession = cameraCaptureSession;
              try {
                // Auto focus should be continuous for camera preview.
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                mPreviewRequest = mPreviewRequestBuilder.build();
                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                    mCaptureCallback, mBackgroundHandler);


              } catch (CameraAccessException e) {
                e.printStackTrace();
              }
            }

            @Override
            public void onConfigureFailed(
                @NonNull CameraCaptureSession cameraCaptureSession) {
              showToast(fragment, "Failed");
            }
          }, null
      );
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  /**
   * Configures the necessary {@link android.graphics.Matrix} transformation to `autoFitTextureView`.
   * This method should be called after the camera preview size is determined in
   * setUpCameraOutputs and also the size of `autoFitTextureView` is fixed.
   *
   * @param viewWidth The width of `autoFitTextureView`
   * @param viewHeight The height of `autoFitTextureView`
   */
  private void configureTransform(Fragment fragment, int viewWidth, int viewHeight) {
    if (null == autoFitTextureView || null == mPreviewSize || null == fragment.getActivity()) {
      return;
    }
    int rotation = fragment.getActivity().getWindowManager().getDefaultDisplay().getRotation();
    Matrix matrix = new Matrix();
    RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
    RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
    float centerX = viewRect.centerX();
    float centerY = viewRect.centerY();
    if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
      bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
      matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
      float scale = Math.max(
          (float) viewHeight / mPreviewSize.getHeight(),
          (float) viewWidth / mPreviewSize.getWidth());
      matrix.postScale(scale, scale, centerX, centerY);
      matrix.postRotate(90 * (rotation - 2), centerX, centerY);
    } else if (Surface.ROTATION_180 == rotation) {
      matrix.postRotate(180, centerX, centerY);
    }
    autoFitTextureView.setTransform(matrix);
  }

  /**
   * Initiate a still image capture.
   */
  private void takePicture() {
    lockFocus();
  }

  /**
   * Lock the focus as the first step for a still image capture.
   */
  private void lockFocus() {
    try {
      // This is how to tell the camera to lock focus.
      mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
          CameraMetadata.CONTROL_AF_TRIGGER_START);
      // Tell #mCaptureCallback to wait for the lock.
      mState = STATE_WAITING_LOCK;
      mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
          mBackgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  /**
   * Run the precapture sequence for capturing a still image. This method should be called when
   * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
   */
  private void runPrecaptureSequence() {
    try {
      // This is how to tell the camera to trigger.
      mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
          CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
      // Tell #mCaptureCallback to wait for the precapture sequence to be set.
      mState = STATE_WAITING_PRECAPTURE;
      mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
          mBackgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  /**
   * Compares two {@code Size}s based on their areas.
   */
  static class CompareSizesByArea implements Comparator<Size> {

    @Override
    public int compare(Size lhs, Size rhs) {
      // We cast here to ensure the multiplications won't overflow
      return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
          (long) rhs.getWidth() * rhs.getHeight());
    }

  }

  /**
   * Shows an error message dialog.
   */
  public static class ErrorDialog extends DialogFragment {

    private static final String ARG_MESSAGE = "message";

    public static ErrorDialog newInstance(String message) {
      ErrorDialog dialog = new ErrorDialog();
      Bundle args = new Bundle();
      args.putString(ARG_MESSAGE, message);
      dialog.setArguments(args);
      return dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      final Activity activity = getActivity();
      return new AlertDialog.Builder(activity)
          .setMessage(getArguments().getString(ARG_MESSAGE))
          .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
              activity.finish();
            }
          })
          .create();
    }

  }
}
