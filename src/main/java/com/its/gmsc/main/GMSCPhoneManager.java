package com.its.gmsc.main;

import com.its.gmsc.listener.GMSCListener;
import com.its.gmsc.listener.GMSCSipCall;

/**
 * @author itshare
 *
 */
public class GMSCPhoneManager {
	public static void main(String[] args) {
		String sipServerAddress = "192.168.1.247";
		int sipPort = 5060;
		String sipTransport = "udp";
		String mylocalAddress = "192.168.1.251";
		int mylocalPort = 8000;
		String mylocalTransport = "udp";
		GMSCListener listener = new GMSCListener();
		try {
			listener.init(sipServerAddress, sipPort, sipTransport, mylocalAddress, mylocalPort, mylocalTransport);
			GMSCSipCall call = new GMSCSipCall(listener);
			call.makeCall(1, sipServerAddress, sipTransport, sipPort, mylocalAddress, "101", "1111", "102");
		} catch (Exception exp) {
			exp.printStackTrace();
			System.exit(0);
		}
	}
}
