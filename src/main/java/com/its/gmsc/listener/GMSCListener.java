package com.its.gmsc.listener;

import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.sip.ClientTransaction;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.ListeningPoint;
import javax.sip.PeerUnavailableException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

public class GMSCListener implements SipListener {
	private SipProvider sipProvider;
	private SipStack sipStack;
	private ListeningPoint udpListeningPoint;
	private SipFactory sipFactory = null;

	private ConcurrentHashMap<String, GMSCSipCall> calls = null;

	public GMSCListener() {
		this.calls = new ConcurrentHashMap<String, GMSCSipCall>();
	}

	public void addCall(String callId, GMSCSipCall call) {
		this.calls.put(callId, call);
	}

	public void removeCall(String callId) {
		this.calls.remove(callId);
	}

	public SipProvider getSipProvider() {
		return sipProvider;
	}

	public SipFactory getSipFactory() {
		return sipFactory;
	}

	public SipStack getSipStack() {
		return sipStack;
	}

	public ListeningPoint getUdpListeningPoint() {
		return udpListeningPoint;
	}

	public void init(String sipServer, int sipPort, String sipTransport, String mylocalAddress, int mylocalPort,
			String mylocalTransport) throws Exception {
		sipStack = null;
		sipFactory = SipFactory.getInstance();
		sipFactory.setPathName("gov.nist");
		String peerHostPort = sipServer + ":" + sipPort;
		Properties properties = new Properties();
		properties.setProperty("javax.sip.OUTBOUND_PROXY", peerHostPort + "/" + sipTransport);
		properties.setProperty("javax.sip.STACK_NAME", "ITSAuth");
		properties.setProperty("gov.nist.javax.sip.MAX_MESSAGE_SIZE", "1048576");
		properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "ItSAuthdebug.txt");
		properties.setProperty("gov.nist.javax.sip.SERVER_LOG", "ItSAuthlog.txt");
		properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");
		properties.setProperty("gov.nist.javax.sip.CACHE_CLIENT_CONNECTIONS", "false");

		try {
			// Create SipStack object
			sipStack = sipFactory.createSipStack(properties);
			System.out.println("createSipStack " + sipStack);
		} catch (PeerUnavailableException e) {
			throw e;
		}
		try {
			udpListeningPoint = sipStack.createListeningPoint(mylocalAddress, mylocalPort,
					mylocalTransport);
			sipProvider = sipStack.createSipProvider(udpListeningPoint);
			sipProvider.addSipListener(this);
		} catch (Exception e) {
			throw e;
		}
	}

	@Override
	public void processDialogTerminated(DialogTerminatedEvent arg0) {

	}

	@Override
	public void processIOException(IOExceptionEvent arg0) {

	}

	@Override
	public void processRequest(RequestEvent requestReceivedEvent) {
		Request request = requestReceivedEvent.getRequest();
		ServerTransaction serverTransactionId = requestReceivedEvent.getServerTransaction();

		System.out.println("Request " + request.getMethod() + " received at " + sipStack.getStackName()
				+ " with server transaction id " + serverTransactionId);
		if (request.getMethod().equals(Request.BYE))
			processBye(request, serverTransactionId);
	}

	public void processBye(Request request, ServerTransaction serverTransactionId) {
		try {
			System.out.println("shootist:  got a bye .");
			if (serverTransactionId == null) {
				System.out.println("shootist:  null TID.");
				return;
			}
			CallIdHeader cseqHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
			GMSCSipCall sipCall = this.calls.get(String.valueOf(cseqHeader.getCallId()));
			Response response = sipCall.getMessageFactory().createResponse(200, request);
			serverTransactionId.sendResponse(response);
			System.out.println("shootist:  Sending OK.");
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void processResponse(ResponseEvent responseReceivedEvent) {
		System.out.println("Got a response");
		Response response = (Response) responseReceivedEvent.getResponse();
		int responseStatusCode = response.getStatusCode();
		ClientTransaction tid = responseReceivedEvent.getClientTransaction();
		CSeqHeader cSeq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
		long seqNumber = cSeq.getSeqNumber();

		CallIdHeader cseqHeader = (CallIdHeader) response.getHeader(CallIdHeader.NAME);
		String callId = cseqHeader.getCallId();
		GMSCSipCall sipCall = this.calls.get(String.valueOf(cseqHeader.getCallId()));

		System.out.println("res: " + response);
		switch (responseStatusCode) {
		case Response.OK:
			switch (cSeq.getMethod()) {
			case Request.INVITE:
				System.out.println("RESPONSE 200OK FOR INVITE RECIEVED \n" + response);
				if (sipCall != null) {
					sipCall.handleInviteOK(seqNumber);
				}
				break;
			case Request.OPTIONS:
				break;
			case Request.INFO:
				if (sipCall != null) {
					sipCall.handleStackResponse(response, cSeq, tid.getDialog());

				}
				break;
			case Request.BYE:
				if (sipCall != null) {
					sipCall.handleStackResponse(response, cSeq, tid.getDialog());
				}
				break;
			case Request.CANCEL:
				if (sipCall != null) {
					this.calls.remove(callId);
				}
				break;
			}
			break;
		case Response.TRYING:
			if (sipCall != null) {
				sipCall.handleStackResponse(response, cSeq, tid.getDialog());
			}
			break;
		case Response.RINGING:
			if (sipCall != null) {
				sipCall.handleStackResponse(response, cSeq, tid.getDialog());
			}
			break;
		case Response.BUSY_HERE:
			System.out.println("RESPONSE BUSY HERE RECIEVED \n" + response);
			break;
		case Response.DECLINE:
			System.out.println("RESPONSE DECLINE RECIEVED -> " + response);
			break;
		case Response.REQUEST_TERMINATED:
			System.out.println("RESPONSE REQUEST TERMINATED RECIEVED \n" + response);
			break;
		case Response.UNAUTHORIZED:
			System.out.println("RESPONSE REQUEST TERMINATED RECIEVED \n" + response);
			if (sipCall != null) {
				sipCall.handleAuthentication(response, tid);
			}
			break;
		}
	}

	@Override
	public void processTimeout(TimeoutEvent timeoutEvent) {
		System.out.println("Timeout connection " + timeoutEvent.getClientTransaction().toString());
	}

	@Override
	public void processTransactionTerminated(TransactionTerminatedEvent event) {
		System.out.println("Terminate connection " + event.getClientTransaction().toString());
	}
}
