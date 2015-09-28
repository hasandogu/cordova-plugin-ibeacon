package com.unarin.cordova.beacon;

import android.app.Application;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.res.Resources;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

public class NotificationOptions
{
	// The original JSON object
    private JSONObject options = new JSONObject();
	
    // Context passed through constructor and used for notification builder.
    private Context context;
	
	private SharedPreferences preferences;
	
    /**
     * Constructor
     *
     * @param context
     *      Application context
     */
    public NotificationOptions(Context context){
    	this.context = context;
		
		preferences = PreferenceManager.getDefaultSharedPreferences(context);
		
		String notificationOptions = preferences.getString("notificationOptions", "");
		
		Log.d("com.unarin.notification", "Notification Options:" + notificationOptions);
		
		if(!notificationOptions.isEmpty()) {
			try {
				options = new JSONObject(notificationOptions);
			}
			catch (JSONException e) {
				e.printStackTrace();
			}
		}
	}
	
    /**
     * Parse and Save given JSON properties for the next time arount
     *
     * @param options
     *      JSON properties
     */
    public NotificationOptions parseAndSave(JSONObject options) {
        this.options = options;
		
		// We need an Editor object to make preference changes.
		// All objects are from android.context.Context
		SharedPreferences.Editor editor = preferences.edit();
		editor.putString("notificationOptions", toString());
		editor.commit();
		
		Log.d("com.unarin.notification", "Save Notification Options" + options.toString());	
		
        return this;
    }
	
    /**
     * ID for the local notification as a number.
     */
    public Integer getId() {
        return options.optInt("id", 0);
    }

	/**
     * Title for the local notification.
     */
    public String getTitle() {
        return options.optString("title", "");
    }	

    /**
     * Text for the local notification.
     */
    public String getText() {
        return options.optString("text", "");
    }

    /**
     * ongoing flag for local notifications.
     */
    public Boolean isOngoing() {
        return options.optBoolean("ongoing", false);
    }

    /**
     * autoClear flag for local notifications.
     */
    public Boolean isAutoClear() {
        return options.optBoolean("autoClear", false);
    }

    /**
     * Text for the local notification.
     */
    public String getSmallIcon() {
        return options.optString("smallIcon", "");
    }

	/**
     * Color for the local notification.
     */
    public String getColor() {
        return options.optString("color", "FFFFFF");
    }
	
    /**
     * Led Color
     */
    public String getLedColor() {
        return options.optString("led", "000000");
    }

    /**
     * Led On Ms
     */
    public int getLedOnMs() {
        return options.optInt("ledOnMs", 500);
    }

	/**
     * Led Off Ms
     */
    public int getLedOffMs() {
        return options.optInt("ledOffMs", 500);
    }

	/**
     * Led Off Ms
     */
    public int getVisibility() {
        return options.optInt("visibility", 0);
    }
	
    /**
     * Text for the local notification.
     */
    public String getSoundPath() {
        return options.optString("soundPath", "");
    }
	
   /**
     * JSON object as string.
     */
    public String toString() {
        return options.toString();
    }	
}
