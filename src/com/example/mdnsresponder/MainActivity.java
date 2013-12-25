package com.example.mdnsresponder;

import com.apple.dnssd.BrowseListener;
import com.apple.dnssd.DNSSD;
import com.apple.dnssd.DNSSDException;
import com.apple.dnssd.DNSSDService;

import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.Menu;

public class MainActivity extends Activity {

	final static String TAG = MainActivity.class.getSimpleName();
	Context mContext;
	MulticastLock lock;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		mContext = this;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	protected void onResume() {
		super.onResume();
		acquireLock();
		new Thread() {
			public void run() {
				ready();
			}
		}.start();
	}

	@Override
	protected void onPause() {
		super.onPause();
		releaseLock();
		finish();
	}

	public void acquireLock() {
		WifiManager mWifiManager = (android.net.wifi.WifiManager) mContext
				.getSystemService(android.content.Context.WIFI_SERVICE);
		lock = mWifiManager.createMulticastLock("MDNS");
		lock.setReferenceCounted(true);
		lock.acquire();
	}

	public void releaseLock() {
		if (lock != null) {
			lock.release();
			lock = null;
		}
	}

	protected void ready() {
		browse("_afpovertcp._tcp");
	}

	public void browse(final String type) {
		try {
			final DNSSDService mDNSSDService = DNSSD.browse(type,
					new BrowseListener() {

						@Override
						public void operationFailed(DNSSDService service,
								int errorCode) {
							Log.d(TAG, "operationFailed " + service + "("
									+ errorCode + ")");
						}

						@Override
						public void serviceFound(DNSSDService browser,
								int flags, int ifIndex, String serviceName,
								String regType, String domain) {
							String s = "Browse found flags:"
									+ String.valueOf(flags) + " ifIndex:"
									+ String.valueOf(ifIndex) + " serviceName:"
									+ serviceName + " regType:" + regType
									+ " domain:" + domain;
							Log.d(TAG, s);

						}

						@Override
						public void serviceLost(DNSSDService browser,
								int flags, int ifIndex, String serviceName,
								String regType, String domain) {
							String s = "Browse lost flags:"
									+ String.valueOf(flags) + " ifIndex:"
									+ String.valueOf(ifIndex) + " serviceName:"
									+ serviceName + " regType:" + regType
									+ " domain:" + domain;
							Log.d(TAG, s);
						}
					});
			Log.d(TAG, "mDNSSDService browse " + type);
			new Thread() {
				public void run() {
					try {
						Thread.sleep(30000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					Log.d(TAG, "mDNSSDService browse stop " + type);
					mDNSSDService.stop();
				}
			}.start();
		} catch (DNSSDException e) {
			e.printStackTrace();
		}
	}
}
