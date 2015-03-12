/*
 * Copyright 2015, Nod Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nod_labs.pointer;

import android.annotation.TargetApi;
import android.app.Application;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.*;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import com.google.inject.Inject;
import net.openspatial.ButtonEvent;
import roboguice.service.RoboService;

import java.util.HashMap;
import java.util.Map;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class PointerService extends RoboService {
    private PointerView mPointerView;
    private final IBinder mBinder = new PointerServiceBinder();

    @Inject
    private WindowManager mWindowManager;
    @Inject
    Application mApplication;

    private Handler mHandler = new Handler(Looper.getMainLooper());
    private final Map<View, PointerViewCallback> mRegisteredViews = new HashMap<View, PointerViewCallback>();
    private final Map<View, Integer> mViewZindex = new HashMap<View, Integer>();

    private final Map<String, Boolean> mTouch0State = new HashMap<String, Boolean>();

    private static final String TAG = PointerService.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();

        mPointerView = new PointerView(mApplication);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.LEFT | Gravity.TOP;

        mWindowManager.addView(mPointerView, params);
        Point point = new Point();
        mWindowManager.getDefaultDisplay().getRealSize(point);
        mPointerView.setWindowBounds(point.x, point.y);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mWindowManager.removeView(mPointerView);
    }

    public void addPointer(final String pointerId,
                           final int radius,
                           final int a,
                           final int r,
                           final int g,
                           final int b,
                           final String caption) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mPointerView.addPointer(pointerId, radius, a, r, g, b, caption);
                mTouch0State.put(pointerId, false);
                mPointerView.invalidate();
            }
        });
    }

    public void removePointer(final String pointerId) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mPointerView.removePointer(pointerId);
                mPointerView.invalidate();
            }
        });
    }

    public void updatePointerPosition(final String pointerId, final int deltaX, final int deltaY) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mPointerView.updatePointerPosition(pointerId, deltaX, deltaY);
                boolean touch2Down = mTouch0State.get(pointerId);
                if (touch2Down) {
                    Point currentPosition = getCurrentPositionOnScreen(pointerId);
                    PointerViewCallback cb = getCallbackForView(pointerId, currentPosition);
                    if (cb != null) {
                        cb.onDrag(pointerId, currentPosition.x, currentPosition.y, PointerService.this);
                    }
                }
                mPointerView.invalidate();
            }
        });
    }

    public void updatePointerRadius(final String pointerId, final int radius) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mPointerView.updatePointerRadius(pointerId, radius);
                mPointerView.invalidate();
            }
        });
    }

    public void updatePointerColor(final String pointerId, final int a, final int r, final int g, final int b) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mPointerView.updatePointerColor(pointerId, a, r, g, b);
                mPointerView.invalidate();
            }
        });
    }

    public void updatePointerColor(final String pointerId, int color) {
        updatePointerColor(pointerId, Color.alpha(color), Color.red(color), Color.green(color), Color.blue(color));
    }

    public void updatePointerCaption(final String pointerId, final String caption) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mPointerView.updatePointerCaption(pointerId, caption);
                mPointerView.invalidate();
            }
        });
    }

    private Point getCurrentPositionOnScreen(String pointerId) {
        Point currentPosition = mPointerView.getCurrentPosition(pointerId);
        int location[] = new int[2];
        mPointerView.getLocationOnScreen(location);
        currentPosition.x += location[0];
        currentPosition.y += location[1];

        return currentPosition;
    }

    private PointerViewCallback getCallbackForView(String pointerId, Point position) {
        PointerViewCallback cb = null;
        View v = getCurrentView(position);

        if (v == null) {
            Log.e(TAG, "Unknown view at position: (" + position.x + ", " + position.y + ")");
            return null;
        }

        return getCallbackForView(pointerId, v);
    }

    private PointerViewCallback getCallbackForView(String pointerId, View v) {
        return  mRegisteredViews.get(v);
    }

    public void performButtonEvent(final String pointerId, final ButtonEvent event) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Point currentPosition = getCurrentPositionOnScreen(pointerId);
                View v = getCurrentView(currentPosition);

                PointerViewCallback cb = getCallbackForView(pointerId, v);
                boolean touch0Down = mTouch0State.get(pointerId);

                if (cb != null) {
                    cb.onButtonEvent(pointerId, event, currentPosition.x, currentPosition.y, PointerService.this);
                }

                switch (event.buttonEventType) {
                    case TOUCH0_DOWN:
                        touch0Down = true;
                        break;
                    case TOUCH0_UP:
                        if (touch0Down) {
                            Log.d(TAG, "Sending click event for device " + pointerId);
                            if (cb != null) {
                                cb.onClick(pointerId, currentPosition.x, currentPosition.y, PointerService.this);
                            } else if (v != null) {
                                v.callOnClick();
                            }
                        }
                        touch0Down = false;
                        break;
                    // We don't care about the others (for now anyway)
                }

                mTouch0State.put(pointerId, touch0Down);
            }
        });
    }

    public void registerView(final View v, final PointerViewCallback cb) {
        registerViewWithZindex(v, cb, 0);
    }
    public void registerViewWithZindex(final View v, final PointerViewCallback cb, final int zIndex) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mRegisteredViews.put(v, cb);
                mViewZindex.put(v, zIndex);
            }
        });
    }

    public void updateViewZindex(final View v, final int zIndex) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mViewZindex.put(v, zIndex);
            }
        });
    }

    public void unregisterView(final View v) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mRegisteredViews.remove(v);
            }
        });
    }

    private boolean withinView(View v, Point position) {
        int[] location = new int[2];
        v.getLocationOnScreen(location);

        Point topLeft = new Point(location[0], location[1]);
        Point bottomRight = new Point(topLeft.x + v.getWidth(), topLeft.y + v.getHeight());

        return (position.x > topLeft.x &&
                position.x < bottomRight.x &&
                position.y > topLeft.y &&
                position.y < bottomRight.y);
    }

    private View getCurrentView(Point position) {
        int maxZindex = Integer.MIN_VALUE;

        View currentView = null;
        for (View v : mRegisteredViews.keySet()) {
            if (withinView(v, position)) {
                int zIndex = mViewZindex.get(v);
                if (zIndex >= maxZindex) {
                    currentView = v;
                    maxZindex = zIndex;
                }
            }
        }

        return currentView;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class PointerServiceBinder extends Binder {
        public PointerService getService() {
            return PointerService.this;
        }
    }

    public interface PointerViewCallback {
        public void onButtonEvent(String pointerId, ButtonEvent event, int x, int y, PointerService service);
        public void onClick(String pointerId, int x, int y, PointerService service);
        public void onDrag(String pointerId, int x, int y, PointerService service);
    }
}
