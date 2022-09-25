package com.github.kilnn.wristband2.sample;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.github.kilnn.wristband2.sample.dfu.DfuDialogFragment;
import com.github.kilnn.wristband2.sample.dial.entity.DialCustom;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.htsmart.wristband2.WristbandApplication;
import com.htsmart.wristband2.WristbandManager;
import com.htsmart.wristband2.bean.DialBinInfo;
import com.htsmart.wristband2.bean.WristbandVersion;
import com.htsmart.wristband2.dfu.DfuCallback;
import com.htsmart.wristband2.dfu.DfuManager;
import com.htsmart.wristband2.dial.DialDrawer;
import com.htsmart.wristband2.dial.DialView;
import com.htsmart.wristband2.dial.DialWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import timber.log.Timber;

/**
 * Sync Data
 */
public class CustomCommandActivity extends BaseActivity implements SensorEventListener {

    private final String TAG = "CustomCommandActivity";

    private final WristbandManager mWristbandManager = WristbandApplication.getWristbandManager();
    private WristbandVersion mWristbandVersion;
    private TextView reportTV;

    private TextView kin_coords_tv;
    private final float[] kin_coords = new float[]{(float) 43.529857, (float) 5.458219};

    private DialView dialView;
    private File bin_template;
    private DfuManager dfuManager;

    private TextView gpsCoords;
    private TextView orientation_text;

    private FusedLocationProviderClient fusedLocationProvider;

    private Location gpsCoordinates = null;
    private SensorManager sensorManager;
    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];

    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    double angle = 0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_command);

        kin_coords_tv = findViewById(R.id.kin_coords);
        kin_coords_tv.setText("KIN: "+kin_coords[0] + "; " +kin_coords[1]);

        reportTV = findViewById(R.id.report_view);
        reportTV.setText("Custom Command");
        dialView = findViewById(R.id.dial_view2);

        mWristbandManager.getConnectedDevice();

        dfuManager = new DfuManager(this);
        dfuManager.setDfuCallback(new DfuCallback(){

            @Override
            public void onError(int i, int i1) {
                DfuDialogFragment.toastError(CustomCommandActivity.this, i, i1);

            }

            @Override
            public void onStateChanged(int i, boolean b) {

            }

            @Override
            public void onProgressChanged(int i) {
                reportTV.setText("progressing "+i+"%");
            }

            @Override
            public void onSuccess() {
                reportTV.setText("success!!!");
            }
        });

        // convert input stream to file object
        InputStream inputStream = getResources().openRawResource(R.raw.bin_template);

        try{
            bin_template = File.createTempFile("pre", "suf");
            OutputStream outputStream = new FileOutputStream(bin_template);
            byte[] buffer = new byte[1024];
            int read;
            while((read = inputStream.read(buffer)) != -1){
                outputStream.write(buffer, 0, read);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Can't create temp file ", e);
        }

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        gpsCoords = findViewById(R.id.text_gps);
        orientation_text = findViewById(R.id.text_orientation);

        fusedLocationProvider = LocationServices.getFusedLocationProviderClient(this);

        findViewById(R.id.update_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                gpsCoords.setText("appuyé");
                if (ActivityCompat.checkSelfPermission(CustomCommandActivity.this, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermission();
                }
                fusedLocationProvider.getLastLocation().addOnSuccessListener(CustomCommandActivity.this, new OnSuccessListener() {
                    @Override
                    public void onSuccess(Object o) {
                        if (o != null) {
                            gpsCoordinates = (Location) o;

                            gpsCoords.setText(gpsCoordinates.toString());
                            angle = calculateAngle(gpsCoordinates.getLatitude(), gpsCoordinates.getLongitude())-orientationAngles[0]*180/Math.PI;
                            reportTV.setText("angle "+angle);
                        }
                    }
                });
                updateOrientationAngles();
                orientation_text.setText(orientationAngles[0]*180/3.14+ "");
            }
        });

        findViewById(R.id.update_dial_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                reportTV.setText("Updating Dial!");
                dfuManager.init();
                updateDial(true, angle);
            }
        });

    }

    protected double calculateAngle(double phone_latitude, double phone_longitude) {
        // convert to radians
        double phi1 = phone_latitude*Math.PI/180;
        double lambda1 = phone_longitude*Math.PI/180;
        double phi2 = kin_coords[0]*Math.PI/180;
        double lambda2 = kin_coords[1]*Math.PI/180;

        double y = Math.sin(lambda2-lambda1) * Math.cos(phi2);
        double x = Math.cos(phi1)*Math.sin(phi2) -
                        Math.sin(phi1)*Math.cos(phi2)*Math.cos(lambda2-lambda1);
        double theta = Math.atan2(y, x);

        return (theta*180/Math.PI + 360) % 360;
    }

    public static Bitmap RotateBitmap(Bitmap source, float angle)
    {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    protected void updateDial(boolean write, double angle){
        if (mWristbandManager.isConnected()){
            mWristbandVersion = mWristbandManager.getWristbandConfig().getWristbandVersion();
            if (mWristbandVersion.isExtDialUpgrade()) {
                DialBinInfo dialBinInfo = mWristbandManager.requestDialBinInfo().blockingGet();

                boolean extGUI = mWristbandVersion.isExtGUI();

                if (!extGUI) {

                    DialCustom dialCustom = new DialCustom();
                    dialCustom.setBinUrl("http://fitcloud.hetangsmart.com/public/getfile/6ec7acde0d88.bin");


                    DialDrawer.Shape shape = DialDrawer.Shape.createRectangle(200,320);
                    Bitmap bousole = BitmapFactory.decodeResource(getResources(), R.drawable.bousole);
                    bousole = RotateBitmap(bousole, (float) angle);
                    Bitmap style = BitmapFactory.decodeResource(getResources(), R.drawable.dial_style2);

                    Bitmap preview = DialDrawer.createDialPreview(bousole, style, shape,
                            DialDrawer.ScaleType.CENTER_CROP, DialDrawer.Position.BOTTOM,
                            800, shape.width(),shape.height());

                    ImageView imageView = findViewById(R.id.imageView);
                    imageView.setImageBitmap(preview);

                    dialView.setBackgroundBitmap(bousole);
                    dialView.setStyleBitmap(style, 800); // 800 is the good one!
                    dialView.setStylePosition(DialDrawer.Position.BOTTOM);
                    dialView.setShape(shape);
                    dialView.setBackgroundScaleType(DialDrawer.ScaleType.CENTER_CROP);


                    Bitmap actualBackground = dialView.createActualBackground();
                    dialView.createActualPreview(100, 200);



                    DialWriter dialWriter = new DialWriter(bin_template, actualBackground, preview, DialDrawer.Position.BOTTOM, false);
                    try {
                        File new_dial = File.createTempFile("dial", ".bin");

                        dialWriter.setCopyFile(new_dial);
                        dialWriter.setAutoScalePreview(true);
                        dialWriter.execute();

                        // now the dfu!
                        reportTV.setText("upgrading...");
                        if (write) {
                            dfuManager.upgradeDial(new_dial.getAbsolutePath(), (byte) 0);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        //throw new RuntimeException("", e);
                        //DialWriter.WriterException.
                        Timber.tag(TAG).d("cant execute the dial writer : "+e);
                    }
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Get updates from the accelerometer and magnetometer at a constant rate.
        // To make batch operations more efficient and reduce power consumption,
        // provide support for delaying updates to the application.
        //
        // In this example, the sensor reporting delay is small enough such that
        // the application receives an update before the system checks the sensor
        // readings again.
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
        Sensor magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magneticField != null) {
            sensorManager.registerListener(this, magneticField,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Don't receive any more updates from either sensor.
        sensorManager.unregisterListener(this);
    }

    private void requestPermission () {
        ActivityCompat.requestPermissions(this, new String[]{ACCESS_FINE_LOCATION}, 1);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading,
                    0, accelerometerReading.length);
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading,
                    0, magnetometerReading.length);
        }

    }

    public void updateOrientationAngles() {
        // Update rotation matrix, which is needed to update orientation angles.
        SensorManager.getRotationMatrix(rotationMatrix, null,
                accelerometerReading, magnetometerReading);

        // "rotationMatrix" now has up-to-date information.

        SensorManager.getOrientation(rotationMatrix, orientationAngles);

        // "orientationAngles" now has up-to-date information.
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dfuManager.release();
    }

}
