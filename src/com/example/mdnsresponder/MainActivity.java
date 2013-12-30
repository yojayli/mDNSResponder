package com.example.mdnsresponder;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.apple.dnssd.BrowseListener;
import com.apple.dnssd.DNSSD;
import com.apple.dnssd.DNSSDException;
import com.apple.dnssd.DNSSDService;
import com.apple.dnssd.QueryListener;
import com.apple.dnssd.ResolveListener;
import com.apple.dnssd.TXTRecord;

import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.Menu;
import android.widget.TextView;

public class MainActivity extends Activity {

	final static String TAG = MainActivity.class.getSimpleName();
	Context mContext;
	MulticastLock lock;

	Browse mBrowse;
	TextView mTextView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		mTextView = (TextView) findViewById(R.id.textView_log);
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

		// Log.d(TAG, "getNameForIfIndex "+DNSSD.getNameForIfIndex(2));
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

	public synchronized void append(final String s) {
		Log.v(TAG, "append-->" + s);
		runOnUiThread(new Runnable() {
			public void run() {
				mTextView.setText(mTextView.getText() + "\n" + s);
			}
		});
	}

	protected void ready() {
		mBrowse = new Browse("_airdrop._tcp");
	}

	class Browse implements BrowseListener {

		String serviceType;
		DNSSDService mDNSSDService;
		int flags;
		int ifIndex;
		String serviceName;
		String regType;
		String domain;
		boolean isRelease = false;

		Set<String> resolveSet;

		public Browse(String serviceType) {
			this.serviceType = serviceType;
			resolveSet = Collections.synchronizedSet(new HashSet<String>());
			operate();
		}

		public void release() {
			isRelease = true;
			stop();
			resolveSet.clear();
		}

		public void operate() {
			stop();
			try {
				Log.d(TAG, "browse " + serviceType);
				mDNSSDService = DNSSD.browse(serviceType, this);
			} catch (DNSSDException e) {
				e.printStackTrace();
			}
		}

		public void stop() {
			if (mDNSSDService != null) {
				mDNSSDService.stop();
				mDNSSDService = null;
			}
		}

		public void serviceFound(DNSSDService browser, int flags, int ifIndex,
				String serviceName, String regType, String domain) {
			this.flags = flags;
			this.ifIndex = ifIndex;
			this.serviceName = serviceName;
			this.regType = regType;
			this.domain = domain;
			String s = "Browse found flags:" + String.valueOf(flags)
					+ " ifIndex:" + String.valueOf(ifIndex) + " serviceName:"
					+ serviceName + " regType:" + regType + " domain:" + domain;
			append("");
			Log.d(TAG, s);
			append(s);
			new Resolve(this);
		}

		public void serviceLost(DNSSDService browser, int flags, int ifIndex,
				String serviceName, String regType, String domain) {
			String s = "Browse lost flags:" + String.valueOf(flags)
					+ " ifIndex:" + String.valueOf(ifIndex) + " serviceName:"
					+ serviceName + " regType:" + regType + " domain:" + domain;
			Log.d(TAG, s);
		}

		public void operationFailed(DNSSDService service, int errorCode) {
			Log.d(TAG, "operationFailed " + service + "(" + errorCode + ")");
		}

	}

	class Resolve implements ResolveListener {

		int flags;
		int ifIndex;
		String serviceName;
		String regType;
		String domain;

		String fullName;
		String hostName;
		int port;
		TXTRecord txtRecord;

		Browse mBrowse;

		public Resolve(Browse browse) {
			mBrowse = browse;
			this.flags = mBrowse.flags;
			this.ifIndex = mBrowse.ifIndex;
			this.serviceName = mBrowse.serviceName;
			this.regType = mBrowse.regType;
			this.domain = mBrowse.domain;
			operate();
		}

		public Browse getBrowse() {
			return mBrowse;
		}

		public void operate() {
			try {
				DNSSD.resolve(flags, ifIndex, serviceName, regType, domain,
						this);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		@Override
		public void operationFailed(DNSSDService service, int errorCode) {
			Log.d(TAG, "operationFailed " + service + "(" + errorCode + ")");
		}

		public void serviceResolved(DNSSDService resolver, int flags,
				int ifIndex, String fullName, String hostName, int port,
				TXTRecord txtRecord) {
			resolver.stop();
			String s = "serviceResolved flags:" + String.valueOf(flags)
					+ " ifIndex:" + String.valueOf(ifIndex) + " fullName:"
					+ fullName + " hostName:" + hostName + " port:"
					+ String.valueOf(port) + " txt: " + txtRecord;
			Log.d(TAG, s);
			// append("");
			// append(s);
			this.ifIndex = ifIndex;
			this.fullName = fullName;
			this.hostName = hostName;
			this.port = port;
			this.txtRecord = txtRecord;

			new Query(this);

			// try {
			// InetAddress address = getInetAddress(hostName);
			// doResolved(address, this);
			// } catch (Exception e) {
			// e.printStackTrace();
			// new Query(this);
			//
			// }
		}

		public void doResolved(InetAddress address, Resolve resolve) {
			String key = serviceName + address.getHostAddress() + resolve.port;
			if (getBrowse().resolveSet.add(key)) {
				append("");
				append("** " + resolve.fullName.split("\\.")[0] + " "
						+ address.getHostAddress() + " " + resolve.port + "\n"
						+ resolve.txtRecord);
			}
		}

	}

	class Query implements QueryListener {

		Resolve mResolve;
		DNSSDService mDNSSDService;

		// Thread timeoutThread;

		public Query(Resolve resolve) {
			mResolve = resolve;
			operate();
		}

		public void operate() {
			try {
				mDNSSDService = DNSSD.queryRecord(0, mResolve.ifIndex,
						mResolve.hostName, 1, 1, this);
				// timeoutThread = new Thread() {
				// public void run() {
				// try {
				// Thread.sleep(QUERY_TIMEOUT);
				// } catch (InterruptedException e) {
				// return;
				// }
				// mDNSSDService.stop();
				// if (!mResolve.getBrowse().isRelease) {
				// mResolve.getBrowse().operate();
				// }
				// }
				// };
				// timeoutThread.start();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		public void queryAnswered(DNSSDService query, int flags, int ifIndex,
				String fullName, int rrtype, int rrclass, byte[] rdata, int ttl) {
			// timeoutThread.interrupt();
			String s = "Query result flags:" + String.valueOf(flags)
					+ " ifIndex:" + String.valueOf(ifIndex) + " fullName:"
					+ fullName + " rrtype:" + String.valueOf(rrtype)
					+ " rrclass:" + String.valueOf(rrclass) + " ttl:"
					+ String.valueOf(ttl) + " rbyte[" + rdata.length + "]";
			Log.d(TAG, s);
			// append("");
			// append(s);
			query.stop();
			try {
				InetAddress address = InetAddress.getByAddress(rdata);
				mResolve.doResolved(address, mResolve);
			} catch (UnknownHostException e1) {
				e1.printStackTrace();
				mResolve.getBrowse().operate();
			}
		}

		@Override
		public void operationFailed(DNSSDService service, int errorCode) {
			Log.d(TAG, "operationFailed " + service + "(" + errorCode + ")");
		}
	}
}
