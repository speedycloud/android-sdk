package com.speedycloud.os.spobjectstorage;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.RequestBody;

import org.w3c.dom.Text;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView resultText = (TextView) findViewById(R.id.show);
        resultText.setText("DD");

//        String ACCESS_KEY = "5C0FA427C421219C0D67FF372AB71784";
//        String SECRET_KEY = "d519b8b1a9c0cc51100ccff69a3f574c87ba2969ab7f8a8f30d243a8d5d7d69b";
//
//        SCObjectStorage os = SCObjectStorage.getInstance();
//        os.init(ACCESS_KEY, SECRET_KEY);
//
//        String bucket = "ddsfsd";
//        String key = "ddf";

        Thread task = new Thread(


                new Runnable() {
                    @Override
                    public void run() {
                        String ACCESS_KEY = "5C0FA427C421219C0D67FF372AB71784";
                        String SECRET_KEY = "d519b8b1a9c0cc51100ccff69a3f574c87ba2969ab7f8a8f30d243a8d5d7d69b";

                        SCObjectStorage os = SCObjectStorage.getInstance();
                        os.init(ACCESS_KEY, SECRET_KEY);

                        String bucket = "ddsfsd";
                        String key = "DDD";

                        String result = "";

//                        result = os.queryAllObjects(bucket);
//                        result = os.createBucket(bucket);
//                        result = os.deleteBucket(bucket);

//                        result = os.queryBucketAcl(bucket);

//                        result = os.queryBucketVersioning(bucket);
//                        result = os.setBucketVersioning(bucket, "Suspended");

                        MediaType type = MediaType.parse("");
                        RequestBody body = RequestBody.create(type, "dssfsdfsdfsdfsdfs");

                        result = os.uploadBigObjectStep2(bucket, key, 1, "2~5rU61ULVGFxx3DAqSFXS4z5uECJ0Ekm", body);
//                        result = os.createObject(bucket, "DDD", body);

//                        result = os.queryAllObjects(bucket);
//                        result = os.deleteObject(bucket, "DDD");

//                        result = os.updateObjectAcl(bucket, key, "private");

//                        result = os.downloadObject(bucket, key);
//                        result = os.uploadBigObjectStep1(bucket, key);
                        Log.i("result", result);
                    }
                }
        );

        task.start();
    }
}
