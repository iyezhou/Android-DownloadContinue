package yezhou.me.downloadcontinue;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "yezhou";
    private static final String fileUrl = "http://dldir1.qq.com/weixin/android/weixin6331android940.apk";
    private static final String fileName = "weixin6331android940.apk";

    private ProgressBar mProgressBar = null;
    private TextView mTvProgressPercent = null;
    private boolean mIsShortPause = false;
    private boolean mIsLongStop = false;
    private Button mBtnPauseShort = null;
    private Button mBtnPauseLong = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();

        mProgressBar = (ProgressBar) this.findViewById(R.id.progressbar);
        mTvProgressPercent = (TextView) this.findViewById(R.id.progress_percent);
        mBtnPauseShort = (Button) this.findViewById(R.id.btn_pause_short);
        mBtnPauseLong = (Button) this.findViewById(R.id.btn_pause_long);

        requestPermissions();
    }

    public void downloadShort(View view) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(fileUrl).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.i(TAG, "请求失败: " + e.getLocalizedMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    ResponseBody body = response.body();
                    if (body != null) {
                        writeFile(body);
                    }
                }
            }
        });
    }

    public void pauseShort(View view) {
        if (!mIsShortPause) {
            mIsShortPause = true;
            mBtnPauseShort.setText("继续传输");
        } else {
            mIsShortPause = false;
            mBtnPauseShort.setText("短暂断点");
        }
    }

    public void downloadLong(View view) {
        SharedPreferences preferences = getSharedPreferences("download", Context.MODE_PRIVATE);
        final long offset = preferences.getLong("offset", 0);
        int progress = preferences.getInt("progress", 0);
        mProgressBar.setProgress(progress);
        mTvProgressPercent.setText(progress+"%");
        mIsLongStop = false;

        Toast.makeText(MainActivity.this, "已下载：" + progress + "%", Toast.LENGTH_LONG).show();

        if (progress == 100) {
            Toast.makeText(MainActivity.this, "您已完成下载，请点击清除信息重新下载！", Toast.LENGTH_LONG).show();
            return;
        }

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(fileUrl).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.i(TAG, "请求失败: " + e.getLocalizedMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    ResponseBody body = response.body();
                    if (body != null) {
                        writeFile(body, offset);
                    }
                }
            }
        });
    }

    public void stopLong(View view) {
        mIsLongStop = true;
    }

    public void clearLong(View view) {
        SharedPreferences preferences = getSharedPreferences("download", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong("offset", 0);
        editor.putInt("progress", 0);
        editor.commit();
        Toast.makeText(MainActivity.this, "信息清除成功！", Toast.LENGTH_LONG).show();
    }

    //主线程更新UI进度
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    int progress = msg.arg1;
                    mTvProgressPercent.setText(progress + "%");
                    mProgressBar.setProgress(progress);
                    break;

                default:
                    break;
            }
        }
    };

    private void writeFile(ResponseBody body) {
        InputStream is = null;  //网络输入流
        FileOutputStream fos = null;  //文件输出流

        is = body.byteStream();

        String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + fileName;
        File file = new File(filePath);
        try {
            fos = new FileOutputStream(file);
            byte[] buffer = new byte[1024];
            int len = 0;
            long totalSize = body.contentLength();  //文件总大小
            long sum = 0;
            while (true) {
                if (!mIsShortPause) {
                    if ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                        sum += len;
                        int progress = (int) (sum * 1.0f / totalSize * 100);
                        Message msg = handler.obtainMessage();
                        msg.what = 1;
                        msg.arg1 = progress;
                        handler.sendMessage(msg);
                    } else {
                        break;
                    }
                } else {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void writeFile(ResponseBody body, long offset) {
        InputStream is = null;  //网络输入流
        is = body.byteStream();

        String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + fileName;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(filePath, "rw");
            raf.seek(offset);
            byte[] buffer = new byte[1024];
            int len = 0;
            long totalSize = body.contentLength();  //文件总大小
            long sum = 0;
            int progress = 0;
            boolean isUpdate = false;
            while (!mIsLongStop && (len = is.read(buffer)) != -1) {
                //Log.i(TAG, "sum = " + sum + ", offset = " + offset);
                if (sum >= offset) {
                    raf.write(buffer, 0, len);
                    isUpdate = true;
                }
                sum += len;
                if (isUpdate) {
                    progress = (int) (sum * 1.0f / totalSize * 100);
                    Message msg = handler.obtainMessage();
                    msg.what = 1;
                    msg.arg1 = progress;
                    handler.sendMessage(msg);
                }
            }

            if (sum > offset) {
                SharedPreferences preferences = getSharedPreferences("download", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putLong("offset", sum);
                editor.putInt("progress", progress);
                editor.commit();
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Dexter.withActivity(this)
                    .withPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE})
                    .withListener(new MultiplePermissionsListener() {
                        @Override
                        public void onPermissionsChecked(MultiplePermissionsReport report) {
                            for (PermissionGrantedResponse grantedResponse : report.getGrantedPermissionResponses()) {
                                Log.i(TAG, "以获取权限: " + grantedResponse.getPermissionName());
                            }
                            for (PermissionDeniedResponse deniedResponse : report.getDeniedPermissionResponses()) {
                                Log.i(TAG, "权限被拒绝: " + deniedResponse.getPermissionName());
                            }
                        }

                        @Override
                        public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {

                        }
                    })
                    .check();
        }
    }
}
