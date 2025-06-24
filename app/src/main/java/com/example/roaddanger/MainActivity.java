package com.example.roaddanger;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.AndroidException;
import android.util.Log;
import android.Manifest;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.roaddanger.DatabaseHelper;
import com.example.roaddanger.Distance;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private LocationManager locationManager;
    private LocationListener locationListener;
    private TextView textView1, textView2;
    private DatabaseHelper dbHelper;

    private String closestDistanceStreetName;
    private double closestDistance;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        textView1 = findViewById(R.id.textView1);
        textView2 = findViewById(R.id.textView2);

        dbHelper = new DatabaseHelper(this);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // 리스너를 만든다.
        // 위치가 변할 때마다 호출되는 함수를 작성!

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {

                // 위도, 경도값을 추출, 여기에서 활용할 코드 작성

                double lat = location.getLatitude(); // 위도

                double lng = location.getLongitude(); // 경도


                try {
                    dbHelper.checkAndCopyDatabase();  // assets → 내부 저장소 복사
                    SQLiteDatabase db = dbHelper.openDatabase();  // DB 열기

                    // 샘플 쿼리 실행
                    Cursor cursor_streetName = db.rawQuery("SELECT StreetName FROM '안양시 보행자 사고 지역 5개년'", null);
                    Cursor cursor_latitude = db.rawQuery("SELECT Latitude FROM '안양시 보행자 사고 지역 5개년'", null);
                    Cursor cursor_longtude = db.rawQuery("SELECT Longitude FROM '안양시 보행자 사고 지역 5개년'", null);


                    //최단거리 초기값 설정
                    cursor_streetName.moveToFirst();
                    cursor_latitude.moveToFirst();
                    cursor_longtude.moveToFirst();

                    closestDistance = Distance.getDistance(lat,lng,cursor_latitude.getDouble(0),cursor_longtude.getDouble(0));


                    while (cursor_streetName.moveToNext()){
                        cursor_latitude.moveToNext();
                        cursor_longtude.moveToNext();

                        String _streetName = cursor_streetName.getString(0);
                        double _latitude = cursor_latitude.getDouble(0);
                        double _longitude = cursor_longtude.getDouble(0);
                        //Log.d("DB", "도로명주소: " + _streetName + ", 위도: " + _latitude + ", " + "경도: " + _longitude);

                        double calculated_distance_save = Distance.getDistance(lat,lng,_latitude,_longitude);
                        if (calculated_distance_save <= closestDistance){
                            closestDistance = calculated_distance_save;
                            closestDistanceStreetName = _streetName;
                        }

                        if(closestDistance < 200){      //200m 이내 접근 시 알림, 알림 터치 시 앱으로 진입.
                            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                            NotificationCompat.Builder builder = new NotificationCompat.Builder(MainActivity.this, "default");

                            // 알림 터치 시 앱으로 진입하기 위한 인텐트 생성
                            Intent intent = new Intent(MainActivity.this, MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            PendingIntent pendingIntent = PendingIntent.getActivity(MainActivity.this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

                            builder.setSmallIcon(R.mipmap.ic_launcher);
                            builder.setContentTitle("위험 알림");
                            builder.setContentText(200 + "m 내 위험지역입니다.");
                            builder.setContentIntent(pendingIntent);
                            builder.setAutoCancel(true); // 알림 터치 시 자동으로 알림이 사라지도록 설정

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                notificationManager.createNotificationChannel(new NotificationChannel("default", "기본 채널", NotificationManager.IMPORTANCE_DEFAULT));
                            }

                            notificationManager.notify(1, builder.build());
                        }
                    }

                    String closestDistance_2f = String.format("%.2f", closestDistance);

                    textView1.setText("가장 가까운 사고 지역: \n" + closestDistanceStreetName);
                    textView2.setText("해당 지역과의 거리: " + closestDistance_2f + "m");

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        //권한 허용하는 코드

        if(ActivityCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){

            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION},
                    100);
            return;
        }

        // 매니저에게 업데이트 될 때마다 아까 만든 코드 동작하도록 해.
        // 3초(3000) , 10m / 안움직일 때는 -1

        locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                3000, -1,
                locationListener
        );
    }

    // 권한 관련. GPS 처리하는 코드(재사용 코드)

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode == 100) {
            // 허용하지 않았으면, 다시 허용하라는 알러트 띄운다.
            if (ActivityCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                finish();
                return;
            }

            // 허용했으면, GPS 정보 가져오는 코드 넣는다.
            locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    3000, -1,
                    locationListener
            );
        }
    }
}