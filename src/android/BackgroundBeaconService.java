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

import org.altbeacon.beacon.*;
import org.altbeacon.beacon.powersave.BackgroundPowerSaver;
import org.altbeacon.beacon.service.BeaconService;
import org.altbeacon.beacon.startup.BootstrapNotifier;
import org.altbeacon.beacon.startup.RegionBootstrap;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by Tom on 01/06/2015.
 */
public class BackgroundBeaconService extends Service implements BootstrapNotifier {
    public static final String TAG = "com.unarin.cordova.beacon";
    private boolean debugEnabled = false;

	public BackgroundBeaconService() {
		super();
	}

	private BackgroundPowerSaver backgroundPowerSaver;
	private BeaconManager iBeaconManager;
	private RegionBootstrap regionBootstrap;

	private MonitorNotifier monitorNotifier;
	
	private SharedPreferences preferences;

	private boolean inRegion;
	private boolean wasInRegionSet;
	
    // Binder given to clients
    private final IBinder mBinder = new BackgroundBeaconServiceBinder();	
	
    private final int NOTIFICATION_ID = 1;	
	
	private NotificationOptions notificationOptions;

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class BackgroundBeaconServiceBinder extends Binder {
        public BackgroundBeaconService getService() {
            // Return this instance of BackgroundBeaconService so clients can call public methods
            return BackgroundBeaconService.this;
        }
    }	

	public void onCreate() {
		debugLog("Creating BackgroundBeaconService.");
		super.onCreate();
		iBeaconManager = BeaconManager.getInstanceForApplication(this);
		
		Context ctx = getApplicationContext();
		preferences = PreferenceManager.getDefaultSharedPreferences(ctx);
		if(preferences != null) {
			debugLog("SharedPreferences were loaded");
		}
		
		iBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));
		
		iBeaconManager.setBackgroundBetweenScanPeriod(45000l);
		iBeaconManager.setBackgroundScanPeriod(8000l);
		setBackgroundMode(true);
		
		// Simply constructing this class and holding a reference to it
		// enables auto battery saving of about 60%
		backgroundPowerSaver = new BackgroundPowerSaver(this);

		if(debugEnabled) {
			iBeaconManager.setDebug(true);
		}
		
        wasInRegionSet = preferences.getBoolean("wasInRegion", false);
		debugLog("wasInRegionSet was set to " + wasInRegionSet);

		// We read the preference, set that back to false now
		setWasInRegionPreference(false);
		
		notificationOptions = new NotificationOptions(ctx);
		
		// We should set this dynamically
		Region region = new Region("backgroundRegion", Identifier.parse("E4007AB3-EFCA-4C89-A106-D4C4EC03DBD2"), null, null);
		regionBootstrap = new RegionBootstrap(this, region);
		debugLog("Created RegionBootstrap in BackgroundBeaconService.");
	}

	public void onDestroy(){
		debugLog("Destroying BackgroundBeaconService");
		
		// The service was forcibly killed. We should not store that we were in a region.
		setWasInRegionPreference(false);
	}

	@Override
	public void didEnterRegion(Region region) {
		debugLog("BackgroundBeaconService.didEnterRegion called!");

		inRegion = true;

		if(monitorNotifier != null) {
			monitorNotifier.didEnterRegion(region);
		}
		else if(!wasInRegionSet){
			showNotification();
		}
		
		// we should check if this is the correct region...
		wasInRegionSet = false;
	}

	@Override
	public void didExitRegion(Region region) {
		debugLog("BackgroundBeaconService.didExitRegion called!");
		
		inRegion = false;
		
		// Cancel the notification once you exit
		cancelNotification();
		
		if(monitorNotifier != null) {
			monitorNotifier.didExitRegion(region);
		}
		
		// we should check if this is the correct region...
		wasInRegionSet = false; // we reset this so that we can trigger the notification if the user has already left the area
	}

	@Override
	public void didDetermineStateForRegion(int state, Region region) {
		debugLog("BackgroundBeaconService.didDetermineStateForRegion called!");
			
		if(monitorNotifier != null) {
			monitorNotifier.didDetermineStateForRegion(state, region);
		}
	}

	public void requestStateForRegion(Region region) {
		debugLog("Region State was requested!");
			
		didDetermineStateForRegion(inRegion ? MonitorNotifier.INSIDE : MonitorNotifier.OUTSIDE, region);
	}
	
	public void setMonitorNotifier(MonitorNotifier monitorNotifier) {
		this.monitorNotifier = monitorNotifier;
	}

	public void setBackgroundMode(boolean backgroundMode) {
		debugLog("Setting background mode to: " + backgroundMode);
		iBeaconManager.setBackgroundMode(backgroundMode);
	}

	public void setNotificationOptions(JSONObject options) {
		debugLog("Setting notificationOptions to" + options.toString());
		this.notificationOptions.parseAndSave(options);
	}

	@Override
	public Context getApplicationContext() {
		return this.getApplication().getApplicationContext();
	}

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
		debugLog("BackgroundBeaconService.restarted!");
        return Service.START_STICKY;
    }
	
    @Override
    public IBinder onBind(Intent intent) {
		setBackgroundMode(false);
		cancelNotification();
		
        return mBinder;
    }
	
    @Override
    public boolean onUnbind(Intent intent) {      
		monitorNotifier = null;
		setBackgroundMode(true);
		setWasInRegionPreference(inRegion);
		
        return false;
    }

	private void setWasInRegionPreference(boolean wasInRegion) {
		// We need an Editor object to make preference changes.
		// All objects are from android.context.Context
		SharedPreferences.Editor editor = preferences.edit();
		editor.putBoolean("wasInRegion", wasInRegion);
		editor.commit();
		
		debugLog("Setting wasInRegion to: " + wasInRegion);
    }

    /**
     * Notification manager for the application.
     */
    private NotificationManager getNotificationManager (Context context) {
        return (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
    }
	
	private void showNotification() {
	
		// prepare intent which is triggered if the notification is selected
        Context context = getApplicationContext();
        String pkgName  = context.getPackageName();

        Intent intent = context
                .getPackageManager()
                .getLaunchIntentForPackage(pkgName);

        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
	
		PendingIntent pIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

		String contentTitle = notificationOptions.getTitle();
		String contentText = notificationOptions.getText();

        Notification.BigTextStyle style = new Notification.BigTextStyle()
                .bigText(contentText);	
				
		// build notification
		// the addAction re-use the same intent to keep the example short
		// We need to be using the translated versions of these sentences!
		Notification.Builder builder = new Notification.Builder(context)
				.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
				.setContentTitle(contentTitle)
				.setContentText(contentText)
				.setSmallIcon(getResIdForDrawable(notificationOptions.getSmallIcon()))
				.setContentIntent(pIntent)
				.setTicker(contentText)
				.setAutoCancel(notificationOptions.isAutoClear())
				.setOngoing(notificationOptions.isOngoing())
				.setStyle(style)
				.setLights(getColor(notificationOptions.getLedColor()), notificationOptions.getLedOnMs(), notificationOptions.getLedOffMs())
				.setVisibility(notificationOptions.getVisibility());
		
		String soundPath = notificationOptions.getSoundPath();
		
		if(soundPath != null && !soundPath.isEmpty()) {
			Uri soundUri = getSoundUri("android.resource://" + pkgName + '/' +soundPath);
			
			debugLog(soundPath);		
			
			if (soundUri != null) {
				
				debugLog(soundUri.toString());
				
				builder.setSound(soundUri);
				builder.setDefaults(Notification.DEFAULT_VIBRATE);
			}
		}
				
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			builder.setColor(getColor(notificationOptions.getColor()));
		}
		else {
			builder.setLargeIcon(getIconFromDrawable("icon"));
		}
				
		Notification n = builder.build();

		getNotificationManager(context).notify(notificationOptions.getId(), n); 
		
		debugLog("Notifying the user");
	}
	
	private void cancelNotification() {
		debugLog("Cancelling notifications");
		
        Context context = getApplicationContext();
		getNotificationManager(context).cancel(NOTIFICATION_ID); 
	}

	// Borrowed from the local-notification plugin
    // Later on we will bring in the AssetUtil into this service
    /**
     * Convert drawable resource to bitmap.
     *
     * @param drawable
     *      Drawable resource name
     */
    Bitmap getIconFromDrawable (String drawable) {
        Resources res = getResources();
		
        int iconId = getResIdForDrawable(drawable);

        if (iconId == 0) {
            iconId = android.R.drawable.ic_menu_info_details;
        }

        return BitmapFactory.decodeResource(res, iconId);
    }	
	
    /**
     * Small icon resource ID for the local notification.
     */
    public int getSmallIcon (String drawable) {
        int resId = getResIdForDrawable(drawable);

        if (resId == 0) {
            resId = android.R.drawable.screen_background_dark;
        }

        return resId;
    }	
	
    /**
     * Resource ID for drawable.
     *
     * @param resPath
     *      Resource path as string
     */
    int getResIdForDrawable(String resPath) {
        int resId = getResIdForDrawable(getPackageName(), resPath);

        if (resId == 0) {
            resId = getResIdForDrawable("android", resPath);
        }

        return resId;
    }
	
    /**
     * Resource ID for drawable.
     *
     * @param clsName
     *      Relative package or global android name space
     * @param resPath
     *      Resource path as string
     */
    int getResIdForDrawable(String clsName, String resPath) {
        String drawable = extractResourceName(resPath);
        int resId = 0;

        try {
            Class<?> cls  = Class.forName(clsName + ".R$drawable");

            resId = (Integer) cls.getDeclaredField(drawable).get(Integer.class);
        } catch (Exception ignore) {}

        return resId;
    }
	
    /**
     * Extract name of drawable resource from path.
     *
     * @param resPath
     *      Resource path as string
     */
    private String extractResourceName (String resPath) {
        String drawable = resPath;

        if (drawable.contains("/")) {
            drawable = drawable.substring(drawable.lastIndexOf('/') + 1);
        }

        if (resPath.contains(".")) {
            drawable = drawable.substring(0, drawable.lastIndexOf('.'));
        }

        return drawable;
    }

    public int getColor(String hex) {
        int aRGB   = Integer.parseInt(hex,16);

        aRGB += 0xFF000000;

        return aRGB;
    }
	
    /**
     * Sound file path for the local notification.
     */
    public Uri getSoundUri(String soundUri) {
        Uri uri = null;

        try{
            uri = Uri.parse(soundUri);
        } catch (Exception e){
            e.printStackTrace();
        }

        return uri;
    }
	
	private void debugLog(String message) {
		if (debugEnabled) {
			Log.d(TAG, "BACKGROUND: " + message);
		}
	}
	
	private void debugWarn(String message) {
		if (debugEnabled) {
			Log.w(TAG, "BACKGROUND: " + message);
		}
	}
}
