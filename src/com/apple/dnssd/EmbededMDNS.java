package com.apple.dnssd;

import android.util.Log;

public class EmbededMDNS {

	final static String TAG = EmbededMDNS.class.getSimpleName();

	public static void init() {
		DNSSD.getInstance();
		new Thread() {
			public void run() {
				Init();
			}
		}.start();
		while (State() == 0) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public static void exit() {
		DNSSD.getInstance();
		Exit();
	}

	protected static native int Init();

	protected static native void Exit();

	protected static native int State();
}
