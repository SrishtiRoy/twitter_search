package com.src.ui;

import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.src.adapter.TweetAdapter;
import com.src.data.Authenticated;
import com.src.data.TweetList;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private ListActivity activity;
    // final static String SearchTerm = "cleartax";
    final static String LOG_TAG = "rnc";
    String Key = null;
    String Secret = null;
    TweetAdapter adapter;
    private ListView lView;
    private AutoCompleteTextView searchtextView;
    private RelativeLayout mProgressBarView;
    TweetList list;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tweet_list);
        adapter = new TweetAdapter(this, new TweetList());
        Key = getStringFromManifest("CONSUMER_KEY");
        Secret = getStringFromManifest("CONSUMER_SECRET");
        lView = (ListView) findViewById(R.id.lview);
        searchtextView = (AutoCompleteTextView) findViewById(R.id.search_text);
        mProgressBarView = (RelativeLayout) findViewById(R.id.progress_bar);


        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() > 3) {
                    if (s.toString().trim().equalsIgnoreCase("cleartax"))
                        downloadSearches(s.toString().trim());
                    else
                        Toast.makeText(MainActivity.this, "No tweets available!", Toast.LENGTH_SHORT).show();
                }
            }
        };
        searchtextView.addTextChangedListener(textWatcher);

    }

    private void openDialog(List<String> arr) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_settings_dm);
        TextView tv1 = (TextView) dialog.findViewById(R.id.tvWord1);
        TextView tv2 = (TextView) dialog.findViewById(R.id.tvWord2);
        TextView tv3 = (TextView) dialog.findViewById(R.id.tvWord3);
        Button btnOk = (Button) dialog.findViewById(R.id.save_btn);

        if (arr != null && arr.size() > 3) {
            tv1.setText(arr.get(0).toString());
            tv2.setText(arr.get(1).toString());
            tv3.setText(arr.get(2).toString());

        }
        btnOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.cancel();
            }
        });

        dialog.setCanceledOnTouchOutside(true);
        dialog.setCancelable(true);
        dialog.show();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings_dm:

                getTopThreeWords();

                return true;

            default:
                return super.onOptionsItemSelected(item);

        }
    }

    private String getStringFromManifest(String key) {
        String results = null;

        try {
            Context context = this.getBaseContext();
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            results = (String) ai.metaData.get(key);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return results;
    }

    // download twitter searches after first checking to see if there is a network connection
    public void downloadSearches(String search) {
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();

        if (networkInfo != null && networkInfo.isConnected()) {
            showProgressBar();
            new DownloadTwitterTask().execute(search);
        } else {
            Toast.makeText(MainActivity.this, "No network connection!", Toast.LENGTH_SHORT).show();
        }
    }

    // Uses an AsyncTask to download data from Twitter
    private class DownloadTwitterTask extends AsyncTask<String, Void, String> {
        final static String TwitterTokenURL = "https://api.twitter.com/oauth2/token";
        final static String TwitterSearchURL = "https://api.twitter.com/1.1/search/tweets.json?q=";

        @Override
        protected String doInBackground(String... searchTerms) {
            String result = null;

            if (searchTerms.length > 0) {
                result = getSearchStream(searchTerms[0]);
            }
            return result;
        }

        // onPostExecute convert the JSON results into a Twitter object (which is an Array list of tweets
        @Override
        protected void onPostExecute(String result) {
            hideProgressBar();
            list = jsonToSearches(result);


            // send the tweets to the adapter for rendering
            lView.setAdapter(adapter);
            adapter.setTweetList(list);
            adapter.notifyDataSetChanged();
        }

        // converts a string of JSON data into a SearchResults object
        private TweetList jsonToSearches(String result) {
            TweetList twits = null;
            if (result != null && result.length() > 0) {
                try {
                    Gson gson = new Gson();
                    twits = gson.fromJson(result, TweetList.class);
                } catch (IllegalStateException ex) {
                    Log.e("LOG", "", ex);
                }
            }
            return twits;
        }

        // convert a JSON authentication object into an Authenticated object
        private Authenticated jsonToAuthenticated(String rawAuthorization) {
            Authenticated auth = null;
            if (rawAuthorization != null && rawAuthorization.length() > 0) {
                try {
                    Gson gson = new Gson();
                    auth = gson.fromJson(rawAuthorization, Authenticated.class);
                } catch (IllegalStateException ex) {
                }
            }
            return auth;
        }

        private String getResponseBody(HttpRequestBase request) {
            StringBuilder sb = new StringBuilder();
            try {

                DefaultHttpClient httpClient = new DefaultHttpClient(new BasicHttpParams());
                HttpResponse response = httpClient.execute(request);
                int statusCode = response.getStatusLine().getStatusCode();
                String reason = response.getStatusLine().getReasonPhrase();

                if (statusCode == 200) {

                    HttpEntity entity = response.getEntity();
                    InputStream inputStream = entity.getContent();

                    BufferedReader bReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8);
                    String line = null;
                    while ((line = bReader.readLine()) != null) {
                        sb.append(line);
                    }
                } else {
                    sb.append(reason);
                }
            } catch (UnsupportedEncodingException ex) {
            } catch (ClientProtocolException ex1) {
            } catch (IOException ex2) {
            }
            return sb.toString();
        }

        private String getStream(String url) {
            String results = null;

            // Step 1: Encode consumer key and secret
            try {
                // URL encode the consumer key and secret
                String urlApiKey = URLEncoder.encode(Key, "UTF-8");
                String urlApiSecret = URLEncoder.encode(Secret, "UTF-8");

                // Concatenate the encoded consumer key, a colon character, and the encoded consumer secret
                String combined = urlApiKey + ":" + urlApiSecret;

                // Base64 encode the string
                String base64Encoded = Base64.encodeToString(combined.getBytes(), Base64.NO_WRAP);

                // Step 2: Obtain a bearer token
                HttpPost httpPost = new HttpPost(TwitterTokenURL);
                httpPost.setHeader("Authorization", "Basic " + base64Encoded);
                httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
                httpPost.setEntity(new StringEntity("grant_type=client_credentials"));
                String rawAuthorization = getResponseBody(httpPost);
                Authenticated auth = jsonToAuthenticated(rawAuthorization);

                // Applications should verify that the value associated with the
                // token_type key of the returned object is bearer
                if (auth != null && auth.token_type.equals("bearer")) {

                    // Step 3: Authenticate API requests with bearer token
                    HttpGet httpGet = new HttpGet(url);

                    // construct a normal HTTPS request and include an Authorization
                    // header with the value of Bearer <>
                    httpGet.setHeader("Authorization", "Bearer " + auth.access_token);
                    httpGet.setHeader("Content-Type", "application/json");
                    // update the results with the body of the response
                    results = getResponseBody(httpGet);
                }
            } catch (UnsupportedEncodingException ex) {
            } catch (IllegalStateException ex1) {
            }
            return results;
        }

        private String getSearchStream(String searchTerm) {
            String results = null;
            try {
                String encodedUrl = URLEncoder.encode(searchTerm, "UTF-8");
                results = getStream(TwitterSearchURL + encodedUrl);
            } catch (UnsupportedEncodingException ex) {
            } catch (IllegalStateException ex1) {
            }
            return results;
        }

    }

    public void showProgressBar() {
        //mProgressBarMessage.setVisibility(View.GONE);
        if (mProgressBarView != null) {
            mProgressBarView.setVisibility(View.VISIBLE);
        }
    }

    public void hideProgressBar() {
        //	mProgressBarMessage.setVisibility(View.GONE);
        if (mProgressBarView != null) {
            mProgressBarView.setVisibility(View.GONE);
        }
    }

    private void getTopThreeWords() {
        Map<String, Integer> map = new HashMap<>();
        if(list!=null) {
            if (list.tweets != null && list.tweets.size() > 0) {
                for (int i = 0; i < list.tweets.size(); i++) {
                    String[] words = list.tweets.get(i).text.split("[ \n\t\r.,;:!?(){}]");

                    for (int counter = 0; counter < words.length; counter++) {
                        String key = words[counter].toLowerCase(); // remove .toLowerCase for Case Sensitive result.
                        if (key.length() > 0) {
                            if (map.get(key) == null) {
                                map.put(key, 1);
                            } else {
                                int value = map.get(key).intValue();
                                value++;
                                map.put(key, value);
                            }
                        }
                    }
                }
                Set<Map.Entry<String, Integer>> entrySet = map.entrySet();
                System.out.println("Words" + "\t\t" + "# of Occurances");
                for (Map.Entry<String, Integer> entry : entrySet) {
                    System.out.println(entry.getKey() + "\t\t" + entry.getValue());
                }
                List sortedKeys = new ArrayList(map.keySet());
                Collections.sort(sortedKeys);
                openDialog(sortedKeys);

            }


        }
        else
            Toast.makeText(MainActivity.this, "No frequent words available!", Toast.LENGTH_SHORT).show();

    }




}
