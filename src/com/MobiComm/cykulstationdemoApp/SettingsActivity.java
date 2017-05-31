package com.MobiComm.cykulstationdemoApp;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.view.MenuItem;
import android.view.View;

/**
 * Created by too1 on 10.01.14.
 */
public class SettingsActivity  extends Activity {
    public static final String SETTINGS_ERL_ID = "SPAR_ERL_ID";
    public static final String SETTINGS_API_KEY = "SPAR_API_KEY";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onCheckboxThrottleClicked(View view) {
    }

    public void onOkPressed(View view) {
        Intent output = new Intent();
        output.putExtra(SETTINGS_ERL_ID, "todo");
        output.putExtra(SETTINGS_API_KEY, "todo");
        setResult(RESULT_OK, output);
        finish();
    }
}