/*

Copyright (C) 2014 - Gareth Edwards / Rapid Information Systems

gareth.edwards@rapid-is.co.uk


This file is part of RapidSOA.

RapidSOA is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version. The terms require you to include
the original copyright, and the license notice in all redistributions.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
in a file named "COPYING".  If not, see <http://www.gnu.org/licenses/>.

 */

package com.rapid.soa;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.dom.DOMSource;

import org.apache.log4j.Logger;

import org.w3c.dom.Document;

import com.rapid.soa.WSFactory;

public class WSGateway extends HttpServlet {
	
	private static final long serialVersionUID = 1L;
	
	private static Logger _logger; 
	private static WSFactory _wsFactory;
	
	public Logger getLogger() {	
		// ensure the logger is initialised
		if (_logger == null) _logger = Logger.getLogger(WSGateway.class);
		// return it
		return _logger; 	
	}
	
	public WSFactory getWSFactory() { 
		// initialise and store a local reference to the WS Factory
		if (_wsFactory == null)	_wsFactory = WSFactory.getWSFactory();
		// ensure the logger is initialised
		getLogger();
		// return it
		return _wsFactory; 
	}
					
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
				
		// keep .net happy as all it can deal with is UTF-8 (default is ISO-8859-1, also changed server.xml)
		response.setCharacterEncoding("UTF-8");
		
		String headers = "";
		
		if (_logger.isTraceEnabled()) {
			
			Enumeration headerNames = request.getHeaderNames();
	        while (headerNames.hasMoreElements()) {
	            String headerName = (String) headerNames.nextElement();
	            headers += "\n" + headerName + " : " + request.getHeader(headerName);
	        }
	        	        			
		}
		
		_logger.debug("GET request from " + request.getLocalAddr() + " " + request.getQueryString() + headers);
												
		try {
			
			//if (request.getParameter("refresh") != null) loadOperations();
			
			if (request.getParameter("wsdl") != null) {
				
				response.addHeader("expires", "-1");
				response.addHeader("Pragma", "no-cache");				
				
				PrintWriter out = response.getWriter();
						
				URL url = new URL(request.getScheme(), request.getServerName(), request.getServerPort(), request.getRequestURI());
				
				response.setContentType("text/xml");
				
				Document wsdl = _wsFactory.getWSDL(request.getParameter("wsdl"),url.toString());
				
				DOMSource ds = new DOMSource(wsdl);
				
				out.print(WSFactory.getXMLString(ds));
				
			} else if (request.getParameter("log") != null) {
								
				response.setContentType("text/plain");
				response.addHeader("expires", "-1");
				response.addHeader("Pragma", "no-cache");
								
				byte[] buf = new byte[100];
				int length = 0;
				
				String logPath = System.getProperty("logPath");
				
				if (logPath == null) throw new Exception("Log path has not been set");
				
				FileInputStream fs = new FileInputStream(logPath);
				
		        DataInputStream in = new DataInputStream(fs);

		        ServletOutputStream out = response.getOutputStream();
		        
		        while ((in != null) && ((length = in.read(buf)) != -1)) out.write(buf, 0, length);
		        		        
		        in.close();
		        out.flush();
		        out.close();
							
			} else {
				
				response.addHeader("expires", "-1");
				response.addHeader("Pragma", "no-cache");
				
				PrintWriter out = response.getWriter();
				
				String operationsHTML = "<html><head><title>Rapid SOA</title></head><body><p>WSDLs :</p>";
				
				for (String operation : _wsFactory.getOperations()) {
					
					operationsHTML += "<p><a href='?wsdl=" + operation + "'>" + operation + "</a></p>";
					
				}
				
				out.print(operationsHTML + "<p>&nbsp;</p><p>(<a href='?log'>view recent logs</a>)</p></body>");
					
			}
			
		} catch (Exception e) {
			
			_logger.error("Error creating wsdl", e);
			
			PrintWriter out = response.getWriter();
			
			out.print(e.getMessage());

		}
		
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		// keep .net happy as all it can deal with is UTF-8 (default is ISO-8859-1, also changed server.xml)
		response.setCharacterEncoding("UTF-8");
		
		String headers = "";
		
		if (_logger.isTraceEnabled()) {
			
			Enumeration headerNames = request.getHeaderNames();
	        while (headerNames.hasMoreElements()) {
	            String headerName = (String) headerNames.nextElement();
	            headers += "\n" + headerName + " : " + request.getHeader(headerName);
	        }
	        	        			
		}
		
		_logger.debug("POST request from " + request.getLocalAddr() + " " + request.getQueryString() + headers);
		
		PrintWriter out = response.getWriter();
						
		try {
		
			response.setContentType("text/xml");
			response.addHeader("expires", "-1");
			response.addHeader("Pragma", "no-cache");
			
			MessageFactory messageFactory = MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL);		    
								
		    SOAPMessage soapRequest = messageFactory.createMessage(null, request.getInputStream());		    	
		    
		    String soapOperation = request.getHeader("soapaction").replace("\"", "");
						
			DOMSource responseSource = new DOMSource(_wsFactory.getSOAPResponse(soapOperation, soapRequest, getServletContext()).getSOAPPart().getEnvelope());
					
			out.println(WSFactory.getXMLString(responseSource));
						
		} catch (SOAPException e) {
			
			response.setContentType("text/plain");
			
			out.println("Error" + e.getMessage());
			
			_logger.error(e.getStackTrace());
						
			e.printStackTrace(System.out);
			
		}
		
		out.close();
		
	}

}
