package com.unarin.cordova.beacon;

import android.app.Application;
import android.app.IntentService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import org.altbeacon.beacon.*;
import org.altbeacon.beacon.powersave.BackgroundPowerSaver;
import org.altbeacon.beacon.service.BeaconService;
import org.altbeacon.beacon.startup.BootstrapNotifier;
import org.altbeacon.beacon.startup.RegionBootstrap;

/**
 * Created by Tom on 01/06/2015.
 */
public class BackgroundBeaconService extends Service implements BootstrapNotifier {

	public BackgroundBeaconService() {
		super();
	}

	private BackgroundPowerSaver backgroundPowerSaver;
	private BeaconManager iBeaconManager;
	private RegionBootstrap regionBootstrap;

	private MonitorNotifier monitorNotifier;

    // Binder given to clients
    private final IBinder mBinder = new BackgroundBeaconServiceBinder();	
	
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
		Log.d("com.unarin.cordova.beacon", "BACKGROUND: Creating BackgroundBeaconService.");
		super.onCreate();
		iBeaconManager = BeaconManager.getInstanceForApplication(this);
		
		iBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));
		
		iBeaconManager.setBackgroundBetweenScanPeriod(0l);
		iBeaconManager.setBackgroundScanPeriod(1100l);
		
		// Simply constructing this class and holding a reference to it
		// enables auto battery saving of about 60%
		backgroundPowerSaver = new BackgroundPowerSaver(this);
		
		iBeaconManager.setDebug(true);

		// We should set this dynamically
		Region region = new Region("backgroundRegion", Identifier.parse("5AFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF"), null, null);
		regionBootstrap = new RegionBootstrap(this, region);
		Log.d("com.unarin.cordova.beacon", "BACKGROUND: Created RegionBootstrap in BackgroundBeaconService.");
	}

	public void onDestroy(){
		Log.d("com.unarin.cordova.beacon", "Destroying BackgroundBeaconService");
	}

	@Override
	public void didEnterRegion(Region region) {
		Log.d("com.unarin.cordova.beacon", "BackgroundBeaconService.didEnterRegion called!");
		if(monitorNotifier != null) {
			monitorNotifier.didEnterRegion(region);
		}
	}

	@Override
	public void didExitRegion(Region region) {
		Log.d("com.unarin.cordova.beacon", "BackgroundBeaconService.didExitRegion called!");
		if(monitorNotifier != null) {
			monitorNotifier.didExitRegion(region);
		}
	}

	@Override
	public void didDetermineStateForRegion(int state, Region region) {
		Log.d("com.unarin.cordova.beacon", "BackgroundBeaconService.didDetermineStateForRegion called!");
		if(monitorNotifier != null) {
			monitorNotifier.didDetermineStateForRegion(state, region);
		}
	}
	
	public void setMonitorNotifier(MonitorNotifier monitorNotifier) {
		this.monitorNotifier = monitorNotifier;
	}

	public void setBackgroundMode(boolean backgroundMode) {
		iBeaconManager.setBackgroundMode(backgroundMode);
	}
	
	@Override
	public Context getApplicationContext() {
		return this.getApplication().getApplicationContext();
	}

    @Override
    public IBinder onBind(Intent intent) {
        // A client is binding to the service with bindService()
        return mBinder;
    }
	
    @Override
    public boolean onUnbind(Intent intent) {      
		monitorNotifier = null;
        return false;
    }
}
