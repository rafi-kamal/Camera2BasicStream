/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.camera2basic;

import android.Manifest;
import android.Manifest.permission;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import com.example.android.camera2basic.FrameExtractor.ErrorDialog;

public final class Camera2BasicFragment extends Fragment {
  private static final int REQUEST_CAMERA_PERMISSION = 1;
  private static final String FRAGMENT_DIALOG = "dialog";

  private FrameExtractor frameExtractor;

  public static Camera2BasicFragment newInstance() {
    return new Camera2BasicFragment();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_camera2_basic, container, false);
    AutoFitTextureView autoFitTextureView = view.findViewById(R.id.texture);
    frameExtractor = new FrameExtractor(autoFitTextureView);
    getFragmentManager().registerFragmentLifecycleCallbacks(frameExtractor, false);
    startExtractingFramesOrRequestPermission();
    return view;
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
      @NonNull int[] grantResults) {
    if (requestCode == REQUEST_CAMERA_PERMISSION) {
      if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
        ErrorDialog.newInstance(getString(R.string.request_permission))
            .show(getChildFragmentManager(), FRAGMENT_DIALOG);
      }
    } else {
      startExtractingFrames();
    }
  }

  private void startExtractingFramesOrRequestPermission() {
    if (ActivityCompat.checkSelfPermission(getActivity(), permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED) {
      if (ActivityCompat
          .shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.CAMERA)) {
        Toast.makeText(getActivity(), "You need to give camera permission to use this app",
            Toast.LENGTH_LONG).show();
        getActivity().finish();
      } else {
        ActivityCompat
            .requestPermissions(getActivity(), new String[]{Manifest.permission.CAMERA},
                REQUEST_CAMERA_PERMISSION);
      }
    } else {
      startExtractingFrames();
    }
  }

  private void startExtractingFrames() {
    frameExtractor.startProcessingFrames(new FrameProcessor() {
      @Override
      public void process(Bitmap frame) {
        Log.d("Frame", "Size: " + frame.getByteCount());
      }
    });
  }

}
