package com.example.snipe_itbulkscanner;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    public static final String hardwareEndpoint = "/hardware/";
    public static final String locationsEndpoint = "/locations/";

    private TextView assetIdOutput;
    private TextView locationIdOutput;
    private TextView responseOutput;

    private String assetId;
    private String locationId;

    private String baseUrl;
    private String apiKey;

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(),
            result -> {
                if(result.getContents() == null) {
                    Toast.makeText(MainActivity.this, "Canceled", Toast.LENGTH_LONG).show();
                } else {
                    boolean goodscan = true;
                    if (result.getContents().startsWith(baseUrl + hardwareEndpoint)) {
                        // Asset
                        assetId = result.getContents().replace(baseUrl + hardwareEndpoint, "");
                        assetIdOutput.setText(assetId);
                    } else if (result.getContents().startsWith(baseUrl + locationsEndpoint)) {
                        // Location
                        locationId = result.getContents().replace(baseUrl + locationsEndpoint, "");
                        locationIdOutput.setText(locationId);
                    } else {
                        Toast.makeText(MainActivity.this, "Not a valid " + baseUrl + " Snipe-IT QR code", Toast.LENGTH_LONG).show();
                        goodscan = false;
                    }
                    if (goodscan && assetId != null && locationId != null) {
                        responseOutput.setText("");
                        new HttpAsyncTask().execute();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        assetIdOutput = findViewById(R.id.assetIdOutput);
        locationIdOutput = findViewById(R.id.locationIdOutput);
        responseOutput = findViewById(R.id.response);

        SharedPreferences sharedPreferences = getSharedPreferences("scannerPrefs", Context.MODE_PRIVATE);
        String baseUrl = sharedPreferences.getString("baseUrl", "");
        String apiKey = sharedPreferences.getString("apiKey", "");
        if (baseUrl.isEmpty()) {
            promptUserForString("base URL", "baseUrl", (input) -> this.baseUrl = input);
        } else {
            this.baseUrl = baseUrl;
        }
        if (apiKey.isEmpty()) {
            promptUserForString("API key", "apiKey", (input) -> this.apiKey = input);
        } else {
            this.apiKey = apiKey;
        }
    }

    private void promptUserForString(String label, String pref, InputLambda inputLambda) {
        EditText editText = new EditText(this);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter the " + label);
        builder.setView(editText);
        builder.setPositiveButton("OK", (dialog, which) -> {
            String userInput = editText.getText().toString();
            // Save the string to SharedPreferences
            SharedPreferences sharedPreferences = getSharedPreferences("scannerPrefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(pref, userInput);
            editor.apply();
            inputLambda.processInput(userInput);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    public void onScanButtonClick(View view) {
        barcodeLauncher.launch(new ScanOptions());
    }

    public void onResetButtonClick(View view) {
        assetId = null;
        locationId = null;
        assetIdOutput.setText("-");
        locationIdOutput.setText("-");
        responseOutput.setText("");
    }

    private static String performAssetPatch(String assetId, String locationId, String baseUrl, String apiKey) {
        StringBuilder result = new StringBuilder();
        HttpURLConnection urlConnection = null;

        try {
            URL url = new URL(baseUrl + "/api/v1/hardware/" + assetId);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("PATCH");
            urlConnection.setRequestProperty("Accept", "application/json");
            urlConnection.setRequestProperty("Content-Type", "application/json");
            urlConnection.setRequestProperty("Authorization", "Bearer " + apiKey);
            urlConnection.setDoOutput(true);
            DataOutputStream outputStream = new DataOutputStream(urlConnection.getOutputStream());
            outputStream.writeBytes("{ \"rtd_location_id\": " + locationId + " }");
            outputStream.flush();
            outputStream.close();

            BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                result.append(line);
            }

            return result.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    private class HttpAsyncTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            return performAssetPatch(assetId, locationId, baseUrl, apiKey);
        }

        @Override
        protected void onPostExecute(String result) {
            try {
                JSONObject jsonObject = new JSONObject(result);
                String status = jsonObject.getString("status");
                String messages = jsonObject.getString("messages");
                responseOutput.setTextColor("success".equals(status) ? Color.GREEN : Color.RED);
                responseOutput.setText(messages);
            } catch (JSONException e) {
                responseOutput.setTextColor(Color.YELLOW);
                responseOutput.setText(result);
            }
        }
    }

    interface InputLambda {
        void processInput(String string);
    }
}