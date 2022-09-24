package com.github.kilnn.wristband2.sample;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.github.kilnn.wristband2.sample.dfu.DfuDialogFragment;
import com.github.kilnn.wristband2.sample.dial.entity.DialCustom;
import com.github.kilnn.wristband2.sample.net.GlobalApiClient;
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
public class CustomCommandActivity extends BaseActivity {

    private String TAG = "CustomCommandActivity";

    private GlobalApiClient apiClient = MyApplication.getApiClient();

    private WristbandManager mWristbandManager = WristbandApplication.getWristbandManager();
    private WristbandVersion mWristbandVersion;
    private TextView reportTV;

    private DialView dialView;

    private DfuManager dfuManager;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_command);

        reportTV = findViewById(R.id.report_view);
        reportTV.setText("Custom Command");

        dialView = findViewById(R.id.custom_dial_view);


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

        dfuManager.init();

        if (mWristbandManager.isConnected()){
            mWristbandVersion = mWristbandManager.getWristbandConfig().getWristbandVersion();
            if (mWristbandVersion.isExtDialUpgrade()) {
                DialBinInfo dialBinInfo = mWristbandManager.requestDialBinInfo().blockingGet();

                boolean extGUI = mWristbandVersion.isExtGUI();

                if (!extGUI) {

                    DialCustom dialCustom = new DialCustom();
                    dialCustom.setBinUrl("http://fitcloud.hetangsmart.com/public/getfile/6ec7acde0d88.bin");


                    DialDrawer.Shape shape = DialDrawer.Shape.createRectangle(200,320);
                    Bitmap bousole = BitmapFactory.decodeResource(getResources(), R.drawable.bousole_blanche);
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



                    // convert input stream to file object
                    InputStream inputStream = getResources().openRawResource(R.raw.bin_template);
                    File bin_template;
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


                    DialWriter dialWriter = new DialWriter(bin_template, actualBackground, preview, DialDrawer.Position.BOTTOM, false);
                    try {
                        File new_dial = File.createTempFile("dial", ".bin");

                        dialWriter.setCopyFile(new_dial);
                        dialWriter.setAutoScalePreview(true);
                        dialWriter.execute();

                        // now the dfu!
                        reportTV.setText("upgrading...");
                        dfuManager.upgradeDial(new_dial.getAbsolutePath(), (byte) 0);

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
    protected void onDestroy() {
        super.onDestroy();
        dfuManager.release();

    }

}
