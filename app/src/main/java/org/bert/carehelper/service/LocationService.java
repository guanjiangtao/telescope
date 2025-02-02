package org.bert.carehelper.service;


import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Looper;
import android.util.Log;


import com.alibaba.fastjson2.JSON;
import com.tencent.map.geolocation.TencentLocation;
import com.tencent.map.geolocation.TencentLocationListener;
import com.tencent.map.geolocation.TencentLocationManager;
import com.tencent.map.geolocation.TencentLocationRequest;

import org.bert.carehelper.common.API;
import org.bert.carehelper.common.CareHelperContext;
import org.bert.carehelper.common.CommandType;
import org.bert.carehelper.common.Operation;
import org.bert.carehelper.entity.CallLogInfo;
import org.bert.carehelper.entity.CommandResponse;
import org.bert.carehelper.entity.Contact;
import org.bert.carehelper.entity.Location;
import org.bert.carehelper.http.HTTPConnection;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

public class LocationService implements TencentLocationListener, Service {

    private final String TAG = "LocationService";

    private Context context;

    private TencentLocation location;

    private TencentLocationManager mLocationManager;

    private boolean canGetLocation = false;

    private CareHelperContext cContext = CareHelperContext.getInstance();

    private ThreadPoolExecutor threadPool = null;

    public LocationService(Context context) {
        this.context = context;

        this.threadPool = cContext.getThreadPoolExecutor();
        this.mLocationManager = TencentLocationManager.getInstance(this.context);
        TencentLocationRequest request = TencentLocationRequest.create();
        request.setAllowGPS(true);
        request.setAllowDirection(true);
        request.setIndoorLocationMode(true);
        request.setInterval(3000);
        this.mLocationManager.requestLocationUpdates(request, this, Looper.getMainLooper());
    }

    public void getAddress(double latitude, double longitude) {
        List<Address> addressList = null;
        Geocoder geocoder = new Geocoder(this.context);
        try {
            addressList = geocoder.getFromLocation(latitude, longitude, 1);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (addressList != null) {
            for (Address address : addressList) {
                Log.d(TAG, String.format("address: %s", address.toString()));
            }
        }
    }

    public void syncLocation() {
        if (this.canGetLocation()) {
            Log.i(TAG, "get location success! location is " + this.location.getAddress() + ";");
            Location location = new Location(
                    this.location.getAddress(),
                    Operation.UPDATE_LOCATION, new Date(),
                    1,
                    this.cContext.getEnvironment().getToken());
            location.setPhone(this.cContext.getPhone());
            this.cContext.getThreadPoolExecutor().execute(() -> new HTTPConnection(
                    API.API_PHONE + "/" + "location",
                    location
                    , "GET").run());

            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 关闭地理位置获取
     */
    public void removeUpdates() {
        this.mLocationManager.removeUpdates(this);
        Log.i(TAG, "关闭成功;");
    }

    @Override
    public void onLocationChanged(TencentLocation tencentLocation, int i, String s) {
        if ("OK".equals(s)) {
            this.location = tencentLocation;
            if (this.location != null) {
                this.setCanGetLocation(true);
            }
            this.syncLocation();
        } else {
            Log.e(TAG, "location failed");
        }
    }

    @Override
    public void onStatusUpdate(String s, int i, String s1) {
        System.out.println(s);
    }

    @Override
    public CommandResponse doCommand(String type) {
        CommandResponse response = new CommandResponse();
        response.setPhone(this.cContext.getPhone());
        response.setToken(this.cContext.getEnvironment().getToken());
        String[] strings = type.split(":");
        if (strings.length < 2) {
            Log.e(TAG, "commands error!");
            return response;
        }
        switch (strings[1]) {
            case CommandType.UPDATE:
                this.syncLocation();
                response.setStatus(0);
                response.setMessage("open sync location success！");
                break;
            case CommandType.CANCEL:
                this.removeUpdates();
                response.setStatus(0);
                response.setMessage("remove location success！");
                break;
        }
        try {
            Log.i(TAG, "commands exec success, wait 4 sec to do upload task!");
            Thread.sleep(4000);
            this.threadPool.execute(()->{
                Log.i(TAG, "doing upload task！");
                new HTTPConnection(
                        API.API_COMMAND + "/receive",
                        response,
                        "POST"
                ).run();
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return response;
    }

    public boolean canGetLocation() {
        return canGetLocation && this.cContext.getEnvironment().getToken() != null;
    }

    public void setCanGetLocation(boolean canGetLocation) {
        this.canGetLocation = canGetLocation;
    }
}
