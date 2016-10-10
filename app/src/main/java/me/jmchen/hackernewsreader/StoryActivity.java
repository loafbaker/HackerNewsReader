package me.jmchen.hackernewsreader;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class StoryActivity extends AppCompatActivity {

    SQLiteDatabase mStoryDB;

    String mStoryUrl;
    String mStoryHTML;

    WebView mStoryView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_story);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Intent intent = getIntent();

        if (intent.hasExtra("storyUrl")) {
            mStoryUrl = intent.getStringExtra("storyUrl");

            mStoryView = (WebView) findViewById(R.id.web_story);
            mStoryView.getSettings().setJavaScriptEnabled(true);

            if (getStoryContent()) {
                mStoryView.setWebViewClient(new WebViewClient());
                mStoryView.loadData(mStoryHTML, "text/html", "UTF-8");
                Toast.makeText(this, "Reading content from local storage", Toast.LENGTH_LONG).show();
            } else {
                mStoryView.addJavascriptInterface(new SaveContentInterface(), "PipeData");
                mStoryView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        super.onPageFinished(view, url);
                        mStoryView.loadUrl("javascript:window.PipeData.saveStoryContent('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');");
                    }
                });
                mStoryView.loadUrl(mStoryUrl);
                Toast.makeText(this, "Retrieving " + mStoryUrl, Toast.LENGTH_LONG).show();
            }
        } else {
            // In case that story url is null
            mStoryUrl = "";
            String storyTitle = intent.getStringExtra("storyTitle");

            if (getStoryContent(storyTitle)) {
                mStoryView = (WebView) findViewById(R.id.web_story);
                mStoryView.getSettings().setJavaScriptEnabled(true);
                mStoryView.setWebViewClient(new WebViewClient());
                mStoryView.loadData(mStoryHTML, "text/html", "UTF-8");
                Toast.makeText(this, "Reading content from local storage", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "No Story details for this news item. Go back to find more interesting news.", Toast.LENGTH_LONG).show();
            }
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

    private boolean getStoryContent() {
        openDatabase();
        Cursor cursor = mStoryDB.rawQuery("SELECT * FROM stories WHERE url = ?", new String[] {mStoryUrl});
        int storyContentIndex = cursor.getColumnIndex("content");

        if (cursor != null && cursor.getCount() == 1) {
            cursor.moveToFirst();
            if (cursor.isNull(storyContentIndex)) {
                return false;
            } else {
                mStoryHTML = cursor.getString(storyContentIndex);
                return true;
            }
        }
        return false;
    }


    private boolean getStoryContent(String title) {
        openDatabase();
        Cursor cursor = mStoryDB.rawQuery("SELECT * FROM stories WHERE title = ?", new String[] {title});
        int storyContentIndex = cursor.getColumnIndex("content");

        if (cursor != null && cursor.getCount() == 1) {
            cursor.moveToFirst();
            if (cursor.isNull(storyContentIndex)) {
                return false;
            } else {
                mStoryHTML = buildHtmlContent(title, cursor.getString(storyContentIndex));
                return true;
            }
        }
        return false;
    }

    private String buildHtmlContent(String title, String text) {
        String result = "";
        result += "<html>";
        result += "  <body>";
        result += "<h1>" + title + "</h1>";
        result += "<p>" + text + "</p>";
        result += "  </body>";
        result += "</html>";

        return result;
    }


    private class SaveContentInterface {
        @JavascriptInterface
        public void saveStoryContent(String content) {
            openDatabase();

            if (!content.isEmpty()) {
                String updateSql = "UPDATE stories SET content = ? WHERE url = ?";
                SQLiteStatement statement = mStoryDB.compileStatement(updateSql);
                statement.bindString(1, content);
                statement.bindString(2, mStoryUrl);
                statement.execute();
            }
        }
    }
}
