package com.example.android.camera2basic;

import android.graphics.Bitmap;

public interface FrameProcessor {
  void process(Bitmap frame);
}
