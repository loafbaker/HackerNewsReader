package me.jmchen.hackernewsreader;

import android.os.AsyncTask;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by Think on 2016/9/6.
 */
public class DownloadTask extends AsyncTask<String, Void, String> {
    @Override
    protected String doInBackground(String... params) {
        String result = "";
        URL url;
        HttpURLConnection urlConnection;

        try {
            url = new URL(params[0]);
            urlConnection = (HttpURLConnection) url.openConnection();

            urlConnection.connect();

            InputStream inputStream = urlConnection.getInputStream();
            InputStreamReader reader = new InputStreamReader(inputStream);

            int data = reader.read();
            while(data != -1) {
                char current = (char) data;
                result += current;
                data = reader.read();
            }

            return result;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }
}
