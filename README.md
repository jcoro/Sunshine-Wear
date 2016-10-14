# Sunshine-Wear
This App is Project 6 for the Udacity Android Developer Nanodegree. Sunshine-Wear showcases custom watchface design and the synching of Assets and DataItems with an Android Wearable device.

![Sunshine-Wear](http://www.coronite.net/assets/img/github/project6.png)

## Android Features / Libraries Implemented:

- [DataItems](https://developer.android.com/training/wearables/data-layer/data-items.html)
- [Transferring Assets](https://developer.android.com/training/wearables/data-layer/assets.html)
- [Watchface Design](https://developer.android.com/design/wear/watchfaces.html)

## Specifications
- `compileSdkVersion 24`
- `buildToolsVersion "24.0.1"`
- `minSdkVersion 10`
- `targetSdkVersion 24`

## Dependencies
```
dependencies {
    compile fileTree(include: ['*.jar'], dir: 'libs')
    compile 'com.android.support:support-annotations:24.2.1'
    compile 'com.android.support:gridlayout-v7:24.2.1'
    compile 'com.android.support:cardview-v7:24.2.1'
    compile 'com.android.support:appcompat-v7:24.2.1'
    compile 'com.android.support:design:24.2.1'
    compile 'com.github.bumptech.glide:glide:3.5.2'
    compile 'com.android.support:recyclerview-v7:24.2.1'
    compile 'com.google.android.apps.muzei:muzei-api:2.0'
    compile 'com.google.android.gms:play-services:9.2.1'
    compile 'com.google.android.gms:play-services-wearable:9.2.1'
}
```

## Implementation

[Step-by-step instructions](http://www.coronite.net/training/android_wear_development/android_wear_development_lesson2.php#addendum-2) on installing an Android Wear Virtual Device, and pairing your handheld device with the emulator.

Notes on synching DataItems and Assets:

Package names of the `app` and `wear` modules must be the same.
DataItems will only be sent if their data is new, to facilitate this during development, add a timestamp to the DataMapRequest, e.g. in `SunshineSyncAdapter.java`:
```
putDataMapRequest.getDataMap().putLong("timestamp", System.currentTimeMillis());
```
Image Assets must be converted to bitmaps on a background thread, as follows in `SunshineWatchFaceService.java`:
```
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
```

This sample uses the Gradle build system. To build this project, use the "gradlew build" command or use "Import Project" in Android Studio.

If you have any questions I'd be happy to try and help. Please contact me at: john@coronite.net.

# License
This is a public domain work under [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/). Feel free to use it as you see fit.