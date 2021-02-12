package com.its.gmsc.auth;

import javax.sip.ClientTransaction;

import gov.nist.javax.sip.clientauthutils.AccountManager;
import gov.nist.javax.sip.clientauthutils.UserCredentials;

public class AccountManagerImpl implements AccountManager {

	private String userName = "";
	private String password = "";
	private String sipServer = "";

	public AccountManagerImpl(String userName, String password, String sipServer) {
		this.userName = userName;
		this.password = password;
		this.sipServer = sipServer;
	}

	public UserCredentials getCredentials(ClientTransaction challengedTransaction, String realm) {
		return new UserCredentialsImpl(this.userName, this.sipServer, this.password);
	}

}
