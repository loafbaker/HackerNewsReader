package me.jmchen.hackernewsreader;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    // Check all the Hacker News APIs in https://github.com/HackerNews/API
    public static final String TOP_STORIES_URL = "https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty";
    public static final String STORY_ITEM_URL = "https://hacker-news.firebaseio.com/v0/item/%1$d.json";

    private int mStoryTotal = 20;

    Map<String, String> mStoryUrls = new HashMap<>();

    SQLiteDatabase mStoryDB;

    List<String> mTitles;
    ArrayAdapter mStoryAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Initialize ListView
        ListView listView = (ListView) findViewById(R.id.list_stories);
        mTitles = new ArrayList<>();
        mStoryAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, mTitles);
        listView.setAdapter(mStoryAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String clickTitle = parent.getItemAtPosition(position).toString();
                String contentUrl = mStoryUrls.get(clickTitle);

                Intent intent = new Intent(getApplicationContext(), StoryActivity.class);
                if (!contentUrl.isEmpty()) {
                    intent.putExtra("storyUrl", contentUrl);
                } else {
                    intent.putExtra("storyTitle", clickTitle);  // In case that story url is null
                }
                startActivity(intent);
            }
        });

        openDatabase();
        updateStories();

        StoriesDownloadTask getStoriesTask = new StoriesDownloadTask();
        try {
            getStoriesTask.execute(TOP_STORIES_URL);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // mStoryDB.close();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                StoriesDownloadTask getNewStoriesTask = new StoriesDownloadTask();
                try {
                    getNewStoriesTask.execute(TOP_STORIES_URL);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private boolean hasStory(int storyId) {
        Cursor cursor = mStoryDB.rawQuery("SELECT * FROM stories WHERE storyId = " + storyId, null);
        return cursor.getCount() > 0;
    }

    private void updateStories() {
        String storyTitle;
        String storyUrl;

        if (mStoryDB != null && mStoryDB.isOpen()) {

            // clear the ListView data
            mTitles.clear();
            mStoryUrls.clear();

            Cursor cursor = mStoryDB.rawQuery("SELECT * FROM stories ORDER BY storyId DESC LIMIT " + mStoryTotal, null);
            int titleIndex = cursor.getColumnIndex("title");
            int urlIndex = cursor.getColumnIndex("url");

            if (cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    storyTitle = cursor.getString(titleIndex);
                    // url can be null
                    storyUrl = (cursor.isNull(urlIndex)) ? "" : cursor.getString(urlIndex);

                    mTitles.add(storyTitle);
                    mStoryUrls.put(storyTitle, storyUrl);

                    cursor.moveToNext();
                }
            }
            mStoryAdapter.notifyDataSetChanged();
        } else {
            Toast.makeText(this, "Reading Hacker News Database Error!", Toast.LENGTH_LONG).show();
        }
    }

    private void openDatabase() {
        // Initialize Database
        if (mStoryDB == null || !mStoryDB.isOpen()) {
            mStoryDB = this.openOrCreateDatabase("Stories.db", MODE_PRIVATE, null);
            mStoryDB.execSQL("CREATE TABLE IF NOT EXISTS stories (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "storyId INTEGER, " +
                    "title VARCHAR, " +
                    "url VARCHAR, " +
                    "content VARCHAR)");
            // mStoryDB.execSQL("DELETE FROM stories");
        }
    }

    public class StoriesDownloadTask extends DownloadTask {
        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            openDatabase();

            try {
                JSONArray jsonArray = new JSONArray(s);
                mStoryTotal = Math.min(mStoryTotal, jsonArray.length());
                for (int i = 0; i < mStoryTotal; i++) {
                    int storyId = jsonArray.getInt(i);

                    // Check whether the content has already store in database
                    if (!hasStory(storyId)) {
                        String storyApi = String.format(STORY_ITEM_URL, storyId);

                        DownloadTask getStoryTask = new DownloadTask();
                        String storyInfo = getStoryTask.execute(storyApi).get();

                        JSONObject jsonObject = new JSONObject(storyInfo);
                        String storyTitle = jsonObject.getString("title");

                        // url is optional data
                        // if no url is given, use text content to create a story detail page
                        String storyUrl;
                        String storyContent;
                        if (jsonObject.has("url")) {
                            storyUrl = jsonObject.getString("url");
                            Log.i("Fetch story", "title - " + storyTitle + ", url - " + storyUrl);

                            String insertSql = "INSERT INTO stories (storyId, title, url) VALUES (?, ?, ?)";
                            SQLiteStatement statement = mStoryDB.compileStatement(insertSql);
                            statement.bindString(1, String.valueOf(storyId));
                            statement.bindString(2, storyTitle);
                            statement.bindString(3, storyUrl);
                            statement.execute();
                        } else {
                            storyContent = jsonObject.getString("text");
                            Log.i("Fetch story", "title - " + storyTitle + ", text - " + storyContent);

                            String insertSql = "INSERT INTO stories (storyId, title, content) VALUES (?, ?, ?)";
                            SQLiteStatement statement = mStoryDB.compileStatement(insertSql);
                            statement.bindString(1, String.valueOf(storyId));
                            statement.bindString(2, storyTitle);
                            statement.bindString(3, storyContent);
                            statement.execute();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Update Stories ListView
            updateStories();
        }
    }
}
