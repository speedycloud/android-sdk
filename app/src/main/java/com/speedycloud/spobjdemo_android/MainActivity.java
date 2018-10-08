package com.speedycloud.spobjdemo_android;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.speedycloud.spobjdemo_android.utils.FileUtils;
import com.speedycloud.spobjsdk.download.spMultiResumeDownTask;
import com.speedycloud.spobjsdk.download.spRequestManager;
import com.speedycloud.spobjsdk.listener.spDownLoadStateListener;
import com.speedycloud.spobjsdk.storge.FileRecorder;
import com.speedycloud.spobjsdk.storge.UpCancellationSignal;
import com.speedycloud.spobjsdk.storge.UpProgressHandler;
import com.speedycloud.spobjsdk.storge.UploadManager;
import com.speedycloud.spobjsdk.storge.UploadOptions;
import com.speedycloud.spobjsdk.utils.AsyncRun;
import com.speedycloud.spobjsdk.utils.Tools;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements View.OnClickListener,spDownLoadStateListener{

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE = 1001;
    private static final int PERMISSION_WRITE_EXTERNAL_STORAGE = 1002;
    private MainActivity context;
    private Button btn_queryBucket;
    private Button btn_createBucket;
    private Button btn_setBucketVersion;
    private Button btn_delBucket;
    private Button btn_updateBucketAcl;
    private Button btn_delBucketObj;
    private Button btn_delBucketAssignObj;
    private Button btn_updateBucketObjAcl;
    private Button btn_updateBucketAssignObj;
    private Button btn_queryBucketObjAcl;
    private Button btn_queryBucketObjVersion;
    private Button btn_queryBucketAllObj;
    private Button btn_downloadBucketObj;
    private Button btn_queryBucketVersion;
    private Button btn_cancelUploadFile;
    private Button btn_startUploadFile;

    private String uploadFilePath;
    private String bucketName;
    private EditText bucketNameEditText;
    private String objName;
    private EditText objNameEditText;
    private TextView objShow;
    private TextView progressShow;
    private TextView errorShow;
    private boolean cancelUpload;
    private long uploadLastTimePoint;
    private long uploadFileLength;
    private long uploadLastOffset;

    private UploadManager uploadManager;
    private UploadOptions uploadOptions;
    private spMultiResumeDownTask multiResumeDownTask = null;

    private boolean isVersionEnabled = true;

    private String strAccessKey = "your AccessKey";
    private String strSecretKey = "your SecretKey";

    private String strVersionId = "AssignVserionId";

    private PowerManager pManager;
    private PowerManager.WakeLock mWakeLock;

    public MainActivity() {
        this.context = this;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btn_queryBucket = (Button)findViewById(R.id.btn_queryBucket);
        btn_queryBucket.setOnClickListener(this);

        btn_createBucket = (Button)findViewById(R.id.btn_createBucket);
        btn_createBucket.setOnClickListener(this);

        btn_setBucketVersion = (Button)findViewById(R.id.btn_setBucketVersion);
        btn_setBucketVersion.setOnClickListener(this);

        btn_delBucket = (Button)findViewById(R.id.btn_delBucket);
        btn_delBucket.setOnClickListener(this);

        btn_updateBucketAcl = (Button)findViewById(R.id.btn_updateBucketAcl);
        btn_updateBucketAcl.setOnClickListener(this);

        btn_delBucketObj = (Button)findViewById(R.id.btn_delBucketObj);
        btn_delBucketObj.setOnClickListener(this);

        btn_delBucketAssignObj = (Button)findViewById(R.id.btn_delBucketAssignObj);
        btn_delBucketAssignObj.setOnClickListener(this);

        btn_updateBucketObjAcl = (Button)findViewById(R.id.btn_updateBucketObjAcl);
        btn_updateBucketObjAcl.setOnClickListener(this);

        btn_updateBucketAssignObj = (Button)findViewById(R.id.btn_updateBucketAssignObj);
        btn_updateBucketAssignObj.setOnClickListener(this);

        btn_queryBucketObjAcl = (Button)findViewById(R.id.btn_queryBucketObjAcl);
        btn_queryBucketObjAcl.setOnClickListener(this);

        btn_queryBucketObjVersion = (Button)findViewById(R.id.btn_queryBucketAllObjVersion);
        btn_queryBucketObjVersion.setOnClickListener(this);

        btn_queryBucketAllObj = (Button)findViewById(R.id.btn_queryBucketAllObj);
        btn_queryBucketAllObj.setOnClickListener(this);

        btn_downloadBucketObj = (Button)findViewById(R.id.btn_downloadBucketObj);
        btn_downloadBucketObj.setOnClickListener(this);

        btn_queryBucketVersion = (Button)findViewById(R.id.btn_queryBucketVersion);
        btn_queryBucketVersion.setOnClickListener(this);

        btn_cancelUploadFile = (Button)findViewById(R.id.btn_cancelUploadFile);
        btn_cancelUploadFile.setOnClickListener(this);

        btn_startUploadFile = (Button)findViewById(R.id.btn_startUploadFile);
        btn_startUploadFile.setOnClickListener(this);

        bucketNameEditText = (EditText)findViewById(R.id.edit_bucketName);

        objNameEditText = (EditText)findViewById(R.id.edit_objName);

        objShow = (TextView)findViewById(R.id.objShow);
        progressShow = (TextView)findViewById(R.id.progressShow);
        errorShow = (TextView)findViewById(R.id.errorShow);

        bucketNameEditText.setText("AndroidBucketTest");
        objNameEditText.setText("AndroidObjTest");

        bucketName = bucketNameEditText.getText().toString().trim();
        objName = objNameEditText.getText().toString().trim();

        this.uploadOptions = new UploadOptions(null, strAccessKey, strSecretKey,null, false,
                new UpProgressHandler() {
                    @Override
                    public void progress(String key,long totalSize, double percent) {
                        if(totalSize > 0) {
                            uploadFileLength = totalSize;
                        }
                        updateStatus(percent);
                        objShow.setText(key);
                    }
                },
                new UpCancellationSignal() {

                    @Override
                    public boolean isCancelled() {
                        return cancelUpload;
                    }
                });

        if (multiResumeDownTask == null) {
            multiResumeDownTask =
                    new spMultiResumeDownTask(this,
                            bucketName,
                            objName,
                            strAccessKey,
                            strSecretKey,
                            this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(null != mWakeLock){
            mWakeLock.release();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        pManager = ((PowerManager) getSystemService(POWER_SERVICE));
        mWakeLock = pManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                | PowerManager.ON_AFTER_RELEASE, TAG);
        mWakeLock.acquire();
    }


    private void checkPermission() {
        //检查权限（NEED_PERMISSION）是否被授权 PackageManager.PERMISSION_GRANTED表示同意授权
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            //用户已经拒绝过一次，再次弹出权限申请对话框需要给用户一个解释
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission
                    .READ_EXTERNAL_STORAGE)) {
                Toast.makeText(this, "请开通相关权限，否则无法正常使用本应用！", Toast.LENGTH_SHORT).show();
            }
            //申请权限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_CODE);

        } else {
//            Toast.makeText(this, "授权成功！", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "checkPermission: 已经授权！");
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        switch (id) {
            case R.id.btn_queryBucket:
                Log.d(TAG,"onclick queryBucket...");
                queryBucket();
                break;
            case R.id.btn_createBucket:
                Log.d(TAG,"onclick createBucket...");
                createBucket();
                break;
            case R.id.btn_setBucketVersion:
                Log.d(TAG,"onclick setBucketVersion...");
                if(isVersionEnabled){
                    isVersionEnabled = false;
                }else{
                    isVersionEnabled = true;
                }
                setBucketVersion(!isVersionEnabled);
                break;
            case R.id.btn_delBucket:
                Log.d(TAG,"onclick delBucket...");
                deleteBucket();
                break;
            case R.id.btn_updateBucketAcl:
                Log.d(TAG,"onclick updateBucketAcl...");
                updateBucket();
                break;
            case R.id.btn_delBucketObj:
                Log.d(TAG,"onclick delBucketObj...");
                deleteBucketObj();
                break;
            case R.id.btn_delBucketAssignObj:
                Log.d(TAG,"onclick delBucketAssignObj...");
                deleteBucketAssignObj();
                break;
            case R.id.btn_updateBucketObjAcl:
                Log.d(TAG,"onclick updateBucketObjAcl...");
                updateBucketObj();
                break;
            case R.id.btn_updateBucketAssignObj:
                Log.d(TAG,"onclick updateBucketAssignObj...");
                updateBucketAssignObj();
                break;
            case R.id.btn_queryBucketObjAcl:
                Log.d(TAG,"onclick queryBucketObjAcl...");
                queryBucketObjAcl();
                break;
            case R.id.btn_queryBucketAllObjVersion:
                Log.d(TAG,"onclick queryBucketObjVersion...");
                queryBucketAllObjVersion();
                break;
            case R.id.btn_queryBucketAllObj:
                Log.d(TAG,"onclick queryBucketAllObj...");
                queryBucketAllObj();
                break;
            case R.id.btn_downloadBucketObj:
                Log.d(TAG,"onclick downloadBucketObj...");
                // 申请Runtime Permission
                requestRuntimePermissions();
                downloadObj();
                break;
            case R.id.btn_queryBucketVersion:
                Log.d(TAG,"onclick queryBucketVersion...");
                queryBucketVersion();
                break;
            case R.id.btn_cancelUploadFile:
                Log.d(TAG,"onclick cancelUploadFile...");
                if(!cancelUpload){
                    cancelUpload = true;
                }else{
                    cancelUpload = false;
                }
                break;
            case R.id.btn_startUploadFile:
                Log.d(TAG,"onclick startUploadFile...");
                startUploadFile();
                break;
            default:
                break;
        }
    }

    public void createBucket(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (context.uploadManager == null) {
                    context.uploadManager = new UploadManager();
                }
                context.uploadManager.createBucket(bucketName, uploadOptions);

            }
        }).start();
    }

    public void queryBucket(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (context.uploadManager == null) {
                    context.uploadManager = new UploadManager();
                }
                context.uploadManager.queryBucket(bucketName, uploadOptions);

            }
        }).start();
    }

    public void setBucketVersion(final boolean isVersionEnable){
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (context.uploadManager == null) {
                    context.uploadManager = new UploadManager();
                }
                String strVersion = "";
                if(isVersionEnable){
                    strVersion = context.uploadManager.strSpBucketVersionEnabled;
                }else{
                    strVersion = context.uploadManager.strSpBucketVersionSuspended;
                }
                context.uploadManager.setBucketVersion(bucketName, strVersion,uploadOptions);

            }
        }).start();
    }

    public void deleteBucket(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (context.uploadManager == null) {
                    context.uploadManager = new UploadManager();
                }
                context.uploadManager.deleteBucket(bucketName,uploadOptions);

            }
        }).start();
    }

    public void updateBucket(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (context.uploadManager == null) {
                    context.uploadManager = new UploadManager();
                }
                context.uploadManager.updateBucket(bucketName,
                        context.uploadManager.strSpBucketAclPrivatRW,uploadOptions);

            }
        }).start();
    }

    public void queryBucketVersion() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (context.uploadManager == null) {
                    context.uploadManager = new UploadManager();
                }
                context.uploadManager.queryBucketVersion(bucketName, uploadOptions);

            }
        }).start();
    }

    public void startUploadFile() {
        Intent target = FileUtils.createGetContentIntent();
        Intent intent = Intent.createChooser(target,
                this.getString(R.string.choose_file));
        try {
            this.startActivityForResult(intent, REQUEST_CODE);
        } catch (ActivityNotFoundException ex) {
            Log.d(TAG,""+ ex.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CODE:
                checkPermission();
                // If the file selection was successful
                if (resultCode == RESULT_OK) {
                    if (data != null) {
                        // Get the URI of the selected file
                        final Uri uri = data.getData();
                        try {
                            // Get the file path from the URI
                            final String path = FileUtils.getPath(this, uri);
                            this.uploadFilePath = path;
                            if (this.uploadFilePath == null) {
                                return;
                            }
//        //reset cancel signal
                            this.cancelUpload = false;

                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    if (context.uploadManager == null) {
                                        try {
                                            context.uploadManager = new UploadManager(new FileRecorder(
                                                    context.getFilesDir() + "/SpAndroid"));
                                        } catch (IOException e) {
                                            Log.e("spError", e.getMessage());
                                        }
                                    }
                                    context.uploadManager.startUploadFile(bucketName,
                                            objName,
                                            uploadFilePath,
                                            uploadOptions);
                                }
                            }).start();
                        } catch (Exception e) {
                            Toast.makeText(
                                    this,
                                    this.getString(R.string.select_upload_file),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void updateStatus(final double percentage) {
        long now = System.currentTimeMillis();
        long deltaTime = now - uploadLastTimePoint;
        long currentOffset = (long) (percentage * uploadFileLength);
        long deltaSize = currentOffset - uploadLastOffset;
        if (deltaTime <= 100) {
            return;
        }
        Log.d("progress","progress = " + percentage);

        final String speed = Tools.formatSpeed(deltaSize, deltaTime);
        // update
        uploadLastTimePoint = now;
        uploadLastOffset = currentOffset;
//
        AsyncRun.runInMain(new Runnable() {
            @Override
            public void run() {
                int progress = (int) (percentage * 100);
                progressShow.setText(progress + " %" + "  speed:" + speed);
            }
        });
    }

    public void deleteBucketObj(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (context.uploadManager == null) {
                    context.uploadManager = new UploadManager();
                }
                context.uploadManager.deleteBucketObj(bucketNameEditText.getText().toString().trim(),
                        objNameEditText.getText().toString().trim(),uploadOptions);

            }
        }).start();
    }

    public void deleteBucketAssignObj(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (context.uploadManager == null) {
                    context.uploadManager = new UploadManager();
                }
                context.uploadManager.deleteBucketAssignObj(bucketNameEditText.getText().toString().trim(),
                        objNameEditText.getText().toString().trim(),strVersionId,uploadOptions);

            }
        }).start();
    }

    public void updateBucketObj(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (context.uploadManager == null) {
                    context.uploadManager = new UploadManager();
                }
                context.uploadManager.updateBucketObj(bucketNameEditText.getText().toString().trim(),
                        objNameEditText.getText().toString().trim(),
                        context.uploadManager.strSpBucketAclPrivatRW,uploadOptions);

            }
        }).start();
    }

    public void updateBucketAssignObj(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (context.uploadManager == null) {
                    context.uploadManager = new UploadManager();
                }
                context.uploadManager.updateBucketAssignObj(bucketNameEditText.getText().toString().trim(),
                        objNameEditText.getText().toString().trim(),
                        strVersionId,
                        context.uploadManager.strSpBucketAclPrivatRW,uploadOptions);

            }
        }).start();
    }

    public void queryBucketObjAcl(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (context.uploadManager == null) {
                    context.uploadManager = new UploadManager();
                }
                context.uploadManager.queryBucketObjAcl(bucketNameEditText.getText().toString().trim(),
                        objNameEditText.getText().toString().trim(),uploadOptions);

            }
        }).start();
    }

    public void queryBucketAllObjVersion(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (context.uploadManager == null) {
                    context.uploadManager = new UploadManager();
                }
                context.uploadManager.queryBucketAllObjVersion(bucketNameEditText.getText().toString().trim(),uploadOptions);

            }
        }).start();
    }

    public void queryBucketAllObj(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (context.uploadManager == null) {
                    context.uploadManager = new UploadManager();
                }
                context.uploadManager.queryBucketAllObj(bucketNameEditText.getText().toString().trim(),uploadOptions);

            }
        }).start();
    }

    public void downloadObj(){
// 下面的url是需要下载的文件在服务器上的url
        if (!multiResumeDownTask.isDownloading()) {
            // 开始下载
            spRequestManager.getInstance().excuteDownTask(multiResumeDownTask);
            AsyncRun.runInMain(new Runnable() {
                @Override
                public void run() {
                    btn_downloadBucketObj.setText("暂停下载对象");
                }
            });

        } else {
            // 暂停下载
            spRequestManager.getInstance().resumeDownTask(multiResumeDownTask);

            AsyncRun.runInMain(new Runnable() {
                @Override
                public void run() {
                    btn_downloadBucketObj.setText("下载桶内对象");
                }
            });
        }
    }

    @Override
    public void OnDownLoadProcessChange(int process,final float percent) {
        Log.i(TAG, "process:" + process);

        AsyncRun.runInMain(new Runnable() {
            @Override
            public void run() {
                String showInfo = String.format("%6s%f%%","下载进度",100*percent);
                progressShow.setText(showInfo);
            }
        });

    }

    @Override
    public void OnDownLoadStart(final int fileLength) {

        AsyncRun.runInMain(new Runnable() {
            @Override
            public void run() {
                String showInfo = String.format("%6s%dMB","开始下载 文件大概:",fileLength / (1024 * 1024));
                progressShow.setText("0.0%");
                btn_downloadBucketObj.setText("暂停下载对象");
            }
        });
    }

    @Override
    public void OnDownLoadResume(int process,final float percent) {

        AsyncRun.runInMain(new Runnable() {
            @Override
            public void run() {
                String showInfo = String.format("%6s%f%%","暂停下载,下载到:",percent * 100);
                progressShow.setText(showInfo);
            }
        });
    }

    @Override
    public void OnDownLoadFinished(File file) {
        Log.i(TAG, "下载完成");

        AsyncRun.runInMain(new Runnable() {
            @Override
            public void run() {
                progressShow.setText("下载完成");
                btn_downloadBucketObj.setText("下载桶内对象");
            }
        });
    }

    @Override
    public void OnDownLoadFailed(final String error) {
        Log.i(TAG, "下载失败:" + error);

        AsyncRun.runInMain(new Runnable() {
            @Override
            public void run() {
                String showInfo = String.format("%6s%s","下载失败:",error);
                progressShow.setText(showInfo);
            }
        });
    }

    // 申请需要的运行时权限
    private void requestRuntimePermissions() {

        // 如果版本低于Android6.0，不需要申请运行时权限
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            //申请WRITE_EXTERNAL_STORAGE权限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_WRITE_EXTERNAL_STORAGE);
        }
    }

    // 对运行时权限做相应处理
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        doNext(requestCode, grantResults);
    }

    private void doNext(int requestCode, int[] grantResults) {
        if (requestCode == PERMISSION_WRITE_EXTERNAL_STORAGE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission Granted
            } else {
                // Permission Denied
                finish();
            }
        }
    }
}
