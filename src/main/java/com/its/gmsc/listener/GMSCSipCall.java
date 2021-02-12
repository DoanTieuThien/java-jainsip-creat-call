package com.its.gmsc.listener;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.UUID;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.InvalidArgumentException;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import com.its.gmsc.auth.AccountManagerImpl;

import gov.nist.javax.sip.SipStackExt;
import gov.nist.javax.sip.clientauthutils.AuthenticationHelper;

public class GMSCSipCall {
	private String callId = "";
	private AddressFactory addressFactory = null;
	private MessageFactory messageFactory = null;
	private HeaderFactory headerFactory = null;
	private String callingNumber = "";
	private String callingPassword = "";
	private String calledNumber = "";
	private String sipServerAddress = "";
	private ContactHeader contactHeader = null;
	private ClientTransaction inviteTid = null;
	private GMSCListener sipListener = null;

	public GMSCSipCall(GMSCListener listener) throws Exception {
		this.callId = UUID.randomUUID().toString();
		this.addressFactory = listener.getSipFactory().createAddressFactory();
		this.addressFactory = listener.getSipFactory().createAddressFactory();
		this.messageFactory = listener.getSipFactory().createMessageFactory();
		this.headerFactory = listener.getSipFactory().createHeaderFactory();
		this.sipListener = listener;
	}

	public ClientTransaction getClientTransaction() {
		return this.inviteTid;
	}

	public String getCallID() {
		return this.callId;
	}

	public AddressFactory getAddressFactory() {
		return addressFactory;
	}

	public void setAddressFactory(AddressFactory addressFactory) {
		this.addressFactory = addressFactory;
	}

	public MessageFactory getMessageFactory() {
		return messageFactory;
	}

	public void setMessageFactory(MessageFactory messageFactory) {
		this.messageFactory = messageFactory;
	}

	public HeaderFactory getHeaderFactory() {
		return headerFactory;
	}

	public void setHeaderFactory(HeaderFactory headerFactory) {
		this.headerFactory = headerFactory;
	}

	public String getCallingNumber() {
		return callingNumber;
	}

	public void setCallingNumber(String callingNumber) {
		this.callingNumber = callingNumber;
	}

	public String getCallingPassword() {
		return callingPassword;
	}

	public void setCallingPassword(String callingPassword) {
		this.callingPassword = callingPassword;
	}

	public String getCalledNumber() {
		return calledNumber;
	}

	public void setCalledNumber(String calledNumber) {
		this.calledNumber = calledNumber;
	}

	public void makeCall(long sequence, String sipAddress, String transport, int udpPort, String myLocalAddress,
			String callingNumber, String callingPassword, String calledNumber) throws Exception {
		try {
			this.sipServerAddress = sipAddress;
			this.callingNumber = callingNumber;
			this.callingPassword = callingPassword;
			this.calledNumber = calledNumber;
			
			this.callId = String.valueOf(sequence);
			this.sipListener.addCall(this.callId, this);
			Request request = createInvite(sequence, sipAddress, transport, udpPort, myLocalAddress);
			this.inviteTid = this.sipListener.getSipProvider().getNewClientTransaction(request);
			this.inviteTid.sendRequest();
		} catch (Exception exp) {
			this.sipListener.removeCall(this.callId);
			throw exp;
		}

	}

	public Request createInvite(long sequence, String sipAddress, String transport, int udpPort, String myLocalAddress)
			throws ParseException, InvalidArgumentException {
		String toDisplayName = this.calledNumber;

		// create >From Header
		SipURI fromAddress = addressFactory.createSipURI(this.callingNumber, sipAddress);

		Address fromNameAddress = addressFactory.createAddress(fromAddress);
		fromNameAddress.setDisplayName(this.callingNumber);
		FromHeader fromHeader = headerFactory.createFromHeader(fromNameAddress, "12345");

		// create To Header
		SipURI toAddress = addressFactory.createSipURI(this.calledNumber, sipAddress);
		Address toNameAddress = addressFactory.createAddress(toAddress);
		toNameAddress.setDisplayName(toDisplayName);
		ToHeader toHeader = headerFactory.createToHeader(toNameAddress, null);

		// create Request URI
		SipURI requestURI = addressFactory.createSipURI(this.calledNumber, sipAddress + ":" + udpPort);

		// Create ViaHeaders
		ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
		ViaHeader viaHeader = headerFactory.createViaHeader(myLocalAddress,
				this.sipListener.getSipProvider().getListeningPoint(transport).getPort(), transport, null);
		// add via headers
		viaHeaders.add(viaHeader);

		// Create ContentTypeHeader
		ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");

		// Create a new CallId header
		CallIdHeader callIdHeader = this.headerFactory.createCallIdHeader(String.valueOf(sequence));
		if (callId.trim().length() > 0)
			callIdHeader.setCallId(callId);

		// Create a new Cseq header
		CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(sequence, Request.INVITE);

		// Create a new MaxForwardsHeader
		MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

		// Create the request.
		Request request = messageFactory.createRequest(requestURI, Request.INVITE, callIdHeader, cSeqHeader, fromHeader,
				toHeader, viaHeaders, maxForwards);

		// Create contact headers
		SipURI contactUrl = addressFactory.createSipURI(this.callingNumber, myLocalAddress);
		contactUrl.setPort(this.sipListener.getUdpListeningPoint().getPort());

		// Create the contact name address.
		SipURI contactURI = addressFactory.createSipURI(this.callingNumber, myLocalAddress);
		contactURI.setPort(this.sipListener.getSipProvider().getListeningPoint(transport).getPort());

		Address contactAddress = addressFactory.createAddress(contactURI);

		// Add the contact address.
		contactAddress.setDisplayName(this.callingNumber);

		contactHeader = headerFactory.createContactHeader(contactAddress);
		request.addHeader(contactHeader);

		String sdpData = "v=0\n" + "o=102 3841 3240 IN IP4 " + myLocalAddress + "\n" + "s=Talk\n" + "c=IN IP4 "
				+ myLocalAddress + "\n" + "t=0 0\n"
				+ "a=rtcp-xr:rcvr-rtt=all:10000 stat-summary=loss,dup,jitt,TTL voip-metrics\n"
				+ "m=audio 7079 RTP/AVP 96 97 98 0 8 18 101 99 100\n" + "a=rtpmap:96 opus/48000/2\n"
				+ "a=fmtp:96 useinbandfec=1\n" + "a=rtpmap:97 speex/16000\n" + "a=fmtp:97 vbr=on\n"
				+ "a=rtpmap:98 speex/8000\n" + "a=fmtp:98 vbr=on\n" + "a=fmtp:18 annexb=yes\n"
				+ "a=rtpmap:101 telephone-event/48000\n" + "a=rtpmap:99 telephone-event/16000\n"
				+ "a=rtpmap:100 telephone-event/8000\n" + "a=rtcp-fb:* trr-int 1000\n" + "a=rtcp-fb:* ccm tmmbr";
		byte[] contents = sdpData.getBytes();
		request.setContent(contents, contentTypeHeader);
		return request;
	}

	/**
	 * 
	 * @param response
	 * @param tid
	 */
	public void handleAuthentication(Response response, ClientTransaction tid) {
		try {
			AuthenticationHelper authenticationHelper = ((SipStackExt) this.sipListener.getSipStack())
					.getAuthenticationHelper(
							new AccountManagerImpl(this.callingNumber, this.callingPassword, this.sipServerAddress),
							headerFactory);
			inviteTid = authenticationHelper.handleChallenge(response, tid, this.sipListener.getSipProvider(), 5);
			inviteTid.sendRequest();
		} catch (Exception exp) {
			exp.printStackTrace();
		}
	}

	public void handleInviteOK(long callId) {
		try {
			Dialog dialog = inviteTid.getDialog();
			Request ackRequest = dialog.createAck(callId);
			dialog.sendAck(ackRequest);
		} catch (Exception exp) {
			exp.printStackTrace();
		}
	}

	public void handleStackResponse(Response response, CSeqHeader cSeq, Dialog dialog) {
		switch (response.getStatusCode()) {
		case Response.OK:
			switch (cSeq.getMethod()) {
			case Request.INVITE:
				break;
			case Request.INFO:
				break;
			case Request.BYE:
				break;
			}
			break;
		case Response.TRYING:
			break;
		case Response.RINGING:
			break;
		case Response.ACCEPTED:
			break;
		case Response.DECLINE:
			break;
		case Response.REQUEST_TERMINATED:
			break;
		}
	}
}
