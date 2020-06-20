package me.lc;

import okhttp3.OkHttpClient;

import java.util.concurrent.TimeUnit;

public class Constants {

    public static String DATA_SOURCE_PORT="";

    public static final OkHttpClient okHttpClient = new OkHttpClient.Builder().connectTimeout(30L, TimeUnit.SECONDS)
            .readTimeout(30L, TimeUnit.SECONDS).build();

}
