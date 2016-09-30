

package com.example.android.sunshine.app;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;


/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFaceService extends CanvasWatchFaceService {

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFaceService.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFaceService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFaceService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        private static final String LOG_TAG = "SUNSHINE WATCH";
        private static final String WEATHER_PATH = "/weather";
        private static final String WEATHER_HIGH_KEY = "weather_temp_high_key";
        private static final String WEATHER_LOW_KEY = "weather_temp_low_key";
        private static final String WEATHER_ICON_KEY = "weather_temp_icon_key";
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        String mHighString;
        String mLowString;
        Bitmap mWeatherIcon = null;
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mDividerPaint;
        Paint mTimePaint;
        Paint mTimePaintBold;
        Paint mDatePaint;
        Paint mTempPaint;
        Paint mTempPaintBold;
        Rect textBounds = new Rect();
        boolean mAmbient;
        SimpleDateFormat mDateFormat;
        float mXOffset;
        float mYOffset;
        Calendar mCalendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                mDateFormat = new SimpleDateFormat("ccc, MMM d yyyy", Locale.getDefault());
                mDateFormat.setCalendar(mCalendar);
                invalidate();
            }
        };
        Date mDate;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        private GoogleApiClient mGoogleApiClient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            mCalendar = Calendar.getInstance();
            mDate = new Date();

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = SunshineWatchFaceService.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.primary));

            mTimePaint = new Paint();
            mTimePaint = createTextPaint(resources.getColor(R.color.white));

            mTimePaintBold = new Paint();
            mTimePaintBold = createTextPaint(resources.getColor(R.color.white));

            mDatePaint = new Paint();
            mDatePaint = createTextPaint(resources.getColor(R.color.white));

            mTempPaint = new Paint();
            mTempPaint = createTextPaint(resources.getColor(R.color.white));
            mTempPaintBold = new Paint();
            mTempPaintBold = createTextPaint(resources.getColor(R.color.white));

            mDividerPaint = new Paint();
            mDividerPaint.setColor(resources.getColor(R.color.white));
            mDividerPaint.setStrokeWidth(0.5f);
            mDividerPaint.setAntiAlias(true);

            mDateFormat = new SimpleDateFormat("ccc, MMM d yyyy", Locale.getDefault());
            mDateFormat.setCalendar(mCalendar);

            mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        public void onConnected(Bundle bundle) {
                            Log.e(LOG_TAG, "onConnected: Successfully connected to Google API client");
                            Wearable.DataApi.addListener(mGoogleApiClient, dataListener);
                        }

                        @Override
                        public void onConnectionSuspended(int i) {
                            Log.e(LOG_TAG, "onConnectionSuspended");
                        }
                    })
                    .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(ConnectionResult connectionResult) {
                            Log.e(LOG_TAG, "onConnectionFailed(): Failed to connect, with result : " + connectionResult);
                        }
                    })
                    .build();
            mGoogleApiClient.connect();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }


        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                mCalendar.setTimeZone(TimeZone.getDefault());
                mDateFormat = new SimpleDateFormat("ccc, MMM d yyyy", Locale.getDefault());
                mDateFormat.setCalendar(mCalendar);
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);

            mTimePaint.setTextSize(resources.getDimension(R.dimen.digital_text_size));
            mTimePaintBold.setTextSize(resources.getDimension(R.dimen.digital_text_size));
            mDatePaint.setTextSize(resources.getDimension(R.dimen.digital_date_text_size));
            mTempPaint.setTextSize(resources.getDimension(R.dimen.digital_temp_text_size));
            mTempPaintBold.setTextSize(resources.getDimension(R.dimen.digital_temp_text_size));
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mBackgroundPaint.setColor(inAmbientMode ? getResources().getColor(R.color.black) : getResources().getColor(R.color.primary));
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimePaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                    mTempPaint.setAntiAlias(!inAmbientMode);
                    mDividerPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            Log.d(LOG_TAG, "onDrawCalled");

            int yOffset = 20;
            int xOffset = 10;
            int y;

            String text;

            int centerX = bounds.width() / 2;
            int centerY = bounds.height() / 2;

            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);
            boolean is24Hour = DateFormat.is24HourFormat(SunshineWatchFaceService.this);

            text = mDateFormat.format(mDate).toUpperCase();
            mDatePaint.getTextBounds(text, 0, text.length(), textBounds);
            canvas.drawText(text, centerX - textBounds.width() / 2, centerY, mDatePaint);
            y = textBounds.height();

            String hourString;
            if (is24Hour) {
                hourString = String.format(Locale.getDefault(), "%02d", mCalendar.get(Calendar.HOUR_OF_DAY));
            } else {
                int hour = mCalendar.get(Calendar.HOUR);
                if (hour == 0) {
                    hour = 12;
                }
                hourString = String.valueOf(hour);
            }

            text = hourString + ":" + String.format(Locale.getDefault(), "%02d", mCalendar.get(Calendar.MINUTE));
            mTimePaintBold.getTextBounds(text, 0, text.length(), textBounds);
            canvas.drawText(text, centerX - textBounds.width() / 2, centerY - yOffset + 4 - y, mTimePaintBold);

            if (!mAmbient) {
                y = yOffset;
                canvas.drawLine(centerX - 30, centerY + yOffset, centerX + 30, centerY + y, mDividerPaint);
                if (mHighString != null && mLowString != null) {

                    text = mHighString;
                    mTempPaintBold.getTextBounds(text, 0, text.length(), textBounds);
                    y = textBounds.height() + yOffset + y;
                    canvas.drawText(text, centerX - textBounds.width() / 2, centerY + y, mTempPaintBold);

                    text = mLowString;
                    canvas.drawText(text, centerX + textBounds.width() / 2 + xOffset, centerY + y, mTempPaint);

                    if (mWeatherIcon != null) {
                        // draw weather icon
                        canvas.drawBitmap(mWeatherIcon,
                                centerX - textBounds.width() / 2 - xOffset - mWeatherIcon.getWidth(),
                                centerY + y - mWeatherIcon.getHeight() / 2 - textBounds.height() / 2, null);
                    }
                } else {
                    // draw temperature high
                    text = getString(R.string.weather_unavailable);
                    mDatePaint.getTextBounds(text, 0, text.length(), textBounds);
                    y = textBounds.height() + yOffset + y;
                    canvas.drawText(text, centerX - textBounds.width() / 2, centerY + y, mDatePaint);

                }
            }

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
        DataApi.DataListener dataListener = new DataApi.DataListener() {
            @Override
            public void onDataChanged(DataEventBuffer dataEvents) {
                Log.e(LOG_TAG, "onDataChanged(): " + dataEvents);

                for (DataEvent event : dataEvents) {
                    if (event.getType() == DataEvent.TYPE_CHANGED) {
                        String path = event.getDataItem().getUri().getPath();
                        if (WEATHER_PATH.equals(path)) {
                            Log.e(LOG_TAG, "Data Changed for " + WEATHER_PATH);
                            try {
                                DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                                mHighString = dataMapItem.getDataMap().getString(WEATHER_HIGH_KEY);
                                mLowString = dataMapItem.getDataMap().getString(WEATHER_LOW_KEY);
                                Asset photo = dataMapItem.getDataMap().getAsset(WEATHER_ICON_KEY);
                                new LoadBitmapAsyncTask().execute(photo);
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "Exception   ", e);
                                mWeatherIcon = null;
                            }

                        } else {

                            Log.e(LOG_TAG, "Unrecognized path:  \"" + path + "\"  \"" + WEATHER_PATH + "\"");
                        }

                    } else {
                        Log.e(LOG_TAG, "Unknown data event type   " + event.getType());
                    }
                }
            }

            /*
             * Extracts {@link android.graphics.Bitmap} data from the
             * {@link com.google.android.gms.wearable.Asset}
             */

        };
        private class LoadBitmapAsyncTask extends AsyncTask<Asset, Void, Bitmap> {

            @Override
            protected Bitmap doInBackground(Asset... params) {

                if (params.length > 0) {

                    Asset asset = params[0];

                    InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                            mGoogleApiClient, asset).await().getInputStream();

                    if (assetInputStream == null) {
                        Log.w(LOG_TAG, "Requested an unknown Asset.");
                        return null;
                    }
                    return BitmapFactory.decodeStream(assetInputStream);

                } else {
                    Log.e(LOG_TAG, "Asset must be non-null");
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {

                if (bitmap != null) {
                    Log.d(LOG_TAG, "AsyncTask Bitmap != null");
                    mWeatherIcon = bitmap;
                }
            }
        }
    }
}
